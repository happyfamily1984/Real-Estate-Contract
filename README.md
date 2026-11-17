# 계약서함 — 부동산 계약서 AI 정리 앱 (Android)

부동산 계약서를 사진으로 찍으면 AI(Google Gemini)가 계약 내용을 정리하고, 사진과 결과를 클라우드에 보관하는 안드로이드 앱입니다.
**모든 구성 요소가 무료 요금제로 동작합니다.** 카드 등록이나 서버, PC가 필요 없습니다.

- 📷 **촬영·파일 업로드**: 구글 문서 스캐너로 찍거나, 휴대폰에 있는 **PDF·JPG 파일**을 올립니다 (한 번에 최대 15페이지).
- 🤖 **AI 분석**: 계약 종류, 주소, 보증금·월세·매매대금, 계약금·중도금·잔금 일정, 당사자, 중개사, 특약사항을 정리하고 **확인이 필요한 부분**도 알려 줍니다.
  - **분양계약서**: 분양금액을 매수 취득금액으로 봅니다.
  - **경매 낙찰 서류**: 잔금 납부일·매각허가결정일 등 식별 가능한 날짜를 취득일로, 잔금 납부금액을 취득금액으로 봅니다. 날짜가 확실하지 않으면 작게 **예상**으로 표시합니다.
- 📁 **계약 폴더**: 부동산(같은 단지·호수)마다 폴더가 자동으로 만들어집니다. 이름은 AI 가 짧게 붙이고(예: 과천 미소가, 목동 건영) 직접 바꿀 수 있습니다. 폴더 안 계약서는 계약 시점 순서로 정렬됩니다(오래된 계약부터 ↔ 최근 계약부터 전환).
- 📝 **계약내용정리**: 폴더마다 한눈에 보는 정리 글을 만듭니다.
  ```
  19년 03월 02일 매매계약 매수 금액 5억 2,000만원 · 현재 진행중
  21년 04월 10일 전세계약 임대 금액 3억 5,000만원 기간 21년 05월 01일 ~ 23년 04월 30일
  23년 04월 01일 월세계약 임대 보증금 5,000만원 월세 120만원 기간 23년 05월 01일 ~ 27년 04월 30일 · 현재 진행중
  ```
  직접 고치거나 내용을 더할 수 있고, **계약서 내용적용**을 누르면 계약서에서 뽑은 내용으로 다시 정리합니다. **복사**(클립보드)와 **내보내기**(텍스트 파일 공유)를 지원합니다.
- 👤 **이해관계자**: 폴더마다 이해관계자(계약서를 보관하는 사람)를 정하면 그 사람 기준으로 매수·매도·임대·임차와 **진행중/완료**가 정해집니다. 보유·임차 중이면 진행중, 매도를 마쳤으면 완료입니다. 홈 화면에서 이해관계자별로 걸러 볼 수 있습니다.
- 📄 **이해관계자별 계약내용정리 문서**: 그 사람과 관련된 과거부터의 모든 계약을 폴더별로 정리한 문서를 만들고, 수정·적용·복사·내보내기를 할 수 있습니다.
- ⚡ **빠른 정리**: 계약서를 올릴 때 한 번만 사진을 AI 로 분석하고, 계약 핵심 내용을 화면에 보이지 않는 텍스트로 저장해 둡니다. 정리·적용은 사진 없이 이 텍스트만 쓰므로 몇 초 안에 끝납니다.
- ☁️ **클라우드 보관**: 사진과 결과는 Firebase Firestore 에 저장되고, 같은 구글 계정이면 어느 기기에서나 보입니다.
- 🔎 폴더·주소·이름·메모 검색, 메모, 다시 분석, 폴더 옮기기, 삭제, 사진 확대 보기.

## 구조 (전부 무료)

```
[안드로이드 앱] ──로그인──▶ Firebase Authentication (Google)        무료
      │
      ├──사진·결과 저장──▶ Cloud Firestore                          무료 (1GB, 하루 읽기 5만·쓰기 2만)
      │
      └──사진 분석──▶ Firebase AI Logic ──▶ Gemini Developer API    무료 등급
```

| 항목 | 쓰는 것 | 비용 |
|---|---|---|
| 로그인 | Firebase Authentication | 무료 |
| 사진·데이터 저장 | Cloud Firestore (사진은 장당 0.7MB 이하로 압축) | 무료 한도 1GB → 계약서 약 500건 |
| AI 분석 | Firebase AI Logic + Gemini Developer API | 무료 등급 (하루 사용량 제한 있음) |
| 앱 빌드·배포 | GitHub Actions + Releases (공개 저장소) | 무료 |

- Firebase 요금제는 **Spark(무료)** 그대로 둡니다. Blaze 로 올리지 않으면 요금이 청구될 일이 없습니다.
- 무료 한도를 넘으면 요금이 나가는 대신 그날 기능이 멈추고 다음 날 다시 됩니다.
- 분석은 휴대폰에서 백그라운드로 실행됩니다. 다른 화면으로 가도 계속되고, 인터넷이 끊기면 연결된 뒤 다시 시도합니다.

> ⚠️ **개인정보 주의**: Gemini **무료** 등급에서는 보낸 내용(계약서 사진)을 Google 이 서비스 개선에 쓸 수 있고, 사람이 검토할 수도 있습니다.
> 계약서에는 이름·전화번호·주소가 들어 있으니 이 점을 알고 쓰세요. 원하지 않으면 Firebase 프로젝트를 Blaze 로 올리고 유료 등급을 쓰면 됩니다(코드 변경 없음, 사용한 만큼 과금).

## 보안: 공개 저장소에서도 안전한 이유

| 비밀 정보 | 저장소에 들어 있나? | 어디에 두나 |
|---|---|---|
| 앱 서명 키 | **암호화된 파일**만 (`android/app/signing.keystore.enc`, AES-256) | 복호화 비밀번호는 GitHub Secret `KEYSTORE_PASSWORD` |
| Firebase 설정 (`google-services.json`) | 아니요 | GitHub Secret `GOOGLE_SERVICES_JSON` |
| Gemini API 키 | 아니요. 앱에도 없음 | Firebase 프로젝트 안에서 관리 |
| 계약서 데이터 | 아니요 | Firestore. 보안 규칙(`firestore.rules`)으로 본인만 접근 |

- GitHub Secret 은 암호화되어 저장되고, 저장소를 공개해도 다른 사람이 볼 수 없습니다.
- `google-services.json` 안의 값은 원래 앱 안에 들어가는 공개용 값입니다. 데이터는 보안 규칙이 지킵니다.

---

## 처음 한 번만 하는 설정 (모두 웹 브라우저에서)

### 1. Firebase 프로젝트 만들기 (무료 Spark 요금제)

1. <https://console.firebase.google.com> 에서 **프로젝트 추가**를 누릅니다. 요금제는 Spark(무료) 그대로 둡니다.
2. **Authentication** → 시작하기 → 로그인 방법에서 **Google** 을 켭니다.
3. **Firestore Database** → 데이터베이스 만들기 → 위치 **asia-northeast3 (서울)** → **프로덕션 모드**로 만듭니다.
4. Firestore → **규칙** 탭에 이 저장소의 [`firestore.rules`](firestore.rules) 내용을 통째로 붙여 넣고 **게시**합니다.
   - ⚠️ 앱을 업데이트하면서 규칙 파일이 바뀌었다면 **다시 붙여 넣고 게시**해야 합니다. 안 하면 저장이 거부됩니다.
5. 왼쪽 메뉴 **AI Logic**(또는 빌드 → AI Logic) → 시작하기 → **Gemini Developer API** 를 고릅니다.
   - API 키는 Firebase 가 알아서 만들고 관리합니다. 따로 복사할 필요가 없습니다.

### 2. 안드로이드 앱 등록

1. Firebase 프로젝트 설정(⚙️) → **앱 추가** → Android 를 고릅니다.
2. 패키지 이름: `com.happyfamily.contract`
3. **SHA-1** 과 **SHA-256** 인증서 지문을 넣습니다. 이 값이 있어야 구글 로그인이 됩니다.
   ```
   SHA-1:   20:8C:C4:DE:CD:4B:75:D9:A8:5F:18:AA:10:84:1D:05:67:6B:6E:39
   SHA-256: DA:C7:78:27:B3:85:BE:30:95:F3:39:0D:4C:F6:54:24:F1:64:9B:B0:0B:ED:2D:98:78:21:B5:FF:A1:A6:99:23
   ```
   - 예전 버전 안내대로 `B4:94:00:F0:...` 지문을 등록했다면 **삭제**하세요. 그 키는 더 이상 쓰지 않습니다.
4. `google-services.json` 을 내려받습니다.
   - ⚠️ 지문을 넣고, Google 로그인과 AI Logic 을 켠 **다음에** 받아야 합니다. 순서가 바뀌었다면 다시 받으세요.

### 3. GitHub Secret 등록

GitHub 저장소 → **Settings → Secrets and variables → Actions → New repository secret**

| 이름 | 값 |
|---|---|
| `GOOGLE_SERVICES_JSON` | `google-services.json` 파일 내용 전체 |
| `KEYSTORE_PASSWORD` | 서명 키 비밀번호 (따로 전달받은 값) |

> 서명 키 비밀번호를 잃어버리면 키를 새로 만들어야 하고, 그러면 Firebase 의 SHA 지문도 바꿔야 합니다.
> 비밀번호 관리 앱 등에 따로 적어 두세요.

### 4. 앱 설치

1. 저장소 **Actions → Android APK** 를 실행합니다. 코드가 바뀌면 자동으로 실행되고, **Run workflow** 로 직접 실행할 수도 있습니다.
2. 기본 브랜치에서 성공하면 **Releases** 에 `contract-app-N.apk` 가 올라옵니다.
3. 휴대폰 브라우저로 저장소의 Releases 페이지를 열어 APK 를 받고 설치합니다.
   처음에는 "출처를 알 수 없는 앱 설치"를 허용해야 합니다.
4. 앱을 열고 Google 계정으로 로그인 → **계약서 촬영**을 누릅니다.

새 버전이 나오면 같은 방법으로 덮어 설치하면 됩니다. 데이터는 클라우드에 있으므로 그대로 남습니다.

### (선택) 다른 사람이 내 무료 한도를 쓰지 못하게 하기

저장소와 Releases 가 공개라서 누구나 APK 를 설치할 수 있습니다. 다른 사람은 **내 계약서를 볼 수 없지만**, 가입해서 내 무료 사용량을 쓸 수는 있습니다.
본인과 가족이 한 번씩 로그인한 뒤 다음을 하면 막을 수 있습니다.

- Firebase → Authentication → **설정 → 사용자 작업**에서 **생성(가입) 허용**을 끕니다.
  (메뉴가 보이지 않으면 건너뛰어도 됩니다.)

---

## 사용 방법

1. **계약서 촬영** → 페이지마다 촬영 → 완료를 누릅니다.
2. 사진이 저장되면 상세 화면으로 이동합니다. 보통 30초~1분 안에 AI 분석 결과가 나타납니다.
3. 결과가 이상하면 오른쪽 위 🔄 **다시 분석**을 누릅니다.
4. 사진을 누르면 크게 볼 수 있습니다. 두 손가락으로 확대할 수 있습니다.

> AI 정리 결과는 참고용입니다. 금액·날짜·특약 같은 중요한 내용은 반드시 원본 사진과 대조하세요.

## 개발자 참고

- 테스트: `cd android && ./gradlew testDebugUnitTest` (정리 글 형식·진행중/완료 규칙 테스트, `-Proborazzi.record=true` 를 붙이면 화면 스크린샷도 `app/build/outputs/roborazzi` 에 생성)
- 로컬 빌드: `android/app/google-services.json` 을 넣고 `cd android && ./gradlew assembleDebug`
  - 서명 키 없이 빌드하면 각 PC 의 디버그 키로 서명되므로 구글 로그인은 되지 않습니다. 로그인을 시험하려면 키를 복호화하세요.
    ```bash
    cd android
    export KEYSTORE_PASSWORD='비밀번호'
    openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -in app/signing.keystore.enc -out app/signing.keystore -pass env:KEYSTORE_PASSWORD
    ./gradlew assembleRelease
    ```
    `app/signing.keystore` 는 `.gitignore` 에 들어 있어 커밋되지 않습니다.
- Gemini 모델: `GeminiAnalyzer.kt` 의 `MODEL_CANDIDATES`. 최신 Flash 를 가리키는 별칭(`gemini-flash-latest`)을 먼저 시도하고, 실패하면 다음 이름으로 넘어갑니다.
- 데이터 구조 (Firestore)
  - `users/{uid}/contracts/{contractId}`: `status`(pending → processing → done / error), `pageCount`, `thumbnail`, `analysis`, `memo`, `title`, `contractType`, `folderId`, `sortDate`, `digest`(숨김 핵심 텍스트), `createdAt`
  - `users/{uid}/folders/{folderId}`: `name`, `address`, `stakeholder`, `status`(ongoing / done), `summaryText`, `summaryEntries`, `summaryEdited`
  - `users/{uid}/stakeholders/{id}`: `name`, `isDefault`, `report`, `reportEdited`
  - `users/{uid}/contracts/{contractId}/pages/{n}`: `image`(JPEG, 0.7MB 이하)
