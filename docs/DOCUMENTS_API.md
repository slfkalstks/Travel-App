# 여행 문서 HTTP 계약

문서의 목적은 사용자가 선택한 PDF·JPEG·PNG를 여행 안에 보관하고, 필요한 경우 직접 기록한 예약과 연결하는 것이다. 실제 공급자의 예약·발권·취소와 연동하지 않는다. 필드 구조의 원본은 [`document.schema.json`](../contracts/document.schema.json), 편집 입력의 원본은 [`document-update.schema.json`](../contracts/document-update.schema.json)이다. 화면, 공통 저장소, 플랫폼 파일 어댑터와 서버는 이 계약을 함께 사용한다. 계약 변경은 루트 통합 담당자가 소비자·서버 담당자와 조정하며, 스키마와 생성 타입을 먼저 변경한다.

생성 타입이나 예시 검증만으로 API·네이티브 파일 열기·오프라인 동작이 검증된 것은 아니다. 실제 확인 범위는 [검증 기록](VERIFICATION.md)에 기록한다.

## 경로와 여행 범위

| 메서드·경로 | 요청 | 성공 응답 |
|---|---|---|
| `POST /api/demo/documents` | `multipart/form-data`; `file`, `name`, `reservationId` | 문서를 포함한 현재 여행의 `Workspace`, 200 |
| `GET /api/demo/documents/{id}/content` | 본문 없음 | 원본 바이너리, 200 |
| `PATCH /api/demo/documents/{id}` | JSON `{ "name": "…", "reservationId": "…" }` | 수정한 문서를 포함한 현재 여행의 `Workspace`, 200 |
| `DELETE /api/demo/documents/{id}` | 본문 없음 | 삭제 후 현재 여행의 `Workspace`, 200 |

앱은 모든 요청에 `X-Trip-Id`를 보낸다. 서버는 이전 호출 호환상 헤더 자체가 없을 때만 기존 기본 여행 선택 규칙을 사용한다. 명시한 여행이 없거나 빈 헤더이면 404다. 모든 문서 조회·편집·삭제와 예약 연결 검증은 선택한 여행 안에서만 수행한다. 다른 여행의 문서 ID를 지정해도 내용을 읽거나 메타데이터를 바꿀 수 없다. 다른 여행의 문서에 대한 DELETE는 현재 여행 안에 없는 ID의 멱등 삭제와 동일하게 현재 여행만 반환하고 상대 여행은 변경하지 않는다.

`Workspace.documents`는 문서 메타데이터의 배열이다. 이전 저장 파일에 이 배열이 없으면 `[]`로 읽는다. 기존 schema-2 컬렉션 및 schema-1 중첩 저장 항목의 버전을 올리거나 다른 자료를 삭제·재작성하는 마이그레이션은 필요하지 않다. 기존 여행·예약·일정·할 일·지출·즐겨찾기·기록·멱등 영수증을 그대로 보존한다.

## 업로드

정확히 하나의 `file` 바이너리 파트, `name` 문자열 파트, `reservationId` 문자열 파트를 받는다. 앱은 예약에 연결하지 않을 때도 `reservationId=""`를 보낸다. 파일 이외의 텍스트 파트는 추가 파일을 받을 수 없으며, 필수 파트 누락·중복·알 수 없는 파트는 400이다. 파일 bytes를 JSON·Base64로 감싸지 않는다.

| 항목 | 제약 |
|---|---|
| 파일 크기 | 1~20,971,520 bytes, 경계 포함. 빈 파일 거절 |
| 파일 형식 | PDF, JPEG, PNG만 허용 |
| 파트 `Content-Type` | `application/pdf`, `image/jpeg`, `image/png` 중 실제 형식과 일치 |
| 전송 파트 filename | 앱은 provider 원본 이름 대신 MIME에 따라 `document.pdf`·`document.jpg`·`document.png`로 정규화해서 전송. 서버는 이 이름 외에도 안전한 이름 허용. PDF는 `.pdf`, JPEG는 `.jpg`·`.jpeg`, PNG는 `.png`; 대소문자 무시. 경로 구분자·C0/C1 제어문자·줄/문단 구분자 금지 |
| `name` | 필수 표시 이름. 확장자는 불필요하며 저장 전 앞뒤 공백 제거. 공백만인 값, 200자 초과, `/`, `\`, C0/C1 제어문자, U+2028·U+2029, `.`·`..` 거절 |
| `reservationId` | 필수 문자열, 최대 200자. 빈 문자열은 미연결. 값이 있으면 같은 여행에 실제로 존재하는 예약이어야 함 |

파일 형식은 확장자·파트 MIME·실제 bytes의 형식 표식을 교차 확인한다. PDF는 `%PDF-`, PNG는 표준 8-byte signature, JPEG는 JPEG SOI 표식으로 식별하고 일치하지 않으면 거절한다. 이 확인은 악성코드 검사나 문서 전체 파싱·정상 렌더링 보장이 아니다. 서버는 bytes를 실행하거나 공개 정적 경로로 제공하지 않는다. 네이티브 뷰어가 손상 문서를 읽지 못하면 앱은 열기 실패를 표시한다. HTML·SVG·실행 파일은 허용하지 않는다.

표시 이름과 전송 filename은 서버 저장 경로에 사용하지 않는다. 서버는 새 UUID로 파일 이름을 정하고 bytes와 `sha256`, `sizeBytes`, `mediaType`, `createdAt`을 확정한다. 같은 내용의 파일을 새 키로 다시 올리면 별도 문서가 된다. 내용 기반 중복 제거는 하지 않는다. 파일 bytes와 무결성 메타데이터는 수정할 수 없으며 파일을 교체하려면 새로 업로드해야 한다.

Spring multipart 파일 제한은 `20MB`, 전체 요청 제한은 `21MB`로 설정한다. 프레임워크가 전체 파트를 처리하는 단계에서 초과한 경우에도 일관된 413 응답을 반환한다. 거절된 본문 drain은 Tomcat `max-swallow-size=32MB`로 제한해 일반적인 초과 요청에도 JSON 413을 전달한다. drain 상한보다 큰 본문은 송신 중 연결이 종료될 수 있으므로 HTTP 클라이언트는 네트워크 오류로 처리할 수도 있다. 저장 가능한 파일·요청 한도는 그대로 적용하며 앱은 전송 전 20MiB를 검사한다. 업로드 검증 실패 시 Workspace·blob·멱등 영수증에 성공 상태를 남기지 않는다.

앱은 POST에 항상 `Idempotency-Key`를 보낸다. 서버는 이전 호출 호환상 키 누락을 허용한다. 키 문법은 기존 저장소와 같은 영문·숫자·점·밑줄·하이픈 1~128자다. 키는 여행별로 분리하며 fingerprint는 정규화한 `name`, `reservationId`, bytes의 `sha256`, `sizeBytes`, `mediaType`을 포함한다. 같은 키·같은 입력은 중복 저장 없이 **현재** Workspace를 반환한다. 같은 키·다른 입력은 409다. 원본 filename은 형식 검증에만 사용하므로 유효한 같은 파일의 filename 변경은 fingerprint를 바꾸지 않는다. 한 번 저장한 문서를 삭제한 뒤 같은 POST를 재시도해도 되살리지 않는다. 저장 응답을 받지 못한 앱은 같은 파일·입력·키를 유지해 재시도하며, 성공 후에는 새 업로드에 새 키를 사용한다.

## 메타데이터와 편집

| 응답 필드 | 타입·의미 |
|---|---|
| `id` | 서버가 생성한 소문자 UUID 문자열. 클라이언트는 불투명 식별자로 취급 |
| `name` | 검증·trim한 표시 이름 |
| `mediaType` | 검증된 MIME 문자열, 변경 불가 |
| `sizeBytes` | 정확한 원본 길이. Kotlin `Long`, 변경 불가 |
| `sha256` | 원본 bytes의 SHA-256, 소문자 16진수 정확히 64자, 변경 불가 |
| `createdAt` | 서버 UTC ISO instant, `Z` 필수·최대 나노초 소수부, 변경 불가 |
| `reservationId` | 같은 여행 예약 ID 또는 `""`. 응답에는 항상 존재하며 이전 디코딩 기본값은 `""` |

PATCH는 부분 필드 병합이 아니라 두 편집 필드를 모두 전달하는 전체 메타데이터 교체다. `name`, `reservationId` 모두 문자열로 필수다. 명시적 `null`, 숫자·boolean·배열·객체, 필드 누락, `id`·`sha256` 같은 읽기 전용 항목과 추가 항목은 거절한다. `reservationId=""`는 연결 해제를 뜻한다. 다른 여행 또는 존재하지 않는 예약 연결은 400이다. 편집 시 bytes·UUID·생성 시각·크기·해시는 보존한다.

예약을 삭제하면 그 예약에 연결된 문서는 `reservationId=""`로 바꾸고 원본 bytes를 유지한다. 문서를 삭제하면 앱의 문서 기록과 서버 blob을 제거한다. 동일 여행의 같은 ID 삭제 재시도는 성공한 현재 Workspace를 반환한다. 문서나 예약 기록 삭제는 공급자의 실제 예약을 취소하지 않는다. 여행 삭제 시 그 여행의 문서 메타데이터와 서버 blob을 정리하며, 다른 여행의 파일은 변경하지 않는다.

## 다운로드와 앱 캐시

서버는 해당 여행의 문서 메타데이터를 먼저 확인한 후 서버가 관리하는 UUID blob을 연다. 응답은 정확한 `Content-Type`, `Content-Length`를 보내고 UTF-8 표시 이름을 안전하게 인코딩한 `Content-Disposition: attachment`를 사용한다. 헤더에 사용자 문자열을 직접 이어 붙이지 않는다. HTTP 본문은 저장한 원본 bytes 그대로다. 원본·서버 절대 경로·로컬 기기 경로를 응답 메타데이터에 포함하지 않는다.

앱은 여행 ID와 문서 ID를 기준으로 앱 전용 캐시를 관리한다. bytes 수와 SHA-256이 메타데이터와 모두 일치한 파일만 검증 완료 캐시로 저장하거나 열기·공유한다. 이름을 수정해도 기존 검증 캐시를 재사용할 수 있다. 공유·미리보기용 확장자는 자유 표시 이름에 의존하지 않고 검증한 MIME으로 결정한다. 기기에서 원래 선택한 파일은 수정·이동·삭제하지 않는다.

네트워크가 끊긴 경우 이미 검증한 캐시와 메타데이터가 있으면 문서를 열 수 있다. 캐시가 없으면 네트워크 필요 상태를 표시하고 다운로드한 것처럼 표시하지 않는다. 다운로드·검증·열기 실패 시 기존 메타데이터·편집 입력·선택 파일을 보존한다. 문서 삭제 성공·여행 삭제 성공·서버에서 문서가 사라진 뒤 동기화 시 해당 앱 캐시를 정리한다. 서버 삭제 성공 후 로컬 캐시 정리만 실패한 경우 서버 실패로 표시하지 않고 기기 정리 실패를 구분해 알린다.

## 오류

오류 JSON은 기존 `{ "code": "…", "message": "…" }` 형태다. 사용자 입력·서버 절대 경로·원본 bytes를 오류에 노출하지 않는다.

| HTTP | code | 경우 |
|---|---|---|
| 400 | `INVALID_INPUT` | 빈 파일, 형식 불일치, 필수/중복/추가 multipart 파트, 잘못된 표시 이름·예약 연결 |
| 400 | `VALIDATION_ERROR` | JSON 문자열의 길이·형식 제약 위반 |
| 400 | `INVALID_JSON` | 잘못된 JSON, 필수 필드 누락, 문자열 이외 값·null·추가 필드 |
| 404 | `NOT_FOUND` | 없는 여행, 현재 여행에 없는 문서의 GET/PATCH, metadata는 있지만 원본이 없는 문서 GET |
| 409 | `IDEMPOTENCY_CONFLICT` | 같은 키로 서로 다른 유효 업로드 |
| 413 | `FILE_TOO_LARGE` | 파일 또는 multipart 요청 크기 초과 |
| 500 | `STORAGE_ERROR` | 원자 저장·메타데이터 영속화·원본 읽기 실패 |

## 생성 및 검증

```sh
python3 scripts/generate-document-models.py
python3 scripts/generate-document-models.py --check
# 기존 requirements-dev.txt의 jsonschema==4.26.0 설치 후
python3 scripts/validate-document-contract.py
# 서버가 켜져 있으면 GET만 수행해 현재 여행별 응답 검사
python3 scripts/validate-document-contract.py --base-url http://127.0.0.1:8088/api/demo
```

생성기는 Python 표준 라이브러리만 사용하며 로컬에 명시한 두 스키마만 읽는다. 지원하지 않는 타입·키·format·외부 참조는 실패하고 다른 파일을 생성하지 않는다. 공통 `DocumentRecord.kt`·`DocumentUpdateInput.kt`, 서버 `DocumentRecord.kt`·`DocumentUpdateRequest.kt`를 결정적으로 생성한다. 서버 편집 DTO는 문자열 강제 변환·명시적 null과 추가 필드를 거절한다. 제약의 서비스 적용·bytes 검증·파일 저장은 생성기의 역할이 아니다.

[`document-*-response.json`](../contracts/examples/document-unlinked-response.json)은 synthetic 메타데이터 예시다. 예시의 파일 크기·해시는 계약 형식을 설명할 뿐 대응하는 실제 파일을 제공하거나 그 내용의 무결성을 입증하지 않는다. `document-*-update.json`은 연결·연결 해제의 필수 두 문자열을 보여 준다. JSON Schema 검증 시 `urn:travel-app:document-update`를 로컬 레지스트리에 등록하고 format 검증도 활성화한다. 네트워크를 통해 `$ref`를 해석하지 않는다. 검증 스크립트는 선택 RFC3339 라이브러리에 의존하지 않도록 UTC 구문은 스키마 패턴, 실제 달력 날짜는 Python 표준 `datetime`으로 확인한다. 로컬 예시 4개, 잘못된 편집 입력 32개, 응답 필드 오류 16개와 크기·이름 경계값을 검사한다.

서버 통합 검증 범위는 PDF/JPEG/PNG 업로드·원본 bytes SHA/길이·동일 키 재시도·다른 본문 409·메타데이터 변경·연결 해제·예약 삭제 후 파일 보존·문서/여행 삭제 정리·다른 여행 접근·경로 filename·MIME/확장자 불일치·빈 파일·20MiB 경계·초과 413·누락/중복/추가 파트·잘못된 PATCH 타입·재시작·이전 저장자료 보존을 포함한다. 앱 검증은 여행 전환 중 응답 경합, 업로드 재시도 키, 다운로드 무결성, 오프라인 캐시, 취소/실패 후 입력 보존 및 기기 캐시 정리 실패 표시를 포함한다.

## 서버 저장 원자성

아래는 HTTP 스키마와 별도로 현재 로컬 서버 어댑터의 내부 저장 방식을 설명한다. UI 작성과 네이티브 진입·선택기 확인 후 서버 구현을 진행했다.

1. 별도 `DocumentService`와 controller에서 multipart를 스트림으로 읽어 크기·형식·SHA를 확인한다. 업로드 검증을 끝낸 뒤 동일 서비스 잠금 → 저장소 잠금 순서로 변경한다. 파일 저장 루트의 실경로와 UUID 단일 파일 이름을 검증하고 심볼릭 링크를 따라 외부 파일을 읽거나 쓰지 않는다.
2. `WorkspaceRepository.update`가 이미 처리한 키인지 확인한 뒤 실행하는 transform 안에서만 새 UUID blob을 전용 임시 파일에 쓰고 flush한 후 원자 rename한다. 그 다음 metadata·기존 영수증을 함께 저장한다. metadata 저장 실패 시 이번 호출이 만든 신규 blob만 정리한다. 기존 blob이나 다른 여행 파일을 cleanup 대상으로 삼지 않는다.
3. 조회는 여행 범위의 metadata를 잠금 안에서 확인하고 안전하게 blob을 연다. 파일 스트림을 연 후 잠금을 해제하고 전달한다. 삭제와 조회가 경합해 다른 자료를 전달하지 않게 한다. 지원 플랫폼에서 파일 핸들이 열려 삭제가 지연될 수 있으면 cleanup 실패로 처리하고 orphan을 남긴다.
4. 문서 삭제는 metadata commit을 먼저 완료하고 blob을 best-effort로 정리한다. cleanup 실패로 남은 orphan은 조회 권한이 되지 않는다. 다음 문서·여행 DELETE(이미 삭제한 ID의 재시도 포함)와 서버 시작 시 모든 여행의 live ID 스냅샷을 기준으로 참조 없는 UUID 일반 파일만 다시 정리한다. `.upload-숫자.tmp` 일반 파일은 서버 시작 시에만 정리한다. 실행 중 DELETE는 업로드 임시 파일을 건드리지 않으며 symlink·디렉터리·임의 이름은 정리 대상에서 제외한다. metadata가 남아 있는데 blob부터 지우는 순서는 사용하지 않는다.
5. 예약 삭제의 unlink와 여행 삭제의 해당 blob 목록 스냅샷·metadata commit·cleanup도 같은 잠금 순서로 통합한다. 기존 `WorkspaceService`의 삭제 경로가 잠금 없이 파일 수명과 경합하지 않게 한다. 여행/문서 UUID 경로 확인을 거친 해당 목록만 정리하며 이름·사용자 입력 기반 광역 삭제는 하지 않는다.
6. 임시 data-file/blob-root를 사용하는 통합 테스트로 기존 실제 실행 데이터와 선택 원본을 변경하지 않고 실패 원자성·동시성·이전 데이터 읽기를 검증한다. 기존 schema-2 멱등 영수증 fingerprint 구조는 그대로 사용한다.
