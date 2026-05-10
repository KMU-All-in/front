const { onCall } = require("firebase-functions/v2/https");

exports.analyzeUrl = onCall((request) => {
  const url = request.data.url;

  if (!url) {
    return {
      success: false,
      message: "URL이 없습니다.",
    };
  }

  return {
    success: true,
    productName: "테스트 상품",
    price: 10000,
    imageUrl: "https://example.com/test.jpg",
    originalUrl: url,
  };
});