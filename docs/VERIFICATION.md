# 구현 검증 기록

검증일: 2026-11-01. 아래 결과는 저장소의 현재 구현 범위에 대한 확인이며 장기 PRD 전체의 출시 인증을 뜻하지 않는다.

## 자동 검증

| 대상 | 실행 | 결과 | 근거 |
|---|---|---|---|
| Spring API | `cd backend && ./gradlew test` | 18개, 실패 0, 오류 0 | `backend/build/test-results/test/TEST-com.yeohaeng.demo.DemoApiTest.xml` |
| 공통 상태 관리 | `cd app && ./gradlew :composeApp:desktopTest` | 7개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.data.TravelStoreTest.xml` |
| 외부 URL 처리 | 위 공통 테스트 명령 | 4개, 실패 0, 오류 0 | `app/composeApp/build/test-results/desktopTest/TEST-com.travel.app.platform.ExternalUrlsTest.xml` |
| Android | `cd app && ./gradlew :androidApp:assembleDebug` | 빌드·설치·실행 성공 | API 35 Android 에뮬레이터 |
| iOS 프레임워크 | `cd app && ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` | 빌드 성공 | Apple Silicon 시뮬레이터 |
| iOS 호스트 | Xcode `MorrowTravel`, Debug, iPhone Simulator 빌드 | 빌드·설치·실행 성공 | iPhone 16 Pro / iOS 18.2 |

공통 테스트의 JVM 타깃 이름은 `desktop`이지만 데스크톱 제품이나 실행 앱은 없다. 빌드·테스트 산출물은 Git에서 제외한다. 최종 라임 UI는 양 플랫폼에서 다시 빌드했다. 일정 메모 PATCH를 추가한 후 데이터·URL 테스트를 다시 실행했다. 메모 저장의 성공·실패 및 서버의 빈 값·길이 제한·재시도·영속화를 검증한다.

## 실제 모바일 화면 확인

- 홈, 날짜별 일정, 준비물, 예약, 경비, 항공권, 장소, 기록, 설정의 이동과 표시.
- 준비물 체크·해제, 장소 저장·해제의 서버 상태 반영.
- 일정 상세 메모를 실제 iOS 입력으로 수정·저장하고 일정 카드 반영과 서버 영속화를 확인했다. 검증용 입력은 확인 후 원래 메모로 복원했다.
- 날짜 위·큰 일차 아래 순서와 카드의 메모 미리보기를 실제 화면에서 확인했다.
- 날짜 달력 열기와 취소, 공항 선택, 왕복·편도 조건.
- 탭을 이동하고 돌아왔을 때 날짜·검색 상태 유지.
- 다크 모드 전환과 설정에서 이전 화면 복귀.
- 편집 경로·입력값·요청 키에 `rememberSaveable`을 적용하고, 저장 중 입력·날짜·카테고리 변경을 비활성화했다. 구성 변경 후 복원은 소스 검토 기준이며 모든 실제 기기의 회전 테스트를 완료했다는 의미는 아니다.
- 필수 입력이 비어 있을 때 필드 옆 오류 표시와 해당 입력 포커스.
- iOS 소프트웨어 키보드 표시 시 상단 제목 유지, 입력 영역 스크롤, 키보드 위 저장 버튼 유지. `OnFocusBehavior.DoNothing`으로 Compose 전체 화면의 추가 이동을 방지한다.
- Android 설정 화면의 시스템 뒤로가기 동작.
- iOS 항공권 버튼에서 Safari의 Google 항공편 검색 결과로 이동하고 공항·날짜가 반영됨을 확인. 왕복·편도 검색 주소는 브라우저에서도 별도 확인했다.

화면 캡처는 [README](../README.md)에 있으며 실제 시뮬레이터 앱을 촬영했다. 합성된 기기 화면을 실행 증거로 사용하지 않는다. 외부 항공편 결과는 제공자의 화면이며 실시간 가격을 앱 내부에서 수집하거나 결제하는 기능은 없다.

## 범위와 후속 검증

입력 저장의 서버 계약·입력 검증·멱등성 및 상태 계층의 실패 보존은 자동 테스트로 확인했다. 모든 입력 조합의 모바일 종단 간 자동 테스트, VoiceOver/TalkBack 전수 사용성 평가, 큰 글자와 모든 기기 크기, 실제 기기·스토어 배포는 아직 확인하지 않았다.

서버는 loopback 주소의 단일 사용자 개발 환경이며 로그인·권한, 다중 여행 편집, PostgreSQL 실제 연결, 오프라인 동기화, 파일 첨부, 자체 실시간 운임 API·예약·결제는 구현 범위에 포함되지 않는다. 실행·실기기 연결 조건은 [플랫폼 문서](../app/PLATFORMS.md)를 따른다.

21st 설치와 스킬 지침 확인은 완료했지만 원격 검색은 인증되지 않았다. 웹 전용 검사에서 Kotlin 파일이 0개 검토된 결과를 코드 검증 통과로 기록하지 않는다. [도구 적용 기록](redesign-review.md)
