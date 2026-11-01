# 외부 공급자 연동 선행 조건

확인일: **2026-11-01**

이 문서는 PRD의 FR-17~20과 외부 API 설계에 필요한 공급자 요건을 공식 공개 문서로 확인한 결과다. 소유자 계정의 실제 승인·계약·발급 상태를 확인한 문서는 아니다. 비밀키를 검색하거나 계정 로그인·신청·계약·결제를 수행하지 않았으며, **실제 결제는 테스트하지 않았다.**

## 현재 구현과 공급자 연동의 구분

현재 `FlightScreen.kt`는 출발·도착 공항과 편도/왕복 날짜를 검증하고 Google 항공편 검색 웹 주소를 만든다. `rememberExternalUrlLauncher()`는 공통 Compose URI 처리기로 주소를 연다. 인원·좌석 변경은 외부 결과 화면에서 하도록 안내한다. 앱이 항공 운임 API를 호출해 내부 비교 결과를 받거나 항공권을 판매하는 구현은 이 흐름에 포함되지 않는다.

기존 [항공 검색 통합 문서](FLIGHT_SEARCH_INTEGRATION.md)는 Chrome에서 확인한 검색 연결과 공식 API 계약을 구분한다. Google 검색어 해석은 버전이 보장되는 API 계약이 아니므로 동작 변경 시 재검증해야 한다. 이 외부 화면 이동은 유용한 구현이지만 FR-17의 내부 가격 비교·관측 통계 또는 FR-19의 내부 거래 전체 완료를 뜻하지 않는다.

ChatGPT에서 연결된 Booking.com·Skyscanner·Gmail 도구의 이용 권한도 이 독립 앱의 API 자격 증명·파트너 계약을 대신하지 않는다.

## Booking.com: 숙소 검색과 내부 거래

- **FR-18 검색 접근:** Demand API 사용 전 Managed Affiliate Partner 등록, 계약 후 Account Manager가 제공하는 Partner Centre 접근, API 토큰과 `X-Affiliate-Id`가 필요하다. 이는 공개 문서 열람과 별개의 조건이다. [공식 사전 요건](https://developers.booking.com/demand/docs/getting-started/prerequisites)
- **FR-19 거래 접근:** 검색 후 Booking.com으로 이동하는 `Search, look and redirect`와 앱에서 예약하는 `Search, look and book`은 다르다. 내부 예약에는 승인, 적절한 사업 근거와 파트너 계약이 필요하다. Orders는 최종 가격·결제 조건 확인, 주문 생성, 주문 조회 및 지원되는 변경·취소를 제공한다. 서비스·계약별 지원 범위를 확인해야 하며 모든 여행 상품의 내부 거래를 보장하는 것으로 해석하지 않는다. [숙소 통합 유형](https://developers.booking.com/demand/docs/accommodations/accommodation-tutorial), [Orders](https://developers.booking.com/demand/docs/orders-api/overview)
- **Sandbox:** 숙소 샘플 재고에서 검색·예약·변경·취소를 시험할 수 있지만 운영과 같은 일반 API 토큰·Affiliate ID와 접근 요건을 사용한다. 무자격 공개 sandbox가 아니다. 현재 공식 문서는 결제가 포함된 숙소 예약 시험에 실제 카드가 필요하고 일시 청구 후 자동 취소·환불된다고 설명한다. 따라서 sandbox를 무조건 무과금 가상 결제로 설명하지 않는다. 이 프로젝트에서 해당 결제를 실행하지 않았다. [공식 sandbox](https://developers.booking.com/demand/docs/getting-started/sandbox)

## Skyscanner: 항공 검색과 관측 통계

- **접근 승인:** API 키는 Partnerships 심사 후 제공되며 모든 요청에 `x-api-key`가 필요하다. 키를 클라이언트 코드나 공개 저장소에 넣지 않도록 공식 문서가 요구한다. [공식 인증](https://developers.skyscanner.net/docs/getting-started/authentication)
- **현재 신청 자격:** 공식 신청 페이지는 상업적 용도만 허용하며 비상업 학생·개인, 준비된 제품·사업 계획이 없는 스타트업, 월간 활성 사용자 10만 미만의 저트래픽 웹사이트 등을 제외한다고 명시한다. 본 프로젝트의 자격·승인을 확보했다고 주장할 수 없다. 이 조건은 **Skyscanner의 해당 API 조건**이며 다른 모든 항공 공급자 연동이 불가능하다는 뜻이 아니다. 대체 공급자의 개발 환경·상품·이용 요건은 별도 조사 대상이다. [공식 신청 조건](https://www.partners.skyscanner.net/contact/travel-api)
- **응답과 예약 경계:** Live Prices API는 검색 세션 생성·조회와 예약 페이지로 이동하는 `deepLink`를 제공한다. 검색 접근이 자체 발권·결제·취소 권한까지 제공한다는 근거는 아니다. 검토한 공식 인증·FAQ에서 승인 없이 사용할 수 있는 공개 sandbox는 확인하지 못했다. 이를 모든 테스트 환경의 부재로 단정하지 않고 승인 담당자에게 확인할 항목으로 남긴다. [Live Prices 계약](https://developers.skyscanner.net/docs/flights-live-prices/overview), [공식 FAQ](https://developers.skyscanner.net/docs/faqs)
- **FR-17 통계의 수집 조건:** 공식 이용 지침은 Live Pricing을 사용자 요청으로만 호출하고 사용자 동작 없는 자동 호출을 하지 않도록 요구한다. 따라서 일별 예약 작업으로 Live Pricing을 수집하는 설계를 기본으로 두면 안 된다. 사용자 검색으로 발생한 관측의 장기 저장·파생 통계 이용도 별도 계약에서 허용 범위를 확인해야 한다. 통계 코호트·표본 수·결측 처리 자체는 공급자와 독립적으로 구현·검증할 수 있지만 실제 가격 이력 확보와 동일하지 않다. [공식 이용 지침](https://developers.skyscanner.net/docs/getting-started/usage-guidelines)

## Gmail: 선택 가져오기

- **앱 소유 자격 증명:** 사용자 이메일 접근에는 Google Cloud 프로젝트의 OAuth 설정, 동의 화면, OAuth Client ID와 사용자 동의가 필요하다. 플랫폼별 Client ID를 생성하며 Android는 패키지명·서명 SHA-1, iOS는 Bundle ID가 필요하다. 일반 API 키만으로 사용자 메일을 읽을 수 없다. [자격 증명](https://developers.google.com/workspace/guides/create-credentials), [동의 화면](https://developers.google.com/workspace/guides/configure-oauth-consent)
- **권한 범위:** 일반 Gmail API의 `gmail.readonly`는 Restricted scope다. 앱에서 선택한 메일만 파싱·저장하도록 구현해도 OAuth 접근 범위 자체가 선택 메일만으로 좁아지는 것은 아니다. `gmail.metadata`는 본문을 제공하지 않으며, Gmail Add-on 전용 현재 메시지 권한은 독립 모바일 앱의 같은 기능으로 취급하지 않는다. [공식 Gmail scopes](https://developers.google.com/workspace/gmail/api/auth/scopes)
- **테스트와 운영:** External/Testing 상태는 지정 테스트 사용자로 시험할 수 있으며 Gmail 범위의 동의·refresh token은 7일 만료 대상이다. 이는 가짜 메일 sandbox가 아니라 동의받은 계정 데이터 접근이다. 공개 서비스의 제한 범위 사용은 검증 요건을 충족해야 하고 서버에서 제한 데이터를 저장·전송하면 보안 평가 요건이 적용될 수 있다. 개인용·개발/테스트·조직 내부용 등의 예외가 있으므로 모든 개인 시연에 공개 운영 검증을 일괄 요구하지 않는다. 해당 배포 방식과 데이터 경로에 맞춰 판단한다. [Testing 상태](https://support.google.com/cloud/answer/15549945), [제한 범위 검증과 예외](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification)

## 자격 증명 없이 계속 구현할 수 있는 범위

여행·일정·준비·예산·기록·로컬 저장·오프라인 편집·동기화·백업 등 핵심 FR의 개발은 위 공급자 승인과 독립적으로 진행한다. 공급자 조건을 이유로 전체 앱 개발을 중단하거나 PRD의 요구를 삭제하지 않는다.

다음 작업은 실제 공급자 응답과 혼동하지 않는 범위에서 진행할 수 있다.

| 영역 | 자격 증명 없이 구현·검증 가능한 작업 | 실제 연동 검증에 필요한 항목 |
|---|---|---|
| FR-17 | 외부 항공 검색 열기, 검색 조건 검증, 통계 계산·결측 처리, 출처별 어댑터와 오류·만료 처리 | 선택 공급자의 승인·키, 실제 운임 응답, 관측 저장·통계 이용 허용 조건 |
| FR-18 | 사용자가 입력한 숙소·좌표와 일정 장소 비교, 조건 입력, 외부 숙소 페이지 열기 | 실시간 재고·총가격·세금·취소 조건을 제공하는 공급자 인증·계약 및 실제 응답 |
| FR-19 | 가격 재확인·중복 방지·결과 불명·재조회·취소/환불 상태기계와 계약 테스트 | 거래 권한·공급자 계약·인증 및 허용된 테스트 환경의 주문·결제·취소 응답 |
| FR-20 | 사용자 제공 메일 파일/텍스트 파서, 미리보기·수정·저장, 출처별 중복 방지 | 앱 소유 OAuth Client ID·동의 구성, 계정 동의, 실제 Gmail 응답과 토큰 수명 처리 |

테스트 fixture·수동 입력·모의 거래 결과는 실제 공급자의 가격·예약 확정으로 표시하지 않는다. 외부 화면 이동도 내부 견적 또는 거래 완료로 기록하지 않는다.

실제 연동을 켤 때에는 소유자가 관리하는 공급자 계정과 승인 범위에 맞는 자격 증명이 필요하다. 비밀값을 채팅에 보내도록 요구하지 않으며, 서버의 비밀 저장소나 실행 환경 설정으로 제공하고 앱·저장소·로그·백업에 포함하지 않는다. 현재 승인 상태와 대체 공급자 조건은 미확인 사항으로 명시하고, 코드 구현·계약 테스트·실제 공급자 검증·운영 거래 검증을 각각 구분해 완료 증거를 남긴다.
