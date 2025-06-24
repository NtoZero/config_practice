# Spring 기반 컨테이너 환경에서 **암호화된 설정 파일** 관리 방안 (라이선스·On‑Premise 고려 포함)

작성일: 2025-06-24

---

## 1. 문서 개요
B2B 솔루션을 **Docker 컨테이너 형태**로 고객사(On‑Premise) 환경에 배포할 때는 **기술적 적합성**뿐 아니라  
**오픈소스 라이선스 준수**와 **재배포(Commercial Distribution) 제한** 여부를 함께 검토해야 합니다.  

본 문서는 기존의 암호화/비밀 관리 솔루션 5종에 대해 **최신 라이선스 정책(2025‑06 기준)** 과  
**On‑Premise 배포 난이도** 를 추가로 정리합니다.

---

## 2. 솔루션별 상세 분석 (라이선스 & On‑Premise)

### 2.1 Jasypt Spring Boot
| 항목 | 내용 |
|------|------|
| **주요 라이브러리** | `jasypt` (core) / `jasypt-spring-boot-starter` |
| **라이선스** | **Apache License 2.0** |
| **재배포 제약** | 없음. 이 라이선스는 사본·수정본을 상용/폐쇄 소프트웨어에 자유롭게 포함 가능. |
| **On‑Premise 지원** | ✔ 라이브러리만 의존성이므로 고객 환경에서도 별도 서비스 없이 동작 |
| **기업 상용 배포 시 유의** | `NOTICE` 파일과 라이선스 전문을 이미지 또는 문서에 포함 |

---

### 2.2 Spring Cloud Config Server `{cipher}`
| 항목 | 내용 |
|------|------|
| **주요 컴포넌트** | Spring Cloud Config Server / Client |
| **라이선스** | **Apache License 2.0** |
| **재배포 제약** | 없음 |
| **On‑Premise 지원** | ◑ 별도 Config Server(자바 애플리케이션)를 고객사에 같이 배포해야 함 |
| **기업 상용 배포 시 유의** | Config Server 이미지에 타 OSS(Git, SSH 등) 포함 라이선스 챙길 것 |

---

### 2.3 Mozilla SOPS (+ age / KMS)
| 항목 | 내용 |
|------|------|
| **주요 컴포넌트** | `sops` CLI (Go 바이너리) / `age` |
| **라이선스** | **MPL 2.0** (file‑level copyleft) |
| **재배포 제약** | ▶ 바이너리 그대로 재배포 **가능**.<br/>▶ 소스 수정 시 수정 파일만 MPL 공개 의무 |
| **On‑Premise 지원** | ✔ CLI만 포함하면 오프라인 환경에서도 동작. KMS 연결은 고객 인프라 필요 |
| **기업 상용 배포 시 유의** | 컨테이너에 `LICENSES/` 디렉터리와 변경 이력 포함 |

---

### 2.4 Docker / Kubernetes Secrets (Config Tree)
| 항목 | 내용 |
|------|------|
| **주요 컴포넌트** | Docker Engine / Compose / Kubernetes |
| **라이선스** | Docker CE & Compose: **Apache 2.0**<br/>Kubernetes: **Apache 2.0** |
| **재배포 제약** | 자체 OSS 컴포넌트를 컨테이너 내부에 포함하지 않으므로 실질적 제약 없음 |
| **On‑Premise 지원** | ✔ 고객사가 Swarm·K8s 클러스터를 운영해야 함 |
| **기업 상용 배포 시 유의** | K8s 배포 매니페스트에 Third‑Party CRD가 있다면 별도 라이선스 확인 |

---

### 2.5 HashiCorp Vault + Spring Vault
| 항목 | 내용 |
|------|------|
| **주요 컴포넌트** | Vault OSS 1.15+ / Vault Enterprise |
| **라이선스 (2025)** | **BUSL 1.1** (HashiCorp trademark) *— 4년 후 Apache 2.0 전환*<br/>Vault < 1.12: MPL 2.0 |
| **재배포 제약** | BUSL는 “Vault를 **서비스형(Managed SaaS)** 로 제공” 시 금지.<br/>On‑Prem 배포(고객 환경에서 자체 운영) **허용** |
| **On‑Premise 지원** | ◑ 고객사에 Vault Server 클러스터 설치·운영 필요 |
| **기업 상용 배포 시 유의** | Enterprise 기능 사용 시 **별도 상업 라이선스 계약** 필수 |

> **BUSL 1.1 FAQ 요약**  
> - **배포 형태**: 제품 이미지에 Vault OSS BIN 포함 → 허용.  
> - **SaaS 형태**: 자사 클라우드에서 Vault API를 외부 고객에게 제공 → 금지 (별도 계약).

---

## 3. 라이선스 & 배포 적합성 요약표

| 솔루션 | 라이선스 | 상용 재배포 위험도 | SaaS 경쟁제한 조항 | On‑Prem 설치 난이도 |
|--------|---------|-------------------|--------------------|---------------------|
| Jasypt | Apache 2.0 | 낮음 | 없음 | **매우 쉬움** |
| Spring Cloud Config | Apache 2.0 | 낮음 | 없음 | 쉽지만 별도 서버 필요 |
| SOPS | MPL 2.0 | 낮음 | 없음 | 쉬움 |
| Docker/K8s Secrets | Apache 2.0 | 낮음 | 없음 | 고객 클러스터 필요 |
| Vault (OSS) | BUSL 1.1 | **중간** | SaaS 제공 금지 | **높음** (클러스터) |

---

## 4. 라이선스 컴플라이언스 체크리스트
1. **컨테이너 이미지** 내 `/licenses` 또는 `/opt/oss-licenses` 폴더에 서드파티 라이선스 전문 & NOTICE 파일 포함  
2. CI 파이프라인에 **`Syft`/`Syft‑Grype` SBOM**(Software Bill of Materials) 생성 단계 삽입  
3. 고객사 배포 문서에 “오픈소스 구성요소 및 라이선스” 별첨표 포함  
4. HashiCorp Vault 사용 시 **BUSL 1.1 허용 범위**(On‑Prem 배포만) 명시  
5. SOPS 바이너리 변조 시 해당 파일만 MPL 2.0 공개 의무 안내  

---

## 5. 권장 시나리오 (B2B Docker 제품)

| 시나리오 | 기술 조합 | 라이선스 대응 방안 |
|----------|-----------|-------------------|
| **경량 단일 컨테이너** | Spring Boot + Jasypt + Docker Secret | Apache 2.0 NOTICE 동봉 |
| **다수 서비스 + GitOps** | Config Server(`{cipher}`) + SOPS | 두 컴포넌트 모두 Apache/MPL → 이미지 NOTICE 및 SBOM |
| **고객사가 K8s 사용** | K8s Secret(ConfigTree) + SOPS | Kubernetes Apache 2.0 → 매니페스트에 라이선스 표기 |
| **은행·금융사 고보안** | Vault (On‑Prem) + Spring Vault | BUSL 1.1 준수 안내 & 엔터프라이즈 계약 필요 여부 확인 |

---

## 6. 참고 링크
- HashiCorp BUSL 1.1 FAQ (2024‑12 개정)  
- Mozilla SOPS GitHub LICENSE  
- Apache License 2.0 템플릿 & NOTICE 작성 가이드  
- CNCF Kubernetes LICENSE & NOTICE  

---

**최종 메모:** 본 문서에서 언급된 라이선스 정보는 2025‑06‑24 기준 공식 저장소 / 배포판을 확인하여 작성했습니다.  
상위 메이저 버전 업그레이드나 라이선스 변경이 발표될 수 있으므로, **릴리스 업그레이드 시점에 다시 검토**하시기 바랍니다.
