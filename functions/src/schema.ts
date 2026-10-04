// Claude 응답을 강제하는 JSON 스키마.
// 알 수 없는 문자열 값은 "" (빈 문자열), 알 수 없는 금액은 0 으로 채우도록 프롬프트에서 지시한다.

const party = {
  type: "object",
  additionalProperties: false,
  required: ["name", "phone", "address"],
  properties: {
    name: { type: "string", description: "성명 또는 법인명" },
    phone: { type: "string", description: "전화번호" },
    address: { type: "string", description: "주소" },
  },
} as const;

const payment = {
  type: "object",
  additionalProperties: false,
  required: ["amount", "date"],
  properties: {
    amount: { type: "integer", description: "금액(원 단위 정수)" },
    date: { type: "string", description: "지급일 YYYY-MM-DD" },
  },
} as const;

export const contractSchema = {
  type: "object",
  additionalProperties: false,
  required: [
    "contractType",
    "propertyType",
    "propertyAddress",
    "area",
    "contractDate",
    "leaseStart",
    "leaseEnd",
    "salePrice",
    "deposit",
    "monthlyRent",
    "managementFee",
    "downPayment",
    "middlePayments",
    "balancePayment",
    "lessor",
    "lessee",
    "agent",
    "specialTerms",
    "summary",
    "cautions",
  ],
  properties: {
    contractType: {
      type: "string",
      enum: ["매매", "전세", "월세", "기타"],
      description: "계약 종류",
    },
    propertyType: {
      type: "string",
      description: "부동산 종류 (아파트, 오피스텔, 다세대, 단독주택, 상가, 토지 등)",
    },
    propertyAddress: { type: "string", description: "부동산 소재지 전체 주소" },
    area: { type: "string", description: "면적 (예: 전용 84.97㎡)" },
    contractDate: { type: "string", description: "계약 체결일 YYYY-MM-DD" },
    leaseStart: { type: "string", description: "임대차 시작일 YYYY-MM-DD (매매면 인도일)" },
    leaseEnd: { type: "string", description: "임대차 종료일 YYYY-MM-DD" },
    salePrice: { type: "integer", description: "매매대금(원). 임대차면 0" },
    deposit: { type: "integer", description: "보증금(원). 매매면 0" },
    monthlyRent: { type: "integer", description: "월 차임(원). 없으면 0" },
    managementFee: { type: "integer", description: "월 관리비(원). 없으면 0" },
    downPayment: payment,
    middlePayments: { type: "array", items: payment, description: "중도금 목록" },
    balancePayment: payment,
    lessor: { ...party, description: "임대인 또는 매도인" },
    lessee: { ...party, description: "임차인 또는 매수인" },
    agent: {
      type: "object",
      additionalProperties: false,
      required: ["officeName", "representative", "registrationNumber", "phone", "address"],
      properties: {
        officeName: { type: "string", description: "중개사무소 명칭" },
        representative: { type: "string", description: "대표 공인중개사 성명" },
        registrationNumber: { type: "string", description: "등록번호" },
        phone: { type: "string" },
        address: { type: "string" },
      },
    },
    specialTerms: {
      type: "array",
      items: { type: "string" },
      description: "특약사항 각 항목 (원문에 가깝게)",
    },
    summary: { type: "string", description: "계약 핵심 내용을 3~5문장으로 요약" },
    cautions: {
      type: "array",
      items: { type: "string" },
      description: "계약자가 확인하거나 주의해야 할 점",
    },
  },
} as const;

export interface Payment {
  amount: number;
  date: string;
}

export interface ContractAnalysis {
  contractType: "매매" | "전세" | "월세" | "기타";
  propertyType: string;
  propertyAddress: string;
  area: string;
  contractDate: string;
  leaseStart: string;
  leaseEnd: string;
  salePrice: number;
  deposit: number;
  monthlyRent: number;
  managementFee: number;
  downPayment: Payment;
  middlePayments: Payment[];
  balancePayment: Payment;
  lessor: { name: string; phone: string; address: string };
  lessee: { name: string; phone: string; address: string };
  agent: {
    officeName: string;
    representative: string;
    registrationNumber: string;
    phone: string;
    address: string;
  };
  specialTerms: string[];
  summary: string;
  cautions: string[];
}
