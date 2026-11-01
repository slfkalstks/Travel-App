# 여행 앱 디자인 근거와 적용 기준

작성일: 2026-11-01. 이 문서는 확인한 출처와 프로젝트에 제안하는 디자인 결정을 구분한다. Apple·토스 재직자의 실제 검토나 승인을 받았다는 뜻은 아니다.

## 확인한 레퍼런스

| 출처 | 확인된 내용 | 최종 프로젝트에서 채택한 원칙 |
|---|---|---|
| [SHI UI](https://shi-ui.vercel.app/) | 종이색 `#e5e3dc`, 먹색 `#2a2926`, 주홍색 `#e14d2a`의 제한된 팔레트, 얇은 선, 평면적 구성, 실제 조작할 수 있는 컴포넌트 | 평면적 그룹과 행동 구분을 참고. 따뜻한 원본 팔레트는 채택하지 않고 최종 라임·잉크로 변경 |
| [Tween UI](https://tween-ui.vercel.app/) | React·GSAP·CSS 기반 소스 제공 방식, 단순 동작은 CSS 전환 사용, 동작 줄이기 지원을 안내 | 상태 전환에만 짧은 모션 사용. KMP에는 해당 코드를 직접 이식하지 않고 Compose의 대응 기능으로 구현 |
| [Sliding Tabs](https://tween-ui.vercel.app/component/sliding-tabs) | 선택·포커스에 반응하는 표시자, 키보드 포커스 대응, 동작 줄이기에서 위치 즉시 변경 | 날짜·하위 메뉴 선택이 눈에 보이도록 표시하고 색만으로 선택 상태를 전달하지 않음 |
| [Minimal Gallery](https://minimal.gallery/) · [Travel](https://minimal.gallery/tag/travel/) | 웹 디자인 큐레이션 및 여행 분류 확인 | 목적지 사진과 간결한 제목의 위계, 여백, 정돈된 정보 밀도를 검토할 참고 목록 |

사용자가 입력한 `mimimal.gallery`는 조회 도구에서 접근할 수 없었다. `minimal.gallery`는 실제 사이트와 여행 분류를 확인했고, 의도한 주소일 수 있다는 가정으로 참고했다. 두 주소가 같다고 단정하지 않는다. 이 조사에서는 사이트 문서·페이지 텍스트를 확인했다. 서브에이전트의 IAB 제약으로 세 사이트의 렌더링 화면을 직접 검증하지는 못했으며, 스크린샷을 확인했다고 주장하지 않는다.

## MengTo 스킬 설치와 사용

사용자 요청에 따라 [MengTo/Skills](https://github.com/MengTo/Skills)에서 아래 세 스킬을 설치하고, 설치 후 실제 `SKILL.md`를 읽었다. 모두 UI/UX 목적에 직접 필요한 스킬이며, 저장소 전체를 설치하거나 포함된 임의의 데모 스크립트를 실행하지 않았다.

| 스킬 | 설치 위치 | 적용 내용 |
|---|---|---|
| [design-first-ui-prompting](https://github.com/MengTo/Skills/tree/main/agent-skills/ui/design-first-ui-prompting) | `~/.codex/skills/design-first-ui-prompting` | 목표→화면 구조→타입→색상→제약 순으로 명세. 한 번에 1~2개 변수만 수정 |
| [clean-minimal-beige-light-mode](https://github.com/MengTo/Skills/tree/main/agent-skills/web-design/clean-minimal-beige-light-mode) | `~/.codex/skills/clean-minimal-beige-light-mode` | 일관된 간격과 평면적 그룹을 채택. 베이지·절제된 강조색 면적은 사용자 전면 변경에 따라 제외 |
| [animation-systems](https://github.com/MengTo/Skills/tree/main/agent-skills/web-design/animation-systems) | `~/.codex/skills/animation-systems` | 행동 확인·상태 변화·공간 연속성을 위한 모션, 지속 시간 토큰, 동작 줄이기 |

설치에 사용한 공식 helper 명령:

```sh
python3 ~/.codex/skills/.system/skill-installer/scripts/install-skill-from-github.py \
  --repo MengTo/Skills \
  --path agent-skills/ui/design-first-ui-prompting \
         agent-skills/web-design/clean-minimal-beige-light-mode \
         agent-skills/web-design/animation-systems \
  --method git
```

기본 다운로드 방식은 로컬 Python 인증서 검증 오류로 실패했고, 도구가 지원하는 Git 방식으로 재시도해 세 디렉터리 설치 성공 메시지를 확인했다. 인증서 검증을 해제하지 않았다. `main`에서 설치했으며 커밋을 고정하지 않았다. 다음 사용자 턴부터 설치 스킬이 자동 발견될 수 있고, 현재 작업에서는 파일을 읽어 적용한다.

설치 시점 `SKILL.md` SHA-256:

```text
design-first-ui-prompting      5e3d4db0eb0ea983f2bedb90f28f358901c292388a7889e2f66ac133ceff743c
clean-minimal-beige-light-mode 675e5d268ae36ae4aa59ff06cdaf19f67169defedc7a2cf5e7ca03a8f3eadb05
animation-systems             ae1e51b8eede6f4d05fda5388f61bbc1d29ea88b7a5d7a488c3ecfed5f18e262
```

## 최종 프로젝트 적용

최신 기준은 [DESIGN.md](../DESIGN.md), 실제 도구 적용·캡처 검수는 [재설계 리뷰](redesign-review.md)를 따른다. 초기의 따뜻한 바탕·산화철 강조·에디토리얼 세리프 제안과 중간 흰색·파란색 시안은 현재 디자인이 아니다.

최종 방향은 Signal `#D7FF4F`, SignalInk `#181B17`, Canvas `#FAFBF6`, Grouped `#EFF1E8`, 의미 강조 `#365300`이다. 굵은 산세리프, 둥근 카드, 티켓 형태 일정, 캡슐 하단 메뉴로 구성한다. 사진은 여행 카드 오른쪽의 세로 이미지이며 글자와 겹치지 않는다.

정보 구조는 여행·항공권·장소·기록 네 탭이며 설정은 상단에서 연다. 여행 상세에는 일정·준비물·예약·경비가 있다. 항공권은 실제 외부 Google 항공편 검색으로, 장소는 Google 지도 검색으로 연결한다. 앱 내부 실시간 운임·결제·예약 API가 구현되었다고 소개하지 않는다.

오사카 여행의 날짜·준비 완료 수·박수·경비를 데이터에서 계산한다. UI에 샘플·더미 배너를 반복하지 않고 개발용 초기 데이터의 범위는 개발 문서에서 관리한다. 실제 데이터가 없는 운임·환율·날씨·예약 확정을 만들어내지 않는다.

동작은 버튼의 짧은 눌림 반응과 180ms 화면 전환으로 한정하며 앱의 동작 줄이기 설정에 반응한다. OS 접근성 설정 자동 연동과 VoiceOver/TalkBack 검증 완료는 별개다. 입력 화면은 헤더·스크롤 입력·저장 영역을 분리해 키보드가 떠도 조작 가능하도록 했다.

## 확인한 증거와 남은 범위

실제 iOS 시뮬레이터의 [홈](screenshots/01-home.png), [일정](screenshots/02-schedule.png), [항공권](screenshots/06-flights.png), [다크 홈](screenshots/10-dark.png), [키보드](screenshots/14-keyboard.png)를 열어 주요 배치와 읽기 순서를 확인했다. 이는 원본 레퍼런스 사이트의 시각 검수와 구분된다.

작업 화면에서 동작하는 것과 플랫폼 전체를 검증한 것은 다르다. 모든 크기·글꼴 확대·보조기술·실물 기기의 통과 여부는 확인하지 않은 상태로 남긴다. 외부 디자이너의 승인이나 자동 품질 점수를 만들어내지 않는다.
