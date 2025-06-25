
# keystore-property-source-spring-boot-starter 설계서

> **작성일:** 2025‑06‑25  
> **대상 환경:** Spring Boot 3.x · Java 17  
> **작성자:** BOAT‑AI

---

## 1. 목표

- **PKCS#12(PKCS#12, `.p12`)** 파일에서 alias 별로 비밀 값을 읽어와  
  **Spring Boot `Environment`** 에 동적으로 주입한다.  
- 메인 모듈의 `application.yml` / `application.properties` 에 선언된  
  place‑holder(예: `${JASYPT_PASSWORD}`)가 keystore 값으로 자동 해석되도록 한다.  
- 여러 서비스가 **공통 스타터** 의존성만 추가하면 재사용할 수 있도록 한다.

---

## 2. 동작 흐름

```text
┌─────────────────────┐
│ main‑module.jar     │              ┌────────────────────┐
│ (Spring Boot 3.x)   │              │ PKCS#12 keystore   │
│                     │ 1. 경로/비번  │  (e.g. secrets.p12 │
│  ──┐                │   지정        │   storepass …)     │
└────▼────────────────┘              └────────▲───────────┘
     │ 2. 환경 초기화                                     ▲
     │            KeystoreEnvironmentPostProcessor        │ 3. alias → 값
     │  (공통 모듈)                                      │   추출
     │                                                   │
     │ 4. MapPropertySource("keystore") 추가             │
     │                                                   │
     └──▶ Spring Property Resolution … ${JASYPT_PASSWORD}─┘
```

1. **keystore 위치/비밀번호**는 JVM 시작 시 시스템 속성·환경변수 등으로 전달  
   - `-Dkeystore.path=file:secrets/keystore.p12`  
   - `export KEYSTORE_PASSWORD=…`
2. `EnvironmentPostProcessor`가 keystore를 열어  
   **`alias == property name`** 인 항목을 `MapPropertySource`로 변환
3. 이후 `application.yml` 파싱 과정에서 `${…}` 플레이스홀더가 해결됨

---

## 3. 모듈 구조

```
keystore-property-source-spring-boot-starter
├── src/main/java
│   └── com/example/keystore/
│       ├── KeystoreEnvironmentPostProcessor.java
│       ├── KeystorePropertySource.java
│       └── KeyExtractor.java
└── src/main/resources
    └── META-INF/spring.factories   ← EnvironmentPostProcessor 등록
```

### 3.1 `KeystoreEnvironmentPostProcessor`

```java
package com.example.keystore;

import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

public class KeystoreEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String path     = env.getProperty("keystore.path", "file:secrets/keystore.p12");
        String password = env.getProperty("keystore.password");
        if (password == null) return;   // 비밀번호 없으면 패스

        KeystorePropertySource src = KeystorePropertySource.from(path, password);
        // 가장 먼저 넣어야 OS /env 값을 덮어쓸 수 있다
        env.getPropertySources().addFirst(src);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
```

### 3.2 `KeystorePropertySource`

```java
package com.example.keystore;

import org.springframework.core.env.EnumerablePropertySource;

import java.security.KeyStore;
import java.util.*;

class KeystorePropertySource extends EnumerablePropertySource<KeyStore> {

    private final Map<String,Object> values = new HashMap<>();

    private KeystorePropertySource(String name, KeyStore ks, char[] pwd) throws Exception {
        super(name, ks);
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!ks.isKeyEntry(alias)) continue;
            byte[] secret = ks.getKey(alias, pwd).getEncoded();
            values.put(alias, Base64.getEncoder().encodeToString(secret));
        }
    }

    static KeystorePropertySource from(String location, String password) {
        try (var in = ResourceUtils.getURL(location).openStream()) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, password.toCharArray());
            return new KeystorePropertySource("keystore", ks, password.toCharArray());
        } catch (Exception e) {
            throw new IllegalStateException("Keystore load error", e);
        }
    }

    @Override public String[] getPropertyNames() { return values.keySet().toArray(String[]::new); }
    @Override public Object getProperty(String name) { return values.get(name); }
}
```

### 3.3 `META-INF/spring.factories`

```
org.springframework.boot.env.EnvironmentPostProcessor=com.example.keystore.KeystoreEnvironmentPostProcessor
```

> **Spring Boot 3.x** 에서도 `spring.factories` 방식은 여전히 지원됨  
> (또는 `org.springframework.boot.autoconfigure.AutoConfiguration.imports` 사용 가능)

---

## 4. 메인 모듈 사용법

```yaml
# application.yml
spring:
  jasypt:
    encryptor:
      password: ${JASYPT_PASSWORD}   # ← keystore alias 와 동일
```

1. **의존성 추가**

```gradle
implementation("com.example:keystore-property-source-spring-boot-starter:1.0.0")
```

2. **실행 시 파라미터 전달**

```bash
java -Dkeystore.path=file:secrets/keystore.p12      -Dkeystore.password="$KEYSTORE_PASSWORD"      -jar main-module.jar
```

keystore 안에 `alias=JASYPT_PASSWORD` 인 `SecretKeyEntry` 가 존재하면  
애플리케이션 기동 시 자동으로 비밀번호가 주입된다.

---

## 5. keystore 항목 넣기 예시

```bash
keytool -importpass   -alias JASYPT_PASSWORD   -keystore secrets/keystore.p12   -storetype PKCS12   -storepass "$KEYSTORE_PASSWORD"   -keypass "$KEY_ENTRY_PASS"   -keyalg AES -keysize 256
```

- `-importpass` 는 **문자 비밀번호 → SecretKeyEntry** 로 저장  
- alias‑value 쌍을 원하는 만큼 반복

---

## 6. 테스트 전략

| 구분 | 검증 포인트 |
|------|-------------|
| **Unit** | `KeystorePropertySource.from()` 반환 Map 검증 |
| **Slice** | `EnvironmentPostProcessor` 우선순위 및 PropertySource 삽입 위치 |
| **E2E** | 실제 애플리케이션에서 Jasypt 복호화 성공 여부 |

---

## 7. 보안 및 한계

1. **메모리 노출**  
   - Post‑processor 가 값을 JVM 메모리에 올리므로 `jcmd VM.system_properties` 등으로 확인 가능  
   - 민감 정보가 로그에 출력되지 않도록 주의
2. **운영계 비밀번호 관리**  
   - 시스템 속성 대신 OS 보안 저장소(예: AWS Parameter Store, HashiCorp Vault) 등을 이용 권장
3. **keystore 비밀번호 보호**  
   - CI/CD 파이프라인에서 비밀번호를 평문으로 노출하지 않도록  
     (예: Secrets Manager, encrypted variables 사용)

---

## 8. 참고 링크

- Spring Boot 공식 문서: *Externalized Configuration – EnvironmentPostProcessor*
- Oracle JDK 17 Docs: *PKCS#12 KeyStore* 사용 가이드
- 블로그: “PropertySource 를 이용한 커스텀 설정 주입 패턴”

---

### ✅ 정리

이 설계서를 기반으로 공통 스타터를 제작하면  
`p12` 파일만 교체하여 **여러 마이크로서비스에서 일관된 방식**으로  
비밀 값을 주입할 수 있습니다.  
궁금한 점이 있으면 언제든 말씀해 주세요, **대장님**!
