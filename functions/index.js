const { onCall, HttpsError } = require("firebase-functions/v2/https");

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

function classifyCategory(storeName, fullText = "") {
  const lowerStore = String(storeName || "").toLowerCase();
  const lowerText = String(fullText || "").toLowerCase();
  const check = (keywords) => keywords.some(kw => lowerStore.includes(kw) || lowerText.includes(kw));

  if (check(["마트", "편의점", "식당", "카페", "커피", "베이커리", "음식점", "배달", "치킨", "피자", "gs25", "cu", "세븐일레븐", "이마트24"])) return "식품/음료";
  if (check(["백화점", "쇼핑", "몰", "의류", "패션", "무신사", "지그재그"])) return "패션/의류";
  if (check(["올리브영", "화장품", "뷰티", "헤어", "미용실"])) return "뷰티/화장품";
  if (check(["하이마트", "전자", "애플", "삼성", "컴퓨터"])) return "전자기기";
  if (check(["서점", "교보", "문구", "다이소", "학원", "학교"])) return "도서/문구";
  if (check(["마트", "이마트", "홈플러스", "다이소", "생활", "세탁"])) return "생활용품";
  if (check(["헬스", "축구", "스포츠", "레저", "골프"])) return "스포츠/레저";
  return "기타";
}

function extractAmountFromText(content) {
  const wonMatch = content.match(/([\d,]+)\s*원/);
  if (wonMatch) return parseInt(wonMatch[1].replace(/,/g, ""), 10) || 0;
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

exports.parsePaymentData = onCall((request) => {
  const data = request.data;
  if (!data || !data.paymentData) throw new HttpsError("invalid-argument", "paymentData 필요");
  try {
    const parsedList = data.paymentData.map(item => inferPaymentItem(item));
    return {
      success: true,
      result: {
        validData: parsedList.filter(i => i.isValid),
        invalidData: parsedList.filter(i => !i.isValid)
      }
    };
  } catch (error) {
    throw new HttpsError("internal", error.message);
  }
});

exports.parseNotification = onCall((request) => {
  const { title = "", text = "" } = request.data || {};
  const fullText = `${title} ${text}`.trim();
  if (!fullText) throw new HttpsError("invalid-argument", "내용이 없습니다.");

  const excludeKeywords = ["입금", "환불", "취소", "입금완료", "(광고)", "광고"];
  if (excludeKeywords.some(kw => fullText.includes(kw))) return { success: false, reason: "excluded" };

  const payKeywords = ["승인", "결제", "일시불", "출금", "카드승인", "자동이체"];
  if (!payKeywords.some(kw => fullText.includes(kw))) return { success: false, reason: "not_payment" };

  const amount = extractAmountFromText(fullText);
  if (amount <= 0) return { success: false, reason: "amount_not_found" };

  let storeName = (title.length >= 2 && title.length <= 12 && !title.includes("메시지"))
    ? title
    : (text.split(/\s+/).slice(0, 2).join(" ") || "알 수 없음");

  return {
    success: true,
    result: {
      amount,
      storeName,
      category: classifyCategory(storeName, fullText),
      date: new Date().getTime(),
      originalText: fullText
    }
  };
});

exports.classifyCategory = onCall((request) => {
  const { storeName = "", fullText = "" } = request.data || {};
  return { success: true, category: classifyCategory(storeName, fullText) };
});

// -----------------------------
// BudgetAnalyzer
// -----------------------------

function roundToTwo(num) {
  return Math.round(num * 100) / 100;
}

function filterByMonth(spendingData, targetMonth) {
  if (!targetMonth) return spendingData;

  return spendingData.filter((item) => {
    if (!item.date) return false;
    return String(item.date).startsWith(targetMonth);
  });
}

function calculateTotalSpent(spendingData) {
  return spendingData.reduce((sum, item) => {
    const amount = Number(item.amount) || 0;
    return sum + amount;
  }, 0);
}

function calculateCategorySpent(spendingData) {
  const categoryMap = {};

  spendingData.forEach((item) => {
    const category = item.category || "기타";
    const amount = Number(item.amount) || 0;

    if (!categoryMap[category]) {
      categoryMap[category] = 0;
    }

    categoryMap[category] += amount;
  });

  return categoryMap;
}

function createCategoryAnalysis(categorySpent, totalSpent, categoryBudgets) {
  return Object.entries(categorySpent).map(([category, spent]) => {
    const budget = categoryBudgets?.[category] || 0;

    const ratio = totalSpent > 0
      ? roundToTwo((spent / totalSpent) * 100)
      : 0;

    const usageRate = budget > 0
      ? roundToTwo((spent / budget) * 100)
      : null;

    return {
      category: category,
      spent: spent,
      ratio: ratio,
      budget: budget,
      usageRate: usageRate,
      isOverBudget: budget > 0 ? spent > budget : false
    };
  });
}

function analyzeSpendingPattern(categoryAnalysis) {
  if (!categoryAnalysis || categoryAnalysis.length === 0) {
    return "분석할 소비 데이터가 없습니다.";
  }

  const sorted = [...categoryAnalysis].sort((a, b) => b.spent - a.spent);
  const topCategory = sorted[0];

  if (topCategory.ratio >= 50) {
    return `이번 달 소비는 ${topCategory.category} 카테고리에 집중되어 있습니다.`;
  }

  return `이번 달 소비는 ${topCategory.category} 카테고리의 비중이 가장 높습니다.`;
}

function analyzeBudgetData(spendingData, monthlyBudget, categoryBudgets, targetMonth) {
  if (!Array.isArray(spendingData)) {
    throw new Error("spendingData는 배열 형태여야 합니다.");
  }

  const filteredData = filterByMonth(spendingData, targetMonth);
  const totalSpent = calculateTotalSpent(filteredData);
  const categorySpent = calculateCategorySpent(filteredData);
  const categoryAnalysis = createCategoryAnalysis(
    categorySpent,
    totalSpent,
    categoryBudgets || {}
  );

  const budgetUsageRate = monthlyBudget > 0
    ? roundToTwo((totalSpent / monthlyBudget) * 100)
    : null;

  const remainingBudget = monthlyBudget > 0
    ? monthlyBudget - totalSpent
    : null;

  return {
    targetMonth: targetMonth || "all",
    totalSpent: totalSpent,
    monthlyBudget: monthlyBudget || 0,
    budgetUsageRate: budgetUsageRate,
    isOverBudget: monthlyBudget > 0 ? totalSpent > monthlyBudget : false,
    remainingBudget: remainingBudget,
    categoryAnalysis: categoryAnalysis,
    patternSummary: analyzeSpendingPattern(categoryAnalysis)
  };
}

exports.analyzeBudget = onCall((request) => {
  const data = request.data;

  if (!data || !data.spendingData) {
    throw new HttpsError(
      "invalid-argument",
      "spendingData가 필요합니다."
    );
  }

  try {
    const result = analyzeBudgetData(
      data.spendingData,
      Number(data.monthlyBudget) || 0,
      data.categoryBudgets || {},
      data.targetMonth
    );

    return {
      success: true,
      message: "예산 분석 완료",
      result: result
    };
  } catch (error) {
    throw new HttpsError(
      "internal",
      "예산 분석 중 오류가 발생했습니다.",
      error.message
    );
  }
});
