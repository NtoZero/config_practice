# Spring Boot + Jasypt (.p12) 플레이북 v0.4

> **작성일** 2025‑06‑24   |   **작성자** ChatGPT   |   **대상** Spring Boot 3.x (자바 17 이상 | Gradle 8.x | Kotlin DSL)

---

## 변경 이력

|  버전      |  날짜          |  작성자          |  주요 변경 내역                                                                      |
| -------- | ------------ | ------------- | ------------------------------------------------------------------------------ |
|  v0.1    |  2024‑??‑??  |  기존 플레이북 작성자  |  최초 작성                                                                         |
|  v0.2    |  2025‑06‑24  |  ChatGPT      |  논리·보안 취약점 보완, 운영 가이드 강화                                                       |
|  v0.3    |  2025‑06‑24  |  ChatGPT      |  Windows Server 스크립트·배포 전략 보강                                                  |
| **v0.4** |  2025‑06‑24  |  ChatGPT      |  **Gradle 전용 의존성 선언 & 자세한 주석** (쉘 스크립트, `build.gradle.kts`, `application.yml`) |

---

## 1. 개요

이 문서는 **Spring Boot** 애플리케이션에서 **Jasypt**를 사용해 민감 정보를 **PKCS#12** 키스토어(.p12)에 저장된 비밀키로 암·복호화하는 방법을 설명합니다. Linux 및 **Windows Server** 환경을 모두 다루며, **모든 의존성 관리는 Gradle( Kotlin DSL )** 로 구성합니다.

---

## 2. 핵심 설계 원칙

1. **비밀키·설정 분리** — 코드/컨테이너 이미지에서 민감 비밀 분리
2. **알고리즘 강제 지정** — `PBEWITHHMACSHA512ANDAES_256` + 100 000회 반복
3. **환경 변수 최소화** — Secret Volume(파일) 우선, 불가피할 때만 환경 변수 사용
4. **권한 최소화** — Keystore 파일은 애플리케이션 사용자만 읽기 권한 (Linux `400`, Windows `icacls /inheritance:r`)
5. **감사 로깅** — Keystore 로드/오류 발생 시 Slack/Webhook 알림
6. **키 회전·SBOM 스캔 자동화**

---

## 3. 준비 사항

| 항목              | 권장 버전          | 비고                                 |
| --------------- | -------------- | ---------------------------------- |
| JDK             | 17 이상          | Unlimited Crypto 내장                |
| Gradle          | **8.x**        | Kotlin DSL 기준 (`build.gradle.kts`) |
| Spring Boot     | 3.2.x          | `spring-boot-gradle-plugin` 사용     |
| Jasypt Starter  | 3.1.1 이상       | `com.github.ulisesbocchio`         |
| Docker / Podman | 선택             | 컨테이너 운용 시                          |
| Kubernetes      | 선택             | 클라우드/온프레미 배포                       |
| SBOM 도구         | CycloneDX 플러그인 | 취약점 스캔 CI 단계에서 사용                  |

---

## 4. Keystore 생성 스크립트

### 4‑1. Linux / macOS (Bash · Zsh)

```bash
#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# create-keystore.sh — Linux/macOS용 .p12 키스토어 생성 스크립트
# -----------------------------------------------------------------------------
set -euo pipefail   # 스크립트 오류·파이프 실패 시 즉시 종료

# ✅ 1) 32바이트 난수 기반 STOREPASS 생성
export STOREPASS="$(openssl rand -base64 32)"
# echo "[INFO] 생성된 STOREPASS = $STOREPASS" # 스크립트 절대 X

# ✅ 2) keytool로 4096‑bit RSA 키 + 자기서명 인증서 생성
keytool -genkeypair \
  -alias jasypt-key \                    # 키 별칭(고유 식별자)
  -storetype PKCS12 \                    # 키스토어 타입 (Java 9+ 표준)
  -keystore keystore.p12 \               # 출력 파일명
  -storepass "$STOREPASS" \              # 키스토어 비밀번호
  -keyalg RSA -keysize 4096 \            # RSA 4096bit
  -validity 3650 \                       # 유효기간(일) = 10년
  -dname "CN=jasypt, OU=Dev, O=YourOrg, L=Seoul, C=KR"  # 주체 DN

# ✅ 3) 결과 안내
printf '\n[OK] keystore.p12 생성 완료\n'
```

### 4‑2. Windows Server (PowerShell 7+)

```powershell
<#+ ---------------------------------------------------------------------------
   create-keystore.ps1 — Windows Server용 .p12 키스토어 생성 스크립트
   실행: 관리자 PowerShell (Run as Administrator)
#> ---------------------------------------------------------------------------#>

$ErrorActionPreference = 'Stop'   # 오류 발생 시 즉시 종료

# ✅ 1) 32바이트 난수 기반 STOREPASS 생성 (Base64 인코딩)
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$STOREPASS = [Convert]::ToBase64String($bytes)
# Write-Host "[INFO] 생성된 STOREPASS = $STOREPASS" -ForegroundColor Yellow # Windows 스크립트 절대 X

# ✅ 2) keytool 경로 확인 (JAVA_HOME 환경 변수 요구)
$keytool = "$Env:JAVA_HOME\bin\keytool.exe"
if (-not (Test-Path $keytool)) {
  Throw "keytool.exe not found. JAVA_HOME가 올바른지 확인하세요."
}

# ✅ 3) .p12 키스토어 생성
& $keytool `
  -genkeypair `
  -alias jasypt-key `               # 별칭
  -storetype PKCS12 `               # p12
  -keystore keystore.p12 `          # 출력 파일
  -storepass $STOREPASS `           # 비밀번호
  -keyalg RSA -keysize 4096 `       # RSA 4096bit
  -validity 3650 `                  # 10년
  -dname "CN=jasypt, OU=Dev, O=YourOrg, L=Seoul, C=KR"

Write-Host "[OK] keystore.p12 생성 완료" -ForegroundColor Green
```

> **참고** Java 11+ 기준으로 keypass / storepass 분리 지원이 제거되었습니다.

---

## 5. 비밀 배포 전략

(이전 버전과 동일 — 파일 권한·경로·Windows NTFS ACL 예시는 v0.3 내용 유지)

---

## 6. Gradle 설정 (`build.gradle.kts`)

```kotlin
/*
 * build.gradle.kts — 단일 모듈 예시 (멀티 모듈일 경우 설정 분리)
 * - Kotlin DSL 기반
 * - Spring Boot + Jasypt + 테스트 의존성 선언
 */

plugins {
    // Spring Boot 플러그인(BOM 관리 및 BootRun 등 제공)
    id("org.springframework.boot") version "3.2.5"
    // Spring 의존성 버전 관리 (io.spring.dependency-management)
    id("io.spring.dependency-management") version "1.1.5"
    // Kotlin 지원 (선택 — Java만 사용 시 제외)
    kotlin("jvm") version "1.9.22" apply false
}

java {
    sourceCompatibility = JavaVersion.VERSION_17   // JDK 17 고정
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    // Maven Central 외 기업 Nexus/Artifactory가 있다면 추가
    mavenCentral()
}

dependencies {
    // ---- Spring Boot Starters ----
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Jasypt Spring Boot Starter (암복호화)
    implementation("com.github.ulisesbocchio:jasypt-spring-boot-starter:3.1.1")

    // ---- 테스트 ----
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
}

// CycloneDX SBOM 플러그인 (취약점 관리)
plugins.apply("org.cyclonedx.bom")

cyclonedxBom {
    schemaVersion.set("1.5")   // 최신 스키마 지정
}

// BootJar 메타데이터 커스텀 (예: Build Info)
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    manifest.attributes["Implementation-Version"] = project.version
}
```

> **TIP** Gradle 8.x 이상에서는 **변경 감지**가 엄격해졌으므로, 커스텀 태스크의 입력/출력을 명확히 등록해 캐시 효과를 극대화하세요.

---

## 7. 애플리케이션 설정 파일

### 7‑1. `src/main/resources/application.yml`

```yaml
spring:
  # ----------------------------
  # Jasypt 암복호화 설정
  # ----------------------------
  jasypt:
    encryptor:
      algorithm: PBEWITHHMACSHA512ANDAES_256      # ✅ 강력한 알고리즘 지정
      key-obtention-iterations: 100000            # ✅ 반복 횟수 (권장 100 000 이상)
      pool-size: 2                                # 병렬 복호화 풀 크기 (CPU 코어 ≤)
      key-store:
        location: ${KEYSTORE_LOCATION:file:/run/secrets/keystore.p12}  # ⬅ Secret 파일 경로(환경에 따라 오버라이드)
        password: ${JASYPT_STOREPASS:}            # ⬅ STOREPASS (빈 값 허용 — Secret Volume 사용 시)
        alias: jasypt-key                         # 키 별칭 (4‑1 단계와 동일)

  # ----------------------------
  # 기본 DataSource etc. (예시)
  # ----------------------------
  datasource:
    url: "jdbc:mysql://localhost:3306/app_db"
    username: ENC(hH6...)
    password: ENC(Xk8...)
  profiles:
    include: actuator

server:
  port: 8080   # 서버 포트

management:
  endpoints.web.exposure.include: "health,info"
```

### 7‑2. `src/main/resources/application-local.yml`

```yaml
spring:
  profiles:
    active: local
  jasypt:
    encryptor:
      algorithm: PBEWITHHMACSHA512ANDAES_256
      key-obtention-iterations: 100000
      pool-size: 2
      key-store:
        # 로컬 OS별 홈 디렉터리 기반 경로 (Windows ↔ Linux 겸용)
        location: ${HOME:file:${USERPROFILE}}/Secrets/keystore.p12
        password: ${JASYPT_STOREPASS:}
        alias: jasypt-key

logging.level.root: INFO   # 로컬 기본 로그 레벨
```

> **중요** 로컬에서 먼저 `JASYPT_STOREPASS` 환경 변수를 export/setx 하거나, IDE 실행 구성(Environment Variables)으로 지정해야 애플리케이션이 keystore를 정상 로드합니다.

---

## 8. CI/CD (발췌)

- v0.3 파트와 동일하되, `` 전에 `./gradlew cyclonedxBom` 태스크를 실행해 SBOM 생성 → 취약점 스캔.
- Windows Agent에서는 **Gradle Wrapper (**``**)** 로 빌드하십시오.

---

## 9. 트러블슈팅 & FAQ

(내용 동일 — 예외 메시지 대응, Windows NTFS ACL 문제 등)

---

## 10. 결론

본 v0.4 플레이북은 **Gradle 전용** 의존성 관리와 **주석이 풍부한** 예제 스크립트·설정 파일을 포함합니다. 그대로 복사해 프로젝트에 적용하면 **운영 = 개발** 동일 구성으로 암복호화를 검증할 수 있습니다.

> 추가 조정이 필요하거나 새로운 시나리오가 생기면 언제든 알려주세요. 대장님을 위한 맞춤 솔루션을 계속 제공하겠습니다!

