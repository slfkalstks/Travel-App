# 오프라인 목적지 데이터

검증·생성일: 2026-11-01 UTC. 앱의 국가 → 도시/지역 선택기는 네트워크 호출 없이 아래 공개 원본을 변환한 리소스를 사용한다. 수량은 원본 설명의 대략적인 숫자가 아니라 실제 내려받은 파일을 처리한 결과다.

## 범위와 한계

| 항목 | 실제 수량 |
|---|---:|
| 국가·영토 코드 | 257 |
| 도시/수도 레코드 | 34,195 |
| 1단계 행정구역 | 3,864 |
| 도시 + 행정구역 선택 항목 | 38,059 |
| 도시가 포함된 국가 코드 | 244 |
| 현재 한국어 별칭이 있는 도시 | 6,105 |
| 현재 한국어 별칭이 있는 1단계 행정구역 | 2,394 |

- 국가·영토는 Unicode CLDR `idStatus=regular`의 257개 코드다. 이는 주권국만의 목록이나 ISO 3166-1의 249개 항목과 동일한 개념이 아니다. 영토·특별 지역도 포함하며, 폐기된 `AN`, `CS`, `YU` 등과 시험용 코드는 제외한다. 이 분류는 여행지 검색용이며 정치적 지위에 대한 앱의 판단을 뜻하지 않는다.
- 도시는 [GeoNames 공식 README](https://download.geonames.org/export/dump/readme.txt)의 `cities15000.zip` 범위다. 원본은 인구 15,000명 초과 도시 또는 수도라고 설명한다. 모든 마을·읍·관광지·섬·숙소가 포함된 목록은 아니다. 이번 파일의 34,195개 도시 레코드를 모두 보존했고 국가별 임의 개수 제한은 없다.
- 지역은 `admin1CodesASCII.txt`의 3,864개 레코드를 모두 보존한다. 1단계 행정구역이며 하위 시·군·구 전부가 아니다. 같은 이름의 도시와 행정구역은 각각 별개 항목이다.
- 원본은 지속적으로 갱신되는 공동 지리 데이터이며 [GeoNames는 정확성·최신성·완전성을 보증하지 않는다](https://www.geonames.org/export/). 이 앱도 원본 데이터의 모든 명칭·경계·인구가 최신이라고 주장하지 않는다. 기본 도시 순서는 해당 스냅샷의 인구 필드 내림차순이다.
- 한국어 표시는 GeoNames `alternateNamesV2`의 `ko` 별칭 중 현재 이름을 사용한다. 역사적/구어적 별칭과 종료 시점이 있는 별칭은 표시명 후보에서 제외하고, preferred → short → 원본 ID 순으로 선택한다. 한국어 이름이 없으면 원래 이름을 보존한다. 영어 이름은 현재 `en` 별칭이 우선이고, 없으면 ASCII 전사명 또는 원래 이름을 쓴다. ASCII 전사가 반드시 통용 영어 명칭이라는 뜻은 아니다. 명칭을 임의 번역하지 않았다.
- 검색은 한국어·영어 별칭, 원래 이름, ASCII 전사명, 국가 코드/ISO3(원본에 있는 경우)를 사용한다. 역사적 영문·한글 별칭은 검색에만 남는다. 국가 검색에는 CLDR의 short/variant 명칭도 포함한다. 다른 언어 전체 별칭은 앱 크기를 줄이기 위해 포함하지 않는다.

## 앱에서의 동작

`DestinationCatalog.load()`는 Compose 리소스를 `Dispatchers.Default`에서 한 번 파싱하고 Mutex로 보호한 공용 캐시에 보관한다. Android/Desktop/iOS 공통 코드이며 플랫폼 파일 경로나 온라인 지오코딩 API가 필요 없다.

`searchCountries(query)`는 국가 목록만, `searchPlaces(countryCode, query)`는 먼저 선택한 국가에 속하는 도시/지역만 대상으로 검색한다. 비어 있는 검색어도 국가 범위를 지킨다. 검색 결과 개수에 임의 상한을 두지 않는다. 영문 대소문자와 공백 차이를 무시하며 한글을 영문으로 전사하지 않는다. 정확한 표시명/영문명 일치 → 별칭 일치 → 접두어 → 부분 문자열 순으로 정렬하고, 동순위는 원본의 인구 순서를 유지한다. 지역 이름 검색은 해당 도시의 표시용 상위 지역도 함께 검색한다. 초성·오타 교정·악센트 제거 검색은 구현하지 않았다.

도시 ID는 `city:<GeoNames ID>`, 지역 ID는 `region:<GeoNames country.admin1 code>`다. 이름이 같더라도 ID가 다르면 삭제/병합하지 않는다. GeoNames admin1 코드 체계는 국가별로 FIPS/ISO 등이 혼재하므로 이를 ISO 3166-2 코드라고 부르지 않는다.

`DestinationCatalogTest`는 국가별 격리(빈 검색어 포함), 동명 지명 보존, 코드/별칭 검색, 한글·공백·대소문자 처리, 결과 250개 비절삭, 손상된 데이터 거부를 검증한다. 통합 빌드와 실행 검증 결과는 별도 검증 문서에 기록한다.

## 원본과 재생성

1. Python 3.10+와 `curl`이 필요하다. 외부 라이브러리나 API 키는 필요 없다.
2. 저장소 루트에서 `python3 scripts/generate-destination-catalog.py --cache /원하는/임시경로/geo-catalog` 실행. 캐시를 생략하면 시스템 임시 디렉터리의 `travelapp-geo-catalog`을 쓴다. `--refresh`는 GeoNames 최신 원본을 다시 내려받는다.
3. `alternateNamesV2.zip` 205,174,661바이트는 캐시의 중간 입력이며 앱에 포함하지 않는다. 스트리밍으로 필요한 지명의 `ko`/`en` 행만 메모리에 유지한다.
4. 원본 GeoNames URL은 매일 바뀌므로 동일 바이트 재생성에는 이번 캐시 파일 또는 아래 SHA-256과 일치하는 입력이 필요하다. CLDR 입력은 각각 불변 커밋 URL로 고정했다. 새 GeoNames 입력으로 재생성하면 건수/해시가 달라질 수 있으므로 이 문서도 함께 갱신한다.
5. 출력은 UTF-8 TSV 두 개, 출처/라이선스 고지, 출처·건수·바이트·해시를 기록한 `manifest.json`이다. 스키마는 국가 `code, name, englishName, aliases`; 지명 `id, countryCode, name, englishName, region, kind, aliases`이며 실제 구분자는 탭, 별칭 구분자는 `|`다.

### 입력 파일 검증값

| 입력 | 바이트 | SHA-256 | 공식 원본 |
|---|---:|---|---|
| `countryInfo.txt` | 32,112 | `79f8f59ea01cbb40438bf1856dd6c2ff201f03f8fc72d0530b2646aaf603ef04` | [원본](https://download.geonames.org/export/dump/countryInfo.txt) |
| `cities15000.zip` | 3,365,242 | `1d1a86cc57a641387453beae0a0f89a59cbea636143fccb17368580df23776eb` | [원본](https://download.geonames.org/export/dump/cities15000.zip) |
| `admin1CodesASCII.txt` | 151,510 | `42458864faa9887933d75c33408d4a808b765edbcd62a1328fa9e8cfcdfc718e` | [원본](https://download.geonames.org/export/dump/admin1CodesASCII.txt) |
| `alternateNamesV2.zip` | 205,174,661 | `f9e7ec200cee791918b55f3def24f5af65fa723fa8bf2d5dec97d71731931eee` | [원본](https://download.geonames.org/export/dump/alternateNamesV2.zip) |
| `geonames-readme.txt` | 8,843 | `b1957379b6c1242c700c98ac9a8aa0a09f56c3c0a50ee72175527005f48ef2c5` | [원본](https://download.geonames.org/export/dump/readme.txt) |
| `cldr-ko.json` | 10,673 | `47b2f98d9cbf823068b0594074fe5db7fde80f9baf6a8038bd24ed81aefe7829` | [원본](https://raw.githubusercontent.com/unicode-org/cldr-json/7d2b9cb11cec08f4d333ad73209deb3df0711c3b/cldr-json/cldr-localenames-full/main/ko/territories.json) |
| `cldr-en.json` | 10,190 | `1326fb3bcdf8b8cf63df73ea02ea7f8bf7a8362cbb60bd12bcdb53f3ad22bf04` | [원본](https://raw.githubusercontent.com/unicode-org/cldr-json/7d2b9cb11cec08f4d333ad73209deb3df0711c3b/cldr-json/cldr-localenames-full/main/en/territories.json) |
| `cldr-region.xml` | 1,905 | `e751e0eedd46b52c38f3cdb72b0fab61ac8b48e052e8b28ba74b6ac26c4c8cb1` | [원본](https://raw.githubusercontent.com/unicode-org/cldr/0c0d98c9d1fd2efaa2cb191a6bf2626d9d1e288b/common/validity/region.xml) |
| `UNICODE-3.0.txt` | 2,033 | `220ba0e1c43b99530d2d5bdb892a99dca0989414f51ab695ecd90163eaa1ec3b` | [원본](https://raw.githubusercontent.com/unicode-org/cldr-json/7d2b9cb11cec08f4d333ad73209deb3df0711c3b/LICENSE) |

### 앱에 포함된 파일 검증값

| 파일 | 바이트 | SHA-256 |
|---|---:|---|
| `countries.tsv` | 16,563 | `237b5f928447f6099ab8b846a30574b2c9ce2d2b7c556b0f0fca2b0a80b4e34b` |
| `places.tsv` | 2,853,967 | `57ce54a5a0a43139135f3a8006a34679bc87188fa3ea8e9b872f7efe2432d63d` |
| `ATTRIBUTION.txt` | 2,748 | `158c293ccca3ecad6e1083c510973a09153352020b76f403f6a572c411597287` |

`manifest.json`은 생성 시각을 포함하므로 자체 해시를 자기 안에 기록하지 않는다. 입력과 TSV가 동일하면 데이터 내용은 동일하며 생성 시각은 달라진다.

## 저작권과 출처 표시

- 도시·행정구역·인구 정렬·지명 별칭: **GeoNames**, [geonames.org](https://www.geonames.org/), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). 변경 사항: 원본의 일부 필드/한국어·영어 별칭 선택, 한국어 표시명 선택, CLDR 유효 국가 코드 필터, TSV 변환, 원본 인구에 따른 정렬. GeoNames의 후원/보증을 의미하지 않는다.
- 국가·영토 표시명과 코드 유효성: **Unicode CLDR**, [공식 프로젝트](https://cldr.unicode.org/), Unicode License V3 (`Unicode-3.0`). 전체 저작권·허가 고지는 [`licenses/UNICODE-3.0.txt`](licenses/UNICODE-3.0.txt)에 보존했다.
- 배포용 통합 고지는 [`licenses/DESTINATION_ATTRIBUTION.txt`](licenses/DESTINATION_ATTRIBUTION.txt)와 앱에 동봉되는 `files/destinations/ATTRIBUTION.txt`에 보존했다. 데이터와 함께 이 고지를 배포하고, 앱 출처 화면에서는 GeoNames와 Unicode CLDR 링크를 제공한다.
