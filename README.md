# 여정 · Travel App

여행 일정, 항공권 검색, 준비물, 예약 계획, 경비와 메모를 한곳에서 관리하는 **Android·iOS 모바일 앱**입니다. Kotlin Multiplatform과 Compose Multiplatform으로 화면·상태·네트워크 코드를 공유합니다.

라임과 잉크 블랙을 중심으로 굵은 제목, 둥근 여행 카드, 떠 있는 캡슐형 하단 메뉴를 사용합니다. 항공권과 저장 같은 주요 행동을 색과 짧은 반응으로 강조합니다.

현재는 **대학 기획 발표 단계**입니다. 실시간 앱 시연 없이 실제 앱 화면 3개와 시스템 구조도로 목적·기능·기술 선택을 설명합니다. [발표 구성](docs/planning/06_기획발표_핵심구성.md) · [디자인 미리보기](docs/planning/07_발표자료_디자인가이드.md)

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

| 일정 수정 | 일정 삭제 확인 | 준비 항목 추가 |
|---|---|---|
| <img src="docs/screenshots/20-event-edit.png" width="240" alt="기존 일정 내용 수정"/> | <img src="docs/screenshots/21-event-delete.png" width="240" alt="일정과 메모 삭제 전 확인"/> | <img src="docs/screenshots/22-task-create.png" width="240" alt="준비 항목 직접 추가"/> |

| 준비 항목 수정 | 준비 항목 삭제 확인 |
|---|---|
| <img src="docs/screenshots/23-task-edit.png" width="240" alt="완료 상태를 유지하며 준비 항목 수정"/> | <img src="docs/screenshots/24-task-delete.png" width="240" alt="항목 이름을 보여주는 삭제 확인"/> |

| 여행 목록 | 새 여행 | 여행 수정 | 여행 보관 |
|---|---|---|---|
| <img src="docs/screenshots/25-trip-list.png" width="200" alt="여러 여행 선택과 검색"/> | <img src="docs/screenshots/26-trip-create.png" width="200" alt="목적지와 기간을 입력하는 새 여행"/> | <img src="docs/screenshots/29-trip-edit.png" width="200" alt="여행 정보 수정"/> | <img src="docs/screenshots/30-trip-archive.png" width="200" alt="여행 보관 확인"/> |

| 다른 도시 홈 | 여행별 항공 검색 | 목록 검색 |
|---|---|---|
| <img src="docs/screenshots/27-new-trip-home.png" width="240" alt="도쿄 여행 홈"/> | <img src="docs/screenshots/28-new-trip-flights.png" width="240" alt="도쿄 여행 날짜와 공항"/> | <img src="docs/screenshots/31-trip-search.png" width="240" alt="여행 목록 검색 상태 유지"/> |

| 보관 목록 | 보관 해제 | 여행 삭제 확인 |
|---|---|---|
| <img src="docs/screenshots/32-trip-archived-list.png" width="240" alt="보관한 여행 목록"/> | <img src="docs/screenshots/33-trip-unarchive.png" width="240" alt="여행 보관 해제 확인"/> | <img src="docs/screenshots/34-trip-delete.png" width="240" alt="여행과 관련 자료 삭제 전 영향 확인"/> |

| 여행이 없는 홈 | 첫 여행 생성 후 홈 |
|---|---|
| <img src="docs/screenshots/35-empty-trips.png" width="240" alt="첫 여행 생성 안내"/> | <img src="docs/screenshots/36-first-trip-home.png" width="240" alt="빈 저장소에서 생성한 첫 여행 홈"/> |

| 국가 검색 | 일본의 도시·지역 | 도시 검색 |
|---|---|---|
| <img src="docs/screenshots/37-country-search.png" width="240" alt="국가 검색"/> | <img src="docs/screenshots/38-country-places.png" width="240" alt="일본의 도시·지역"/> | <img src="docs/screenshots/39-place-search.png" width="240" alt="도시 검색"/> |

| 목적지 선택 결과 | 국가 변경 | 기존 도시 초기화 |
|---|---|---|
| <img src="docs/screenshots/40-selected-destination.png" width="240" alt="목적지 선택 결과"/> | <img src="docs/screenshots/41-changed-country.png" width="240" alt="국가 변경"/> | <img src="docs/screenshots/42-reset-place.png" width="240" alt="기존 도시 초기화"/> |

| 국가별 검색 범위 | 검색 키보드 | 목적지 공항 연동 |
|---|---|---|
| <img src="docs/screenshots/43-country-scoped-empty.png" width="240" alt="국가별 검색 범위"/> | <img src="docs/screenshots/44-place-keyboard.png" width="240" alt="검색 키보드"/> | <img src="docs/screenshots/45-selected-city-flights.png" width="240" alt="목적지 공항 연동"/> |

지역 데이터 출처

<img src="docs/screenshots/46-destination-sources.png" width="240" alt="지명 데이터 출처와 라이선스"/>

| 예약 추가 | 예약 수정 | 시간대 검색 |
|---|---|---|
| <img src="docs/screenshots/47-reservation-create.png" width="240" alt="종류별 예약 추가"/> | <img src="docs/screenshots/48-reservation-edit.png" width="240" alt="기존 예약 내용 유지와 수정"/> | <img src="docs/screenshots/49-reservation-time-zone.png" width="240" alt="예약 현지 시각의 시간대 검색"/> |

| 예약 삭제 확인 | 활동 예약 상세 |
|---|---|
| <img src="docs/screenshots/50-reservation-delete-confirm.png" width="240" alt="예약처 취소와 구분한 앱 기록 삭제"/> | <img src="docs/screenshots/51-reservation-activity-detail.png" width="240" alt="저장한 활동 예약의 현지 시간과 시간대"/> |

항공편 검색 결과는 외부 제공자 화면이며, 표시된 가격과 이용 가능 여부는 조회 시점에 따라 달라집니다.

| 여행 서류 목록 | 파일 업로드 | 예약 연결 |
|---|---|---|
| <img src="docs/screenshots/52-documents-empty.png" width="240" alt="여행 서류가 없는 목록"/> | <img src="docs/screenshots/53-document-upload.png" width="240" alt="선택한 PDF 업로드 확인"/> | <img src="docs/screenshots/54-document-reservation-link.png" width="240" alt="같은 여행의 예약과 서류 연결"/> |

| 서류 상세 | 서류 삭제 확인 |
|---|---|
| <img src="docs/screenshots/55-document-detail.png" width="240" alt="파일 정보와 저장 상태"/> | <img src="docs/screenshots/56-document-delete-confirm.png" width="240" alt="서류 기록 삭제 전 확인"/> |

## 주요 기능

- **여행 관리:** 여러 여행의 생성·선택·검색·수정·보관/해제·삭제, 여행별 일정·준비물·경비·예약·장소·기록 분리. 이름·도시/지역·국가·시작/종료일·인원·원화 예산을 편집하며, 여행이 없으면 새 여행 생성으로 안내합니다.
- **목적지 선택:** 국가를 검색·선택한 뒤 해당 국가의 도시·지역만 검색합니다. 국가 변경 시 이전 도시를 초기화하고, 한글·영문 검색과 목록 밖 지역의 직접 입력을 지원합니다. 오프라인 지명 데이터와 라이선스는 [데이터 문서](docs/DESTINATION_DATA.md)에 기록했습니다.
- **여행 상세:** 날짜별 일정 조회·추가·수정·삭제, 일정 카드의 메모 미리보기와 상세 메모 편집, 준비물 추가·수정·삭제·완료 표시, 지출 추가·예산 합계, 여행 메모 작성.
- **예약 기록:** 항공·숙소·교통·활동의 직접 추가·조회·수정·삭제, 장소·예약처·예약번호·메모·상태, 시각 미정과 현지 시간대, 체크아웃·취소 마감·규정 메모. 날짜변경선을 넘는 이동은 실제 시각으로 비교하고 서머타임 중복 시각은 직접 선택합니다. [예약 계약](docs/RESERVATION_API.md)
- **항공권:** 출발·도착 공항 선택, 왕복·편도, 날짜 선택 후 Google 항공편 검색으로 이동. 노선과 날짜를 전달하며 인원·좌석·가격 확인과 예약은 제공자 화면에서 진행합니다.
- **장소:** 검색·카테고리·저장 필터, 즐겨찾기, Google 지도 열기.
- **여행 서류:** PDF·PNG·JPEG 선택·업로드, 예약 연결, 표시 이름·연결 편집, 다운로드·캐시·삭제. 파일 크기·형식·해시를 확인하며, 네이티브 미리보기와 오프라인 재시작의 실제 검수 범위는 검증 기록에 구분합니다. [서류 API 계약](docs/DOCUMENTS_API.md)
- **기기 보관:** 여행 조회 스냅샷과 검증한 파일 캐시. 오프라인에서는 보관한 여행을 조회하며 편집·자동 동기화는 후속 범위입니다.
- **모바일 UX:** 4개 하단 탭, 날짜 달력, 입력 필드별 오류, 저장 진행 상태, 실패 시 내용 유지, 탭 이동 시 검색 조건 유지, 다크 모드·동작 줄이기.
- **데이터 처리:** Spring API와 여행별 JSON 저장, 입력 검증, POST 멱등성 키, 조회와 저장의 경쟁 방지. 기존 단일 여행 저장 파일은 원본 바이트 백업 후 schema 2로 이전합니다.

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
| 현지 시각 | kotlinx-datetime + 서버 JDK ZoneRules | 모바일 공통 시간대 처리와 서버의 엄격한 gap·overlap 검증; 고정 UTC 차이만으로 날짜를 계산하지 않음 |
| 서버 | Kotlin + Spring Boot | 입력 검증, 계층 분리, API 통합 테스트와 향후 인증·DB 전환 |
| 현재 저장 | 원자적 JSON 파일 교체 | 로컬 실행과 변경 유지가 간단하며 저장 실패 시 기존 데이터를 보존 |
| 목표 DB | PostgreSQL | 여행·일정·지출·예약의 관계와 트랜잭션 관리. 현재 migration은 설계 단계 |

Flutter·React Native, SwiftUI 분리, 다른 백엔드·DB와의 비교는 [기술 스택 선정 문서](docs/planning/03_기술스택_선정과_비교.md)에 정리했습니다.

## 검증과 현재 범위

최종 Android APK와 iOS 시뮬레이터 앱 빌드가 성공했습니다. 백엔드 63개, 공통 코드의 JVM 테스트 70개·iOS 시뮬레이터 테스트 69개가 통과했습니다. JVM·iOS 수치는 공통 시나리오가 겹치므로 합산하지 않습니다. 실제 화면 캡처 56개와 설치·화면 조작·네이티브 파일 확인 범위는 [검증 기록](docs/VERIFICATION.md)에 구분했습니다.

서버는 로컬 단일 사용자 개발 환경을 대상으로 합니다. 기존 개발 여행 데이터는 보존하며 새 여행에는 예시 일정이나 준비물을 복사하지 않습니다. 로그인·사용자별 권한, 오프라인 편집·자동 동기화, PostgreSQL 실제 연결, 내부 실시간 운임 API와 예약·결제는 후속 목표입니다. 현재 기획 발표를 우선하며 운영 기능 확장은 보류합니다. 장기 요구사항과 실제 기기·배포 검증은 [전체 목표 감사](GOAL_AUDIT.md)에 기록합니다.

## 문서

- [기획·PRD](docs/planning/01_기획_PRD.md)
- [시스템·데이터·API 설계](docs/planning/02_시스템_데이터_API_설계.md)
- [현재 다중 여행 API·저장 계약](docs/MULTITRIP_API.md)
- [예약 API·시간대·스키마 계약](docs/RESERVATION_API.md)
- [여행 서류 API·파일 보관 계약](docs/DOCUMENTS_API.md)
- [전체 목표 진행 감사](GOAL_AUDIT.md)
- [모바일 UI·항공권 수정사항](docs/planning/05_모바일_UI_재설계_및_항공권.md)
- [대학 발표 설명 원고](docs/planning/04_대학발표_설명원고.md)
- [기획 발표 핵심 구성·앱 화면](docs/planning/06_기획발표_핵심구성.md)
- [발표 디자인·시스템 구조도](docs/planning/07_발표자료_디자인가이드.md)
- [디자인 기준](DESIGN.md) / [디자인 도구 적용 기록](docs/redesign-review.md) / [설치 기록](design-references/INSTALLATIONS.md)

발표 구성·설명 원고·실제 앱 캡처·구조도·디자인 방향을 문서와 이미지로 정리했습니다. PPTX는 아직 생성하지 않았습니다. MengTo, Taste, Vercel, 21st, awesome-design-md, ECC의 관련 지침을 활용했습니다. 21st 원격 검색은 계정 인증이 필요하며 웹 전용 자동 검사를 Kotlin 검증 결과로 간주하지 않습니다.

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
