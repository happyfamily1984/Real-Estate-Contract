// 배포 전에 내 컴퓨터(또는 Cloud Shell)에서 분석 결과를 미리 확인하는 스크립트.
//   npm run build
//   ANTHROPIC_API_KEY=sk-ant-... node scripts/try-analyze.js 1페이지.jpg 2페이지.jpg
const fs = require("fs");
const { analyzeContractImages } = require("../lib/analyze");

const files = process.argv.slice(2);
if (files.length === 0) {
  console.error("사용법: node scripts/try-analyze.js <사진1> [사진2 ...]");
  process.exit(1);
}

analyzeContractImages(files.map((f) => fs.readFileSync(f)), process.env.ANTHROPIC_API_KEY)
  .then((result) => console.log(JSON.stringify(result, null, 2)))
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
