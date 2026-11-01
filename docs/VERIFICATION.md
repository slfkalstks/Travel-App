# 구현 검증 기록

검증일: 2026-11-01. 아래 결과는 저장소의 현재 구현 범위에 대한 확인이며 장기 PRD 전체의 출시 인증을 뜻하지 않는다. 현재 사용자 범위는 **기획 발표와 실제 앱 화면 캡처**다. 발표 중 앱 실시간 시연은 하지 않으며 [5장 PPTX](../presentation/여정_프로젝트_기획발표.pptx)를 제작했다. 운영 기능의 추가 개발과 전면 인수 검증은 보류한다.

## 자동 검증

| 대상 | 실행 | 결과 | 근거 |
|---|---|---|---|
| Spring 기존 API | `cd backend && ./gradlew test` | 24개, 실패 0, 오류 0 | `backend/build/test-results/test/TEST-com.yeohaeng.demo.DemoApiTest.xml` |
| Spring 다중 여행 API | 위 서버 테스트 명령 | 9개, 실패 0, 오류 0 | `backend/build/test-results/test/TEST-com.yeohaeng.demo.MultitripApiTest.xml` |
| Spring 예약 API | 위 서버 테스트 명령 | 13개, 실패 0, 오류 0 | `backend/build/test-results/test/TEST-com.yeohaeng.demo.ReservationApiTest.xml` |
| Spring 문서 API | 위 서버 테스트 명령 | 17개, 실패 0, 오류 0 | `backend/build/test-results/test/TEST-com.yeohaeng.demo.DocumentApiTest.xml` |
| 공통 상태 관리 | `cd app && ./gradlew :composeApp:desktopTest` | 12개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.data.TravelStoreTest.xml` |
| 다중 여행 상태 관리 | 위 공통 테스트 명령 | 9개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.data.MultitripStoreTest.xml` |
| 목적지 검색 | 위 공통 테스트 명령 | 4개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.data.DestinationCatalogTest.xml` |
| 예약 상태 관리 | 위 공통 테스트 명령 | 4개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.data.ReservationStoreTest.xml` |
| 예약 현지 시각 | 위 공통 테스트 명령 | 5개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.data.ReservationTimeTest.xml` |
| JVM 전체 시간대 전환 비교 | 위 공통 테스트 명령 | 1개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.data.ReservationTimeZoneRulesTest.xml` |
| 외부 URL 처리 | 위 공통 테스트 명령 | 4개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.platform.ExternalUrlsTest.xml` |
| 문서 업로드 상태 관리 | 위 공통 테스트 명령 | 4개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.data.DocumentUploadStoreTest.xml` |
| 문서 해시·다운로드 캐시 | 위 공통 테스트 명령 | 12개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.data.DocumentCacheTest.xml` |
| 문서 캐시 인덱스 복구 | 위 공통 테스트 명령 | 4개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.data.DocumentCacheRecoveryTest.xml` |
| 여행 오프라인 조회 캐시 | 위 공통 테스트 명령 | 7개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.data.TravelOfflineCacheTest.xml` |
| 파일 형식·플랫폼 공통 처리 | 위 공통 테스트 명령 | 4개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.platform.DocumentPlatformTest.xml` |
| iOS 공통 테스트 | `cd app && ./gradlew :composeApp:iosSimulatorArm64Test` | 69개, 실패 0, 오류 0 | `app/composeApp/build/test-results/iosSimulatorArm64Test/TEST-*.xml` |
| Android | `cd app && ./gradlew :androidApp:assembleDebug` | 최종 APK 빌드 성공·releases 복사 | 최신 빌드 후 추가 설치 QA는 하지 않음. 앞선 설치·Activity 시작 기록과 구분 |
| iOS 프레임워크 | `cd app && ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` | 빌드 성공 | Apple Silicon 시뮬레이터 |
| iOS 호스트 | Xcode `MorrowTravel`, Debug, iPhone Simulator 빌드 | 최종 호스트 빌드 성공 | 실제 iOS 조작 범위는 아래 항목으로 구분 |

최신 XML 합계는 **서버 63개·JVM 70개·iOS 69개**, 모두 실패 0·오류 0이다. JVM의 시간대 전체 전환 비교 1개는 iOS 테스트에 포함되지 않는다. 공통 테스트의 JVM 타깃 이름은 `desktop`이지만 데스크톱 제품이나 실행 앱은 없다. 빌드·테스트 산출물은 Git에서 제외한다. 문서·캐시 변경 후 서버 `test bootJar`와 공통 JVM/iOS 테스트·Android APK·iOS 호스트 빌드를 다시 확인했다. 최종 앱 Gradle 로그는 `/private/tmp/travel-showcase-final-tests.log`의 `BUILD SUCCESSFUL`이다. 빌드 성공과 실제 화면 조작 확인은 아래와 같이 구분한다.

## 다중 여행·기존 데이터 보존

- 서버 9개 테스트: 빈 여행 생성, 요청별 여행 선택과 모든 하위 데이터의 격리, 여행별 멱등성 키, 여행 생성 재시도·재시작·다른 본문 충돌, 수정·보관 시 ID/표지/하위 데이터 보존, 기간 축소 시 기존 일정·지출 보호, 기간 밖 예약 유지, 최종 여행 삭제 후 재시작 빈 상태, 삭제한 여행의 지연 생성 요청에 의한 부활 방지.
- schema 1 → 2 마이그레이션 테스트: 기존 데이터·요청 영수증 보존, 원본 파일과 바이트가 같은 백업, 지원하지 않는 버전·손상·불일치 파일의 덮어쓰기 방지.
- 실행 중 데이터도 schema 2로 이전했다. 기존 일정 18개·준비물 10개·지출 4개·예약 4개·장소 5개·기록 2개·멱등성 영수증 1개가 백업과 동일함을 비교했다. 기존 여행에는 `archived=false`만 추가했으며 원본 바이트 백업을 유지한다.
- 공통 상태 9개 테스트: 선호 여행 선택, 보관하지 않은 여행 우선 선택, `X-Trip-Id` 전달, 저장 중 여행 전환 차단, 다른 여행 응답의 덮어쓰기 방지, 빈 목록에서 첫 여행 생성, 생성·삭제 실패 시 기존 상태 보존, 수정·보관 후 목록 반영과 비선택 여행을 관리할 때 현재 여행 유지, 선택 여행/마지막 여행 삭제 후 상태 정리.
- 앱의 여행 하위 조회·변경 요청은 `X-Trip-Id`로 대상 여행을 지정한다. 서버에 전역 선택 여행 상태를 두지 않는다. 헤더 생략 시 호환 동작을 포함한 현재 계약은 [다중 여행 API](MULTITRIP_API.md)를 따른다.

## 국가 → 도시·지역 선택

- 국가·영토 257개, 도시 34,195개, 1단계 행정지역 3,864개를 공식 GeoNames/Unicode 원본에서 변환했다. [출처·스냅샷 검증값](DESTINATION_DATA.md), 재생성 스크립트, 앱 내 출처/라이선스 고지를 포함한다.
- 공통 테스트 4개: 한글·영문·공백·대소문자·국가 코드 검색, 국가별 결과 격리(빈 검색어 포함), 같은 이름의 지명과 ID 보존, 결과 250개 비절삭, 손상된 리소스 거부를 확인했다.
- 실제 iOS에서 Japan 검색 → 일본 선택 → 일본 도시 목록 → Osaka 검색 → 오사카 시 선택 → 입력 중 여행명 보존 → 날짜 입력·여행 생성 → API의 일본/오사카 시 저장을 확인했다. 항공권은 KIX와 해당 여행 날짜로 열린다.
- 국가를 대한민국으로 변경하면 오사카 선택이 초기화되고 대한민국 도시 목록만 표시된다. 이 상태에서 Osaka 검색 결과가 비어 있음을 확인했다. 검색 키보드를 표시해 검색 입력과 직접 입력 버튼이 키보드 위에 유지됨도 확인했다.
- 목록에 없는 이름을 직접 입력하는 경로는 사용자가 선택한 국가 아래의 명시적인 사용자 입력이다. 이를 지명 데이터베이스가 검증한 장소로 간주하지 않는다. 추가 검증용 여행은 API로 정리하고 기존 여행은 유지했다.
- 지명은 로컬 리소스에서 읽으므로 검색 요청이 외부 서버로 전송되지 않는다. 항공·숙소의 실시간 API가 추가됐다는 뜻은 아니다.

## 직접 기록한 예약

- 계약 원본은 JSON Schema 두 개이며 요청은 정확히 20개 문자열 필드다. 결정적 DTO 생성기의 `--check`가 통과했다. `requirements-dev.txt`의 jsonschema로 예시 5개 통과·잘못된 타입/추가 필드 123개 거절을 확인했고, 실행 중 두 여행의 기존 예약 4개 응답도 스키마를 통과했다. [계약·재생성·검증 방법](RESERVATION_API.md)
- 서버 13개 테스트는 생성·전체 수정·반복 삭제, 여행 격리, 멱등성 재시도/다른 본문 충돌, 재시작 영속성, 기존 8필드 기록 보존, 잘못된 요청에서 파일 보존을 확인한다. 시간 미정·자정, 시간대 누락, DST gap·overlap, 취소 마감, 날짜변경선을 넘는 이동과 날짜만 지정한 종료 순서도 포함한다.
- 공통 상태 테스트 4개는 20필드 JSON·헤더, 중복 저장 차단, PATCH 대상 ID·응답 반영, 빈 본문 DELETE·다른 여행 선택·실패 보존을 확인한다. 현지 시각 테스트 5개는 일반 시각·존재하지 않는 시각·중복 시각·날짜변경선·잘못된 입력을 확인한다.
- 독립 JVM 테스트는 JDK의 604개 시간대 ID, 고유 규칙 345개에서 1900~2100년 전환 38,310건의 경계 주변 191,497개 분 단위 시각을 비교했다. gap 내부 57,708개, overlap 내부 57,169개와 초 단위 과거 오프셋·비1시간 전환·날짜변경선을 포함해 모두 일치했다. 이 수치는 사용한 JDK 데이터 기준이며 iOS/Android 시간대 DB 전체가 동일하다는 증거로 해석하지 않는다.
- 실제 iOS에서 활동 예약 생성(시각 미정) → 이름 수정 → `12:34`, `Asia/Tokyo` 지정 → 저장 → 앱 재실행 → 상세의 `UTC+09:00` 유지와 서버 값을 확인했다. 시간대 검색에서 폼으로 돌아와도 입력 내용이 유지된다.
- 독립 리뷰에서 찾은 비이동 예약의 `endLocation` 손실을 수정했다. 별도 활동 예약의 제목만 실제 iOS에서 수정·저장한 후 숨긴 `endLocation`을 포함한 다른 모든 값이 동일함을 서버와 비교했다. 시간대 선택 후 폼 스크롤 초기화도 수정해 키보드로 이동한 중간 위치가 선택 복귀 후 유지됨을 실제 iOS에서 확인했다.
- 기존 항공 예약의 상세·편집 값, 삭제 영향 확인창과 취소를 확인했다. 네이티브에서 삭제 실행은 하지 않았으며 반복 삭제는 API 테스트와 직접 생성한 검증용 예약의 API 정리로 구분한다.
- 예약 폼이 열리자마자 중간으로 이동하던 문제는 공용 분류 탭의 ancestor `bringIntoView` 요청이 원인이었다. 탭 자신의 가로 위치만 조정하도록 수정해 폼 상단 시작과 기존 항공 일정 편집의 선택된 항공 탭 전체 표시를 실제 iOS에서 다시 확인했다.
- 서버 교체 전 원본 파일을 바이트 단위로 백업했다(SHA-256 `981bf6f35d2e420721a637d35ea746d028f0d17e07b3df0d9c2a95cc61389ddd`). 검수 후 직접 만든 예약만 API로 정리했으며 두 여행의 원래 자료·기존 예약의 모든 값과 순서가 보존됐음을 비교했다.
- 예약 구현 당시 Android APK 빌드·설치·Activity 시작, iOS 호스트 빌드·설치·실행을 확인했다. 문서 변경을 포함한 최신 빌드 결과는 위 표와 구분한다. Android 예약 UI 조작, 두 모바일 플랫폼 전체 DST 조합·큰 글자·화면 회전은 미검증이다. 예약과 서류 연결의 구현·검증은 아래 문서 보관 항목으로 구분한다.

## 문서 보관·예약 연결·조회 캐시

- 계약 원본은 `document.schema.json`, `document-update.schema.json`이며 공통/서버 타입 생성기의 `--check`가 통과했다. 예시 4개가 유효하고 잘못된 편집 입력 32개·응답 필드 16개를 거절했다. [문서 HTTP 계약](DOCUMENTS_API.md)
- 서버 17개 테스트는 PDF/JPEG/PNG 업로드·원본 다운로드의 bytes/MIME/길이/SHA, 실제 HTTP multipart 업로드, 파일 20MiB+1·전체 요청 22MiB의 413, 다른 여행 격리, 이름·예약 연결 수정, 예약 삭제 시 연결만 해제, 문서·여행 삭제, 멱등 재시도·다른 bytes 충돌·재시작·삭제한 기록 부활 방지, 이전 자료·영수증 보존을 확인했다.
- 서버 저장 실패 시 신규 blob만 정리하고 기존 파일을 보존한다. 실제 삭제 권한 오류 후 반복 DELETE/새 서비스 시작에서 orphan 정리를 재시도하며, 다른 여행 live 파일·metadata·임의 파일·디렉터리·symlink 외부 원본을 보존하는 테스트도 통과했다. 임시 저장소를 사용한 API 검증이며 네이티브 화면에서 삭제 실행을 완료했다는 뜻은 아니다.
- 업로드 상태 관리 4개는 multipart·여행 헤더·중복 요청 차단·같은 키 재시도·실패 입력 보존·다른 여행 응답 차단·문서 PATCH/DELETE와 선택한 여행 캐시 갱신을 확인했다.
- 문서 캐시 12개·복구 4개·여행 오프라인 조회 7개는 크기/SHA 무결성 확인, 손상 bytes 열기 차단, 네트워크 없이 검증된 캐시를 opener 콜백에 전달, 내려받지 않은 파일 오류, 여행별 격리·삭제 캐시 정리, 손상 인덱스 원본 백업·복구와 쓰기 실패 재시도를 확인했다. 재생성한 상태 객체의 캐시 조회·여행 전환과 오프라인 변경 요청 차단, 서버 저장 성공과 로컬 캐시 실패 구분도 포함한다. 이는 데이터·캐시 로직의 자동 테스트이며 실제 앱 재시작 후 비행기 모드 UI/파일 열기의 종단 간 검증과 구분한다.
- 실제 iOS에서 서류 진입·빈 목록, 시스템 파일 선택기 열기와 취소, PDF 선택·예약 연결·업로드, 상세 표시, 네이티브 입력으로 표시 이름 편집·저장, 삭제 영향 확인창 열기·취소를 확인했다. 파일 삭제 API 동작은 위 서버·공통 테스트로 확인했다.
- **QuickLook에서 문서가 실제로 열리는 동작, 네이티브 공유 완료, 앱 재시작 후 오프라인 화면·파일 열기는 미검증**이다. Android 문서 UI의 실제 조작·기기별 파일 provider, 큰 글자·화면 회전도 전수 확인하지 않았다.
- 현재 발표에는 앱의 실시간 조작 대신 실제 앱 캡처를 사용한다. 서류 캡처 52~56은 빈 목록·업로드·예약 연결·상세·삭제 확인창이며 전체 캡처는 56개다.

## 실제 모바일 화면 확인

- 홈, 날짜별 일정, 준비물, 예약, 경비, 항공권, 장소, 기록, 설정의 이동과 표시.
- 준비물 체크·해제, 장소 저장·해제의 서버 상태 반영.
- 실제 iOS에서 기존 일정 카테고리 수정, 준비 항목 생성, 완료된 준비 항목의 카테고리 수정 후 저장 결과와 완료 상태 보존을 확인했다. 검수 후 변경한 분류는 원복하고 직접 생성한 검증용 항목만 정리했다.
- 일정·준비물 삭제 확인창의 대상 표시와 취소 동작을 확인했다. 삭제 실행·재시도·실패 보존은 API 및 공통 데이터 계층 테스트로 검증했다.
- 일정 상세 메모를 실제 iOS 입력으로 수정·저장하고 일정 카드 반영과 서버 영속화를 확인했다. 검증용 입력은 확인 후 원래 메모로 복원했다.
- 날짜 위·큰 일차 아래 순서와 카드의 메모 미리보기를 실제 화면에서 확인했다.
- 날짜 달력 열기와 취소, 공항 선택, 왕복·편도 조건.
- 탭을 이동하고 돌아왔을 때 날짜·검색 상태 유지.
- 기존 항공 일정의 수정 화면에서 선택된 카테고리가 가로 스크롤 안으로 자동 이동해 온전히 보임을 확인했다.
- 다크 모드 전환과 설정에서 이전 화면 복귀.
- 편집 경로·입력값·요청 키에 `rememberSaveable`을 적용하고, 저장 중 입력·날짜·카테고리 변경을 비활성화했다. 구성 변경 후 복원은 소스 검토 기준이며 모든 실제 기기의 회전 테스트를 완료했다는 의미는 아니다.
- 필수 입력이 비어 있을 때 필드 옆 오류 표시와 해당 입력 포커스.
- iOS 소프트웨어 키보드 표시 시 상단 제목 유지, 입력 영역 스크롤, 키보드 위 저장 버튼 유지. `OnFocusBehavior.DoNothing`으로 Compose 전체 화면의 추가 이동을 방지한다.
- Android 설정 화면의 시스템 뒤로가기 동작.
- iOS 항공권 버튼에서 Safari의 Google 항공편 검색 결과로 이동하고 공항·날짜가 반영됨을 확인. 왕복·편도 검색 주소는 브라우저에서도 별도 확인했다.
- 다중 여행 추가 후 실제 iOS에서 도쿄 여행(12월 4~7일)을 생성했다. 새 여행의 일정·준비물이 각각 0개이며 항공권 검색 날짜가 새 여행 기간과 일치함을 확인했다.

- 실제 iOS에서 새 여행에 준비물을 추가하고 여행 예산을 1,800,000원으로 수정한 뒤 서버 저장과 앱 재실행 후 유지되는 것을 확인했다. 검증 후 도시·여행·준비물 표시명은 API로 한국어로 정리했다.
- 여행 보관/해제, 목록의 보관 탭 유지, 검색한 목록에서 편집 후 복귀할 때 검색어 유지, 오사카/도쿄 여행 전환을 확인했다. 비선택 도쿄 여행을 보관하고 돌아가도 오사카 홈이 유지된다.
- 상단 여행 관리 메뉴와 삭제 영향 확인창(6종 관련 자료), 취소 후 편집 복귀를 확인했다. 여행 삭제 실행은 API·공통 상태 테스트 범위이며 네이티브에서 실행 완료로 기록하지 않는다.
- 별도 빈 저장소에서 iOS 홈의 첫 여행 안내 → 입력 → 저장 → 새 여행 홈(일정·준비물 0개)을 확인했다. 검증 중 기존 저장 파일은 바이트 단위로 변경되지 않았고 이후 원래 서버로 복귀했다.
- 앞선 다중 여행 검수 단계의 APK는 Android 에뮬레이터 설치·Activity 시작 요청 성공을 확인했다. 문서 변경 후 최신 APK는 빌드와 releases 복사까지이며 추가 설치 QA는 하지 않았다. Android 다중 여행 UI 조작, 큰 글자·회전은 미검증이다.

실제 앱 화면 캡처 56개는 [README](../README.md)에 있으며 실제 시뮬레이터 앱을 촬영했다. 합성된 기기 화면을 실행 증거로 사용하지 않는다. 외부 항공편 결과는 제공자의 화면이며 실시간 가격을 앱 내부에서 수집하거나 결제하는 기능은 없다.

## 범위와 후속 검증

입력 저장의 서버 계약·입력 검증·멱등성 및 상태 계층의 실패 보존은 자동 테스트로 확인했다. 모든 입력 조합의 모바일 종단 간 자동 테스트, VoiceOver/TalkBack 전수 사용성 평가, 큰 글자와 모든 기기 크기, 실제 기기·스토어 배포는 아직 확인하지 않았다.

서버는 loopback 주소의 단일 사용자 개발 환경이다. 파일 첨부·예약 연결·검증된 파일 캐시와 여행 metadata 오프라인 조회는 위 범위까지 구현했다. 로그인·사용자 권한, PostgreSQL 실제 연결, 오프라인 편집·동기화 큐/다중 기기 충돌 처리, 자체 실시간 운임 API·예약·결제는 미구현이며 **현재 보류한 장기 백로그**다. 전체 FR-01~24·NFR-01~08은 [전체 목표 감사](../GOAL_AUDIT.md)에서 추적하며 전체 완료로 판정하지 않는다. 실행·실기기 연결 조건은 [플랫폼 문서](../app/PLATFORMS.md)를 따른다. 남은 운영 기능·기기 인수 조건을 현재 발표 준비를 위한 추가 개발 과제로 취급하지 않는다.

21st 설치와 스킬 지침 확인은 완료했지만 원격 검색은 인증되지 않았다. 웹 전용 검사에서 Kotlin 파일이 0개 검토된 결과를 코드 검증 통과로 기록하지 않는다. [도구 적용 기록](redesign-review.md)

## 발표자료 파일 검증

2026-11-01 사용자의 PPTX 제작 요청에 따라 5장 파일을 작성했다. 실제 앱 화면은 국가별 목적지 38·홈 01·일정 02의 원본 캡처를 사용한다. 구조도의 연결선·설명과 기술 비교표는 편집 가능한 개체이며, 출처와 상세 설명은 발표자 노트에 있다.

파일 구조·슬라이드 수·배치·폰트 지정·편집 가능한 표를 검사하고, 최종 PPTX를 다시 불러와 모든 슬라이드를 렌더링하여 잘림과 연결선을 확인했다. Microsoft PowerPoint 앱에서 직접 열어 확인한 결과는 아니다. Noto Sans KR 글꼴은 파일에 포함하지 않았으며 동일한 글꼴로 편집·발표하려면 PC에 설치해야 한다. 이 파일 검증은 앱의 미완료 기능 검증을 대신하지 않는다.
