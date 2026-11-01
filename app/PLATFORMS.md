# KMP 앱 실행

이 프로젝트의 제품 대상은 Android와 iOS 모바일 앱입니다. 공통 화면과 데이터 코드는 `composeApp/src/commonMain`에 있고, 두 플랫폼이 동일한 `TravelApp()`을 사용합니다.

`desktop`이라는 이름의 JVM Gradle 타깃은 공통 코드 테스트 러너로만 남아 있습니다. 데스크톱 실행 진입점과 DMG/MSI/Deb 배포 설정은 없습니다.

## Android

Android Studio에서 이 `app` 폴더를 열고 Android SDK 37을 설치합니다. `local.properties`의 `sdk.dir`은 자신의 SDK 경로로 설정합니다. Android Studio에서 `androidApp`을 실행하거나 아래 명령을 사용합니다.

```sh
./gradlew :androidApp:assembleDebug
```

생성 APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

로컬 데모 서버는 Android 에뮬레이터에서 `http://10.0.2.2:8088`에 연결합니다. 로컬 HTTP 허용은 debug manifest로 한정됩니다. Windows PowerShell에서는 위 빌드 명령 대신 `.\gradlew.bat :androidApp:assembleDebug`를 사용합니다.

### Android 실기기: USB 포트 연결

백엔드는 개발 PC의 `127.0.0.1:8088`에만 바인딩합니다. 앱의 주소를 PC의 LAN IP로 바꾸는 것만으로는 접속할 수 없습니다. USB로 연결한 개발 기기에서는 다음 절차를 사용하세요.

1. 백엔드를 실행하고 휴대폰의 USB 디버깅을 허용합니다. Android SDK `platform-tools`의 `adb`가 PATH에 있어야 합니다.
2. `composeApp/src/androidMain/kotlin/com/travel/app/data/PlatformClient.kt`의 `demoBaseUrl()` 반환값을 실기기 개발용 `http://127.0.0.1:8088`로 변경합니다. 현재 저장소 기본값은 에뮬레이터용이며 환경변수로 전환하는 기능은 없습니다.
3. 아래 명령으로 USB 포트 연결을 설정하고 Debug 앱을 다시 빌드·설치합니다. `-d`는 연결된 USB 기기 한 대를 선택합니다. 여러 기기라면 `-s 기기일련번호`를 사용하세요.

```sh
adb devices
adb -d reverse tcp:8088 tcp:8088
./gradlew :androidApp:assembleDebug
adb -d install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

휴대폰의 `127.0.0.1:8088` 요청이 개발 PC의 서버로 전달되며 서버 바인딩은 유지됩니다. 앱을 직접 열어 사용하고, USB 연결을 끊거나 기기를 다시 시작한 뒤에는 포트 연결 명령을 다시 실행하세요. 끝나면 `adb -d reverse --remove tcp:8088`로 해제할 수 있습니다. 이후 일반 에뮬레이터 실행으로 돌아갈 때는 기본 URL `http://10.0.2.2:8088`을 복원하세요.

## iOS

macOS에서 Xcode를 설치한 뒤 `iosApp/MorrowTravel.xcodeproj`를 열고 MorrowTravel scheme과 iPhone 시뮬레이터를 선택합니다. 실제 기기 설치는 자신의 Apple 개발 팀을 Signing 설정에 지정해야 합니다.

Xcode 빌드 스크립트가 `:composeApp:embedAndSignAppleFrameworkForXcode`를 실행합니다. Xcode에서 사용하는 JDK가 없으면 JAVA_HOME을 JDK 21로 설정합니다. `project.yml`은 XcodeGen 소스이며 생성된 `.xcodeproj`도 포함했습니다.

```sh
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

로컬 서버 연결 주소는 iOS 시뮬레이터 `http://127.0.0.1:8088`, Android 에뮬레이터 `http://10.0.2.2:8088`입니다.

실제 iPhone에서 `127.0.0.1`은 iPhone 자신을 가리킵니다. 개발 Mac의 LAN 주소로 바꿔도 loopback 전용 백엔드에는 연결되지 않습니다. 현재 검증 범위는 iOS 시뮬레이터이며, 실기기 서버 연결은 별도 구현이 필요합니다. 실기기에서 원격 서버를 사용하려면 접근 제어가 있는 사설 개발 네트워크 또는 인증된 HTTPS 게이트웨이와 iOS 앱 URL 설정을 함께 준비하세요. 현재 백엔드에는 사용자 인증이 없으므로 공개 터널이나 `0.0.0.0` 바인딩으로 그대로 노출하면 안 됩니다.

## 검증 결과 (2026-11-01)

- Android: 최종 UI 전면 수정 후 `:androidApp:assembleDebug` 성공(7초), API 35 에뮬레이터에 재설치·실행 확인. APK는 `../releases/yeojeong-android-debug.apk`에 저장하며 저장소에서는 제외합니다.
- iOS: `:composeApp:linkDebugFrameworkIosSimulatorArm64` 성공. 최종 모바일 수정 후 Xcode 26.6의 Debug iPhone simulator 앱 빌드 성공, iPhone 16 Pro / iOS 18.2 시뮬레이터 재설치·실행 확인. Bundle ID는 `com.travel.app`입니다.
- 공통 상태 관리 12개 + 다중 여행 상태 9개 + 목적지 검색 4개 + 예약 상태 4개 + 현지 시각 5개 + 외부 URL 4개 + JVM 전체 시간대 전환 비교 1개: 총 39개 통과 (내부 JVM 테스트 러너).
- 예약 CRUD 추가 후 Android·iOS를 다시 빌드·설치했다. 실제 iOS에서 예약 생성·이름/현지 시각 수정·시간대 검색·재실행 후 유지를 확인했다. Android 새 예약 UI 조작은 아직 검증하지 않았다.
- Android 시스템 뒤로가기: 설정 화면에서 시스템 Back을 눌렀을 때 홈이 복원되고 앱이 전면에 남는 것을 접근성 트리로 확인했습니다.
- 항공권 연결: Google 항공편의 왕복·편도 주소가 공항·날짜를 반영하는 것을 실제 브라우저로 확인했습니다. 자세한 근거는 `../docs/FLIGHT_SEARCH_INTEGRATION.md`에 있습니다.
- 실제 iPhone 기기 서명과 앱스토어 배포는 수행하지 않았습니다.

## 고정 버전과 근거

- Kotlin / Compose compiler: 2.4.20
- Compose Multiplatform: 1.12.1
- Material 3: 1.9.0
- Android Gradle Plugin: 9.1.1
- Gradle wrapper: 9.6.0
- Ktor client: 3.6.0
- Coroutines / Serialization JSON: 1.11.0
- kotlinx-datetime: 0.7.1 (현재 빌드에서 고정한 버전)
- Android: minSdk 26 / compileSdk 37 / targetSdk 36
- iOS: deployment target 16.0

Kotlin 공식 호환성 표에 따르면 Kotlin 2.4.20은 Gradle 7.6.3–9.7.0 및 AGP 8.5.2–9.3.1 범위에 해당합니다. Compose 플러그인과 Compose compiler 버전을 별도로 관리하며 compiler는 Kotlin과 동일 버전입니다.

출처:
- https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html
- https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- https://developer.android.com/kotlin/multiplatform/plugin

Android 호스트를 독립 모듈로 분리하고, 공통 라이브러리에는 공식 Android KMP 플러그인을 사용합니다. iOS 실제 기기 서명 및 스토어 배포는 이 데모에 포함되지 않습니다.
