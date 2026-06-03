const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const OpenAI = require("openai");

admin.initializeApp();

function getOpenAIClient() {
  if (!process.env.OPENAI_API_KEY) {
    return null;
  }

  return new OpenAI({
    apiKey: process.env.OPENAI_API_KEY
  });
}

const openai = getOpenAIClient();

exports.checkEmailExists = onCall(async (request) => {
  const email = String(request.data?.email || "").trim().toLowerCase();

  if (!email) {
    throw new HttpsError("invalid-argument", "Email is required.");
  }

  try {
    await admin.auth().getUserByEmail(email);
    return { exists: true };
  } catch (error) {
    if (error.code === "auth/user-not-found") {
      return { exists: false };
    }

    console.error("checkEmailExists error:", error);
    throw new HttpsError("internal", "Failed to check email.");
  }
});

const fieldMap = {
  date: ["date", "payment_date", "paymentDate", "used_at", "승인일자", "결제일", "이용일자", "거래일자"],
  amount: ["amount", "price", "payment_amount", "paymentAmount", "이용금액", "결제금액", "거래금액", "금액"],
  storeName: ["storeName", "store_name", "merchant", "place", "title", "가맹점명", "사용처", "결제처", "상호명"],
  category: ["category", "rawCategory", "type", "카테고리", "업종", "분류"],
  paymentMethod: ["paymentMethod", "payment_method", "method", "결제수단", "카드명", "계좌"]
};

function getByFieldName(item, candidates) {
  for (const field of candidates) {
    if (item[field] !== undefined && item[field] !== null && item[field] !== "") {
      return item[field];
    }
  }
  return null;
}

function scoreDate(value) {
  const text = String(value).trim();
  if (/^\d{4}-\d{1,2}-\d{1,2}$/.test(text)) return 95;
  if (/^\d{4}\.\d{1,2}\.\d{1,2}$/.test(text)) return 95;
  if (/^\d{4}\/\d{1,2}\/\d{1,2}$/.test(text)) return 95;
  if (/^\d{8}$/.test(text)) return 85;
  if (/^\d{1,2}월\s?\d{1,2}일$/.test(text)) return 70;
  if (/^\d{1,2}\/\d{1,2}$/.test(text)) return 60;
  return 0;
}

function scoreAmount(value) {
  const text = String(value).trim();
  if (scoreDate(text) >= 60) return 0;
  if (/^[₩￦]\s?\d{1,3}(,\d{3})+$/.test(text)) return 95;
  if (/^\d{1,3}(,\d{3})+원$/.test(text)) return 95;
  if (/^\d+원$/.test(text)) return 90;
  if (/^\d{1,3}(,\d{3})+$/.test(text)) return 85;
  const cleaned = text.replace(/[^0-9-]/g, "");
  if (/^-?\d+$/.test(cleaned)) {
    const amount = Math.abs(Number(cleaned));
    if (amount < 100) return 10;
    if (amount >= 100 && amount < 10000000) return 70;
  }
  return 0;
}

function scoreStoreName(value) {
  const text = String(value).trim();
  if (!text || scoreDate(text) > 0 || scoreAmount(text) >= 70 || /^\d+$/.test(text)) return 0;
  const lower = text.toLowerCase();
  const knownStores = ["gs25", "cu", "세븐일레븐", "이마트24", "스타벅스", "이디야", "메가커피", "투썸"];
  if (knownStores.some((store) => lower.includes(store))) return 95;
  if (/^[가-힣a-zA-Z0-9\s()._-]+$/.test(text) && text.length <= 25) return 75;
  return 40;
}

function normalizeDate(value) {
  const text = String(value).trim();
  if (/^\d{4}[./-]\d{1,2}[./-]\d{1,2}$/.test(text)) {
    const parts = text.split(/[./-]/);
    return `${parts[0]}-${parts[1].padStart(2, "0")}-${parts[2].padStart(2, "0")}`;
  }
  if (/^\d{8}$/.test(text)) return `${text.slice(0, 4)}-${text.slice(4, 6)}-${text.slice(6, 8)}`;
  return text;
}

function normalizeAmount(value) {
  if (typeof value === "number") return value;
  const text = String(value).trim();
  const cleaned = text.replace(/[^0-9-]/g, "");
  return Number(cleaned);
}

const allowedCategories = [
  "식품/음료",
  "패션/의류",
  "뷰티/화장품",
  "전자기기",
  "도서/문구",
  "생활용품",
  "스포츠/레저",
  "기타"
];

const categoryRules = [
  {
    category: "식품/음료",
    confidence: 0.9,
    exactKeywords: [
      "cu"
    ],
    keywords: [
      "gs25",
      "씨유",
      "세븐일레븐",
      "이마트24",
      "카페",
      "커피",
      "스타벅스",
      "이디야",
      "메가커피",
      "메가엠지씨",
      "투썸",
      "빽다방",
      "컴포즈",
      "공차",
      "식당",
      "음식점",
      "분식",
      "김밥",
      "국밥",
      "고기",
      "삼겹살",
      "치킨",
      "피자",
      "버거",
      "맥도날드",
      "버거킹",
      "롯데리아",
      "맘스터치",
      "kfc",
      "노브랜드버거",
      "파리바게뜨",
      "파리바게트",
      "뚜레쥬르",
      "베스킨라빈스",
      "배스킨라빈스",
      "던킨",
      "서브웨이",
      "홍콩반점",
      "본죽",
      "한솥",
      "봉구스",
      "엽떡",
      "두끼",
      "명랑핫도그",
      "배달",
      "배달의민족",
      "요기요",
      "쿠팡이츠",
      "별차이나",
      "중식",
      "중국집",
      "반점",
      "마라",
      "짜장",
      "짬뽕"
    ]
  },
  {
    category: "패션/의류",
    confidence: 0.9,
    keywords: [
      "백화점",
      "쇼핑",
      "쇼핑몰",
      "몰",
      "의류",
      "패션",
      "옷",
      "신발",
      "가방",
      "잡화",
      "무신사",
      "지그재그",
      "에이블리",
      "브랜디",
      "29cm",
      "w컨셉",
      "크림",
      "kream",
      "퀸잇",
      "하이버",
      "유니클로",
      "자라",
      "스파오",
      "탑텐",
      "무탠다드",
      "지오다노",
      "h&m",
      "cos",
      "에잇세컨즈",
      "나이키",
      "아디다스",
      "뉴발란스",
      "abc마트",
      "폴더",
      "슈마커"
    ]
  },
  {
    category: "뷰티/화장품",
    confidence: 0.9,
    keywords: [
      "올리브영",
      "화장품",
      "뷰티",
      "헤어",
      "미용실",
      "네일",
      "피부",
      "올영",
      "컬리뷰티",
      "시코르",
      "chicor",
      "에뛰드",
      "이니스프리",
      "아리따움",
      "미샤",
      "토니모리",
      "마녀공장",
      "닥터지",
      "라운드랩",
      "롬앤",
      "클리오",
      "롭스",
      "랄라블라",
      "무신사뷰티"
    ]
  },
  {
    category: "전자기기",
    confidence: 0.9,
    keywords: [
      "하이마트",
      "전자",
      "전자랜드",
      "애플",
      "apple",
      "삼성",
      "samsung",
      "lg전자",
      "컴퓨터",
      "노트북",
      "아이폰",
      "아이패드",
      "맥북",
      "에어팟",
      "휴대폰",
      "스마트폰",
      "쿠팡전자",
      "프리스비",
      "윌리스",
      "다나와",
      "컴퓨존",
      "아이코다"
    ]
  },
  {
    category: "도서/문구",
    confidence: 0.9,
    keywords: [
      "서점",
      "교보",
      "교보문고",
      "영풍문고",
      "알라딘",
      "예스24",
      "yes24",
      "리디",
      "밀리의서재",
      "윌라",
      "카카오페이지",
      "네이버웹툰",
      "레진",
      "문피아",
      "북앤라이프",
      "문구",
      "문구점",
      "아트박스",
      "핫트랙스",
      "학원",
      "학교",
      "대학교",
      "복사",
      "인쇄",
      "프린트",
      "스터디",
      "독서실",
      "스카",
      "스터디카페"
    ]
  },
  {
    category: "생활용품",
    confidence: 0.9,
    keywords: [
      "이마트",
      "홈플러스",
      "롯데마트",
      "마트",
      "코스트코",
      "트레이더스",
      "다이소",
      "생활",
      "생활용품",
      "잡화",
      "세탁",
      "빨래방",
      "크린토피아",
      "편의용품",
      "주방",
      "욕실",
      "청소",
      "가구",
      "오늘의집",
      "한샘",
      "이케아",
      "ikea",
      "마켓컬리",
      "컬리",
      "ssg",
      "쓱",
      "롯데온"
    ]
  },
  {
    category: "스포츠/레저",
    confidence: 0.9,
    keywords: [
      "헬스",
      "헬스장",
      "피트니스",
      "짐",
      "요가",
      "필라테스",
      "축구",
      "스포츠",
      "레저",
      "골프",
      "스크린골프",
      "볼링",
      "수영",
      "테니스",
      "배드민턴",
      "클라이밍",
      "등산",
      "자전거",
      "스포츠센터",
      "데카트론",
      "젝시믹스",
      "안다르",
      "뮬라웨어",
      "야놀자",
      "여기어때",
      "인터파크티켓"
    ]
  }
];

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function hasExactKeyword(text, keyword) {
  const pattern = new RegExp(`(^|[^0-9a-z가-힣])${escapeRegExp(keyword)}([^0-9a-z가-힣]|$)`, "i");
  return pattern.test(text);
}

function classifyCategoryWithConfidence(storeName, fullText = "") {
  const lowerStore = String(storeName || "").toLowerCase();
  const lowerText = String(fullText || "").toLowerCase();

  for (const rule of categoryRules) {
    const matchedKeywords = rule.keywords.filter((keyword) => {
      const lowerKeyword = String(keyword).toLowerCase();
      return lowerStore.includes(lowerKeyword) || lowerText.includes(lowerKeyword);
    });
    const exactMatchedKeywords = (rule.exactKeywords || []).filter((keyword) => {
      const lowerKeyword = String(keyword).toLowerCase();
      return hasExactKeyword(lowerStore, lowerKeyword) || hasExactKeyword(lowerText, lowerKeyword);
    });
    const allMatchedKeywords = [...matchedKeywords, ...exactMatchedKeywords];

    if (allMatchedKeywords.length > 0) {
      return {
        category: rule.category,
        confidence: rule.confidence,
        source: "rule",
        matchedKeywords: allMatchedKeywords
      };
    }
  }

  return {
    category: "기타",
    confidence: 0.3,
    source: "rule",
    matchedKeywords: []
  };
}

function classifyCategory(storeName, fullText = "") {
  return classifyCategoryWithConfidence(storeName, fullText).category;
}

async function classifyCategoryWithAI(storeName, fullText = "") {
  if (!openai) {
    return {
      category: "기타",
      confidence: 0.3,
      source: "ai",
      reason: "OpenAI 클라이언트가 초기화되지 않았습니다."
    };
  }

  const input = {
    storeName: String(storeName || ""),
    fullText: String(fullText || ""),
    allowedCategories
  };

  try {
    const completion = await openai.chat.completions.create({
      model: "gpt-4o-mini",
      temperature: 0,
      messages: [
        {
          role: "system",
          content: `
너는 결제내역 카테고리 분류 엔진이다.
입력된 결제처와 결제문구를 보고 허용 카테고리 중 하나만 선택한다.

규칙:
- 반드시 JSON만 반환한다.
- category는 allowedCategories 중 하나만 사용한다.
- 확실하지 않으면 "기타"로 분류하고 confidence를 0.5 이하로 둔다.
- 결제처가 PG사/간편결제명만 있으면 실제 사용처가 불명확하므로 confidence를 낮게 둔다.
- 개인정보를 추론하지 않는다.
`
        },
        {
          role: "user",
          content: JSON.stringify(input)
        }
      ],
      response_format: {
        type: "json_schema",
        json_schema: {
          name: "payment_category_result",
          strict: true,
          schema: {
            type: "object",
            additionalProperties: false,
            properties: {
              category: {
                type: "string",
                enum: allowedCategories
              },
              confidence: {
                type: "number"
              },
              reason: {
                type: "string"
              }
            },
            required: ["category", "confidence", "reason"]
          }
        }
      }
    });

    const content = completion.choices[0].message.content;
    const parsed = JSON.parse(content);

    return {
      category: allowedCategories.includes(parsed.category) ? parsed.category : "기타",
      confidence: Math.max(0, Math.min(1, Number(parsed.confidence) || 0.5)),
      source: "ai",
      reason: parsed.reason || ""
    };
  } catch (error) {
    console.error("AI category classification failed:", error);

    return {
      category: "기타",
      confidence: 0.3,
      source: "ai",
      reason: "AI 호출 실패"
    };
  }
}

async function classifyCategorySmart(storeName, fullText = "") {
  const ruleResult = classifyCategoryWithConfidence(storeName, fullText);

  if (ruleResult.category !== "기타" && ruleResult.confidence >= 0.85) {
    return {
      ...ruleResult,
      usedAI: false
    };
  }

  const aiResult = await classifyCategoryWithAI(storeName, fullText);

  return {
    ...aiResult,
    usedAI: true,
    ruleResult
  };
}

function extractAmountFromText(content) {
  const paymentAmountPatterns = [
    /([\d,]+)\s*원\s*(?:카드)?(?:결제완료|결제|승인|사용|출금)/,
    /(?:결제완료|결제|승인|사용|출금)\s*([\d,]+)\s*원/
  ];

  for (const pattern of paymentAmountPatterns) {
    const match = content.match(pattern);
    if (match) return parseInt(match[1].replace(/,/g, ""), 10) || 0;
  }

  const wonMatches = [...content.matchAll(/([\d,]+)\s*원/g)];
  for (const match of wonMatches) {
    const nearbyText = content.slice(match.index + match[0].length, match.index + match[0].length + 12);
    if (!nearbyText.includes("캐시백")) return parseInt(match[1].replace(/,/g, ""), 10) || 0;
  }

  const numMatches = content.match(/[\d,]{3,}/g) || [];
  if (numMatches.length === 0) return 0;
  const withComma = numMatches.find(n => n.includes(","));
  if (withComma) return parseInt(withComma.replace(/,/g, ""), 10) || 0;
  if (numMatches.length > 1) {
    const notFourDigits = numMatches.filter(n => n.length !== 4);
    if (notFourDigits.length > 0) return parseInt(notFourDigits[notFourDigits.length - 1], 10) || 0;
  }
  return parseInt(numMatches[numMatches.length - 1], 10) || 0;
}

function cleanStoreName(rawName) {
  if (!rawName) return "";
  const stopWords = ["잔액", "누적", "승인번호", "일시불", "체크", "카드", "계좌", "알림"];
  let name = String(rawName)
    .replace(/[\[\]]/g, " ")
    .replace(/[\d,]+\s*원/g, " ")
    .replace(/(결제완료|결제|승인|사용|출금)/g, " ")
    .trim();

  for (const stopWord of stopWords) {
    const index = name.indexOf(stopWord);
    if (index > 0) name = name.slice(0, index).trim();
  }

  return name.replace(/\s+/g, " ").trim();
}

function isLikelyStoreName(name) {
  if (!name || name.length < 2 || name.length > 25) return false;
  if (/[\d,]+\s*원/.test(name)) return false;

  const paymentWords = ["결제", "결제완료", "승인", "출금", "입금", "캐시백", "알림", "메시지"];
  if (paymentWords.some(word => name.includes(word))) return false;

  const bankOrCardNames = [
    "토스뱅크", "토스", "카카오뱅크", "케이뱅크", "국민카드", "신한카드", "우리카드", "하나카드",
    "현대카드", "삼성카드", "롯데카드", "농협", "기업은행", "우리은행", "하나은행", "신한은행", "국민은행"
  ];
  if (bankOrCardNames.some(namePart => name.includes(namePart))) return false;

  return true;
}

function extractStoreNameFromNotification(title, text, fullText) {
  const patterns = [
    /(?:가맹점|사용처|결제처)[:\s]+([^\n\r]+)/,
    /([가-힣a-zA-Z0-9()._\-\s]+?)에서\s*[\d,]+\s*원/,
    /[\d,]+\s*원\s*(?:카드)?(?:결제완료|결제|승인|사용|출금)\s+([^\n\r]+)/
  ];

  for (const pattern of patterns) {
    const match = fullText.match(pattern);
    if (match) {
      const candidate = cleanStoreName(match[1]);
      if (candidate) return candidate;
    }
  }

  const cleanTitle = cleanStoreName(title);
  if (isLikelyStoreName(cleanTitle)) return cleanTitle;

  const fallback = String(text || fullText)
    .split(/\s+/)
    .map(cleanStoreName)
    .find(isLikelyStoreName);

  return fallback || "알 수 없음";
}

function pickBestCandidate(values, scoreFunction) {
  let bestValue = null, bestScore = 0;
  for (const value of values) {
    const score = scoreFunction(value);
    if (score > bestScore) { bestScore = score; bestValue = value; }
  }
  return { value: bestValue, score: bestScore };
}

function inferPaymentItem(item) {
  const values = Object.values(item).filter(v => v !== null && v !== undefined && v !== "");
  let rawDate = getByFieldName(item, fieldMap.date);
  let rawAmount = getByFieldName(item, fieldMap.amount);
  let rawStoreName = getByFieldName(item, fieldMap.storeName);
  let rawCategory = getByFieldName(item, fieldMap.category);
  let rawPaymentMethod = getByFieldName(item, fieldMap.paymentMethod);

  let confidence = { date: rawDate ? 100 : 0, amount: rawAmount ? 100 : 0, storeName: rawStoreName ? 100 : 0 };

  if (!rawDate) {
    const c = pickBestCandidate(values, scoreDate);
    if (c.score >= 60) { rawDate = c.value; confidence.date = c.score; }
  }
  if (!rawAmount) {
    const c = pickBestCandidate(values, scoreAmount);
    if (c.score >= 70) { rawAmount = c.value; confidence.amount = c.score; }
  }
  if (!rawStoreName) {
    const c = pickBestCandidate(values, scoreStoreName);
    if (c.score >= 60) { rawStoreName = c.value; confidence.storeName = c.score; }
  }

  const parsed = {
    date: rawDate ? normalizeDate(rawDate) : null,
    amount: rawAmount ? normalizeAmount(rawAmount) : null,
    storeName: rawStoreName || null,
    category: rawCategory || classifyCategory(rawStoreName),
    paymentMethod: rawPaymentMethod || "unknown",
    confidence: confidence
  };

  const errors = [];
  if (!parsed.date) errors.push("날짜를 찾을 수 없습니다.");
  if (!parsed.amount) errors.push("금액을 찾을 수 없습니다.");
  if (!parsed.storeName) errors.push("가맹점명을 찾을 수 없습니다.");
  parsed.isValid = errors.length === 0;
  parsed.errors = errors;
  return parsed;
}

exports.parseNotification = onCall(async (request) => {
  const { title = "", text = "" } = request.data || {};
  const fullText = `${title} ${text}`.trim();

  if (!fullText) {
    throw new HttpsError("invalid-argument", "내용이 없습니다.");
  }

  const excludeKeywords = [
    "입금",
    "환불",
    "취소",
    "입금완료",
    "(광고)",
    "광고",
    "모임통장",
    "모임 통장"
  ];

  if (excludeKeywords.some(kw => fullText.includes(kw))) {
    return { success: false, reason: "excluded" };
  }

  const payKeywords = ["승인", "결제", "일시불", "출금", "카드승인", "자동이체"];

  if (!payKeywords.some(kw => fullText.includes(kw))) {
    return { success: false, reason: "not_payment" };
  }

  const amount = extractAmountFromText(fullText);

  if (amount <= 0) {
    return { success: false, reason: "amount_not_found" };
  }

  const storeName = extractStoreNameFromNotification(title, text, fullText);
  const categoryResult = await classifyCategorySmart(storeName, fullText);

  return {
    success: true,
    result: {
      amount,
      storeName,
      category: categoryResult.category,
      categoryConfidence: categoryResult.confidence,
      categorySource: categoryResult.source,
      usedAI: categoryResult.usedAI,
      categoryReason: categoryResult.reason || "",
      date: new Date().getTime(),
      originalText: fullText
    }
  };
});

exports.classifyCategory = onCall((request) => {
  const { storeName = "", fullText = "" } = request.data || {};

  const categoryResult = classifyCategoryWithConfidence(storeName, fullText);

  return {
    success: true,
    category: categoryResult.category,
    confidence: categoryResult.confidence,
    source: categoryResult.source,
    matchedKeywords: categoryResult.matchedKeywords
  };
});

// -----------------------------
// BudgetAnalyzer
// -----------------------------

function roundToTwo(num) {
  return Math.round(num * 100) / 100;
}

function getDaysInMonth(targetMonth) {
  if (!targetMonth || targetMonth === "전체") return 30;

  const [year, month] = targetMonth.split("-").map(Number);

  if (!year || !month) return 30;

  return new Date(year, month, 0).getDate();
}

function filterByMonth(spendingData, targetMonth) {
  if (!targetMonth) return spendingData;

  return spendingData.filter((item) => {
    if (!item.date) return false;

    let itemMonth = "";
    const timestamp = Number(item.date);

    if (!isNaN(timestamp) && timestamp > 1000000000) {
      let realTimestamp = timestamp;

      // 초 단위 timestamp면 밀리초로 변환
      if (timestamp < 10000000000) {
        realTimestamp = timestamp * 1000;
      }

      const d = new Date(realTimestamp);
      itemMonth = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    } else {
      itemMonth = String(item.date).substring(0, 7);
    }

    return itemMonth === targetMonth;
  });
}

function analyzeBudgetData(spendingData, monthlyBudget, categoryBudgets, targetMonth) {
  if (!Array.isArray(spendingData)) {
    throw new Error("spendingData는 배열 형태여야 합니다.");
  }

  const filteredData = filterByMonth(spendingData, targetMonth);
  const totalSpent = filteredData.reduce(
    (sum, item) => sum + (Number(item.amount) || 0),
    0
  );

  const categoryMap = {};

  filteredData.forEach((item) => {
    const cat = item.category || "기타";
    categoryMap[cat] = (categoryMap[cat] || 0) + (Number(item.amount) || 0);
  });

  const categoryAnalysis = Object.entries(categoryMap)
    .map(([category, spent]) => {
      const budget = categoryBudgets?.[category] || 0;

      return {
        category,
        spent,
        ratio: totalSpent > 0 ? roundToTwo((spent / totalSpent) * 100) : 0,
        budget,
        usageRate: budget > 0 ? roundToTwo((spent / budget) * 100) : null,
        isOverBudget: budget > 0 ? spent > budget : false,
      };
    })
    .sort((a, b) => b.spent - a.spent);

  const mostExpensive =
    [...filteredData].sort(
      (a, b) => (Number(b.amount) || 0) - (Number(a.amount) || 0)
    )[0] || null;

  const merchantMap = {};

  filteredData.forEach((item) => {
    const merchant = item.storeName || "알 수 없음";
    merchantMap[merchant] =
      (merchantMap[merchant] || 0) + (Number(item.amount) || 0);
  });

  const topMerchants = Object.entries(merchantMap)
    .map(([storeName, amount]) => ({
      storeName,
      amount,
    }))
    .sort((a, b) => b.amount - a.amount)
    .slice(0, 3);

  const usageRate =
    monthlyBudget > 0 ? roundToTwo((totalSpent / monthlyBudget) * 100) : null;

  const remainingBudget =
    monthlyBudget > 0 ? monthlyBudget - totalSpent : null;

  let status = "정상";
  let reportMessage = "이번 달도 알뜰하게 소비하고 계시네요!";

  if (usageRate !== null && usageRate >= 100) {
    status = "초과";
    reportMessage = "예산을 초과했습니다! 지출 내역을 점검해보세요.";
  } else if (usageRate !== null && usageRate >= 80) {
    status = "경고";
    reportMessage = "예산의 80%를 넘었습니다. 조금만 더 아껴볼까요?";
  }

  if (categoryAnalysis.length > 0) {
    reportMessage += ` 특히 '${categoryAnalysis[0].category}' 비중이 가장 높습니다.`;
  }

  const daysInMonth = getDaysInMonth(targetMonth);

  return {
    targetMonth: targetMonth || "전체",
    summary: {
      totalSpent,
      monthlyBudget: monthlyBudget || 0,
      usageRate,
      remainingBudget,
      status,
      isOverBudget: monthlyBudget > 0 && totalSpent > monthlyBudget,
    },
    stats: {
      transactionCount: filteredData.length,
      dailyAverage: roundToTwo(totalSpent / daysInMonth),
      topMerchants,
      mostExpensiveItem: mostExpensive
        ? {
            storeName: mostExpensive.storeName,
            amount: mostExpensive.amount,
            category: mostExpensive.category,
          }
        : null,
    },
    categoryAnalysis,
    reportMessage,
  };
}

exports.analyzeBudget = onCall((request) => {
  const { spendingData, monthlyBudget, categoryBudgets, targetMonth } =
    request.data || {};

  if (!spendingData) {
    throw new HttpsError("invalid-argument", "spendingData 필요");
  }

  try {
    const result = analyzeBudgetData(
      spendingData,
      Number(monthlyBudget) || 0,
      categoryBudgets || {},
      targetMonth
    );

    return {
      success: true,
      message: "소비 리포트 생성 완료",
      result,
    };
  } catch (error) {
    throw new HttpsError(
      "internal",
      "예산 분석 중 오류가 발생했습니다: " + error.message
    );
  }
});

// -----------------------------
// 리포트 생성기 (ReportGenerator)
// -----------------------------

/**
 * [API] analyzeBudget의 결과를 바탕으로 시각화 정보와 추천 메시지가 포함된 상세 리포트 생성
 */
exports.generateMonthlyReport = onCall((request) => {
  const { analysisResult, previousAnalysisResult } = request.data || {};

  if (!analysisResult) {
    throw new HttpsError(
      "invalid-argument",
      "analysisResult(analyzeBudget 결과)가 필요합니다."
    );
  }

  try {
    const {
      summary,
      stats,
      categoryAnalysis,
      targetMonth,
      reportMessage,
    } = analysisResult;

    if (!summary) {
      throw new HttpsError("invalid-argument", "summary 데이터가 필요합니다.");
    }

    if (!Array.isArray(categoryAnalysis)) {
      throw new HttpsError("invalid-argument", "categoryAnalysis 배열이 필요합니다.");
    }

    // 1. 차트 데이터 생성
    const chartData = categoryAnalysis.map((cat) => ({
      label: cat.category,
      value: cat.spent,
      percentage: cat.ratio,
    }));

    // 2. 가장 많이 지출한 카테고리 찾기
    const topCategory =
      categoryAnalysis.length > 0
        ? categoryAnalysis.reduce((max, cat) => {
            return cat.spent > max.spent ? cat : max;
          }, categoryAnalysis[0])
        : null;

    // 3. 소비 진단 및 맞춤형 추천 메시지 생성
    const recommendations = [];

    if (summary.status === "초과") {
      recommendations.push(
        "이번 달 예산을 초과했습니다. 다음 달에는 변동 지출(식비, 쇼핑 등)을 더 관리해보세요."
      );
    } else if (summary.status === "경고") {
      recommendations.push(
        "예산 소진 속도가 빠릅니다. 꼭 필요한 지출이 아니라면 다음 달로 미루는 것이 좋겠어요."
      );
    } else {
      recommendations.push(
        "설정한 예산 내에서 계획적으로 소비하고 있습니다."
      );
    }

    if (topCategory) {
      recommendations.push(
        `지출 1위인 '${topCategory.category}' 항목의 소비 패턴을 점검하면 더 많은 예산을 아낄 수 있습니다.`
      );
    }

    // 4. 전월 대비 소비 변화 분석
    const comparisonTexts = [];

    if (
      previousAnalysisResult &&
      Array.isArray(previousAnalysisResult.categoryAnalysis)
    ) {
      categoryAnalysis.forEach((current) => {
        const previous = previousAnalysisResult.categoryAnalysis.find(
          (p) => p.category === current.category
        );

        if (previous && previous.spent > 0) {
          const diff = current.spent - previous.spent;
          const rate = Math.round((diff / previous.spent) * 100);

          if (rate > 0) {
            comparisonTexts.push(
              `${current.category} 지출이 지난달보다 ${rate}% 증가했습니다.`
            );
          } else if (rate < 0) {
            comparisonTexts.push(
              `${current.category} 지출이 지난달보다 ${Math.abs(rate)}% 감소했습니다.`
            );
          } else {
            comparisonTexts.push(
              `${current.category} 지출은 지난달과 동일합니다.`
            );
          }
        }
      });
    }

    // 5. 최종 리포트 결과 반환
    return {
      success: true,
      report: {
        month: targetMonth,
        summaryText: reportMessage,
        totalSpent: summary.totalSpent,
        budgetUsageRate: summary.usageRate,
        budgetStatus: summary.status,
        chartData,
        recommendations,
        comparisonTexts,
        stats,
      },
    };
  } catch (error) {
    if (error instanceof HttpsError) {
      throw error;
    }

    throw new HttpsError(
      "internal",
      "상세 리포트 생성 중 오류 발생: " + error.message
    );
  }
});

// -----------------------------
// 고급 파싱 기능 (Puppeteer 활용)
// -----------------------------

const http = require("http");
const https = require("https");
const dns = require("dns").promises;
const net = require("net");

const PRODUCT_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

function cleanProductName(name) {
  return String(name || "")
    .replace(/\s+/g, " ")
    .replace(/\s*[-|]\s*(무신사|MUSINSA|지그재그|ZIGZAG).*$/i, "")
    .trim();
}

function extractPriceValue(value) {
  if (value === null || value === undefined) return 0;
  const text = String(value);
  const cleaned = text.replace(/[^0-9]/g, "");
  const price = parseInt(cleaned, 10);
  return Number.isFinite(price) ? price : 0;
}

function extractPriceFromText(text) {
  const content = String(text || "");
  const patterns = [
    /(?:판매가|할인가|쿠폰적용가|가격|price)["':\s]*([\d,]+)\s*원?/i,
    /([\d,]+)\s*원/
  ];

  for (const pattern of patterns) {
    const match = content.match(pattern);
    const price = extractPriceValue(match && match[1]);
    if (price > 0) return price;
  }
  return 0;
}

function extractSharedProduct(sharedText = "") {
  const text = String(sharedText || "").trim();
  if (!text) return { name: "", price: 0 };

  const withoutUrl = text.replace(/https?:\/\/\S+/g, " ").replace(/\s+/g, " ").trim();
  const price = extractPriceFromText(withoutUrl);
  const name = cleanProductName(
    withoutUrl
      .replace(/[\d,]+\s*원/g, " ")
      .replace(/\s+/g, " ")
      .trim()
  );

  return { name, price };
}

function absolutizeUrl(value, baseUrl) {
  if (!value) return "";
  try {
    return new URL(value, baseUrl).toString();
  } catch (error) {
    return value;
  }
}

function normalizeHostname(hostname) {
  return String(hostname || "").toLowerCase().replace(/^\[|\]$/g, "");
}

function isBlockedIpAddress(address) {
  const normalized = normalizeHostname(address);
  const ipVersion = net.isIP(normalized);

  if (ipVersion === 4) {
    const parts = normalized.split(".").map((part) => Number(part));
    const [a, b] = parts;
    return (
      a === 0 ||
      a === 10 ||
      a === 127 ||
      a >= 224 ||
      (a === 100 && b >= 64 && b <= 127) ||
      (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168)
    );
  }

  if (ipVersion === 6) {
    return (
      normalized === "::1" ||
      normalized.startsWith("fc") ||
      normalized.startsWith("fd") ||
      normalized.startsWith("fe8") ||
      normalized.startsWith("fe9") ||
      normalized.startsWith("fea") ||
      normalized.startsWith("feb") ||
      normalized.startsWith("::ffff:127.") ||
      normalized.startsWith("::ffff:10.") ||
      normalized.startsWith("::ffff:192.168.")
    );
  }

  return false;
}

async function assertPublicProductUrl(url) {
  const parsed = new URL(validateProductUrl(url));
  const hostname = normalizeHostname(parsed.hostname);

  if (isBlockedIpAddress(hostname)) {
    throw new HttpsError("invalid-argument", "내부 네트워크 URL은 분석할 수 없습니다.");
  }

  if (net.isIP(hostname)) return parsed.toString();

  try {
    const addresses = await dns.lookup(hostname, { all: true, verbatim: true });
    if (addresses.some((entry) => isBlockedIpAddress(entry.address))) {
      throw new HttpsError("invalid-argument", "내부 네트워크 URL은 분석할 수 없습니다.");
    }
  } catch (error) {
    if (error instanceof HttpsError) throw error;
  }

  return parsed.toString();
}

async function requestUrl(url, options = {}) {
  const { method = "GET", maxBytes = 700000, timeoutMs = 12000, redirectLimit = 5 } = options;
  const safeUrl = await assertPublicProductUrl(url);

  return new Promise((resolve, reject) => {
    const client = safeUrl.startsWith("https://") ? https : http;
    const req = client.request(safeUrl, {
      method,
      headers: {
        "User-Agent": PRODUCT_USER_AGENT,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.7,en;q=0.6",
        "Cache-Control": "no-cache",
      },
      timeout: timeoutMs,
    }, (res) => {
      const status = res.statusCode || 0;
      const location = res.headers.location;
      if ([301, 302, 303, 307, 308].includes(status) && location && redirectLimit > 0) {
        res.resume();
        const nextUrl = absolutizeUrl(location, safeUrl);
        requestUrl(nextUrl, { ...options, method: "GET", redirectLimit: redirectLimit - 1 })
          .then(resolve)
          .catch(reject);
        return;
      }

      const chunks = [];
      let size = 0;
      res.on("data", (chunk) => {
        if (maxBytes > 0 && size < maxBytes) {
          const remaining = maxBytes - size;
          chunks.push(chunk.length > remaining ? chunk.subarray(0, remaining) : chunk);
        }
        size += chunk.length;
      });
      res.on("end", () => {
        resolve({
          finalUrl: res.responseUrl || safeUrl,
          status,
          headers: res.headers,
          body: Buffer.concat(chunks).toString("utf8"),
        });
      });
    });

    req.on("timeout", () => req.destroy(new Error("URL 요청 시간이 초과되었습니다.")));
    req.on("error", reject);
    req.end();
  });
}

async function resolveProductUrl(url) {
  try {
    const response = await requestUrl(url, { method: "HEAD", maxBytes: 0, timeoutMs: 8000 });
    return response.finalUrl || url;
  } catch (error) {
    try {
      const response = await requestUrl(url, { method: "GET", maxBytes: 0, timeoutMs: 8000 });
      return response.finalUrl || url;
    } catch (ignored) {
      return url;
    }
  }
}

function getMeta(html, names) {
  for (const name of names) {
    const patterns = [
      new RegExp(`<meta[^>]+(?:property|name)=["']${name}["'][^>]+content=["']([^"']+)["'][^>]*>`, "i"),
      new RegExp(`<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']${name}["'][^>]*>`, "i")
    ];
    for (const pattern of patterns) {
      const match = html.match(pattern);
      if (match && match[1]) return match[1].replace(/&amp;/g, "&").trim();
    }
  }
  return "";
}

function findProductInJson(value) {
  if (!value || typeof value !== "object") return null;
  const type = value["@type"] || value.type;
  const types = Array.isArray(type) ? type : [type];
  if (types.some((item) => String(item).toLowerCase() === "product")) return value;

  if (Array.isArray(value)) {
    for (const item of value) {
      const found = findProductInJson(item);
      if (found) return found;
    }
    return null;
  }

  for (const item of Object.values(value)) {
    const found = findProductInJson(item);
    if (found) return found;
  }
  return null;
}

function extractImageValue(value) {
  if (typeof value === "string") return value;
  if (Array.isArray(value)) return extractImageValue(value[0]);
  if (value && typeof value === "object") return value.url || value.contentUrl || "";
  return "";
}

function extractOfferPrice(offers) {
  if (Array.isArray(offers)) return extractOfferPrice(offers[0]);
  if (offers && typeof offers === "object") {
    return extractPriceValue(offers.price || offers.lowPrice || offers.highPrice);
  }
  return 0;
}

function parseProductFromHtml(html, pageUrl, sharedText = "") {
  const shared = extractSharedProduct(sharedText);
  const jsonMatches = [...String(html || "").matchAll(/<script[^>]+type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi)];
  let jsonProduct = null;

  for (const match of jsonMatches) {
    try {
      const parsed = JSON.parse(match[1].trim());
      jsonProduct = findProductInJson(parsed);
      if (jsonProduct) break;
    } catch (error) {
      // Ignore malformed script blocks.
    }
  }

  const jsonName = jsonProduct ? cleanProductName(jsonProduct.name) : "";
  const jsonImage = jsonProduct ? extractImageValue(jsonProduct.image) : "";
  const jsonPrice = jsonProduct ? extractOfferPrice(jsonProduct.offers) : 0;

  const metaName = cleanProductName(
    getMeta(html, ["og:title", "twitter:title", "title"]) ||
    (html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1] || "")
  );
  const metaImage = getMeta(html, ["og:image", "twitter:image"]);
  const metaPrice = extractPriceValue(
    getMeta(html, ["product:price:amount", "og:price:amount", "product:sale_price:amount", "twitter:data1"])
  );

  return {
    name: jsonName || metaName || shared.name,
    price: jsonPrice || metaPrice || shared.price || extractPriceFromText(html),
    imageUrl: absolutizeUrl(jsonImage || metaImage, pageUrl),
  };
}

function validateProductUrl(value) {
  let parsed;
  try {
    parsed = new URL(value);
  } catch (error) {
    throw new HttpsError("invalid-argument", "올바른 URL이 아닙니다.");
  }

  if (!["http:", "https:"].includes(parsed.protocol)) {
    throw new HttpsError("invalid-argument", "http 또는 https URL만 분석할 수 있습니다.");
  }

  const hostname = parsed.hostname.toLowerCase();
  const blockedHosts = ["localhost", "127.0.0.1", "0.0.0.0", "::1"];
  if (blockedHosts.includes(hostname) || hostname.endsWith(".local")) {
    throw new HttpsError("invalid-argument", "내부 네트워크 URL은 분석할 수 없습니다.");
  }

  return parsed.toString();
}

function stripHtmlForAI(html) {
  return String(html || "")
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<noscript[\s\S]*?<\/noscript>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, "\"")
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 12000);
}

function normalizeAIProductAnalysis(parsed, fallbackProductInfo) {
  const price = Number(parsed.price || fallbackProductInfo.price || 0);
  const confidence = Math.max(0, Math.min(1, Number(parsed.confidence) || 0));

  return {
    productName: cleanProductName(parsed.productName || fallbackProductInfo.name || ""),
    price: Number.isFinite(price) ? price : 0,
    currency: parsed.currency || "KRW",
    brand: parsed.brand || "",
    description: parsed.description || "",
    productInfo: Array.isArray(parsed.productInfo) ? parsed.productInfo : [],
    imageUrl: parsed.imageUrl || fallbackProductInfo.imageUrl || "",
    availability: parsed.availability || "",
    confidence,
    reason: parsed.reason || "",
  };
}

async function analyzeProductWithAI({ url, resolvedUrl, html, productInfo, sharedText }) {
  if (!openai) {
    throw new HttpsError("failed-precondition", "OPENAI_API_KEY가 설정되어 있지 않습니다.");
  }

  const pageText = stripHtmlForAI(html);
  const input = {
    url,
    resolvedUrl,
    sharedText: String(sharedText || "").slice(0, 3000),
    extractedMetadata: productInfo,
    pageText,
  };

  const completion = await openai.chat.completions.create({
    model: "gpt-4o-mini",
    temperature: 0,
    messages: [
      {
        role: "system",
        content: `
너는 쇼핑몰 상품 페이지 분석 엔진이다.
URL에서 추출된 메타데이터와 페이지 텍스트를 바탕으로 실제 판매 상품의 정보를 구조화한다.

규칙:
- 반드시 JSON만 반환한다.
- 제품명은 쇼핑몰명/사이트명/불필요한 문구를 제거한 실제 상품명으로 작성한다.
- price는 숫자만 사용한다. 가격을 찾지 못하면 0을 사용한다.
- currency는 KRW, USD, JPY 같은 ISO 통화 코드로 작성한다. 원화 가격이면 KRW를 사용한다.
- productInfo에는 색상, 옵션, 카테고리, 배송/할인/판매 상태 등 확인 가능한 핵심 정보만 짧게 넣는다.
- 페이지에 없는 정보는 추측하지 않는다.
- 여러 가격이 있으면 현재 구매자가 결제할 가능성이 가장 높은 판매가/할인가를 선택한다.
`
      },
      {
        role: "user",
        content: JSON.stringify(input)
      }
    ],
    response_format: {
      type: "json_schema",
      json_schema: {
        name: "product_url_analysis",
        strict: true,
        schema: {
          type: "object",
          additionalProperties: false,
          properties: {
            productName: { type: "string" },
            price: { type: "number" },
            currency: { type: "string" },
            brand: { type: "string" },
            description: { type: "string" },
            productInfo: {
              type: "array",
              items: { type: "string" }
            },
            imageUrl: { type: "string" },
            availability: { type: "string" },
            confidence: { type: "number" },
            reason: { type: "string" }
          },
          required: [
            "productName",
            "price",
            "currency",
            "brand",
            "description",
            "productInfo",
            "imageUrl",
            "availability",
            "confidence",
            "reason"
          ]
        }
      }
    }
  });

  const content = completion.choices[0].message.content;
  return normalizeAIProductAnalysis(JSON.parse(content), productInfo);
}

exports.analyzeProductUrl = onCall(async (request) => {
  const { url, sharedText = "" } = request.data || {};
  const targetUrl = validateProductUrl(url);

  try {
    const resolvedUrl = await resolveProductUrl(targetUrl);
    const htmlResponse = await requestUrl(resolvedUrl, {
      method: "GET",
      timeoutMs: 15000,
      maxBytes: 1200000,
    });
    const finalUrl = htmlResponse.finalUrl || resolvedUrl;
    const productInfo = parseProductFromHtml(htmlResponse.body, finalUrl, sharedText);
    const analysis = await analyzeProductWithAI({
      url: targetUrl,
      resolvedUrl: finalUrl,
      html: htmlResponse.body,
      productInfo,
      sharedText,
    });

    return {
      success: true,
      result: {
        ...analysis,
        sourceUrl: targetUrl,
        resolvedUrl: finalUrl,
        rawExtracted: productInfo,
      },
    };
  } catch (error) {
    console.error("AI Product URL Analysis Error:", error);

    if (error instanceof HttpsError) {
      throw error;
    }

    throw new HttpsError(
      "internal",
      "상품 URL AI 분석 중 오류가 발생했습니다: " + error.message
    );
  }
});

exports.advancedProductParse = onCall(async (request) => {
  const { url, sharedText = "" } = request.data || {};
  if (!url) throw new HttpsError("invalid-argument", "URL이 필요합니다.");
  
  let browser = null;

  try {
    const targetUrl = await assertPublicProductUrl(url);
    const resolvedUrl = await assertPublicProductUrl(await resolveProductUrl(targetUrl));
    let productInfo = { name: "", price: 0, imageUrl: "" };

    try {
      const htmlResponse = await requestUrl(resolvedUrl, { method: "GET", timeoutMs: 12000 });
      productInfo = parseProductFromHtml(htmlResponse.body, htmlResponse.finalUrl || resolvedUrl, sharedText);
      productInfo.resolvedUrl = htmlResponse.finalUrl || resolvedUrl;
    } catch (error) {
      productInfo = { ...extractSharedProduct(sharedText), imageUrl: "", resolvedUrl };
    }

    if (productInfo.name && productInfo.price > 0 && productInfo.imageUrl) {
      return { success: true, result: productInfo };
    }

    const chromium = require('chrome-aws-lambda');
    const puppeteer = require('puppeteer-core');

    browser = await puppeteer.launch({
      args: chromium.args,
      defaultViewport: chromium.defaultViewport,
      executablePath: await chromium.executablePath,
      headless: chromium.headless,
    });

    const page = await browser.newPage();
    
    // 유저 에이전트 설정 (매우 중요)
    await page.setUserAgent(PRODUCT_USER_AGENT);
    
    await page.goto(productInfo.resolvedUrl || resolvedUrl, { waitUntil: 'networkidle2', timeout: 30000 });

    const renderedInfo = await page.evaluate(() => {
      const textOf = (selector) => document.querySelector(selector)?.textContent?.trim() || "";
      const meta = (selector) => document.querySelector(selector)?.content || "";
      const priceText = [
        meta('meta[property="product:price:amount"]'),
        meta('meta[property="og:price:amount"]'),
        textOf('[class*="price" i]'),
        textOf('[class*="sale" i]'),
        document.body?.innerText || ""
      ].find(Boolean) || "";

      return {
        name: meta('meta[property="og:title"]') || meta('meta[name="twitter:title"]') || document.title,
        priceText,
        imageUrl: meta('meta[property="og:image"]') || meta('meta[name="twitter:image"]') || "",
      };
    });

    const shared = extractSharedProduct(sharedText);
    productInfo = {
      name: cleanProductName(productInfo.name || renderedInfo.name || shared.name),
      price: productInfo.price || extractPriceFromText(renderedInfo.priceText) || shared.price,
      imageUrl: productInfo.imageUrl || absolutizeUrl(renderedInfo.imageUrl, productInfo.resolvedUrl || resolvedUrl),
      resolvedUrl: productInfo.resolvedUrl || resolvedUrl,
    };

    return { success: true, result: productInfo };

  } catch (error) {
    console.error("Advanced Parse Error:", error);
    return { success: false, message: error.message };
  } finally {
    if (browser !== null) await browser.close();
  }
});
