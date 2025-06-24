# Spring 기반 컨테이너 환경에서 **암호화된 설정 파일** 관리 방안

작성일: 2025-06-24

---

## 1. 배경 및 목적
애플리케이션의 **설정 파일(예: `application.yml`, 인증서, API Key)** 에 포함된 민감 정보는
코드 저장소와 컨테이너 이미지에 평문으로 남을 경우 심각한 보안 사고로 이어질 수 있습니다.
따라서 *파일 자체* 또는 *개별 속성*을 암호화하고, **컨테이너 실행 시점**에 안전하게 복호화하여
Spring 환경변수·프로퍼티로 주입하는 패턴이 필요합니다.

이 문서는 Spring Boot/Spring Cloud 생태계에서 널리 사용하는 다음 다섯 가지 솔루션을
① 암호화 원리 ② Spring Config 통합 방법 ③ 컨테이너 배포 패턴 ④ 장‧단점으로 구조화하여 설명합니다.

1. Jasypt Spring Boot
2. Spring Cloud Config `{cipher}`
3. Mozilla SOPS(+age/GPG/KMS)
4. Docker·Kubernetes Secrets(Config Tree)
5. HashiCorp Vault + Spring Vault

---

## 2. 솔루션별 상세 설명

### 2.1 Jasypt Spring Boot
- **암호화 방식**: 기본 AES-256. 속성 값을 `ENC(...)` 형태로 감싸 Git에 커밋합니다.
- **키 주입**: 컨테이너 실행 시 `JASYPT_ENCRYPTOR_PASSWORD` 환경변수로 전달.
- **Spring 통합**: `jasypt-spring-boot-starter` 의존성 추가 후 별도 코드 수정 없이 복호화 처리.
- **Dockerfile 예시**:
  ```dockerfile
  FROM eclipse-temurin:21-jre
  ARG JAR_FILE=target/app.jar
  COPY ${JAR_FILE} app.jar
  ENTRYPOINT ["java","-jar","/app.jar"]
  ```
  ```bash
  # 실행 예시 (docker-compose.yml)
  environment:
    - JASYPT_ENCRYPTOR_PASSWORD=${JASYPT_KEY}
  ```
- **장점**: 도입 간단, 라이브러리만 추가하면 끝. 속성 단위 세밀 제어.
- **단점**: 키 보관 전략을 직접 설계해야 하며, 운영 중 키 교체(회전)가 번거로움. citeturn0search0turn0search5

### 2.2 Spring Cloud Config Server `{cipher}`
- **암호화 방식**: Config Server가 Git에서 `{cipher}` 프리픽스가 붙은 값을 발견하면 복호화 후
  클라이언트에 평문으로 전달.
- **키 주입**: Config Server 프로세스가 `encrypt.key=…` 혹은 외부 KMS를 설정.
- **아키텍처**: `client ⇆ Config Server ⇆ Git` 3계층 구조로 중앙 집중 관리.
- **파일 제공**: 속성 단위 암호화가 기본이지만, 바이너리 파일 자체를 Git에 암호화 후 별도 URL로
  내려주고 클라이언트 사이드카에서 복호화하는 패턴도 가능.
- **장점**: 마이크로서비스 다수일 때 키 회전·롤백·감사에 유리.
- **단점**: 별도 서버를 운영해야 하고 네트워크 장애 시 의존성 발생. citeturn0search1turn0search6

### 2.3 Mozilla SOPS (+ age / KMS)
- **암호화 방식**: YAML/JSON/ENV 파일 전체를 암호화하여 GitOps Workflow에 그대로 커밋.
  - SOPS는 파일 내부 *각 키 값*을 개별적으로 암호화해 git diff 가독성을 유지.
- **키 주입**: AWS KMS, GCP KMS, Azure Key Vault, PGP, age 중 선택.
- **런타임 처리**: 컨테이너 Entrypoint 스크립트에서 `sops -d`로 평문 파일을 생성 후
  `--spring.config.import=file:/tmp/app.yml` 옵션으로 Spring에 주입.
- **장점**: “비밀은 절대 평문으로 Git에 남기지 않는다” 규정 준수, 다중 KMS 지원.
- **단점**: 애플리케이션이 아닌 *배포 파이프라인* 단계에서 복호화‧주입 로직을 관리해야 함. citeturn0search2turn0search7

### 2.4 Docker / Kubernetes Secrets (Config Tree)
- **암호화 방식**: 비밀을 Swarm/K8s Secret 객체로 저장(ETCD 암호화 가능).
- **마운트 경로**: 컨테이너 내부 `/run/secrets/<name>` 혹은 K8s `/var/run/secrets/...`.
- **Spring 통합**: Spring Boot 2.4+는 `spring.config.import=configtree:/run/secrets/` 옵션만 추가하면
  파일명 = 프로퍼티 키, 파일 내용 = 값 으로 자동 매핑.
- **장점**: 이미지 빌드 결과물에 비밀이 아예 존재하지 않아 재현성 보존.
- **단점**: Swarm/K8s 환경이 전제, 로컬 개발 시 별도 시뮬레이션 필요. citeturn0search3turn0search8

### 2.5 HashiCorp Vault + Spring Vault
- **암호화 방식**: Vault Transit Secrets Engine이 ‘Encryption‑as‑a‑Service’를 제공.
- **통합 라이브러리**: `spring-vault-core`, `spring-cloud-starter-vault-config`.
- **워크플로**:
  1. 애플리케이션이 Vault Agent Sidecar 또는 AppRole/Token으로 인증
  2. `@ConfigurationProperties` 바인딩 시 Vault Template/Environment Repository를 통해 평문 수신
- **장점**: 강력한 정책(RBAC), 감사 로그, 동적 DB Credentials 발급 등 엔터프라이즈 기능.
- **단점**: Vault 클러스터 설치·운영이 필요, 초기 학습 비용 高. citeturn0search4turn0search9

---

## 3. Spring Config 로딩 패턴 비교
| 솔루션 | Spring 구성 예시 | 비고 |
|--------|-----------------|------|
| Jasypt | `spring.datasource.password=ENC(...)`<br/>`JASYPT_ENCRYPTOR_PASSWORD` 환경변수 | 속성 단위 |
| Config Server | Git `{cipher}` 기록 → Config Server → Client | 중앙 집중 방식 |
| SOPS | `ENTRYPOINT: sops -d enc.yml > /tmp/app.yml && \`<br/>`--spring.config.import=file:/tmp/app.yml` | 파일 전체 | 
| Docker Secret | `spring.config.import=configtree:/run/secrets/` | 파일명=키 |
| Vault | `spring.cloud.vault.*` 설정, Transit 엔진 호출 | 런타임 API |

---

## 4. 컨테이너 주입 전략 Best Practice
1. **Build → Run 단계 분리**: 비밀은 *빌드 시* 이미지 레이어에 포함하지 말고, *런타임*에 `docker secret`,
   `--env-file`, 혹은 `sidecar` 패턴으로 주입합니다.
2. **Key Rotation**: Jasypt·SOPS 등 파일 암호화 방식의 경우, *CI/CD 파이프라인 속성 재암호화 스크립트*를
   포함해 키 교체를 자동화합니다.
3. **Audit & Policy**: Vault·K8s Secret 사용 시 RBAC·감사 로그를 활성화하고, 최소 권한 원칙을 적용합니다.
4. **현지 개발 환경**: Docker Secret/K8s Secret을 사용하더라도, `docker-compose.override.yml` 또는
   `Kind` 클러스터로 개발자가 동일한 경로와 설정을 체험할 수 있게 합니다.

---

## 5. 사용성 총평
| 평가 항목 | Jasypt | Config Server | SOPS | Docker/K8s Secret | Vault |
|-----------|-------|--------------|------|-------------------|-------|
| 초기 설정 난이도 | **낮음** | 중간 | 중간 | 낮음 | **높음** |
| 키 회전 편의성 | 낮음 | **높음** | 중간 | 중간 | **높음** |
| Git 평문 노출 | 일부 있음 | 일부 있음 | **없음** | 없음 | 없음 |
| 컨테이너 친화도 | 중간 | 중간 | 중간 | **높음** | 중간 |
| 엔터프라이즈 기능 | 낮음 | 중간 | 낮음 | 낮음 | **매우 높음** |

---

## 6. 결론 및 권장 조합
- **소규모·단일 서비스**: Jasypt + Docker Secret 환경변수 주입
- **다수 마이크로서비스, GitOps**: Spring Cloud Config Server + `{cipher}`
- **엄격한 규정(“Git 평문 금지”)**: SOPS(+KMS) 파일 암호화
- **대규모 엔터프라이즈**: HashiCorp Vault + Spring Vault 통합

---

## 7. 참고 문헌
1. Java Techie, “Spring Boot Password Encryption using Jasypt” citeturn0search0
2. GeeksforGeeks, “How to encrypt passwords in a Spring Boot project using Jasypt” citeturn0search5
3. Spring Docs, “Encryption and Decryption — Spring Cloud Config” citeturn0search1
4. Medium, “Spring Cloud Config — Encryption and Decryption at Rest” citeturn0search6
5. Techno Tim, “Encrypting with Mozilla SOPS and AGE” citeturn0search2
6. getsops/sops GitHub README citeturn0search7
7. StackOverflow, “How to handle Docker-Secrets in application.properties files” citeturn0search3
8. Spring Boot Issue #25095, ConfigTree Doc citeturn0search8
9. HashiCorp, “Transit secrets engine” Docs citeturn0search4
10. Spring Guide, “Accessing Vault” citeturn0search9
