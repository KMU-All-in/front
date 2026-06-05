package com.example.allin

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

object ProductParser {
    // [개선] 모바일 브라우저인 것처럼 위장하여 쿠팡 등의 차단 우회
    private const val USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    data class ParsedProduct(
        var name: String? = null,
        var price: Int? = null,
        var imageUrl: String? = null,
        var resolvedUrl: String? = null
    )

    suspend fun parse(url: String, sharedText: String = ""): ParsedProduct {
        val result = ParsedProduct()
        var triedServerParsing = false

        try {
            triedServerParsing = true
            fetchFromServer(url, sharedText)?.let { result.fillMissingWith(it) }
        } catch (e: Exception) {
            Log.e("ProductParser", "Initial server parse error: ${e.message}")
        }
        
        // 1. 서버 분석 결과가 부족하면 클라이언트 사이드 Jsoup 파싱 시도
        try {
            val doc = withContext(Dispatchers.IO) {
                Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Cache-Control", "no-cache")
                    .header("Referer", "https://www.google.com/")
                    .timeout(10000)
                    .followRedirects(true)
                    .get()
            }

            // (1) JSON-LD 파싱
            val jsonLds = doc.select("script[type=application/ld+json]")
            for (jsonLd in jsonLds) {
                try {
                    val data = jsonLd.data().trim()
                    if (data.isEmpty()) continue
                    val json = if (data.startsWith("[")) JSONArray(data).optJSONObject(0) else JSONObject(data)
                        val productData = findProductInJson(json)
                        if (productData != null) {
                            result.name = result.name ?: productData.optString("name").takeIf { it.isNotEmpty() }
                            result.imageUrl = result.imageUrl ?: extractImage(productData.opt("image"))
                            val offers = productData.opt("offers")
                        if (offers is JSONObject) {
                            val p = extractPrice(offers.opt("price"))
                            if (p != null) result.price = result.price ?: p
                        } else if (offers is JSONArray && offers.length() > 0) {
                            val p = extractPrice(offers.getJSONObject(0).opt("price"))
                            if (p != null) result.price = result.price ?: p
                        }
                    }
                } catch (e: Exception) {}
            }

            // (2) Meta Tags (Open Graph, Twitter)
            if (result.name.isNullOrEmpty()) {
                result.name = doc.select("meta[property=og:title]").attr("content").takeIf { it.isNotEmpty() }
                    ?: doc.select("meta[name=twitter:title]").attr("content").takeIf { it.isNotEmpty() }
                    ?: doc.select("meta[name=title]").attr("content").takeIf { it.isNotEmpty() }
            }
            if (result.imageUrl.isNullOrEmpty()) {
                result.imageUrl = doc.select("meta[property=og:image]").attr("content").takeIf { it.isNotEmpty() }
                    ?: doc.select("meta[name=twitter:image]").attr("content").takeIf { it.isNotEmpty() }
            }
            if (result.price == null) {
                val priceSelectors = listOf(
                    "meta[property=product:price:amount]",
                    "meta[property=og:price:amount]",
                    "meta[name=product:price:amount]",
                    "meta[property=product:sale_price:amount]",
                    "meta[name=twitter:data1]"
                )
                for (selector in priceSelectors) {
                    val p = extractPrice(doc.select(selector).attr("content"))
                    if (p != null) {
                        result.price = p
                        break
                    }
                }
            }

            // (3) Fallback: Title Tag 및 Regex
            if (result.name.isNullOrEmpty()) {
                val title = doc.title()
                result.name = if (title.contains("|")) title.split("|")[0].trim()
                else if (title.contains("-")) title.split("-")[0].trim()
                else title.trim()
            }
            if (result.price == null) {
                val text = doc.text()
                val priceRegex = Regex("([0-9,]{3,})원")
                val match = priceRegex.find(text)
                result.price = match?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
            }
            
            // 이미지 절대 경로 변환
            if (!result.imageUrl.isNullOrEmpty() && !result.imageUrl!!.startsWith("http")) {
                result.imageUrl = doc.select("meta[property=og:image]").firstOrNull()?.absUrl("content")
                    ?.takeIf { it.isNotEmpty() }
                    ?: result.imageUrl
            }

        } catch (e: Exception) {
            Log.e("ProductParser", "Jsoup error: ${e.message}")
        }

        // 2. 정보가 부족할 경우 서버 사이드 파싱(AI -> 기존 파서) 시도
        if (result.needsServerParsing() && !triedServerParsing) {
            try {
                val serverResult = fetchFromServer(url, sharedText)
                if (serverResult != null) {
                    result.fillMissingWith(serverResult)
                }
            } catch (e: Exception) {
                Log.e("ProductParser", "Server parse error: ${e.message}")
            }
        }

        return result
    }

    private suspend fun fetchFromServer(url: String, sharedText: String): ParsedProduct? {
        val functions = FirebaseFunctions.getInstance()
        val data = hashMapOf(
            "url" to url,
            "sharedText" to sharedText
        )

        callProductParser(functions, "analyzeProductUrl", data)?.let { parsed ->
            if (!parsed.needsServerParsing()) return parsed
            return callProductParser(functions, "advancedProductParse", data)
                ?.also { it.fillMissingWith(parsed) }
                ?: parsed
        }

        return callProductParser(functions, "advancedProductParse", data)
    }

    private suspend fun callProductParser(
        functions: FirebaseFunctions,
        functionName: String,
        data: HashMap<String, String>
    ): ParsedProduct? {
        return try {
            val result = functions.getHttpsCallable(functionName)
                .call(data)
                .await()
            
            val map = result.getData() as? Map<String, Any>
            if (map?.get("success") == true) {
                val res = map["result"] as? Map<String, Any>
                val rawExtracted = res?.get("rawExtracted") as? Map<String, Any>
                ParsedProduct(
                    name = (res?.get("name") as? String)
                        ?: (res?.get("productName") as? String)
                        ?: (rawExtracted?.get("name") as? String),
                    price = ((res?.get("price") as? Number)
                        ?: (rawExtracted?.get("price") as? Number))?.toInt(),
                    imageUrl = (res?.get("imageUrl") as? String)
                        ?: (rawExtracted?.get("imageUrl") as? String),
                    resolvedUrl = res?.get("resolvedUrl") as? String
                )
            } else null
        } catch (e: Exception) {
            Log.e("ProductParser", "$functionName error: ${e.message}")
            null
        }
    }

    private fun ParsedProduct.needsServerParsing(): Boolean {
        return name.isNullOrBlank() || (price ?: 0) <= 0 || imageUrl.isNullOrBlank()
    }

    private fun ParsedProduct.fillMissingWith(other: ParsedProduct) {
        name = name.takeUnless { it.isNullOrBlank() } ?: other.name
        price = if ((price ?: 0) <= 0) other.price else price
        imageUrl = imageUrl.takeUnless { it.isNullOrBlank() } ?: other.imageUrl
        resolvedUrl = resolvedUrl.takeUnless { it.isNullOrBlank() } ?: other.resolvedUrl
    }

    private fun findProductInJson(json: JSONObject?): JSONObject? {
        if (json == null) return null
        val type = json.optString("@type") ?: json.optString("type")
        if (type == "Product") return json
        
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.opt(key)
            if (value is JSONObject) {
                val result = findProductInJson(value)
                if (result != null) return result
            } else if (value is JSONArray) {
                for (i in 0 until value.length()) {
                    val result = findProductInJson(value.optJSONObject(i))
                    if (result != null) return result
                }
            }
        }
        return null
    }

    private fun extractPrice(value: Any?): Int? {
        if (value == null) return null
        val str = value.toString().replace(Regex("[^0-9]"), "")
        return str.toIntOrNull()
    }

    private fun extractImage(value: Any?): String? {
        return when (value) {
            is String -> value.takeIf { it.isNotEmpty() }
            is JSONArray -> value.optString(0).takeIf { it.isNotEmpty() }
            is JSONObject -> value.optString("url").takeIf { it.isNotEmpty() }
            else -> null
        }
    }
}
