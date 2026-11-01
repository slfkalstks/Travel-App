# 라임·잉크 재설계 — 적용과 검수 기록

2026-11-01. 최종 방향은 선명한 라임 `#D7FF4F`와 잉크 `#181B17`, 굵은 산세리프, 둥근 카드, 떠 있는 형태의 캡슐 내비게이션이다. 사용자 요청에 따라 화면 구성까지 바꾸었으며, 중간 단계의 흰색·파란색 시안과 다르다. 실제 사양은 [DESIGN.md](../DESIGN.md)를 기준으로 한다.

## 도구별 실제 적용

| 도구·자료 | 수행 및 채택 | 적용 한계 |
|---|---|---|
| [MengTo design-first-ui-prompting](https://github.com/MengTo/Skills/tree/main/agent-skills/ui/design-first-ui-prompting) | 설치된 지침을 읽고 화면 질문·정확한 카피·토큰·위계·검수 기준으로 설계 | 지침 사용은 별도 디자인 툴 검수나 기업 승인과 다름 |
| [MengTo clean-minimal-beige-light-mode](https://github.com/MengTo/Skills/tree/main/agent-skills/web-design/clean-minimal-beige-light-mode) | 평면 표면·일관된 간격·정돈된 그룹 구조 적용 | 베이지 팔레트·절제된 색 면적은 사용자의 새 요청에 따라 적용하지 않음 |
| [MengTo animation-systems](https://github.com/MengTo/Skills/tree/main/agent-skills/web-design/animation-systems) | 버튼 눌림 피드백, 전환 시간, 동작 줄이기 반영 | 앱 설정으로 모션을 줄임. OS 설정 자동 연동은 확인하지 않음 |
| [Taste](https://github.com/Leonxlnx/taste-skill) | 앱 전용 지침을 읽고 화면당 한 질문, 실제 데이터 합계, 모바일 읽기 순서·상태 규칙 적용 | 라임 강조면·캡슐 바·큰 굵은 제목은 사용자 요청에 따른 프로젝트 결정. 웹용 lint/DOM 검사는 Kotlin 검사기가 아님 |
| [Vercel Web Interface Guidelines](https://github.com/vercel-labs/web-interface-guidelines) | 최신 공식 command.md를 조회하고 입력 오류, 포커스 가림, 안전 영역, 긴 내용·상태 보존 점검 | HTML/ARIA는 Compose semantics로 대응. 웹 자동 검사 통과로 주장하지 않음 |
| [21st UI build/review](https://github.com/21st-dev/codex-plugin) | 설치된 build/review 지침의 토큰 재사용, 실제 프레임워크 구현, 결함과 취향 구분을 적용 | 원격 검색은 인증되지 않아 `Not signed in`. 원격 컴포넌트 검색·가져오기·호스팅 생성은 완료하지 않음 |
| [VoltAgent awesome-design-md](https://github.com/voltagent/awesome-design-md) | Apple·Airbnb 분석의 정보 위계·일관된 행동색·대상 중심 정보를 재해석 | 제3자 웹 분석이며 기업 공식 모바일 사양이 아님 |
| ECC accessibility | 이름·역할·선택/체크 값, 포커스, 단일 체크 영역, 키보드·터치 조작 지침 적용 | VoiceOver/TalkBack 전체 인증을 의미하지 않음 |

SHI의 평면적인 구분, Tween의 선택 피드백은 참고 원칙으로 유지했다. 따뜻한 팔레트를 복제하지 않았다. `mimimal.gallery`는 이전 조사에서 열리지 않았고 `minimal.gallery`를 대체 후보로 확인했으나 두 주소를 동일 사이트로 단정하지 않는다.

## Taste 기록을 해석하는 방법

seed `20261102`의 `achromatic-ledger` 추출은 **중간 단계의 구조 검토 자료**다. 보관된 `design-references/taste-direction`은 최종 라임 테마 생성 증거가 아니다. 추출에 포함된 데스크톱 인사말·12열·통계 타일은 채택하지 않았다. 최종 색·캡슐 내비게이션·카드 형태는 이후 사용자가 요청한 더 젊고 경쾌한 방향을 반영해 직접 설계했다. ‘모든 Taste 규칙을 통과했다’고 쓰지 않는다.

## 구현과 소스 검토

`TravelApp.kt`, `DesignSystem.kt`, `HomeScreen.kt`, `TripScreen.kt`, `OtherScreens.kt`, `EditorDialog.kt`, `FlightScreen.kt`를 검토했다. 확인한 문제와 최종 소스의 대응은 다음과 같다.

| 발견한 문제 | 현재 대응 |
|---|---|
| 최상위 탭 이동 시 검색 조건·필터 초기화 | SaveableStateHolder와 rememberSaveable 사용 |
| 상세 내부 탭 이동 시 선택 날짜 초기화 | 선택 날짜를 TripScreen 상태로 올림 |
| 일정 날짜·연도 하드코딩 | 여행 시작/종료 날짜에서 범위 생성 |
| 상세→설정→복귀 후 뒤로가기 반복 | 설정은 이전 페이지, 상세는 홈으로 분기; 편집기는 별도 뒤로가기 |
| 설정 스위치 이름 누락 | 스위치 contentDescription 추가 |
| 경비 정수 입력에 소수 키보드 | Number 키보드 사용 |
| 날짜 직접 입력만 제공 | 달력 추가, 선택기 범위 밖 초기 날짜 보호 |
| 항공권 화면이 길어 버튼이 첫 화면 밖에 있음 | 장식 문구 제거, 가로 노선 카드, 간격 축소 |
| 편집기에서 키보드가 입력·저장을 가림 | 전체 화면 편집기와 스크롤 입력/고정 헤더/저장 영역, IME inset 구성 |

날짜는 달력과 직접 입력을 제공한다. 시간은 현재 HH:mm 텍스트 입력이다. ‘시간 선택기 구현 완료’라고 쓰지 않는다. 여행 생성·장소의 일정 직접 추가·예약 편집도 현재 기능에 없다.

## 실제 iOS 화면 검수

아래 이미지를 직접 열어 확인했다. 시뮬레이터 화면 증거이며 모든 기기·크기에서의 결과를 의미하지 않는다.

| 증거 | 확인한 결과 |
|---|---|
| [홈](screenshots/01-home.png) | 잉크 여행 카드·세로 사진·라임 카운트다운이 분리되어 읽힘. 4/10 완료와 6개 남음이 일치. 18개 일정·날짜가 보임 |
| [항공권](screenshots/06-flights.png) | 출발/도착·왕복·날짜·인원 안내·CTA·외부 제공자 설명이 하단 바 위에 보임. 현재 캡처에서 잘림·겹침 없음 |
| [일정](screenshots/02-schedule.png) | 선택 날짜와 티켓형 목록의 시간·제목·장소 위계가 분명함. 목록은 하단 영역 위에서 스크롤 |
| [다크 홈](screenshots/10-dark.png) | 어두운 바탕·그룹·본문이 구분되고 라임 강조와 사진이 유지됨 |
| [키보드·오류 포커스](screenshots/14-keyboard.png) | 한국어 소프트웨어 키보드가 열린 상태에서 장소 오류 입력이 보이고 상단 제목이 유지됨. 저장 버튼은 키보드 위에 있음 |

홈의 아래쪽 항공권 카드가 첫 캡처에서 일부만 보이는 것은 스크롤 구성이다. 항공권 폼의 남은 하단 여백은 불필요한 콘텐츠로 채우지 않았다. 준비 진행률 오른쪽 작은 점은 기본 컴포넌트의 정지 표시이며 시각적으로 더 줄일 수 있는 경미한 항목으로 전달했다.

## 색상 계산과 검증 한계

불투명 sRGB 토큰 대비를 계산했다: SignalInk/Signal 15.15:1, SignalInk/Canvas 16.72:1, TextSecondary/Canvas 5.76:1, TextSecondary/Grouped 5.25:1, Accent/Canvas 8.43:1. 실제 사진·투명도·모든 입력 상태를 포괄하는 자동 접근성 보고서는 아니다.

전체 화면 크기 행렬, 글꼴 200%, VoiceOver·TalkBack, 실물 Android/iOS 기기 검증은 이 리뷰에서 통과로 선언하지 않는다. 21st의 이전 `filesReviewed: 0`은 Kotlin 지원 한계이며 품질 통과 증거가 아니다. 빌드·서버·외부 URL 동작의 최종 검증 기록은 별도 구현 검증 문서를 따른다. PPTX는 생성하지 않았다.

## 항공권 공급사 범위

현재 Google 항공편은 URL의 자연어 검색어로 노선·날짜를 전달하고 제공자 화면을 연다. 모든 조건이 제공자에서 정확히 해석된다는 공식 보장을 이번 조사에서 확인하지 않았다. 앱 내 실시간 운임 조회·예약·결제와 구분한다. 장소는 Google Maps 외부 검색이다.

Skyscanner 공식 Affiliates Link API는 비교 조사만 했다. GET 리디렉션과 mediaPartnerId 등 제휴 설정을 요구하는 구조를 설명하며, 예시 제휴 ID를 우리 앱의 ID처럼 사용하지 않았다. [공식 개요](https://developers.skyscanner.net/docs/referrals/overview)

공식 day-view 자료의 IATA 코드·출발/반환일·좌석·승객 조건을 검색 폼 검토에 참고했다. 이는 Skyscanner 연동 완료를 의미하지 않는다. [항공권 파라미터](https://developers.skyscanner.net/docs/referrals/flights-parameters), [공식 예시](https://developers.skyscanner.net/docs/referrals/examples)
