# 추가 디자인 도구 설치 및 사용 기록

확인일: 2026-11-01. 설치 성공과 인증·검증 성공을 구분한다.

| 사용자 요청 저장소 | 완료한 작업 | 현재 상태 |
|---|---|---|
| [21st-dev/codex-plugin](https://github.com/21st-dev/codex-plugin) | 공식 CLI로 마켓플레이스 등록 후 `21st@21st` 0.5.0 설치. 설치 목록에서 `installed: true`, `enabled: true` 확인. `21st-ui-build`, `21st-ui-review`, `21st-cli-use` 지침 읽음 | 플러그인 설치 완료. 현재 채팅에 MCP 도구는 아직 노출되지 않음. CLI 검색을 실제 시도했으나 로그인 필요 응답. OAuth 인증 완료로 주장하지 않음 |
| [Leonxlnx/taste-skill](https://github.com/Leonxlnx/taste-skill) | 공식 skill-installer로 `skills/taste-skill`을 `design-taste-frontend` 이름으로 설치. 앱 화면 지침을 읽고 앱 스타일 추첨 스크립트 실행 | 설치·로컬 스타일 생성 완료. Kotlin 자동 lint는 지원하지 않아 수동 리뷰에 적용 |
| [vercel-labs/web-interface-guidelines](https://github.com/vercel-labs/web-interface-guidelines) | 원본 문서를 로컬 참고 자료로 복제. README가 안내하는 `vercel-labs/agent-skills`의 `web-design-guidelines`도 설치. 현재 `command.md` 규칙 읽음 | 설치·문서 확보 완료. 폼·키보드·상태·모션 규칙을 Compose에 대응시켜 리뷰 |
| [voltagent/awesome-design-md](https://github.com/voltagent/awesome-design-md) | 저장소를 로컬 참고 자료로 복제하고 Apple·Airbnb 분석 문서 검토 | 디자인 문서 모음 확보 완료. 실행형 플러그인으로 설치했다고 주장하지 않음 |

## 실제 설치 명령

```sh
codex plugin marketplace add 21st-dev/codex-plugin --json
codex plugin add 21st@21st --json
codex plugin list --marketplace 21st --json

python3 ~/.codex/skills/.system/skill-installer/scripts/install-skill-from-github.py \
  --repo Leonxlnx/taste-skill --path skills/taste-skill \
  --name design-taste-frontend --method git

python3 ~/.codex/skills/.system/skill-installer/scripts/install-skill-from-github.py \
  --repo vercel-labs/agent-skills --path skills/web-design-guidelines --method git

git clone --depth 1 https://github.com/voltagent/awesome-design-md.git \
  outputs/travel-app/design-references/awesome-design-md
git clone --depth 1 https://github.com/vercel-labs/web-interface-guidelines.git \
  outputs/travel-app/design-references/web-interface-guidelines
```

설치 경로:

- 21st: `~/.codex/plugins/cache/21st/21st/0.5.0`
- Taste: `~/.codex/skills/design-taste-frontend`
- Vercel 리뷰: `~/.codex/skills/web-design-guidelines`

설치 스킬은 다음 사용자 턴부터 자동 발견될 수 있다. 현재 작업에서는 설치된 파일을 직접 읽었다. 21st의 이미 실행 중인 채팅에 도구가 자동으로 다시 로드되었다고 가정하지 않는다.

참고 자료 복제 커밋:

- awesome-design-md: `fcc70284932276d085f01ef9f83edde90b1258cb`
- web-interface-guidelines: `434b7f91364665f2f733b310ec54809bf8f37937`

## 실제 사용과 확인 결과

### 21st

현재 manifest의 서버는 `https://21st.dev/api/mcp`, `streamable-http`이며 인증 정책은 `ON_INSTALL`이다. 현행 portable manifest는 OAuth 방식이고, 레거시 `.mcp.json`은 API 키 호환 방식을 제공한다. 따라서 API 키가 반드시 있어야만 설치할 수 있다는 설명은 부정확하다.

공식 npm 패키지 버전 1.18.0을 조회한 뒤, 설치 lifecycle script를 끈 상태로 CLI를 실행했다. 기본 npm 캐시의 권한 오류는 사용자 폴더 권한을 변경하지 않고 작업 폴더의 전용 캐시로 해결했다.

```sh
npm --cache /tmp/travel-design-work/npm-cache \
  exec --yes --ignore-scripts --package=@21st-dev/cli@1.18.0 -- \
  21st search 'travel itinerary' --limit 3 --json
```

실제 응답: `Not signed in. Run 21st login or set TWENTYFIRST_TOKEN.`

```sh
npm --cache /tmp/travel-design-work/npm-cache \
  exec --yes --ignore-scripts --package=@21st-dev/cli@1.18.0 -- \
  21st review ./app/composeApp/src/commonMain/kotlin --json
```

실제 결과: `filesReviewed: 0`, `errors: 0`, `warnings: 0`, `suggestions: 0`.
이는 Kotlin 검사를 통과한 결과가 아니다. 지원 파일이 없어 검토한 파일이 0개라는 뜻이다. 검색 결과·컴포넌트 코드·AI 생성물을 받아 사용했다고 주장하지 않는다. 프로젝트별 토큰 우선, 실제 프레임워크 유지, 결정적 오류와 취향 의견의 구분, 접근성·모바일 상태 검토는 읽은 스킬 지침을 수동 리뷰에 적용했다.

인증 연결은 사용자 계정 로그인이 필요한 미완료 항목이다. 호스팅 AI 생성은 사용 가능 여부를 확인하지 않았고 실행하지 않았다.

### Taste Skill — 초기 실행과 최종 적용 구분

스크립트 import와 파일 쓰기 범위를 확인한 뒤 로컬 앱 스타일 생성 경로를 실행했다. 이력 파일은 `TASTE_SKILL_HISTORY`로 작업 폴더를 지정했다.

초기 실행 seed `20261101`에서는 `editorial-greeting`과 `achromatic-ledger`가 반환되어 전자를 선택했다. 당시에는 기존 팔레트·글꼴을 유지했다. **이 초기 방향은 사용자의 전면 재설계 요청으로 폐기되었으며 최종 화면 기준이 아니다.**

전면 재설계에서는 seed `20261102`로 다시 실행하고 `achromatic-ledger`를 구조 참고로 선택했다. 현재 보관된 [방향 문서](taste-direction/DIRECTION.md)와 [JSON](taste-direction/direction.json)은 이 두 번째 실행 결과다.

```sh
TASTE_SKILL_HISTORY="$PWD/work/taste-history.json" \
node "$HOME/.codex/skills/design-taste-frontend/scripts/draw.mjs" \
  --app --kind workspace \
  --brief 'Korean Android and iOS travel planner, practical itinerary and flight search, cool white blue ink, no greeting or editorial hero' \
  --screens home,list,detail,settings --theme light --keep palette,type \
  --seed 20261102 --style achromatic-ledger --out work/travel-redesign-taste
```

그 뒤 사용자가 색상과 UI 전체를 더 젊고 경쾌하게 바꾸라고 명시해 **라임·잉크, 굵은 제목, 둥근 카드, 티켓형 일정, 캡슐 하단 메뉴**를 직접 설계했다. 흰색·파란색 brief는 중간 단계 실행 기록으로 보존하며 최신 팔레트라고 주장하지 않는다. 무채색 크롬·인사말·작은 데스크톱 컨트롤을 최종 앱에 강제하지 않았다. 최종 기준은 [DESIGN.md](../DESIGN.md)다.

Taste에서 실제 적용한 것은 `references/app-screens.md`의 화면당 한 질문, 데이터에서 합계 계산, 정돈된 목록, 모바일 읽기 순서·빈 값·오류·입력 상태 규칙이다. 사용자 요청이 muted-only 색상·떠 있는 메뉴 제한보다 우선한다. 라임 디자인 전체를 Taste가 생성했거나 모든 규칙을 통과했다고 표현하지 않는다.

`lint.mjs`는 `.kt`를 지원하지 않는다. DOM inspector가 Compose의 canvas 내부를 일반 HTML처럼 검사한다고 주장하지 않는다. 자동 점수 대신 Compose 소스와 실제 iOS 캡처를 검토했으며 [재설계 리뷰](../docs/redesign-review.md)에 범위를 기록했다.

### Vercel 및 DESIGN.md 컬렉션

Vercel 규칙에서는 단일 체크박스 터치 영역, 필드 근처 오류, 첫 오류 포커스, 동작 줄이기, 의미 있는 레이블, 긴 내용 대응을 검토 기준으로 채택했다. HTML·React 전용 API는 Kotlin 코드에 복사하지 않고 Compose의 semantics·focus·layout 개념에 대응한다.

awesome-design-md의 Apple·Airbnb 문서는 제3자의 웹사이트 분석이다. Apple·Airbnb의 공식 HIG나 직원 승인으로 소개하지 않는다. 사진의 역할, 정보 위계, 행동색의 일관성, 읽기 쉬운 본문과 터치 영역을 여행 앱에 재해석한 [프로젝트 DESIGN.md](../DESIGN.md)를 작성했다. 원본 MIT LICENSE는 복제 저장소에 그대로 보존했다.
