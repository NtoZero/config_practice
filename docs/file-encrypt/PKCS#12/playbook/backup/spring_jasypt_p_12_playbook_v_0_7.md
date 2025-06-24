# Spring Boot + Jasypt (.p12) 플레이북 v0.7

> **작성일** 2025‑06‑24   |   **작성자** ChatGPT   |   **대상** Spring Boot 3.x (자바 17 이상 | Gradle 8.x | Kotlin DSL)

---

## 변경 이력

| 버전       | 날짜         | 작성자        | 주요 변경 내역                           |
| -------- | ---------- | ---------- | ---------------------------------- |
| v0.1     | 2024‑??‑?? | 최초 작성                              |
| v0.2     | 2025‑06‑24 | 논리·보안 취약점 보완, 운영 가이드 강화            |
| v0.3     | 2025‑06‑24 | Windows Server 스크립트·배포 전략 보강       |
| v0.4     | 2025‑06‑24 | Gradle 전용 구성 & 세부 주석 추가            |
| v0.6     | 2025‑06‑24 | Storepass 안전 저장 & 빈 비밀번호 허용 제거     |
| **v0.7** | 2025‑06‑24 | application.yml 주석 보강 & 문체 통일(\~함) |

---

## 1. 개요

본 문서는 **Spring Boot** 애플리케이션에서 **Jasypt**를 사용해 민감 정보를 **PKCS#12** 키스토어(.p12)에 저장된 비밀키로 암·복호화하는 절차를 다룸. Linux 및 **Windows Server** 환경을 모두 지원하며, **모든 의존성 관리는 Gradle( Kotlin DSL )** 로 구성함.

---

## 2. 핵심 설계 원칙

1. **비밀키·설정 분리** — 코드/컨테이너 이미지에서 민감 비밀 분리
2. **알고리즘 강제 지정** — `PBEWITHHMACSHA512ANDAES_256` + 100 000회 반복
3. **환경 변수 최소화** — Secret Volume(파일) 우선, 불가피할 때만 환경 변수 사용
4. **권한 최소화** — Keystore 파일과 비밀번호 파일은 애플리케이션 사용자만 읽기 권한 (Linux `400`, Windows `icacls /inheritance:r`)
5. **감사 로깅** — Keystore 로드/오류 발생 시 Slack/Webhook 알림
6. **키 회전·SBOM 스캔 자동화**

---

## 3. 준비 사항

| 항목              | 권장 버전          | 비고                                 |
| --------------- | -------------- | ---------------------------------- |
| JDK             | 17 이상          | Unlimited Crypto 내장                |
| Gradle          | **8.x**        | Kotlin DSL 기준 (`build.gradle.kts`) |
| Spring Boot     | 3.2.x          | `spring-boot-gradle-plugin` 사용     |
| Jasypt Starter  | 3.1.1 이상       | `com.github.ulisesbocchio`         |
| Docker / Podman | 선택             | 컨테이너 운용 시                          |
| Kubernetes      | 선택             | 클라우드/온프레미 배포                       |
| SBOM 도구         | CycloneDX 플러그인 | 취약점 스캔 CI 단계                       |

---

## 4. Keystore 생성 스크립트

### 4‑1. Linux / macOS (Bash · Zsh)

```bash
#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# create-keystore.sh — Linux/macOS용 .p12 키스토어 생성 스크립트
# -----------------------------------------------------------------------------
set -euo pipefail   # 스크립트 오류·파이프 실패 시 즉시 종료

# ✅ 1) 32바이트 난수 기반 STOREPASS 생성 (평문 노출 금지)
STOREPASS="$(openssl rand -base64 32)"

# ✅ 2) 비밀번호를 안전한 파일에 저장 (현재 디렉터리 기준)
echo "$STOREPASS" > .keystore_pass
chmod 400 .keystore_pass   # 소유자 읽기 전용

# ✅ 3) keytool로 4096‑bit RSA 키 + 자기서명 인증서 생성
keytool -genkeypair \
  -alias jasypt-key \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -storepass "$STOREPASS" \
  -keyalg RSA -keysize 4096 \
  -validity 3650 \
  -dname "CN=jasypt, OU=Dev, O=YourOrg, L=Seoul, C=KR"

unset STOREPASS   # 메모리 해제
printf '\n[OK] keystore.p12 생성 완료 — 비밀번호는 .keystore_pass 파일에 저장됨.\n'
```

### 4‑2. Windows Server (PowerShell 7+)

```powershell
<#+ ---------------------------------------------------------------------------
   create-keystore.ps1 — Windows Server용 .p12 키스토어 생성 스크립트
   실행: 관리자 PowerShell (Run as Administrator)
#> ---------------------------------------------------------------------------#>

$ErrorActionPreference = 'Stop'

# ✅ 1) 32바이트 난수 기반 STOREPASS 생성
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$STOREPASS = [Convert]::ToBase64String($bytes)

# ✅ 2) 비밀번호를 안전한 파일에 저장 (현재 경로)
$passFile = '.\keystore_pass.txt'
[IO.File]::WriteAllText($passFile, $STOREPASS)
# 현재 사용자만 읽기 권한
icacls $passFile /inheritance:r | Out-Null
icacls $passFile /grant:r "$Env:USERNAME:(R)" | Out-Null

# ✅ 3) keytool 경로 확인
$keytool = "$Env:JAVA_HOME\bin\keytool.exe"
if (-not (Test-Path $keytool)) { Throw "keytool.exe not found — JAVA_HOME 확인" }

# ✅ 4) .p12 키스토어 생성
& $keytool `
  -genkeypair `
  -alias jasypt-key `
  -storetype PKCS12 `
  -keystore keystore.p12 `
  -storepass $STOREPASS `
  -keyalg RSA -keysize 4096 `
  -validity 3650 `
  -dname "CN=jasypt, OU=Dev, O=YourOrg, L=Seoul, C=KR"

Remove-Variable STOREPASS  # 메모리 해제
Write-Host "[OK] keystore.p12 생성 완료 — 비밀번호는 $passFile 파일에 저장됨." -ForegroundColor Green
```

> **참고** Java 11+에서는 keypass와 storepass를 분리할 수 없음.

---

## 5. 비밀 배포 전략 (요약)

| 환경              | keystore 경로                  | 비밀번호 주입 방법                                |
| --------------- | ---------------------------- | ----------------------------------------- |
| Linux Systemd   | `/etc/secrets/keystore.p12`  | `/etc/secrets/.keystore_pass` 파일 읽기       |
| Docker Compose  | `/run/secrets/keystore.p12`  | 별도 Secret 파일 mount                        |
| Kubernetes      | `/mnt/keystore/keystore.p12` | `secretVolume` + init container로 환경 변수 주입 |
| Windows Service | `C:\Secrets\keystore.p12`    | `C:\Secrets\keystore_pass.txt` 파일 읽기      |

> **비밀번호를 환경 변수로 노출하지 않는** 구조를 권장함. 불가피하게 환경 변수를 쓸 경우 런타임 직후 비밀번호를 `unset` 처리함.

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

> **TIP** Gradle 8.x 이상에서는 **변경 감지**가 엄격해졌으므로, 커스텀 태스크의 입력/출력을 명확히 등록해 캐시 효과를 극대화함.

---

## 7. 애플리케이션 설정 파일

### 7‑1. `src/main/resources/application.yml`

```yaml
spring:
  jasypt:
    encryptor:
      algorithm: PBEWITHHMACSHA512ANDAES_256   # SHA‑512 HMAC + AES‑256 조합(PBE)
      key-obtention-iterations: 100000        # PBKDF2 반복 횟수(≥100 000)
      pool-size: 2                            # 병렬 Encryptor 인스턴스 수
      key-store:
        location: ${KEYSTORE_LOCATION:file:/run/secrets/keystore.p12}  # 실 배포 위치
        password: ${JASYPT_STOREPASS}        # ⬅ 환경 변수에서 주입되는 Keystore 비밀번호
        alias: jasypt-key                    # keystore 내부 키 별칭
```

### 7‑2. `src/main/resources/application-local.yml`

```yaml
spring:
  jasypt:
    encryptor:
      algorithm: PBEWITHHMACSHA512ANDAES_256   # SHA‑512 HMAC + AES‑256 조합(PBE)
      key-obtention-iterations: 100000        # PBKDF2 반복 횟수(≥100 000)
      pool-size: 2                            # 병렬 Encryptor 인스턴스 수
      key-store:
        location: ${HOME:file:${USERPROFILE}}/Secrets/keystore.p12  # 로컬 개발 위치
        password: ${JASYPT_STOREPASS}        # 환경 변수에서 주입되는 비밀번호
        alias: jasypt-key
```

> **애플리케이션 시작 전**: Linux 예시 — `export JASYPT_STOREPASS=$(cat .keystore_pass)` / Windows 예시 — `set JASYPT_STOREPASS=(Get-Content keystore_pass.txt)` 명령으로 환경 변수에 비밀번호를 주입함.

---

## 8. CI/CD 파이프라인

(변경 없음 — Gradle Wrapper 빌드 & SBOM 생성 단계 유지)

---

## 9. 트러블슈팅 & FAQ

| 증상                                                               | 원인               | 해결책                    |
| ---------------------------------------------------------------- | ---------------- | ---------------------- |
| `Keystore was tampered with, or password was incorrect`          | 잘못된 STOREPASS 로드 | 비밀번호 파일·환경 변수 값 확인     |
| `java.lang.IllegalArgumentException: Password must not be empty` | STOREPASS 미설정    | 실행 스크립트에 비밀번호 주입 명령 추가 |

---

## 10. 결론

v0.7은 **application.yml 주석 보강**과 **문체 통일(\~함)** 을 통해 가독성과 일관성을 향상시킴. `.keystore_pass`/`keystore_pass.txt` 파일을 안전하게 관리하고, 애플리케이션 실행 전 해당 값을 환경 변수로 주입하면 실·개발 환경 모두 안정적으로 동작함.

> 추가 피드백이나 문의 사항이 있으면 언제든 알림 바람. 대장님을 위한 최적의 가이드를 지속 제공할 예정임.

