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

  // 날짜처럼 보이면 금액 후보에서 제외
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

  if (!text) return 0;
  if (scoreDate(text) > 0) return 0;
  if (scoreAmount(text) >= 70) return 0;
  if (/^\d+$/.test(text)) return 0;
  if (text.length > 40) return 20;

  const lower = text.toLowerCase();

  const knownStores = [
    "gs25", "cu", "세븐일레븐", "이마트24",
    "스타벅스", "이디야", "메가커피", "투썸",
    "쿠팡", "무신사", "네이버쇼핑", "배달의민족", "요기요"
  ];

  if (knownStores.some((store) => lower.includes(store.toLowerCase()))) {
    return 95;
  }

  // 한글, 영어가 섞인 짧은 문자열이면 가맹점명 가능성 있음
  if (/^[가-힣a-zA-Z0-9\s()._-]+$/.test(text) && text.length <= 25) {
    return 75;
  }

  return 40;
}

function normalizeDate(value) {
  const text = String(value).trim();

  if (/^\d{4}[./-]\d{1,2}[./-]\d{1,2}$/.test(text)) {
    const parts = text.split(/[./-]/);
    return `${parts[0]}-${parts[1].padStart(2, "0")}-${parts[2].padStart(2, "0")}`;
  }

  if (/^\d{8}$/.test(text)) {
    return `${text.slice(0, 4)}-${text.slice(4, 6)}-${text.slice(6, 8)}`;
  }

  return text;
}

function normalizeAmount(value) {
  const text = String(value).trim();
  const cleaned = text.replace(/[^0-9-]/g, "");
  return Number(cleaned);
}

function classifyCategory(storeName) {
  const name = String(storeName || "").toLowerCase();

  if (name.includes("gs25") || name.includes("cu") || name.includes("세븐일레븐") || name.includes("이마트24")) {
    return "편의점";
  }

  if (name.includes("스타벅스") || name.includes("이디야") || name.includes("메가커피") || name.includes("투썸")) {
    return "카페";
  }

  if (name.includes("쿠팡") || name.includes("무신사") || name.includes("네이버쇼핑")) {
    return "쇼핑";
  }

  if (name.includes("버스") || name.includes("지하철") || name.includes("택시")) {
    return "교통";
  }

  return "기타";
}

function pickBestCandidate(values, scoreFunction) {
  let bestValue = null;
  let bestScore = 0;

  for (const value of values) {
    const score = scoreFunction(value);

    if (score > bestScore) {
      bestScore = score;
      bestValue = value;
    }
  }

  return {
    value: bestValue,
    score: bestScore
  };
}

function inferPaymentItem(item) {
  const values = Object.values(item).filter(
    (value) => value !== null && value !== undefined && value !== ""
  );

  // 1차: 필드명 기반
  let rawDate = getByFieldName(item, fieldMap.date);
  let rawAmount = getByFieldName(item, fieldMap.amount);
  let rawStoreName = getByFieldName(item, fieldMap.storeName);
  let rawCategory = getByFieldName(item, fieldMap.category);
  let rawPaymentMethod = getByFieldName(item, fieldMap.paymentMethod);

  let confidence = {
    date: rawDate ? 100 : 0,
    amount: rawAmount ? 100 : 0,
    storeName: rawStoreName ? 100 : 0
  };

  // 2차: 값 형태 기반 추론
  if (!rawDate) {
    const candidate = pickBestCandidate(values, scoreDate);
    if (candidate.score >= 60) {
      rawDate = candidate.value;
      confidence.date = candidate.score;
    }
  }

  if (!rawAmount) {
    const candidate = pickBestCandidate(values, scoreAmount);
    if (candidate.score >= 70) {
      rawAmount = candidate.value;
      confidence.amount = candidate.score;
    }
  }

  if (!rawStoreName) {
    const candidate = pickBestCandidate(values, scoreStoreName);
    if (candidate.score >= 60) {
      rawStoreName = candidate.value;
      confidence.storeName = candidate.score;
    }
  }

  const parsed = {
    date: rawDate ? normalizeDate(rawDate) : null,
    amount: rawAmount ? normalizeAmount(rawAmount) : null,
    storeName: rawStoreName || null,
    category: rawCategory || classifyCategory(rawStoreName),
    paymentMethod: rawPaymentMethod || "unknown",
    confidence: confidence
  };

  // 3차: 필수값 검증
  const errors = [];

  if (!parsed.date) errors.push("날짜를 찾을 수 없습니다.");
  if (!parsed.amount) errors.push("금액을 찾을 수 없습니다.");
  if (!parsed.storeName) errors.push("가맹점명을 찾을 수 없습니다.");

  parsed.isValid = errors.length === 0;
  parsed.errors = errors;

  return parsed;
}

function parsePaymentData(paymentData) {
  if (!Array.isArray(paymentData)) {
    throw new Error("paymentData는 배열 형태여야 합니다.");
  }

  const parsedList = paymentData.map((item) => inferPaymentItem(item));

  return {
    validData: parsedList.filter((item) => item.isValid),
    invalidData: parsedList.filter((item) => !item.isValid)
  };
}

exports.parsePaymentData = onCall((request) => {
  const data = request.data;

  if (!data || !data.paymentData) {
    throw new HttpsError("invalid-argument", "paymentData가 필요합니다.");
  }

  try {
    const result = parsePaymentData(data.paymentData);

    return {
      success: true,
      message: "결제 데이터 파싱 완료",
      validCount: result.validData.length,
      invalidCount: result.invalidData.length,
      result: result
    };
  } catch (error) {
    throw new HttpsError(
      "internal",
      "결제 데이터 파싱 중 오류가 발생했습니다.",
      error.message
    );
  }
});