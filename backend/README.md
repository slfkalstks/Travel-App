# 여정 · 로컬 데모 API

Kotlin 1.9.25 · Spring Boot 3.5.16 · Java 21 · Gradle 8.5. 기존 여행 웹 프로젝트는 수정하지 않고 독립적으로 구현했습니다. ECC의 `springboot-patterns`를 적용해 Controller → Service → Repository로 분리하고 입력 검증, 공통 오류 응답을 구성했습니다.

## 실행

JDK 21이 필요합니다. `JAVA_HOME`을 JDK 21 경로로 지정하세요. macOS Homebrew JDK 21은 실행 스크립트가 자동으로 찾습니다.

```bash
cd backend
./run.sh
curl http://127.0.0.1:8088/api/demo/workspace
```

첫 빌드에는 Gradle 및 Maven Central 네트워크 연결이 필요합니다. 빌드가 이미 있으면 실행 스크립트는 기존 JAR를 사용합니다. 소스 수정 후에는 실행 중인 서버를 먼저 종료하고 아래 명령으로 다시 빌드하세요.

```bash
GRADLE_USER_HOME="${TMPDIR:-/tmp}/yeohaeng-gradle-cache" ./gradlew --no-daemon test bootJar
```

Windows PowerShell에서는 JDK 21의 `JAVA_HOME`을 설정한 뒤 `backend` 폴더에서 다음 명령을 사용합니다. `run.sh`는 Bash용 스크립트입니다.

```powershell
.\gradlew.bat --no-daemon test bootJar
& "$env:JAVA_HOME\bin\java.exe" -jar build/libs/yeohaeng-demo-api-0.1.0.jar
```

기본 주소는 **127.0.0.1:8088**입니다. 저장 경로는 `backend/data/workspace.json`이며 `TRAVEL_DATA_FILE`로 바꿀 수 있습니다. 초기 실행 때만 클래스패스의 `seed-workspace.json`을 읽습니다. 저장 파일이 손상되면 기존 데이터를 조용히 덮어쓰지 않고 기동에 실패합니다. 초기화하려면 서버를 종료하고 저장 파일을 별도 백업한 뒤 이동하세요.

## API 계약

모든 변경 응답은 갱신된 전체 workspace입니다. 작업 완료와 장소 저장은 반전(toggle) 명령이 아니라 원하는 상태를 지정하므로 같은 PATCH를 재전송해도 결과가 같습니다.

| 메서드 | 경로 | JSON 요청 |
|---|---|---|
| GET | `/api/demo/workspace` | 없음 |
| GET | `/api/demo/health` | 없음 |
| PATCH | `/api/demo/tasks/{id}` | `{"done":true}` |
| POST | `/api/demo/tasks` | `{"title":"보조배터리 챙기기","category":"packing"}` |
| PATCH | `/api/demo/tasks/{id}/details` | `{"title":"eSIM 설치 방법 확인","category":"booking"}` |
| DELETE | `/api/demo/tasks/{id}` | 없음 |
| PATCH | `/api/demo/favorites/{id}` | `{"saved":false}` |
| PATCH | `/api/demo/events/{id}/note` | `{"note":"교통카드 챙기기"}` |
| PATCH | `/api/demo/events/{id}` | `{"date":"2026-11-13","startTime":"10:00","endTime":"11:00","title":"공원 산책","category":"place","location":"오사카성 공원","note":"걷기 편한 신발"}` |
| DELETE | `/api/demo/events/{id}` | 없음 |
| POST | `/api/demo/expenses` | `{"title":"커피","category":"food","amountKrw":7500,"date":"2026-11-13","method":"카드"}` |
| POST | `/api/demo/events` | `{"date":"2026-11-13","startTime":"10:00","endTime":"11:00","title":"산책","category":"place","location":"난바","note":"","costKrw":0}` |
| POST | `/api/demo/journals` | `{"date":"2026-11-13","title":"오늘의 순간","body":"가을 골목을 걸었다.","mood":"행복"}` |

Workspace 키는 `trip`, `events`, `tasks`, `expenses`, `reservations`, `favorites`, `journals`입니다. 모델 원본은 `Models.kt`, 전체 예시는 `src/main/resources/seed-workspace.json`입니다. 누락될 수 있는 문자열은 빈 문자열로, 목록은 배열로 응답합니다. 알 수 없는 필드·필수 값 누락·잘못된 범주·잘못된 날짜는 400, 알 수 없는 ID는 404입니다. 오류 형식은 `{"code":"INVALID_INPUT","message":"…"}`입니다.

일정 메모 PATCH는 해당 일정의 `note`만 변경하고 전체 workspace를 반환합니다. 최대 2,000자를 허용하며 빈 문자열 `{"note":""}`로 메모를 지웁니다. 줄바꿈과 공백은 그대로 저장합니다. 필드 누락·null·길이 초과는 400, 존재하지 않는 일정은 404이며, 실패한 요청은 기존 데이터를 변경하지 않습니다. 같은 내용을 다시 보내도 결과가 같으므로 별도 멱등성 키 없이 재시도할 수 있습니다.

일정 전체 PATCH는 표의 일곱 필드를 모두 받습니다. 날짜·시간·제목·분류·장소·메모를 변경하고 기존 ID·예상 비용·위도·경도는 유지합니다. 날짜와 시간 검증은 생성과 같으며 목록을 날짜·시작 시각순으로 정렬합니다. 제목은 공백을 제외한 내용이 있어야 하고 최대 120자, 장소는 최대 200자, 메모는 최대 2,000자입니다. 제목·장소의 앞뒤 공백은 제거하고 메모 원문은 유지합니다.

준비물 생성·상세 수정은 제목 최대 120자와 `packing` 또는 `booking` 분류를 받습니다. 생성 시 완료 상태는 `false`이며 `Idempotency-Key`를 지원합니다. 상세 수정은 ID와 완료 상태를 유지합니다. 없는 일정·준비물을 PATCH하면 404입니다. 일정·준비물 DELETE는 이미 삭제되었거나 없는 ID에도 최신 workspace와 200을 반환하여 안전하게 재시도할 수 있습니다. 삭제 후 이전 생성 요청을 같은 멱등성 키로 재전송해도 항목을 다시 만들지 않습니다. 모든 변경은 기존 원자적 저장 경로를 사용합니다.

- 일정 날짜: 여행 기간 2026-11-12~15 안에 있어야 합니다. 기록 날짜는 여행 전 준비와 여행 후 회고를 위해 기간 밖도 허용하되 실제 달력 날짜여야 합니다.
- 일정 시간: 현지 시각 `HH:mm`, 같은 날 종료 > 시작. 자정을 넘는 일정·출도착 시간대 별도 모델링은 다음 단계입니다.
- 지출 날짜: 사전 항공권 결제를 지원하기 위해 여행 시작 전도 허용하고 종료 후는 거부합니다.
- 금액: 정수 KRW. 지출 1~1,000,000,000원, 일정 예상 비용 0~1,000,000,000원. 환전·실시간 환율·영수증 인식은 구현하지 않았습니다.
- 새 일정의 좌표를 생략하면 0.0/0.0입니다. 이는 위치 미지정 값이며 실제 위치로 지도에 표시하면 안 됩니다.

## 데이터와 저장 의미

오사카 3박 4일, 1인, 예산 150만 원의 가상 여행입니다. 18개 일정, 준비물 10개, 샘플 예약 4개, 장소 5개, 준비 메모 2개를 제공합니다. 초기 지출 4개 합계는 722,000원이며 앱은 목록에서 합계를 계산해야 합니다. 예약 데이터는 사용자가 직접 정리한 계획이며 상태는 `planned`, 예약번호는 빈 값입니다. 실제 항공권 발권·숙소 예약·운항·가격 조회 결과를 나타내지 않습니다. 장소명은 일정 표현용이며 위치 좌표는 대략적인 표시 위치입니다. 기록 2개는 11월 1일 작성한 출국·교토 이동 준비 메모입니다. 화면에는 개발용 표식을 반복하지 않으며 데이터 출처와 개발 fixture의 성격은 이 문서에서 설명합니다.

수정은 프로세스 내부에서 동기화하고, 새 JSON을 같은 디렉터리의 임시 파일에 완전히 쓴 뒤 원자적 파일 교체로 반영합니다. 저장 성공 뒤에만 메모리 상태를 변경합니다. **단일 서버 프로세스용 로컬 데모 어댑터**로 다중 인스턴스, 사용자 인증, 소유권 검증, 동기화 충돌 해결을 제공하지 않습니다. POST에는 선택적으로 `Idempotency-Key` 헤더를 전달할 수 있습니다. UUID 또는 영문·숫자·점·밑줄·하이픈 1~128자를 허용합니다. 같은 키·같은 경로·같은 DTO 내용이면 다시 생성하지 않고 최신 workspace를 반환하며, 키를 다른 요청에 재사용하면 409 `IDEMPOTENCY_CONFLICT`를 반환합니다. 클라이언트는 저장 성공을 확인할 때까지 같은 편집 요청의 키를 유지하고, 새 요청에는 새 키를 사용해야 합니다. 키 없이 보낸 POST는 재시도 중복을 방지하지 않습니다. 요청 해시와 workspace를 하나의 `{workspace,receipts,schemaVersion}` 저장 파일에 원자적으로 기록하여 재시작 후에도 중복을 막습니다. 키 영수증은 자동 삭제하지 않습니다. 기존 평면 workspace 파일도 읽어 기존 데이터를 유지하며 다음 저장 때 새 형식으로 전환합니다.

로그인 없이 동작하므로 인터넷에 배포하지 마세요. loopback 바인딩과 제한된 개발 Origin CORS를 사용합니다. CORS는 사용자 인증이나 네이티브 클라이언트 접근 제어를 대신하지 않습니다. 브라우저 개발 Origin은 localhost/127.0.0.1의 5173·8080 포트만 허용합니다.

서버가 `127.0.0.1`에만 바인딩되므로 모바일 앱의 주소를 개발 PC의 LAN IP로 바꾸는 것만으로는 접속할 수 없습니다. Android 실기기는 USB 디버깅 연결 후 `adb -d reverse tcp:8088 tcp:8088`을 사용하고 앱의 Android `demoBaseUrl()`을 `http://127.0.0.1:8088`로 바꿔 Debug 앱을 다시 빌드하면 됩니다. 이 방식은 서버를 외부 네트워크에 노출하지 않습니다. 에뮬레이터는 기본 `10.0.2.2:8088` 설정을 그대로 사용할 수 있습니다. 자세한 순서는 [앱 실행 문서](../app/PLATFORMS.md)에 있습니다.

iPhone 실기기의 `127.0.0.1`은 개발 Mac이 아니라 iPhone 자신입니다. 현재 저장소에서 검증한 iOS 연결은 시뮬레이터입니다. 실기기 원격 접속을 추가하려면 접근 제어가 있는 사설 개발 네트워크 또는 인증된 HTTPS 게이트웨이와 앱 URL 설정이 필요합니다. 인증 없는 공개 터널로 현재 API를 노출하지 마세요.

## PostgreSQL 전환 계획

`../db/migrations/V001__travel_core.sql`은 정규화된 다음 단계 스키마입니다. 현재 런타임에서 실행되지 않으며 PostgreSQL 적용 검증은 아직 하지 않았습니다. 실제 전환 때 Flyway, JDBC/JPA 어댑터, 사용자 소유권 필터, 버전 기반 충돌 검증, DB 기반 멱등성 영수증, 인증, migration 통합 테스트를 함께 추가해야 합니다. 현재 JSON의 `task-01` 같은 ID를 DB UUID로 직접 옮길 수 없으므로 ID 변환도 필요합니다.

## 검증

테스트는 임시 파일 저장소와 실제 Spring MVC를 사용합니다. 파일 재로딩으로 재시작 후 저장 유지, PATCH 멱등성, 음수·소수 지출 거부, 잘못된 날짜·여행 기간·시간 범위·분류 거부, 존재하지 않는 ID, 필수 필드 누락, 외부 Origin 차단, 일정·기록·장소 저장 변경, POST 재시도 중복 방지·키 충돌·재시작 후 영수증 유지·기존 저장 파일 이전·여행 전후 기록 날짜, 일정 메모 수정·비우기·재로딩·길이 제한·실패 시 원본 유지, 일정 전체 수정의 숨은 필드 보존, 준비물 생성·상세 수정·완료 상태 보존, 삭제 재시도와 삭제 후 생성 요청 재시도 안전성을 검증합니다. **2026-11-01 기준 통합 테스트 24개 모두 통과**했으며 빌드한 JAR의 실제 HTTP health/workspace 응답 및 소수 금액 400 응답도 확인했습니다. 실제 예약 제공사 연동은 없습니다.

기술 호환성 확인: [Spring Boot 3.5 공식 시스템 요구 사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html). 이 데모는 기존 환경과의 연속성을 위해 3.5 계열을 선택했으며 최신 메이저라는 의미는 아닙니다.
