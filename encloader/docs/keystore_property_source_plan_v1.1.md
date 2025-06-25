
# keystore-property-source-spring-boot-starter 설계서 (개정 v1.1)

> **최신 개정일:** 2025-06-25  
> **대상 환경:** Spring Boot 3.x · Java 17  
> **작성자:** BOAT‑AI  
> **변경 요약:** *`KeystorePropertySource.from()` 메서드에서 `ResourceUtils` → `ResourceLoader` 로 교체하여 classpath·fat JAR 호환성 및 Spring 표준성 강화.*

---

## 1. 목표 _(변경 없음)_

- **PKCS#12(`.p12`)** 파일에서 alias 별 비밀 값을 읽어와 **Spring Boot `Environment`** 에 동적으로 주입한다.  
- 메인 모듈의 `application.yml` / `application.properties` 에 선언된 place‑holder(예: `${JASYPT_PASSWORD}`)가 keystore 값으로 자동 해석되도록 한다.  
- 여러 서비스가 **공통 스타터** 의존성만 추가하면 재사용할 수 있도록 한다.

---

## 2. 동작 흐름 _(변경 없음)_

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

---

## 3. 모듈 구조 _(경로 동일, 소스 일부 변경)_ 

```
keystore-property-source-spring-boot-starter
├── src/main/java
│   └── com/example/keystore/
│       ├── KeystoreEnvironmentPostProcessor.java
│       ├── KeystorePropertySource.java   ← **수정**
│       └── KeyExtractor.java
└── src/main/resources
    └── META-INF/spring.factories
```

### 3.1 `KeystoreEnvironmentPostProcessor` _(변경 없음)_

```java
package com.example.keystore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

public class KeystoreEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String path     = env.getProperty("keystore.path", "file:secrets/keystore.p12");
        String password = env.getProperty("keystore.password");
        if (password == null)
            return;   // 비밀번호 없으면 패스

        KeystorePropertySource src = KeystorePropertySource.from(path, password);
        env.getPropertySources().addFirst(src); // 가장 먼저 삽입
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
```

### 3.2 `KeystorePropertySource` **(개선된 코드)**

> **Why?** `org.springframework.util.ResourceUtils.getURL()` 는 Spring 부트 팻 JAR 내부 classpath 리소스를 File 로 변환하지 못해 예외가 발생할 수 있습니다.  
> 대신 **`ResourceLoader`** 를 사용하면 `file:`, `classpath:`, `https:` 등 모든 리소스를 안전하게 처리할 수 있습니다.

```java
package com.example.keystore;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.security.KeyStore;
import java.util.*;

class KeystorePropertySource extends EnumerablePropertySource<KeyStore> {

    private final Map<String, Object> values = new HashMap<>();

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

    /** 
     * location 예) file:secrets/keystore.p12, classpath:keystore.p12
     */
    static KeystorePropertySource from(String location, String password) {
        ResourceLoader loader = new DefaultResourceLoader();
        Resource resource = loader.getResource(location);

        try (var in = resource.getInputStream()) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, password.toCharArray());
            return new KeystorePropertySource("keystore", ks, password.toCharArray());
        } catch (Exception e) {
            // 실패 시 부팅을 중단하는 편이 안전
            throw new IllegalStateException("Keystore load error from location: " + location, e);
        }
    }

    @Override
    public String[] getPropertyNames() { return values.keySet().toArray(String[]::new); }

    @Override
    public Object getProperty(String name) { return values.get(name); }
}
```

### 3.3 `META-INF/spring.factories` _(변경 없음)_

```
org.springframework.boot.env.EnvironmentPostProcessor=com.example.keystore.KeystoreEnvironmentPostProcessor
```

---

## 4. 메인 모듈 사용법 _(변경 없음)_

```yaml
spring:
  jasypt:
    encryptor:
      password: ${JASYPT_PASSWORD}
```

```bash
java -Dkeystore.path=file:secrets/keystore.p12 \
     -Dkeystore.password="$KEYSTORE_PASSWORD" \
     -jar main-module.jar
```

---

## 5. keystore 항목 넣기 예시 _(변경 없음)_

```bash
keytool -importpass -alias JASYPT_PASSWORD \
        -keystore secrets/keystore.p12 -storetype PKCS12 \
        -storepass "$KEYSTORE_PASSWORD" -keypass "$ENTRY_PASS" \
        -keyalg AES -keysize 256
```

---

## 6. 테스트 전략 _(주요 케이스 동일)_

| 구분 | 검증 포인트 |
|------|-------------|
| **Unit** | `KeystorePropertySource.from()` 반환 Map 검증 |
| **Slice** | `EnvironmentPostProcessor` 우선순위 및 PropertySource 삽입 위치 |
| **E2E** | 실제 애플리케이션에서 Jasypt 복호화 성공 여부 |
- `from(String,String)` 이 `classpath:` 리소스도 정상 처리하는지 **추가 단위 테스트** 권장.

---

## 7. 보안 및 한계 _(변경 없음)_

1. **메모리 노출**
    - Post‑processor 가 값을 JVM 메모리에 올리므로 `jcmd VM.system_properties` 등으로 확인 가능
    - 민감 정보가 로그에 출력되지 않도록 주의
2. **운영계 비밀번호 관리**
    - 시스템 속성 대신 OS 보안 저장소(예: AWS Parameter Store, HashiCorp Vault) 등을 이용 권장
3. **keystore 비밀번호 보호**
    - CI/CD 파이프라인에서 비밀번호를 평문으로 노출하지 않도록  
      (예: Secrets Manager, encrypted variables 사용)

---

## 8. 참고 링크 _(ResourceLoader 관련 내용 추가)_ 

- Spring Framework Core `ResourcePatternUtils` / `ResourceLoader` 가이드  
- Spring Boot Docs: *Externalized Configuration – EnvironmentPostProcessor*

---

### ✅ 정리

`ResourceLoader` 로의 전환으로 **fat JAR / native 이미지 / jar-in-war** 배포 환경에서도 안전하게 keystore 리소스를 찾을 수 있습니다.  
설계서의 다른 내용은 유지하면서 최신 Spring 스타일에 부합하도록 개선되었습니다.  

