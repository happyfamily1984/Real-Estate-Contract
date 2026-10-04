# 계약서함 — 부동산 계약서 AI 정리 앱 (Android)

부동산 계약서를 사진으로 찍으면 AI(Claude)가 계약 내용을 정리하고, 사진과 결과를 클라우드에 보관하는 안드로이드 앱입니다.

- 📷 **촬영**: 구글 문서 스캐너로 여러 장을 찍습니다. 테두리와 기울기는 자동으로 보정됩니다. 갤러리에서 고를 수도 있습니다.
- 🤖 **AI 정리**: 계약 종류, 주소, 보증금·월세·매매대금, 계약금·중도금·잔금 일정, 당사자, 중개사, 특약사항을 정리합니다. **확인이 필요한 부분**도 함께 알려 줍니다.
- ☁️ **클라우드 보관**: 모든 처리는 Firebase(구글 클라우드)에서 이루어집니다. **PC를 켜 둘 필요가 없습니다.**
- 🔐 **어느 기기에서나**: 구글 계정으로 로그인하면 다른 휴대폰에서도 같은 계약서가 보입니다.
- 🔎 주소·이름·메모 검색, 메모 작성, 다시 분석, 삭제, 사진 확대 보기를 지원합니다.

## 구조

```
[안드로이드 앱] ──사진 업로드──▶ [Firebase Storage]
      │                                │
      └─문서 생성(status=pending)─▶ [Firestore] ──트리거──▶ [Cloud Functions]
                                       ▲                         │ 사진 + 프롬프트
                                       └────── 분석 결과 저장 ◀── [Claude API]
```

| 폴더 | 내용 |
|---|---|
| `android/` | 안드로이드 앱 (Kotlin, Jetpack Compose) |
| `functions/` | 서버 코드 (Firebase Cloud Functions, TypeScript). 사진을 Claude 에게 보내 분석 |
| `firestore.rules`, `storage.rules` | 보안 규칙. 본인 데이터만 읽고 쓸 수 있음 |
| `.github/workflows/android.yml` | GitHub 에서 APK 를 자동으로 빌드하고 Releases 에 올림 |
| `.github/workflows/firebase.yml` | GitHub 에서 서버 코드를 Firebase 에 자동 배포 |

앱 빌드와 서버 배포는 모두 **GitHub Actions** 에서 실행됩니다. 아래 설정은 웹 브라우저에서 할 수 있습니다.

---

## 처음 한 번만 하는 설정

### 1. Firebase 프로젝트 만들기

1. <https://console.firebase.google.com> 에서 **프로젝트 추가**를 누릅니다.
2. 왼쪽 아래 **업그레이드**에서 **Blaze(종량제)** 요금제로 바꿉니다.
   - Cloud Functions 와 Storage 를 쓰려면 필요합니다. 개인 사용량은 대부분 무료 한도 안에 들어갑니다.
   - 걱정되면 Google Cloud 콘솔 → 결제 → **예산 및 알림**에서 월 예산 알림(예: 5,000원)을 걸어 두세요.
3. **Authentication** → 시작하기 → 로그인 방법에서 **Google** 을 켭니다.
4. **Firestore Database** → 데이터베이스 만들기 → 위치는 **asia-northeast3 (서울)** 을 고르고 **프로덕션 모드**로 만듭니다.
   - 서울이 아닌 곳을 골랐다면 `functions/src/index.ts` 의 `REGION` 을 같은 리전으로 바꾸세요.
5. **Storage** → 시작하기 → 같은 위치(서울)로 만듭니다.

### 2. 안드로이드 앱 등록

1. Firebase 프로젝트 설정(⚙️) → **앱 추가** → Android 를 고릅니다.
2. 패키지 이름: `com.happyfamily.contract`
3. **디버그 서명 인증서 SHA-1** 에 아래 값을 넣습니다. 이 값이 있어야 구글 로그인이 됩니다.
   ```
   B4:94:00:F0:EC:A4:39:C3:EB:A8:81:3B:7F:03:34:58:90:50:C0:C6
   ```
   (SHA-256 도 추가하면 좋습니다: `DD:18:99:06:73:7F:D2:61:99:D0:5F:B6:A2:97:42:BA:B3:9D:F4:02:AE:E9:0C:AC:A4:78:57:F1:35:60:CF:99`)
4. `google-services.json` 을 내려받습니다.
   - ⚠️ SHA-1 을 넣고, Google 로그인을 켠 **다음에** 받아야 합니다. 순서가 바뀌었다면 다시 받으세요.
5. GitHub 저장소 → **Settings → Secrets and variables → Actions → New repository secret**
   - 이름: `GOOGLE_SERVICES_JSON`
   - 값: `google-services.json` 파일 내용 전체를 그대로 붙여 넣습니다.

### 3. Claude API 키 발급

1. <https://console.anthropic.com> 에서 가입하고 결제 수단을 등록합니다.
2. **API Keys** 에서 키를 만듭니다 (`sk-ant-...`).
3. Google Cloud 콘솔(<https://console.cloud.google.com>)에서 Firebase 프로젝트를 고릅니다.
   **Secret Manager** → **보안 비밀 만들기**
   - 이름: `ANTHROPIC_API_KEY`
   - 값: 방금 만든 키

### 4. 서버 배포 (GitHub Actions 자동 배포)

1. Google Cloud 콘솔 → **IAM 및 관리자 → 서비스 계정 → 서비스 계정 만들기**
   - 이름 예: `github-deploy`
   - 역할: **Firebase 관리자**, **Cloud Functions 관리자**, **서비스 계정 사용자**, **Secret Manager 관리자**
2. 만든 서비스 계정 → **키** 탭 → 키 추가 → **JSON** 을 고르면 키 파일이 내려받아집니다.
3. GitHub 저장소 Settings → Secrets and variables → Actions
   - **Secrets** 탭: 이름 `FIREBASE_SERVICE_ACCOUNT`, 값은 JSON 키 파일 내용 전체
   - **Variables** 탭: 이름 `FIREBASE_PROJECT_ID`, 값은 Firebase 프로젝트 ID (예: `my-contract-app-1a2b3`)
4. `.firebaserc` 의 `YOUR_FIREBASE_PROJECT_ID` 도 같은 ID 로 바꿉니다. (Cloud Shell 로 배포할 때 사용)
5. `main` 브랜치에 코드가 올라가면 자동으로 배포됩니다. 수동 실행은 **Actions → Firebase 서버 배포 → Run workflow** 에서 할 수 있습니다.
   - 처음 배포할 때는 필요한 Google Cloud API 를 켜느라 5~10분 정도 걸립니다.
   - 권한 오류가 나면 오류 메시지에 나온 역할을 서비스 계정에 추가하고 다시 실행하세요.

<details>
<summary>대안: Google Cloud Shell(브라우저 터미널)에서 직접 배포</summary>

Google Cloud 콘솔 오른쪽 위 `>_` 아이콘으로 Cloud Shell 을 엽니다.

```bash
git clone https://github.com/happyfamily1984/Real-Estate-Contract.git
cd Real-Estate-Contract
npm install -g firebase-tools
firebase login --no-localhost
cd functions && npm ci && cd ..
firebase deploy --only functions,firestore,storage --project <프로젝트ID>
```
</details>

### 5. 앱 설치

1. GitHub 저장소 **Actions → Android APK** 가 `main` 에서 성공하면 **Releases** 에 `contract-app-N.apk` 가 올라옵니다.
2. 휴대폰 브라우저로 저장소의 Releases 페이지를 열어 APK 를 받고 설치합니다.
   (처음에는 "출처를 알 수 없는 앱 설치"를 허용해야 합니다.)
3. 앱을 열고 Google 계정으로 로그인 → **계약서 촬영**을 누릅니다.

새 버전이 나오면 같은 방법으로 덮어 설치하면 됩니다. 데이터는 클라우드에 있으므로 그대로 남아 있습니다.

---

## 사용 방법

1. **계약서 촬영** → 페이지마다 촬영 → 완료를 누릅니다.
2. 사진이 업로드되면 상세 화면으로 이동합니다. 보통 1~2분 안에 AI 분석 결과가 나타납니다.
   앱을 닫아도 서버에서 계속 처리됩니다.
3. 결과가 이상하면 오른쪽 위 🔄 **다시 분석**을 누릅니다.
4. 사진을 누르면 크게 볼 수 있습니다. 두 손가락으로 확대할 수 있습니다.

> AI 정리 결과는 참고용입니다. 금액·날짜·특약 같은 중요한 내용은 반드시 원본 사진과 대조하세요.

## 비용 (대략)

| 항목 | 비용 |
|---|---|
| Firebase (Firestore·Storage·Functions) | 개인 사용량은 대부분 무료 한도 안 |
| Claude API (`claude-opus-5-5`) | 계약서 1건(3~5장)당 약 200~500원 |

모델을 바꾸려면 `functions/src/analyze.ts` 의 `MODEL` 을 수정하세요.

## 개발자 참고

- 서버 코드 빌드 확인: `cd functions && npm ci && npm run build`
- 배포 전에 사진 몇 장으로 분석 결과 미리 보기:
  ```bash
  cd functions && npm run build
  ANTHROPIC_API_KEY=sk-ant-... node scripts/try-analyze.js page1.jpg page2.jpg
  ```
- 앱 로컬 빌드: `android/app/google-services.json` 을 넣고 `cd android && ./gradlew assembleDebug`
- 데이터 구조
  - Firestore `users/{uid}/contracts/{contractId}`: `status`(pending → processing → done / error), `imagePaths`, `analysis`, `memo`, `title`, `contractType`, `createdAt`
  - Storage `users/{uid}/contracts/{contractId}/page_N.jpg`
- 서명 키 `android/app/signing.keystore` 는 저장소에 들어 있습니다. 그래서 어디서 빌드해도 SHA-1 이 같습니다.
  저장소를 **비공개(Private)** 로 유지하세요. 공개 저장소라면 키를 새로 만들어 GitHub Secret 으로 옮기는 것이 안전합니다.
