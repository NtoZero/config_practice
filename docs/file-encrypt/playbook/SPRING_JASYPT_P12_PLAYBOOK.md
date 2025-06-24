# Spring Boot + Jasypt (.p12) 전체 플레이북

본 플레이북은 `.p12` 확장자의 PKCS#12 KeyStore를 활용하여 Jasypt를 구성하는 전체 과정을 단계별로 제공합니다.

---

## 1. 프로젝트 설정 (`build.gradle`)

```groovy
plugins {
    id 'org.springframework.boot' version '3.1.2'
    id 'io.spring.dependency-management' version '1.1.0'
    id 'java'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '17'

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

---

## 2. PKCS#12 키스토어 생성

```bash
keytool -genseckey \
  -alias jasypt-secret-key \
  -keyalg AES \
  -keysize 256 \
  -storetype PKCS12 \
  -keystore src/main/resources/keystore.p12 \
  -storepass $JASYPT_STOREPASS \
  -keypass $JASYPT_KEYPASS
```

```powershell
keytool -genseckey `
  -alias jasypt-secret-key `
  -keyalg AES `
  -keysize 256 `
  -storetype PKCS12 `
  -keystore src/main/resources/keystore.p12 `
  -storepass $env:JASYPT_STOREPASS `
  -keypass $env:JASYPT_KEYPASS
```

- `storepass` : 키스토어 보호 비밀번호 (`$JASYPT_STOREPASS`)  
- `keypass`   : 키 접근 비밀번호 (`$JASYPT_KEYPASS`)  

---

## 3. 애플리케이션 설정 (`src/main/resources/application.yml`)

```yaml
spring:
  jasypt:
    enabled: true
    encryptor:
      key-store:
        location: classpath:keystore.p12
        type: PKCS12
        provider: SUN
        secret: ${JASYPT_STOREPASS}
        alias: jasypt-secret-key
        key-password: ${JASYPT_KEYPASS}

example:
  password: ENC(vG3k1Q5DfG2m9wz8YxA==)
```

- 환경 변수를 통해 비밀번호를 주입하여 코드에 하드코딩하지 않습니다.

---

## 4. 설정 클래스 (`src/main/java/com/example/config/JasyptConfig.java`)

```java
package com.example.config;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.ulisesbocchio.jasyptspringboot.annotation.KeyStorePropertySource;

@Configuration
@KeyStorePropertySource(
    name = "jasyptKeyStore",
    location = "classpath:keystore.p12",
    type = "PKCS12",
    provider = "SUN",
    password = "${spring.jasypt.encryptor.key-store.secret}",
    alias = "${spring.jasypt.encryptor.key-store.alias}",
    keyPassword = "${spring.jasypt.encryptor.key-store.key-password}"
)
public class JasyptConfig {
    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor() {
        // Starter가 자동으로 빈을 생성하므로 null 리턴만으로 충분
        return null;
    }
}
```

---

## 5. 실행 및 테스트

```bash
export JASYPT_STOREPASS=myKeystorePass
export JASYPT_KEYPASS=myKeyPass
./gradlew bootRun
```

- `example.password` 프로퍼티에 `ENC(...)` 형식으로 암호화된 값을 설정하면 실행 시 자동 복호화됩니다.

---

## 6. 보안 주의사항

- `keystore.p12` 파일은 버전 관리 시스템에 커밋하지 말고, 배포 파이프라인 또는 서버의 안전한 위치에서 주입하세요.  
- 환경 변수(`$JASYPT_STOREPASS`, `$JASYPT_KEYPASS`)는 CI/CD 비밀 저장소나 OS 네이티브 비밀관리 기능을 활용해 관리하세요.  
- 키스토어 비밀번호와 키 비밀번호는 주기적으로 변경(rotating)하고, 접근 로그를 남겨 감사(logging) 체계를 구축하세요.
