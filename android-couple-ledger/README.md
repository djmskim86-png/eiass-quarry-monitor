# 우리가계부 (OurLedger) — 부부 공유 가계부 앱 구조

카드 결제 문자(SMS)를 자동으로 읽어 지출을 기록하고, 정해진 시간마다 배우자와
동기화하는 안드로이드 앱의 **아키텍처 스캐폴드**입니다. 요청하신 4가지 기능을
각각 어떤 컴포넌트가 담당하는지 먼저 정리하고, 그 아래에 실제 코드 구조를 둡니다.

| 요구사항 | 담당 컴포넌트 |
|---|---|
| 카드선택(국민카드 등 이름 추가) | `ui/cards/*`, `data/local/entity/CardEntity`, `sms/parser/CardIssuerRules` |
| 정해진 시간마다 문자 읽어서 업데이트 | `sms/SmsReceiver`(실시간) + `sync/PeriodicSmsScanWorker`(주기 보완) |
| 정해진 시간마다 서로 공유·동기화 | `sync/PeriodicSyncWorker` + Firebase Firestore |
| 부부 계정 연결 | `data/repository/HouseholdRepository` (초대 코드 방식) |

---

## 1. 전체 그림

```
문자 도착
  ├─ [실시간] SmsReceiver(BroadcastReceiver) ──┐
  └─ [주기, N분마다] PeriodicSmsScanWorker ─────┼─> SmsParser (카드사별 정규식)
                                                 │        │
                                                 │        ▼
                                                 │  등록된 카드(issuerCode) 매칭?
                                                 │        │ Yes
                                                 └────────▼
                                          SmsTransactionRecorder
                                                    │
                                                    ▼
                                         Room(로컬 DB) — 즉시 화면 반영
                                                    │
                                    [주기, N분마다] PeriodicSyncWorker
                                                    │  push 미동기화 내역
                                                    ▼
                                   Firebase Firestore (households/{id}/transactions)
                                                    │  pull 배우자가 올린 내역
                                                    ▼
                                              내 기기 Room에 병합
```

- **왜 두 겹(실시간 + 주기)인가**: `SmsReceiver`는 앱이 켜져 있고 리시버가 살아있을 때
  즉시 반응하지만, 기기가 리시버 프로세스를 죽이거나 배터리 최적화로 지연되는 경우가
  실무에서 흔합니다. `PeriodicSmsScanWorker`가 지난 스캔 이후 문자함을 다시 훑어서
  "놓친 문자"를 보완합니다. 이게 "정해진 시간마다 문자 읽어서 사용량 업데이트"의 본체입니다.
- **왜 Firestore인가**: 부부 두 사람이 각자 폰을 쓰므로, 동기화는 결국 "각자 로컬 DB ↔
  공용 서버" 구조가 되어야 합니다. Firestore는 오프라인 캐시, 실시간 리스너,
  간단한 보안 규칙(같은 가구 구성원만 read/write)을 기본 제공해서 이런 개인/커플용
  앱에는 백엔드를 직접 만드는 것보다 훨씬 적은 노력으로 시작할 수 있습니다.
- **거래 id는 결정적(deterministic) 해시**: `(cardId + 발생시각 + 금액 + 가맹점)`을
  해시해서 id로 씁니다. 그래서 실시간 리시버와 주기 스캔이 같은 문자를 중복 처리해도,
  Room(`OnConflictStrategy.IGNORE`)과 Firestore(문서 id 고정) 양쪽에서 자동으로
  중복 저장이 막힙니다.

## 2. 폴더 구조

```
app/src/main/java/com/coupleledger/app/
├── OurLedgerApp.kt                 # Application, WorkManager+Hilt 연결
├── MainActivity.kt                 # NavHost, SMS 권한 요청
├── di/                              # Hilt DI 모듈 (DB, DataStore, Firebase)
├── data/
│   ├── local/
│   │   ├── entity/                 # CardEntity, TransactionEntity, HouseholdEntity
│   │   ├── dao/                    # CardDao, TransactionDao (월별 합계 쿼리 포함)
│   │   └── AppDatabase.kt
│   ├── remote/                     # Firestore 매핑/데이터소스, HouseholdSession
│   └── repository/                 # CardRepository, TransactionRepository, HouseholdRepository
├── sms/
│   ├── SmsReceiver.kt              # 실시간 캡처
│   ├── SmsTransactionRecorder.kt   # 파싱 결과 → 거래로 저장 (공통 로직)
│   ├── TransactionIdGenerator.kt   # 중복 방지용 결정적 id
│   └── parser/
│       ├── CardIssuerRule.kt       # 카드사별 발신자/정규식 규칙 (국민/신한/삼성/현대/우리/하나/롯데/NH)
│       └── SmsParser.kt            # 문자 1통 → 금액/가맹점/시각 추출
├── sync/
│   ├── ProcessSmsWorker.kt         # 실시간 문자 1통 처리 워커
│   ├── PeriodicSmsScanWorker.kt    # 주기적 문자함 재스캔
│   ├── PeriodicSyncWorker.kt       # 주기적 부부간 push/pull
│   ├── SyncScheduler.kt            # 두 워커의 주기 등록/재등록 (최소 15분)
│   ├── SyncPreferences.kt          # 마지막 스캔 시각 저장
│   └── BootRescheduleReceiver.kt   # 재부팅 후 워커 재등록
└── ui/
    ├── onboarding/                 # 가계부 생성(초대코드 발급) / 참여
    ├── dashboard/                  # 이번 달 합계, 카드별 사용액
    ├── cards/                      # 카드 목록·추가(카드사 선택 + 별칭 입력)
    ├── transactions/               # 전체 내역 리스트
    └── settings/                   # 동기화 주기 설정, 즉시 동기화
```

## 3. 데이터 모델 요약

- **Household(가구)**: 부부 단위. `inviteCode`로 배우자가 참여. Firestore가 원본(source of truth).
- **Card**: `issuerCode`(카드사, 파싱 규칙 매칭용) + `nickname`(사용자가 직접 붙이는 이름).
  카드를 먼저 등록해야 그 카드사 문자가 자동 인식됩니다(광고/인증 문자를 걸러내는
  1차 필터 역할도 겸함).
- **Transaction**: 금액/가맹점/발생시각/누가 쓴 카드인지/원본 문자(`rawSmsBody`, 디버깅용).
  `source`로 SMS 자동/수동 입력을 구분, `syncStatus`로 Firestore 업로드 여부를 추적.

## 4. 시작하기 (로컬 빌드)

1. Android Studio(최신)에서 `android-couple-ledger/` 폴더를 프로젝트로 엽니다.
2. [Firebase 콘솔](https://console.firebase.google.com)에서 새 프로젝트를 만들고
   Android 앱(패키지명 `com.coupleledger.app`)을 등록한 뒤 `google-services.json`을
   `app/` 폴더에 넣습니다(이 파일은 개인 키를 포함하므로 `.gitignore`에 이미 제외되어 있습니다).
3. Firestore를 "테스트 모드"로 생성한 뒤, 아래 보안 규칙으로 교체합니다(가구 구성원만 접근):
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /households/{householdId} {
         allow read, update: if request.auth.uid in resource.data.memberUserIds;
         allow create: if request.auth != null;
         match /{subcollection}/{docId} {
           allow read, write: if request.auth.uid in
             get(/databases/$(database)/documents/households/$(householdId)).data.memberUserIds;
         }
       }
     }
   }
   ```
4. Firebase Authentication에서 "익명 로그인"을 활성화합니다(온보딩 화면이 익명 로그인으로
   시작하도록 되어 있음 — 나중에 Google 로그인으로 승격 가능).
5. Gradle Sync 후 실행.

## 5. ⚠️ 꼭 알아야 할 제약: SMS 권한과 스토어 정책

`READ_SMS`/`RECEIVE_SMS` 권한은 **Google Play 정책상 "기본 문자 앱(default SMS handler)"으로
등록된 앱만 스토어에 배포**할 수 있습니다(가계부/가족예산 앱이라는 이유로는 예외를
받을 수 없습니다). 즉:

- **부부 두 사람만 쓰는 용도(스토어 미공개)** 라면 문제 없습니다 — APK를 직접
  빌드해서 사이드로드하거나, Firebase App Distribution / 내부 테스트로 배포하면
  Play 정책 심사를 받지 않으므로 이 앱 구조 그대로 사용 가능합니다.
- **Play 스토어에 공개 배포**하려면 이 앱을 기본 문자 앱으로 만들어야 하므로
  (문자 수신·발신 UI까지 구현해야 함) 지금 구조보다 훨씬 커집니다. 이 경우 보통
  "문자 직접 파싱" 대신 카드사 알림(Notification Listener, 이것도 민감 권한이지만
  결제 알림 리더 앱들이 실제로 쓰는 방식)이나, 카드사가 제공하는 오픈뱅킹/카드사
  API 연동으로 우회합니다.

지금 스캐폴드는 "부부 둘이서 사이드로드해서 쓰는 개인 앱"을 전제로, 문자 직접
파싱 방식으로 설계했습니다.

## 6. 다음 단계로 다듬을 만한 것들

- `sms/parser/CardIssuerRule.kt`의 정규식은 실제 수신하는 문자 샘플로 검증/보정 필요
  (카드사가 포맷을 바꾸는 경우가 종종 있음).
- 카테고리 자동 분류(가맹점명 기반), 월 예산 대비 알림(`CardEntity.monthlyBudget` 필드는
  이미 마련해둠).
- Firestore 대신 자체 백엔드로 바꾸고 싶다면 `data/remote/*`만 교체하면 되고,
  `data/repository/*` 상위 레이어는 그대로 재사용됩니다.
- 현재 UI는 기능 검증용 최소 구현입니다(디자인 다듬기 필요).
