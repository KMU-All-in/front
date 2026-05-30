package com.example.allin

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.allin.data.FakeCartRepository
import com.example.allin.data.FakeProduct
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.allin.worker.FakeCartWorker
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private lateinit var fakeCartRepository: FakeCartRepository
    private var isExpiredCheckDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fakeCartRepository = FakeCartRepository()

        hideSystemBars()
        checkOverlayPermission()

        if (savedInstanceState == null) {
            if (!handleSharedIntent(intent)) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, HomeFragment())
                    .commit()
            }
        }

        setupNavigation()
        setupBackPress()
        scheduleFakeCartExpiryCheck()
        checkFakeCartExpirations()
    }

    private fun handleSharedIntent(intent: Intent?): Boolean {
        if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: 
                            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            
            if (sharedText != null) {
                Log.d("SHARE_DEBUG", "Raw shared text: $sharedText")
                val (productName, price, url) = parseSharedText(sharedText)
                
                val fragment = FakeCartFragment().apply {
                    arguments = Bundle().apply {
                        putString("EXTRA_NAME", productName)
                        putInt("EXTRA_PRICE", price)
                        putString("EXTRA_URL", url)
                        putString("EXTRA_SHARED_TEXT", sharedText)
                        putBoolean("SHOW_ADD_POPUP", true)
                    }
                }
                replaceFragment(fragment)
                return true
            }
        }
        return false
    }

    private fun parseSharedText(text: String): Triple<String, Int, String> {
        // 1. URL 추출 (가장 확실한 패턴)
        val urlPattern = Pattern.compile("(https?://[^\\s]+)", Pattern.CASE_INSENSITIVE)
        val matcher = urlPattern.matcher(text)
        val url = if (matcher.find()) {
            (matcher.group(1) ?: "").trimEnd('.', ',', ')', ']', '}', '"', '\'')
        } else ""

        // 2. 가격 추출 (숫자 + 원)
        val priceRegex = "([0-9,]{2,})\\s*원".toRegex()
        val priceMatch = priceRegex.find(text)
        val price = priceMatch?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0

        // 3. 상품명 추출
        var name = text.replace(url, "").replace(priceMatch?.value ?: "", "").trim()
        
        // 지그재그/무신사 등 특수 문자 정제
        if (name.contains("|")) name = name.split("|")[0].trim()
        if (name.contains("-")) name = name.split("-")[0].trim()
        if (name.contains("\n")) name = name.split("\n")[0].trim()
        
        name = name.replace("[", "").replace("]", "").trim()
        
        if (name.isEmpty() || name.startsWith("http")) name = "상품 정보를 가져오는 중..."

        Log.d("SHARE_DEBUG", "Parsed -> Name: $name, Price: $price, URL: $url")
        return Triple(name, price, url)
    }

    private fun scheduleFakeCartExpiryCheck() {
        NotificationWorkScheduler.syncAll(this)
    }

    private fun checkFakeCartExpirations() {
        lifecycleScope.launch {
            try {
                val products = fakeCartRepository.allProducts.first()
                val nowCal = Calendar.getInstance()
                val expiredProducts = products.filter { 
                    val expiryCal = Calendar.getInstance().apply {
                        timeInMillis = it.addedTime
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        add(Calendar.DAY_OF_YEAR, it.expiryDays)
                    }
                    nowCal.timeInMillis >= expiryCal.timeInMillis
                }
                if (expiredProducts.isNotEmpty() && !isExpiredCheckDone) {
                    showExpiredDialog(expiredProducts)
                    isExpiredCheckDone = true
                }
            } catch (e: Exception) { Log.e("MainActivity", "Expired check failed", e) }
        }
    }

    private fun showExpiredDialog(expiredProducts: List<FakeProduct>) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("숙고 기간 만료 안내")
            .setMessage("날짜가 지난 상품이 ${expiredProducts.size}건 있습니다. 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch { expiredProducts.forEach { fakeCartRepository.delete(it) } }
            }
            .setNegativeButton("유지", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#212121"))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#212121"))
        }
        dialog.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handleSharedIntent(intent)) {
            val target = intent?.getStringExtra("TARGET_FRAGMENT")
            if (target == "BUDGET") replaceFragment(BudgetFragment())
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 1001)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupNavigation() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener { replaceFragment(HomeFragment()) }
        findViewById<LinearLayout>(R.id.navFakeCart).setOnClickListener { replaceFragment(FakeCartFragment()) }
        findViewById<LinearLayout>(R.id.navBudget).setOnClickListener { replaceFragment(BudgetFragment()) }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.findFragmentById(R.id.fragment_container) is HomeFragment) finish()
                else replaceFragment(HomeFragment())
            }
        })
    }

    private fun replaceFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        when {
            currentFragment is HomeFragment && fragment is FakeCartFragment ->
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)

            currentFragment is FakeCartFragment && fragment is HomeFragment ->
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)

            currentFragment is HomeFragment && fragment is BudgetFragment ->
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)

            currentFragment is BudgetFragment && fragment is HomeFragment ->
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)

            currentFragment is BudgetFragment && fragment is FakeCartFragment ->
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)

            currentFragment is FakeCartFragment && fragment is BudgetFragment ->
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)

            else ->
                transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        transaction.replace(R.id.fragment_container, fragment)
        transaction.commit()
    }
}
