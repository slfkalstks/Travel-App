# 직접 기록한 예약 HTTP 계약

이 문서는 예약 등록·수정·삭제의 공통 계약이다. 필드 구조의 원본은 [`reservation-input.schema.json`](../contracts/reservation-input.schema.json), 단일 응답 레코드는 [`reservation.schema.json`](../contracts/reservation.schema.json)이다. DTO 생성 성공만으로 화면·서비스 구현이나 HTTP 검증 완료를 뜻하지 않는다. 예약은 사용자가 직접 기록하는 자료이며 항공사·숙소·예약 판매처와 연결된 실제 발권·예약·변경·취소 기능을 제공하지 않는다.

## 경로와 여행 선택

| 메서드·경로 | 본문·헤더 | 성공 응답 |
|---|---|---|
| `POST /api/demo/reservations` | 공통 요청; 앱은 `X-Trip-Id`, `Idempotency-Key` 전달 | 해당 여행의 `Workspace` |
| `PATCH /api/demo/reservations/{id}` | 공통 요청; 앱은 `X-Trip-Id` 전달 | 수정한 예약을 포함한 해당 여행의 `Workspace` |
| `DELETE /api/demo/reservations/{id}` | 본문 없음; 앱은 `X-Trip-Id` 전달 | 삭제 후 해당 여행의 `Workspace` |

응답은 예약 하나만이 아니라 `Workspace`이며 그 안의 `reservations[]` 요소가 응답 스키마를 따른다. PATCH는 공통 요청으로 전체 편집값을 교체하는 방식이다. 생략한 선택 필드는 기본값으로 교체하므로 앱은 편집 중 기존 값을 모두 보존해 전송한다. 서버가 정한 `id`와 기존 `source`는 유지한다.

다중 여행 앱은 헤더를 항상 보낸다. 서버는 이전 호출과의 호환을 위해 **헤더 자체를 생략**한 경우에만 기존 기본 여행 선택 규칙을 적용한다. 명시한 여행이 없거나 빈 헤더이면 404다. 예약 ID는 지정한 여행 안에서만 찾는다. POST 멱등성 키도 여행별로 분리하며 같은 키·같은 요청은 중복 생성하지 않고, 같은 키·다른 요청은 409다. 서버의 이전 호출 호환상 멱등성 키 생략은 허용하되 앱은 생성 시 항상 보낸다.

DELETE는 이 앱의 기록만 지운다. 같은 여행에 같은 ID의 DELETE를 반복해도 성공한 현재 `Workspace`를 반환한다. 실제 공급자의 예약을 취소하지 않으며, `status=cancelled`로 바꾸어도 공급자에게 취소 요청을 보내지 않는다.

## 공통 요청: 정확히 20개의 문자열 필드

모든 값은 JSON 문자열이다. `null`, 숫자, 배열, 객체로 대체할 수 없다. 생성된 서버 DTO의 필드별 Jackson deserializer가 자동 문자열 변환과 명시적 `null`을 거절하고, `@JsonAnySetter`가 알 수 없는 필드를 거절한다. `type`, `title`, `date`만 항상 필수다. 다른 필드 생략은 아래 기본값을 사용한다. JSON Schema의 `default`는 문서·코드 생성 정보이며 일반적인 JSON Schema 검증기가 입력 자체에 기본값을 삽입하는 기능은 아니다. `id`, `source` 및 알 수 없는 추가 필드는 요청에서 거절한다.

| 필드 | 허용값·최대 길이 | 생략 시 |
|---|---|---|
| `type` | `flight`, `stay`, `transport`, `activity` | 필수 |
| `title` | 공백만으로 구성할 수 없음; 120자; 저장 전 앞뒤 공백 제거 | 필수 |
| `date` | 실제 존재하는 `YYYY-MM-DD`, 1900~2100년 | 필수 |
| `time` | `HH:mm` 또는 빈 문자열 | `""` |
| `reference` | 120자 | `""` |
| `detail` | 4000자 | `""` |
| `status` | `planned`, `confirmed`, `cancelled` | `"planned"` |
| `location` | 200자 | `""` |
| `endLocation` | 200자 | `""` |
| `provider` | 120자 | `""` |
| `endDate` | 실제 존재하는 `YYYY-MM-DD`, 1900~2100년 또는 빈 문자열 | `""` |
| `endTime` | `HH:mm` 또는 빈 문자열 | `""` |
| `timeZone` | 유효한 IANA 시간대 ID, 80자 | `""` |
| `endTimeZone` | 유효한 IANA 시간대 ID, 80자 | `""` |
| `cancellationDeadline` | 실제 존재하는 `YYYY-MM-DDTHH:mm`, 1900~2100년 또는 빈 문자열 | `""` |
| `cancellationTimeZone` | 유효한 IANA 시간대 ID, 80자 | `""` |
| `cancellationPolicy` | 2000자 | `""` |
| `utcOffset` | `Z` 또는 부호 있는 `HH:mm[:ss]`, 또는 빈 문자열 | `""` |
| `endUtcOffset` | 위와 같음 | `""` |
| `cancellationUtcOffset` | 위와 같음 | `""` |

시간은 24시간제로 `00:00`~`23:59`다. 빈 시간은 **시간 미정**이며 `00:00`으로 바꾸지 않는다. 오프셋 범위는 −18:00~+18:00이고 경계의 분·초는 0이어야 한다. 초 단위 오프셋도 허용하는 이유는 과거 시간대 규칙을 손실 없이 표현하기 위해서다. 이 범위는 [JDK 21 `ZoneOffset` 계약](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/ZoneOffset.html)과 같다.

## 시간·날짜의 서비스 검증

JSON Schema와 생성된 Bean Validation 제약은 타입, 열거값, 길이와 표기 형식을 확인한다. 다음의 필드 간 의미 검증은 서비스가 수행하며 저장·멱등성 영수증 생성 전에 완료해야 한다.

- `time`이 비어 있으면 `timeZone`, `utcOffset`도 비어 있어야 한다. `time`이 있으면 `timeZone`이 필수다.
- `endTime`이 있으면 `endDate`, `endTimeZone`이 필수다. `endTime`이 비어 있으면 `endTimeZone`, `endUtcOffset`도 비어 있어야 한다. 종료 날짜만 아는 기록은 허용한다.
- `type=stay`는 `endDate`가 필수다. 체크인·체크아웃 시간이 미정이어도 날짜는 기록할 수 있다.
- 시작·종료 시간이 모두 알려져 있으면 각각의 시간대·오프셋으로 변환한 **종료 instant가 시작 instant보다 커야 한다**. 두 시간이 모두 알려진 경우가 아니면 지정한 `endDate`는 `date` 이상이어야 한다. 숙박을 포함해 같은 날짜의 시작·종료는 시간이 유효한 순서이면 허용한다.
- `cancellationDeadline`이 있으면 `cancellationTimeZone`이 필수다. 마감이 비어 있으면 `cancellationTimeZone`, `cancellationUtcOffset`도 비어 있어야 한다. 과거의 마감도 기록할 수 있다. 마감·취소 정책은 공급자에게 별도로 확인해야 하는 사용자 기록이다.
- 지정한 시간대는 실행 환경의 시간대 데이터베이스에 있어야 한다. 단순 `+09:00` 같은 고정 오프셋을 IANA 시간대 필드에 대신 넣지 않는다. `UTC`처럼 실행 환경에서 제공하는 시간대 ID는 허용한다.
- 시작·종료·취소마감 각각의 로컬 날짜·시각에 `zone.rules.getValidOffsets(localDateTime)`를 적용한다. 유효 오프셋이 0개인 DST gap은 거절한다. 2개 이상인 overlap은 사용자가 명시한 오프셋이 필수이며 유효 목록 안에 있어야 한다. 1개이면 오프셋 생략이 가능하지만 명시했다면 일치해야 한다. 자동으로 한 시간을 보정하거나 겹친 시간의 첫 오프셋을 선택하지 않는다. JDK는 로컬 시각에 대해 gap의 0개, 일반 시각의 1개, overlap의 2개 오프셋을 제공한다. [JDK 21 `ZoneRules.getValidOffsets` 문서](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/zone/ZoneRules.html#getValidOffsets(java.time.LocalDateTime))

예약의 시작·종료·취소마감 날짜는 여행의 표시 기간 밖이어도 허용한다. 장거리 이동·전후 숙박·이미 지난 예약을 기록하고 조회할 수 있어야 한다. 예약을 추가·수정하거나 여행 기간을 변경할 때 기존 예약을 기간 밖이라는 이유로 삭제하거나 숨기지 않는다. 다른 하위 자료의 날짜 정책은 해당 계약을 따른다.

## 응답·이전 데이터

응답 레코드에는 20개 필드가 전부 존재하고, 불투명한 문자열 `id`와 `source="manual"`이 추가된다. `id` 형식을 해석하거나 클라이언트가 ID를 새로 정하지 않는다. 새 예약은 서버가 ID를 생성한다.

기존 예약의 `id`, `type`, `title`, `date`, `time`, `reference`, `detail`, `status`와 목록 순서는 유지한다. 새 선택 필드가 없으면 빈 문자열로 읽고 `source`가 없으면 `manual`로 읽는다. 이전의 시간이 있지만 시간대가 없는 레코드도 읽기 단계에서 지우거나 임의의 시간대를 덧붙이지 않는다. 새 생성·수정 요청에는 현재의 의미 검증을 적용한다. 스키마는 새 요청·외부 응답 계약이며 저장 파일 자체의 이전 형식을 일괄 거절하는 도구가 아니다.

## 예시와 재생성

- [`reservation-minimal-input.json`](../contracts/examples/reservation-minimal-input.json): 시간 미정의 최소 요청.
- [`reservation-flight-input.json`](../contracts/examples/reservation-flight-input.json): 도쿄 `2026-11-03 00:30 +09:00` 출발, LA `2026-11-02 17:30 -08:00` 도착. 현지 도착 날짜는 앞서지만 UTC로는 `2026-11-02T15:30Z` 출발, `2026-11-03T01:30Z` 도착으로 10시간 뒤다. 실제 판매 상품이 아닌 입력 예시다.
- [`reservation-stay-input.json`](../contracts/examples/reservation-stay-input.json): 체크아웃·취소마감·시간대를 포함한 숙박 요청.
- `reservation-*-response.json`: 위 요청에 서버 `id`, `source`를 더한 단일 예약 응답 예시. 실제 HTTP 응답은 이를 포함한 `Workspace`다.

```sh
python3 scripts/generate-reservation-models.py
python3 scripts/generate-reservation-models.py --check
```

생성기는 Python 표준 라이브러리만 사용하고 네트워크 접근이나 외부 코드 생성기 다운로드를 하지 않는다. 원본 스키마에서 프런트엔드 `ReservationInput.kt`와 서버 `ReservationRequest.kt`를 결정적으로 생성한다. `--check`는 파일을 변경하지 않고 누락·차이를 비정상 종료로 알린다. 지원하지 않는 필드 타입·키·format은 무시하지 않고 실패한다. 응답 스키마는 `urn:travel-app:reservation-input`의 속성을 `$ref`로 공유하므로 검증기는 요청 스키마를 이 ID로 로컬 레지스트리에 등록해 사용한다. 날짜·시간대·오프셋 의미 검증은 별도 서비스 테스트로 확인한다.

## 검증 실행

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements-dev.txt
.venv/bin/python scripts/validate-reservation-contract.py
# 서버가 켜져 있으면 GET만 수행해 여행별 현재 응답도 검사
.venv/bin/python scripts/validate-reservation-contract.py --base-url http://127.0.0.1:8088/api/demo
```

Windows에서는 `.venv/bin/python` 대신 `.venv\Scripts\python.exe`를 사용한다. 개발 검증 의존성은 `requirements-dev.txt`에 고정하며 앱 런타임에는 포함하지 않는다. 스키마 검사는 로컬 레지스트리만 사용한다. 실제 CRUD·필드 간 의미 검증·영속성은 서버 통합 테스트, 모바일 요청·오류 보존은 공통 테스트에서 실행한다. 최신 수치와 네이티브 조작 범위는 [검증 기록](VERIFICATION.md)에 기록한다.
