# keystore-property-source-spring-boot-starter 리팩토링 가이드 (v1.2)

> **최종 수정일:** 2025년 6월 25일\
> **작성자:** BOAT‑AI

## 핵심 목표

keystore-property-source-spring-boot-starter 모듈의 데이터 불일치 문제를 해결하고, 원본 비밀번호 문자열을 Spring Environment에 안정적으로 주입하는 것을 목표로 합니다.

## 1. 문제 정의

기존 접근 방식은 '쓰기'와 '읽기' 로직의 비대칭성 문제를 가지고 있었습니다.

- **쓰기 문제 (keytool 사용 시):**\
  keytool -importpass 명령어는 입력된 문자열을 원본 그대로 저장하는 것이 아니라, PBE(패스워드 기반 암호화) 알고리즘을 통해 파생된 암호학적 키를 저장했습니다. 이 과정에서 원본 문자열 정보가 손실되었습니다.
- **읽기 문제 (기존 KeystorePropertySource):**\
  저장된 '키'의 바이트 배열을 읽어온 후, 이를 원본 문자열로 복원하지 않고 다시 Base64로 인코딩했습니다.
- **결과:**\
  Spring Environment에는 실제 비밀번호가 아닌, 의미 없는 Base64 문자열이 주입되어 Jasypt 등 후속 로직이 모두 실패하는 근본적인 결함이 있었습니다.

## 2. 목표

- **PKCS#12(**``**)** 파일에서 alias 별 비밀 값을 읽어와 **Spring Boot **`` 에 동적으로 주입한다.
- 메인 모듈의 `application.yml` / `application.properties` 에 선언된 placeholder(예: `${JASYPT_PASSWORD}`)가 keystore 값으로 자동 해석되도록 한다.
- 여러 서비스가 **공통 스타터** 의존성만 추가하면 재사용할 수 있도록 한다.

## 3. 동작 흐름

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

## 4. 모듈 구조

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

### 4.1 KeystoreEnvironmentPostProcessor

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

### 4.2 KeystorePropertySource (리팩토링된 코드)

```java
package com.example.keystore;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
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
            // 대칭성 확보: 원본 UTF-8 바이트 배열을 문자열로 복원
            values.put(alias, new String(secret, StandardCharsets.UTF_8));
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

### 4.3 META-INF/spring.factories

```
org.springframework.boot.env.EnvironmentPostProcessor=com.example.keystore.KeystoreEnvironmentPostProcessor
```

## 5. 해결 전략: 쓰기/읽기 로직의 대칭성 확보

- **쓰기 도구 (Writer): KeystoreCreator.java**\
  원본 비밀번호 문자열을 그대로 UTF-8 바이트 배열로 변환하여 SecretKeySpec으로 감싼 후 키스토어에 저장합니다. 이 방식은 원본 데이터의 손실을 막습니다.
- **읽기 로직 (Reader): KeystorePropertySource.java**\
  키스토어에서 SecretKeySpec으로 저장된 엔트리를 읽어온 후, `.getEncoded()`를 통해 얻은 UTF-8 바이트 배열을 다시 원본 문자열로 복원합니다.

## 6. 실행 계획 (Action Plan)

### Phase 1: 자바 코드 수정 (1회성)

KeystorePropertySource.java 파일의 생성자 로직을 다음과 같이 수정하여, 바이트 배열을 올바른 문자열로 복원하도록 변경합니다.

```java
// ...
import java.nio.charset.StandardCharsets; // StandardCharsets 임포트 필요

// ...
byte[] secret = ks.getKey(alias, pwd).getEncoded();
// 바이트를 원본 문자열로 복원하는 핵심 수정 사항
values.put(alias, new String(secret, StandardCharsets.UTF_8));
// ...
```

### Phase 2: 키스토어 생성 방식 변경

keytool을 사용하는 모든 배치(.bat) 및 쉘(.sh) 스크립트는 이 프로젝트에서 폐기(deprecated) 합니다. 앞으로는 반드시 **KeystoreCreator.java** 유틸리티를 사용하여 `.p12` 파일을 생성합니다.

사용 명령어:

```bash
# 1. (필요시) KeystoreCreator.java 컴파일
javac com/example/keystore/KeystoreCreator.java

# 2. 데모 키스토어 생성
java com.example.keystore.KeystoreCreator secrets/keystore.p12 [원하는_키스토어_비밀번호] demo
```

### Phase 3: 애플리케이션 실행

KeystoreCreator로 생성한 `.p12` 파일과 해당 비밀번호를 사용하여 Spring Boot 애플리케이션을 실행합니다.

```bash
java -Dkeystore.path=file:secrets/keystore.p12 \
     -Dkeystore.password=[위에서_사용한_비밀번호] \
     -jar your-app.jar
```

## 7. 기대 효과

- Spring Environment에는 JASYPT\_PASSWORD, DB\_PASSWORD 등 원본 비밀번호 문자열이 정확하게 주입됩니다.
- `application.yml` 등의 설정 파일에 있는 `${JASYPT_PASSWORD}`와 같은 플레이스홀더가 올바르게 해석됩니다.
- Jasypt 암/복호화 로직이 정상적으로 동작합니다.
- 관련 단위 테스트 및 통합 테스트가 모두 통과합니다.

## 8. 메인 모듈 사용법

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

## 9. keystore 항목 넣기 예시

```bash
keytool -importpass -alias JASYPT_PASSWORD \
        -keystore secrets/keystore.p12 -storetype PKCS12 \
        -storepass "$KEYSTORE_PASSWORD" -keypass "$ENTRY_PASS" \
        -keyalg AES -keysize 256
```

> **Deprecated:** 기존 `.bat`/`.sh` 스크립트 대신 Phase 2의 **KeystoreCreator.java** 사용을 권장합니다.

## 10. 테스트 전략

| 구분        | 검증 포인트                                                 |
| --------- | ------------------------------------------------------ |
| **Unit**  | `KeystorePropertySource.from()` 반환 Map 검증              |
| **Slice** | `EnvironmentPostProcessor` 우선순위 및 PropertySource 삽입 위치 |
| **E2E**   | 실제 애플리케이션에서 Jasypt 복호화 성공 여부                           |

- `from(String,String)`이 `classpath:` 리소스도 정상 처리하는지 **추가 단위 테스트** 권장.

## 11. 보안 및 한계

1. **메모리 노출**
   - Post-processor 가 값을 JVM 메모리에 올리므로 `jcmd VM.system_properties` 등으로 확인 가능
   - 민감 정보가 로그에 출력되지 않도록 주의
2. **운영계 비밀번호 관리**
   - 시스템 속성 대신 OS 보안 저장소(예: AWS Parameter Store, HashiCorp Vault) 등을 이용 권장
3. **keystore 비밀번호 보호**
   - CI/CD 파이프라인에서 비밀번호를 평문으로 노출하지 않도록\
     (예: Secrets Manager, encrypted variables 사용)

## 12. 참고 링크

- Spring Framework Core `ResourcePatternUtils` / `ResourceLoader` 가이드
- Spring Boot Docs: *Externalized Configuration – EnvironmentPostProcessor*

## 최종 요약

KeystoreCreator.java (쓰기)와 수정된 KeystorePropertySource.java (읽기)는 하나의 완벽한 쌍입니다. 이 조합을 통해 데이터 무결성을 보장하고, 민감 정보를 안전하고 안정적으로 애플리케이션에 주입하는 견고한 아키텍처를 완성할 수 있습니다.

