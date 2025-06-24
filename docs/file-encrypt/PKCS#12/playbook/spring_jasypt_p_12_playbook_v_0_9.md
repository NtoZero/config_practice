# Spring Boot + Jasypt (.p12) 플레이북 v0.9 — 보안 강화판
> **작성일** 2025‑06‑24   |   **작성자** ChatGPT  
> **대상** Spring Boot 3.x (자바 17 이상 | Gradle 8.x | Kotlin DSL)  

---
## 변경 이력
| 버전 | 날짜 | 주요 변경 내역 |
|------|------|---------------|
| v0.8 | 2025‑06‑24 | CI/CD 파이프라인 상세화 · (검토본) |
| **v0.9** | 2025‑06‑24 | • password‑file 속성 도입으로 환경 변수 의존 제거<br>• 키 유효 기간 365일로 축소 및 회전 절차 명시<br>• Keystore / 비밀번호 파일 전달·보관 절차 추가<br>• Docker 이미지 민감 정보 포함 금지 지침 보강<br>• 의존성 자동 업데이트(Dependabot·Renovate) 가이드 |
---
## 1. 개요
Spring Boot 애플리케이션에서 **Jasypt**를 사용해 민감 설정 값을 **PKCS#12** 키스토어(.p12)의 비밀키로 암·복호화하는 절차를 정리함.  
Linux·Windows Server·Docker·Kubernetes 환경을 모두 지원하며, **환경 변수 노출을 완전 배제**하고 **파일 기반 Secret** 주입을 기본으로 설계함.

## 2. 핵심 설계 원칙
1. **비밀키·설정 완전 분리** — 컨테이너 이미지·Git Repo에 어떤 비밀도 포함하지 않음.  
2. **알고리즘 고정** — `PBEWITHHMACSHA512ANDAES_256` + 100 000회 반복.  
3. **환경 변수 사용 금지** — `password‑file` 속성을 활용해 Keystore 비밀번호를 파일로 주입함.  
4. **최소 권한** — Keystore · 비밀번호 파일은 애플리케이션 사용자만 읽기(`400`/`icacls (R)`).  
5. **키 회전 자동화** — 유효 기간 365일, 만료 30일 전 알림 ↓ (11장 참조).  
6. **SBOM·취약점 스캔·의존성 자동 업데이트** — CycloneDX·Trivy·Dependabot / Renovate 적용.

## 3. 준비 사항
| 항목 | 권장 버전 | 비고 |
|------|----------|------|
| JDK | 17 이상 | Unlimited Crypto 내장 |
| Gradle | 8.x | Kotlin DSL(`build.gradle.kts`) |
| Spring Boot | 3.2.x | `spring-boot-gradle-plugin` |
| Jasypt Starter | 3.1.1 이상 | `com.github.ulisesbocchio` |
| Docker/Podman | 선택 | 컨테이너 운용 |
| Kubernetes | 선택 | 클러스터 배포 |
| SBOM 도구 | CycloneDX 플러그인 | 취약점 스캔 |

## 4. 키 관리·회전 정책
| 항목 | 값 |
|------|----|
| 키 타입 | RSA 4096 bit 자기서명 |
| 유효 기간 | **365일** |
| 회전 주기 | 12개월 ± SLA |
| 만료 알림 | 만료 **30일 전** Slack / 메일 알림 |
| 회전 절차 | 1) 새 키스토어·비밀번호 파일 생성<br>2) Secret Manager 등록<br>3) 새 버전으로 Blue‑Green 배포<br>4) 24h 안정화 후 구버전 Secret 삭제 |

## 5. Keystore 생성 스크립트
### 5‑1 Linux/macOS (Bash · Zsh)
```bash
#!/usr/bin/env bash
set -euo pipefail
STOREPASS="$(openssl rand -base64 32)"
echo "$STOREPASS" > .keystore_pass && chmod 400 .keystore_pass

keytool -genkeypair -alias jasypt-key -storetype PKCS12 \
  -keystore keystore.p12 -storepass "$STOREPASS" \
  -keyalg RSA -keysize 4096 -validity 365 \
  -dname "CN=jasypt,OU=Dev,O=YourOrg,L=Seoul,C=KR"

unset STOREPASS
echo "[OK] keystore.p12 · .keystore_pass 생성 완료."
```
### 5‑2 Windows Server (PowerShell 7+)
```powershell
$ErrorActionPreference = 'Stop'
$bytes = [byte[]]::new(32); [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$STOREPASS = [Convert]::ToBase64String($bytes)
$passFile = '.\keystore_pass.txt'
[IO.File]::WriteAllText($passFile,$STOREPASS)
icacls $passFile /inheritance:r | Out-Null
icacls $passFile /grant:r "$Env:USERNAME:(R)" | Out-Null

& "$Env:JAVA_HOME\bin\keytool.exe" -genkeypair -alias jasypt-key `
  -storetype PKCS12 -keystore keystore.p12 -storepass $STOREPASS `
  -keyalg RSA -keysize 4096 -validity 365 `
  -dname "CN=jasypt,OU=Dev,O=YourOrg,L=Seoul,C=KR"

Remove-Variable STOREPASS
Write-Host "[OK] keystore.p12 · keystore_pass.txt 생성 완료."
```

## 6. 비밀 전달·보관 절차
1. **CI Stage “secure‑artifacts”** — 생성된 `keystore.p12` 및 비밀번호 파일을 **Git Ignore**로 제외하고 Runner 워크스페이스에서만 존재토록 함.  
2. **Secret Storage** —  
   * 온프레미스 → HashiCorp Vault KV v2 `secret/jasypt/*`  
   * AWS → Secrets Manager `jasypt/keystore` (binary), `jasypt/storepass` (text)  
   * Kubernetes → `Secret` (opaque) + `secretVolume`  
3. **배포 시점** —  
   * **Docker Compose** → `secrets:` / `--mount type=secret`  
   * **K8s Helm** → `values.yaml` 에 `keystoreSecretName`, `passSecretName` 지정  
4. **런타임** — 애플리케이션 컨테이너 내 `/run/secrets/keystore.p12`, `/run/secrets/keystore_pass` 경로로 마운트.  

## 7. 애플리케이션 설정
`src/main/resources/application.yml`
```yaml
spring:
  jasypt:
    encryptor:
      algorithm: PBEWITHHMACSHA512ANDAES_256
      key-obtention-iterations: 100000
      pool-size: 2
      key-store:
        location: ${KEYSTORE_LOCATION:file:/run/secrets/keystore.p12}
        # ⬇️ 비밀번호가 아닌 **비밀번호 파일** 경로
        password-file: ${JASYPT_STOREPASS_PATH:file:/run/secrets/keystore_pass}
        alias: jasypt-key
```
> 🚫 `password:` 속성 사용 금지. 환경 변수 주입이 필요 없음.

## 8. Gradle 설정 (`build.gradle.kts`)
```kotlin
plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.5"
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.github.ulisesbocchio:jasypt-spring-boot-starter:3.1.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

/* 의존성 자동 업데이트 */
tasks.register("dependabotReminder") {
    doLast { println("➡️  Dependabot PR / Renovate MR 확인 잊지 말 것.") }
}
```

## 9. Dockerfile 발췌
```dockerfile
FROM eclipse-temurin:17-jre
ARG JAR_FILE
COPY ${JAR_FILE} app.jar
# 🚫 keystore.p12 포함 금지 → 런타임 Secret Volume 사용
ENTRYPOINT ["java","-jar","/app.jar"]
```

## 10. GitHub Actions 파이프라인 요약
| Job | 주요 단계 | 비밀 처리 |
|-----|-----------|-----------|
| build-test | Gradle Build · JUnit | 비밀 미사용 |
| secure-artifacts | 키 생성 스크립트 실행, secrets push(Vault) | Runner 메모리·디스크 한정 |
| containerize | Docker Build · Trivy Scan | 이미지에 비밀 미포함 |
| deploy | Helm 차트 적용, Secret Volume Mount | 클러스터 Secret Store 읽기 |

> **전체 워크플로우 예시는 별도 `ci.yaml` 참고.**

## 11. 키 회전 자동화
### 11‑1 만료 모니터링 스크립트 (Bash)
```bash
#!/usr/bin/env bash
exp=$(keytool -list -v -keystore keystore.p12 -storetype pkcs12 \
              -storepass "$(cat keystore_pass)" | grep "Valid from" | awk '{print $4,$5,$6}')
expiry_epoch=$(date -d "$exp" +%s)
now=$(date +%s); diff=$(( (expiry_epoch - now)/86400 ))
[[ $diff -le 30 ]] && curl -X POST -H 'Content-Type: application/json' \
  -d '{"text":"[Jasypt] 키스토어 만료까지 '"$diff"'일 — 회전 필요"}' $SLACK_WEBHOOK
```
### 11‑2 회전 프로세스 (간략)
1. `create‑keystore.sh` 재실행 → 새 `keystore.p12`·pass 파일 생성.  
2. `secure-artifacts` Job 수행 → Vault `secret/jasypt/2025‑new` 경로 저장.  
3. Helm `values.yaml` 패치 → `secretVersion: 2025‑new`.  
4. `kubectl rollout restart deployment spring-jasypt`.  
5. 24h 모니터링 후 구버전 Secret 삭제.

## 12. 트러블슈팅 & FAQ
| 증상 | 원인 | 해결책 |
|------|------|-------|
| `Password file not found` | Secret Volume 경로 오타 | Deployment yaml 확인 |
| `Keystore tampered or password incorrect` | pass 파일·keystore 불일치 | 둘 다 최신 버전인지 확인 후 재배포 |

## 13. 결론
v0.9 부터 **환경 변수 제거** · **키 회전 자동화** · **민감 정보 전달 절차 강화**를 통해 설계 원칙과 구현이 완전히 일치하도록 조정함.  
대장님께서는 본 가이드를 적용해 **운영 환경 보안 수준을 한층 강화**할 수 있음. 추가 문의 사항은 언제든 전달 바람.