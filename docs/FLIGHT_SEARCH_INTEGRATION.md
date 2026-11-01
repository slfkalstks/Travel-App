# 항공권 검색 연결

앱의 출발·도착 공항 및 날짜를 Google 항공편 검색으로 전달합니다. 앱 내부에서 가격을 생성하거나 실시간 운임 API 응답처럼 표시하지 않습니다. 항공편 가격 확인과 예약 진행은 연결된 제공자 화면에서 이루어집니다.

## URL 검증 (2026-11-01)

실제 Chrome에서 다음 두 주소의 도착 화면을 확인했습니다.

- [왕복 검색: ICN → KIX, 2026-11-20~23](https://www.google.com/travel/flights?q=Flights%20from%20ICN%20to%20KIX%20on%202026-11-20%20through%202026-11-23&hl=ko&curr=KRW): 왕복, ICN·KIX, 해당 날짜가 검색 필드에 반영되고 결과 목록이 표시되었습니다.
- [편도 검색: ICN → NRT, 2026-12-05](https://www.google.com/travel/flights?q=One%20way%20flights%20from%20ICN%20to%20NRT%20on%202026-12-05&hl=ko&curr=KRW): 편도, ICN·NRT, 해당 출발일이 반영되고 결과 목록이 표시되었습니다.

검증한 검색어 형식:

```text
Flights from {IATA} to {IATA} on {YYYY-MM-DD} through {YYYY-MM-DD}
One way flights from {IATA} to {IATA} on {YYYY-MM-DD}
```

검색어 전체를 쿼리 값으로 인코딩하고 `hl=ko`, `curr=KRW`를 전달합니다. 이 `q` 해석은 실제 동작을 확인한 웹 검색 연결이며 공식적으로 버전이 보장되는 API 계약이 아닙니다. 제공자 동작 변경 시 재검증해야 합니다.

`for 2 adults`를 덧붙이는 형식은 검증에서 검색 조건이 반영되지 않았으므로 사용하지 않습니다. 승객 수와 좌석 등급은 제공자 화면에서 조정합니다. 운임 수치는 변동하므로 앱에 복사해 고정하지 않습니다.

## 플랫폼 동작

`com.travel.app.platform.rememberExternalUrlLauncher()`는 Compose `LocalUriHandler`를 사용합니다. Android와 iOS의 시스템 URL 처리기로 웹 주소를 열며, 지원하지 않는 주소 또는 실행 실패 시 `false`를 반환합니다. 호출 화면에서 실패 안내와 재시도를 제공합니다.

허용 입력은 명시적인 `http://` 또는 `https://` 주소입니다. 빈 호스트, 사용자 자격 증명이 포함된 URL, 공백·제어 문자, 사용자 정의 앱 스킴은 거부합니다. 이 검증은 공통 테스트로 확인합니다.

## 공식 자료와 관찰의 구분

- [Google 항공편 검색 공식 페이지](https://www.google.com/travel/flights): 실제 항공권 검색 진입점.
- [Google 항공편 검색 도움말](https://support.google.com/travel/answer/2475306): 항공편 검색의 사용자 흐름.
- [Skyscanner Live Prices API](https://developers.skyscanner.net/docs/flights-live-prices/overview): 공식 API는 검색 결과 및 예약용 `deepLink`를 반환합니다. 이 프로젝트에서는 API 키가 없는 상태에서 그 결과를 가장하지 않습니다.

향후 앱 내부 실시간 항공권 결과가 필요하면 공급자 API 계약, 키 관리, 검색 세션·만료·재조회·오류 처리와 제공자 표시 조건을 별도 구현합니다.
