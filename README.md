# 여정 · Travel App

여행 일정, 항공권 검색, 준비물, 예약 계획, 경비와 메모를 한곳에서 관리하는 **Android·iOS 모바일 앱**입니다. Kotlin Multiplatform과 Compose Multiplatform으로 화면·상태·네트워크 코드를 공유합니다.

라임과 잉크 블랙을 중심으로 굵은 제목, 둥근 여행 카드, 떠 있는 캡슐형 하단 메뉴를 사용합니다. 항공권과 저장 같은 주요 행동을 색과 짧은 반응으로 강조합니다.

## 모바일 화면

실행 중인 iPhone 16 Pro 시뮬레이터에서 캡처했습니다. 화면 이미지를 별도로 합성하거나 목업으로 대체하지 않았습니다.

| 여행 | 일정 | 준비물 |
|---|---|---|
| <img src="docs/screenshots/01-home.png" width="240" alt="다가오는 여행과 항공권 검색"/> | <img src="docs/screenshots/02-schedule.png" width="240" alt="날짜별 여행 일정"/> | <img src="docs/screenshots/03-checklist.png" width="240" alt="여행 준비물 체크리스트"/> |

| 예약 | 경비 | 항공권 |
|---|---|---|
| <img src="docs/screenshots/04-bookings.png" width="240" alt="직접 입력한 예약 계획"/> | <img src="docs/screenshots/05-budget.png" width="240" alt="예산과 지출 내역"/> | <img src="docs/screenshots/06-flights.png" width="240" alt="왕복 편도 항공권 검색 조건"/> |

| 장소 | 기록 | 설정 |
|---|---|---|
| <img src="docs/screenshots/07-places.png" width="240" alt="장소 검색 저장 지도 연결"/> | <img src="docs/screenshots/08-notes.png" width="240" alt="여행 메모"/> | <img src="docs/screenshots/09-settings.png" width="240" alt="화면 설정"/> |

| 다크 모드 | 일정 입력 | 날짜 선택 |
|---|---|---|
| <img src="docs/screenshots/10-dark.png" width="240" alt="다크 모드"/> | <img src="docs/screenshots/11-editor.png" width="240" alt="일정 추가 폼"/> | <img src="docs/screenshots/12-calendar.png" width="240" alt="달력에서 날짜 선택"/> |

| 메모 작성 | 지출 입력 | 입력 오류·키보드 |
|---|---|---|
| <img src="docs/screenshots/15-note-editor.png" width="240" alt="메모 작성 폼"/> | <img src="docs/screenshots/16-expense-editor.png" width="240" alt="지출 입력 폼"/> | <img src="docs/screenshots/14-keyboard.png" width="240" alt="필드 오류와 키보드 위 저장 버튼"/> |

| 예약 상세 | 공항 선택 | 항공편 검색 결과 |
|---|---|---|
| <img src="docs/screenshots/17-booking-detail.png" width="240" alt="예약 계획 상세"/> | <img src="docs/screenshots/18-airport-picker.png" width="240" alt="공항 선택"/> | <img src="docs/screenshots/13-flight-results.png" width="240" alt="앱에서 이동한 Google 항공편 결과"/> |

일정 상세·메모 편집

<img src="docs/screenshots/19-flight-detail.png" width="240" alt="일정 상세에서 메모 수정과 저장"/>

항공편 검색 결과는 외부 제공자 화면이며, 표시된 가격과 이용 가능 여부는 조회 시점에 따라 달라집니다.

## 주요 기능

- **여행 관리:** 날짜별 일정 조회·추가, 일정 카드의 메모 미리보기와 상세 메모 편집, 준비물 완료 표시, 예약 계획 상세 조회, 지출 추가·예산 합계, 여행 메모 작성.
- **항공권:** 출발·도착 공항 선택, 왕복·편도, 날짜 선택 후 Google 항공편 검색으로 이동. 노선과 날짜를 전달하며 인원·좌석·가격 확인과 예약은 제공자 화면에서 진행합니다.
- **장소:** 검색·카테고리·저장 필터, 즐겨찾기, Google 지도 열기.
- **모바일 UX:** 4개 하단 탭, 날짜 달력, 입력 필드별 오류, 저장 진행 상태, 실패 시 내용 유지, 탭 이동 시 검색 조건 유지, 다크 모드·동작 줄이기.
- **데이터 처리:** Spring API와 JSON 파일 저장, 입력 검증, POST 멱등성 키, 조회와 저장의 경쟁 방지.

## 실행

JDK 21, Android Studio와 Android SDK 37이 필요합니다. iOS 빌드는 macOS와 Xcode가 필요합니다.

### 1. 백엔드

```sh
cd backend
./run.sh
```

기본 주소는 `127.0.0.1:8088`입니다. Windows와 변경 후 재빌드 방법은 [백엔드 실행 문서](backend/README.md)를 확인하세요.

### 2. Android

Android Studio에서 `app/`을 열고 `androidApp`을 실행하거나:

```sh
cd app
./gradlew :androidApp:assembleDebug
```

APK는 `app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`에 생성됩니다. Android 에뮬레이터는 `10.0.2.2:8088`의 개발 PC 서버에 연결합니다.

### 3. iOS

`app/iosApp/MorrowTravel.xcodeproj`를 Xcode에서 열고 `MorrowTravel` scheme과 iPhone 시뮬레이터를 선택해 실행합니다. 시뮬레이터는 `127.0.0.1:8088`에 연결합니다. 실제 iPhone은 개발 팀 서명과 서버 연결 구성이 추가로 필요합니다.

[플랫폼별 상세 실행 방법](app/PLATFORMS.md) · [항공권 연결 방식](docs/FLIGHT_SEARCH_INTEGRATION.md)

## 기술 선택

| 영역 | 사용 기술 | 선택 이유 |
|---|---|---|
| 모바일 | Kotlin Multiplatform + Compose Multiplatform | Android·iOS의 UI와 업무 규칙을 공유하고 플랫폼 기능은 필요한 부분만 분리 |
| 비동기·네트워크 | Coroutines, StateFlow, Ktor, kotlinx.serialization | 공통 Kotlin에서 상태와 요청·응답 처리를 일관되게 관리 |
| 서버 | Kotlin + Spring Boot | 입력 검증, 계층 분리, API 통합 테스트와 향후 인증·DB 전환 |
| 현재 저장 | 원자적 JSON 파일 교체 | 로컬 실행과 변경 유지가 간단하며 저장 실패 시 기존 데이터를 보존 |
| 목표 DB | PostgreSQL | 여행·일정·지출·예약의 관계와 트랜잭션 관리. 현재 migration은 설계 단계 |

Flutter·React Native, SwiftUI 분리, 다른 백엔드·DB와의 비교는 [기술 스택 선정 문서](docs/planning/03_기술스택_선정과_비교.md)에 정리했습니다.

## 검증과 현재 범위

Android APK와 iOS 시뮬레이터 앱을 빌드·설치해 확인했습니다. 백엔드 통합 테스트 18개, 공통 데이터·외부 URL 테스트 11개를 통과했습니다. [검증 기록](docs/VERIFICATION.md)

현재 개발용 초기 여행 데이터로 실행하며, 서버는 로컬 단일 사용자 환경을 대상으로 합니다. 로그인·사용자별 권한, 다중 여행 생성·수정, 오프라인 동기화, 파일 첨부, 내부 실시간 운임 API와 예약·결제는 후속 범위입니다. 실제 기기와 스토어 배포는 별도 검증이 필요합니다.

## 문서

- [기획·PRD](docs/planning/01_기획_PRD.md)
- [시스템·데이터·API 설계](docs/planning/02_시스템_데이터_API_설계.md)
- [모바일 UI·항공권 수정사항](docs/planning/05_모바일_UI_재설계_및_항공권.md)
- [대학 발표 설명 원고](docs/planning/04_대학발표_설명원고.md)
- [디자인 기준](DESIGN.md) / [디자인 도구 적용 기록](docs/redesign-review.md) / [설치 기록](design-references/INSTALLATIONS.md)

발표 자료는 문서로 정리했으며 PPTX는 생성하지 않았습니다. MengTo, Taste, Vercel, 21st, awesome-design-md, ECC의 관련 지침을 활용했습니다. 21st 원격 검색은 계정 인증이 필요하며 웹 전용 자동 검사를 Kotlin 검증 결과로 간주하지 않습니다.

## 구성

```text
app/                 Android·iOS 호스트와 공통 Compose 코드
backend/             Spring API와 통합 테스트
db/migrations/       PostgreSQL 전환 설계
docs/planning/       기획·PRD·설계·발표 원고
docs/screenshots/    실제 모바일 화면 캡처
design-references/   설치·디자인 방향 기록
```

사진: [Ale / Unsplash](https://unsplash.com/photos/a-tall-white-building-sitting-on-top-of-a-lush-green-hillside-SFT7yBakla0). 글꼴: Noto Sans KR, [OFL](docs/FONT_LICENSE.txt).
