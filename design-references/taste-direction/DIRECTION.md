# 중간 구조 검토용 Taste 추출 기록

2026-11-01 실행, seed 20261102, achromatic-ledger. 초기 editorial-greeting을 대체한 두 번째 추출이다.

이후 사용자가 색상과 화면 전체를 더 젊고 경쾌하게 바꾸라고 요청하여 최종 UI는 라임·잉크, 굵은 제목, 둥근 카드, 티켓형 일정, 캡슐 하단 메뉴로 직접 재설계했다. 아래 cool white blue brief와 무채색 규칙은 과거 실행 원본이며 최신 색상 명세가 아니다. 최종 규격은 [DESIGN.md](../../DESIGN.md), 적용 과정은 [재설계 리뷰](../../docs/redesign-review.md)에 있다.

추출된 데스크톱 인사말·12열·통계 타일은 채택하지 않았다. 이 문서는 최종 라임 UI의 자동 생성·검사 통과 증거가 아니다.

---

# APP SHEET — Achromatic ledger
seed 20261102 · drawn 2026-11-01 · kind workspace · light
Brief: Korean Android and iOS travel planner, practical itinerary and flight search, cool white blue ink, no greeting or editorial hero

This is the style for this product. Adopt it whole and keep its move intact; everything the sheet does not fix comes from the
shared defaults in references/app-screens.md. Redraw only for a reason you can state (it contradicts the brand, or the product is
used in a way the style does not suit) and say why in your report. The values were measured on real product screens: use them
as given rather than rounding them towards your habits.

## 1. The style
The chrome holds no colour at all; money and counts are set in ink, the one chart hue marks the screen's main data element, and the only other hue is a small signal where a figure changed.

**The move:** No brand colour anywhere in the chrome: the one primary action is near-black (a grey pill, #e5e5e5, is the secondary), the main data element (today's series, the booked bars, the current period) takes the one chart hue, #1d5fbf, and everything else is drawn in ink with the comparison or the part after the cursor in grey, zero balances are set in grey, and the only hue on the screen is a small triangle or a short coloured number where a figure changed.

**Colour budget:** under 1% coloured pixels; the chrome is ink, the one chart hue goes on the main data element, and the only other hue is a small change signal.
**Off-style here:** a brand-coloured primary button; tinted cards or tinted KPI tiles; a coloured chart where one series in ink says it; bold on anything but money; a chart with no y-scale (the study's one example of this look hid magnitude behind hover and was flagged); a grey pill as the only button for the screen's main job: in testing an Approve repeated in grey on every row left the screen without a primary.
Distilled from 4 screens: a seller home, a per-agent insights page, a generated report, a settings page with usage numbers. The studied screens of this kind used 11-12px card radii, which §9.4 counts among the rare middle values; the library sets the tight pair (8 and 6) so the style lands on the bimodal rule. After testing, the chart hue moved from an unused third series colour onto the main data element: a screen whose only hue was a delta read as a kit.

**Its face.** Choose one of these (or one of your own of the same weight) before you build, write it in your notes next to
the screen's question, and check that it shows on the first screen. Without one the screen reads as a well-applied kit.
- A ground from the product's world in place of #f8f8f8, one level off white and low in chroma: a stone (#f4f3f1) for a bakery or a shop, a sage (#f2f5f2) for a garden or a greenhouse, a blue-grey (#f2f4f7) for a ledger or a meter. Cards stay white; a warm tint stays under the cream check (saturation under about 15%).
- The one chart hue (#1d5fbf, or the brand's own hue at 3:1 or more on white) on the screen's main data element: today's line, the booked bars, the current period's column, the margin that breaks. The chrome stays ink and the hue stays under 1% of the screen.
- Every figure in Geist Mono, as the style's decision, so money and counts read as a ledger's columns while the text stays Geist. Lint and the inspector flag mono; give the reason in the report.

## 2. Tokens
Kept from the brand: map its colours onto this style's roles (ground, surface, raised, border, rule, control, text, muted, accent, status pills, chart) and keep its proportions: under 1% coloured pixels; the chrome is ink, the one chart hue goes on the main data element, and the only other hue is a small change signal. Take the sizes, radii, row and control heights from the style as they are.

## 4. Shell — Open plane
The labelled sidebar sits on the page ground with nothing or one hairline between it and the canvas; there is no top bar, and the page header and cards sit straight on the ground about 32-36px from the top. Workspace or account switcher at the top of the sidebar, primary nav on a 34px pitch, groups under 12-13px sentence-case grey labels with 22-56px of air above, utilities and the user row pushed to the bottom. Cards are white on the off-white ground (or the sidebar is white with a 1px border on a #f9fafb canvas). The canvas is fluid with one gutter value, or a centred column for personal homes.
Numbers: sidebarWidth 230-256 (230, 240, 256 in the study; 280 and 292 with a built-in icon rail); separation none, or one 1px hairline; the sidebar sits on the page ground or within 1-2% of it; navPitch 34; navRowHeight 32; navIcon 16px outline, about 1.5px stroke; navLabel 14px, 400, grey or ink; activeItem neutral grey pill, radius 6-12, inset 8-12px from the sidebar edges (#e9e9e9-#ededed); topBar none; canvas fluid with a 24-64px gutter, or a centred 1100px column.
On a phone: The sidebar folds into a 48px top bar holding the screen's name, a menu button that opens a full-screen sheet with the nav and the switcher, and at most one action. Content takes the full width with 16px margins.

## 5. Screens

### home — Workspace home with a greeting
A centred column on a real 12-column grid (77px columns, 16px gutters, 1100px), about 72px from the top. The greeting is the h1 (48/700 in a serif, or 24/400 in the sans), with the date after it or in a grey line under it, never as a tracked eyebrow, and no emoji. 50px below, 4 count tiles spanning 3 columns each (128px tall, 16px padding): label, value at regular weight in tabular or mono figures, a plain caption ('Ready when you are'), no deltas. Then spans that change by row but share column lines (9 + 3, then 6 + 6, so each internal edge meets one above it) for the main objects: panels with 20px padding, a 14/600 title, a count chip and a 'View all' link, holding row cards 63px tall with 8px gaps, one step lighter than the panel with a border one step darker. Pale tints may code the panels, each in the hue of the tile that counts it. Only a floating composer, if there is one, carries a shadow, and it must not cover the last rows.
On a phone: Greeting at 24-28px; tiles in a 2 x 2 grid; panels stack in reading order.

### list
No pattern in the library for this screen. Compose it from the rules in references/app-screens.md.

### detail
No pattern in the library for this screen. Compose it from the rules in references/app-screens.md.

### settings — Settings in two columns
A centred column of about 1152px. Each section is one row: a label column of about 450px (16/500 title, 14px grey description; the title's cap top 6-8px below the panel top, level with the panel's first line) beside a panel of about 700px with a #fafafa fill, 16px radius, 16px padding, no border and no shadow. Sections are split by a full-width 1px rule with 32px above and below. Inside a panel: field labels 14/500 above 40px white inputs (1px #e8e8e8, radius 10), 8px from label to field, 16px between fields, 24px from the description to the first label, 16px to the Save button; switch rows with a 14/500 title, a 20px status badge 8px after it, a 14px grey description and a 28x16 toggle 16px from the right edge, centred on the title line. A plan-gated feature shows a 48px tinted strip with a 32px button directly above its panel, and the panel stays readable. When a panel runs much taller than its label, put the section's help or a summary of the current values in the label column rather than 250px of nothing.
On a phone: Each section stacks: title and description, then the panel at full width. Inside, label, 8-10px, field, 16-26px to the next pair; toggles stay on the right of their title.

## 6. Always
Real data for this product, written before the markup: names, amounts that add up, dates in order, units on every figure,
honest zeros and dashes. One chart answers one question. No gradients, glows, glass, icons in tinted squares, sparklines on KPIs,
rainbow charts, emoji, lorem or Acme. `<skill>/assets/app.css` and `<skill>/assets/charts.js` are a neutral start set to these
rules. Check with `node <skill>/scripts/lint.mjs <files>` and `node <skill>/scripts/inspect.mjs <page>`: this sheet switches both
to app rules.

Saved to <workspace>/work/travel-redesign-taste/direction.json · reprint with `node draw.mjs --show`
