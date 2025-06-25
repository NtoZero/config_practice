package com.example.keystore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import javax.crypto.spec.SecretKeySpec;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KeystoreEnvironmentPostProcessorTest {

    @TempDir
    Path tempDir;
    
    private KeystoreEnvironmentPostProcessor processor;
    private ConfigurableEnvironment environment;
    private SpringApplication application;
    private Path keystorePath;
    
    private final String keystorePassword = "test-password";
    private final String keyPassword = "test-password"; // 키스토어 패스워드와 동일하게 설정
    private final String testAlias = "jasypt_password"; // PKCS#12는 별칭을 소문자로 저장함

    @BeforeEach
    void setUp() throws Exception {
        processor = new KeystoreEnvironmentPostProcessor();
        environment = new StandardEnvironment();
        application = new SpringApplication();
        
        keystorePath = tempDir.resolve("test-keystore.p12");
        createTestKeystore();
    }

    private void createTestKeystore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        // 테스트용 비밀키 생성
        byte[] testSecretBytes = "my-secret-jasypt-password".getBytes();
        SecretKeySpec secretKey = new SecretKeySpec(testSecretBytes, "AES");

        // KeyStore에 키 저장
        KeyStore.SecretKeyEntry secretKeyEntry = new KeyStore.SecretKeyEntry(secretKey);
        KeyStore.PasswordProtection passwordProtection = 
            new KeyStore.PasswordProtection(keyPassword.toCharArray());
        
        keyStore.setEntry(testAlias, secretKeyEntry, passwordProtection);

        // 파일에 저장
        try (FileOutputStream fos = new FileOutputStream(keystorePath.toFile())) {
            keyStore.store(fos, keystorePassword.toCharArray());
        }
    }

    @Test
    void testPostProcessEnvironmentWithValidKeystore() {
        // Given
        Map<String, Object> properties = new HashMap<>();
        properties.put("keystore.path", "file:" + keystorePath.toString());
        properties.put("keystore.password", keystorePassword);
        
        MapPropertySource testPropertySource = new MapPropertySource("test", properties);
        environment.getPropertySources().addFirst(testPropertySource);

        // When
        processor.postProcessEnvironment(environment, application);

        // Then
        assertTrue(environment.containsProperty(testAlias));
        assertNotNull(environment.getProperty(testAlias));
    }

    @Test
    void testPostProcessEnvironmentWithoutPassword() {
        // Given - keystore.password 속성 없음
        Map<String, Object> properties = new HashMap<>();
        properties.put("keystore.path", "file:" + keystorePath.toString());
        
        MapPropertySource testPropertySource = new MapPropertySource("test", properties);
        environment.getPropertySources().addFirst(testPropertySource);

        // When
        processor.postProcessEnvironment(environment, application);

        // Then - 아무 처리도 하지 않아야 함
        assertFalse(environment.containsProperty(testAlias));
    }

    @Test
    void testPostProcessEnvironmentWithDefaultPath() {
        // Given - keystore.path 기본값 사용 (존재하지 않는 파일)
        Map<String, Object> properties = new HashMap<>();
        properties.put("keystore.password", keystorePassword);
        
        MapPropertySource testPropertySource = new MapPropertySource("test", properties);
        environment.getPropertySources().addFirst(testPropertySource);

        // When & Then - 기본 경로의 파일이 없으므로 예외 발생
        assertThrows(IllegalStateException.class, () -> 
            processor.postProcessEnvironment(environment, application));
    }

    @Test
    void testGetOrder() {
        // When
        int order = processor.getOrder();

        // Then
        assertTrue(order > Integer.MIN_VALUE);
        assertTrue(order < Integer.MAX_VALUE);
    }

    @Test
    void testPostProcessEnvironmentWithCustomPath() {
        // Given
        Map<String, Object> properties = new HashMap<>();
        properties.put("keystore.path", "file:" + keystorePath.toString());
        properties.put("keystore.password", keystorePassword);
        
        MapPropertySource testPropertySource = new MapPropertySource("test", properties);
        environment.getPropertySources().addFirst(testPropertySource);

        // When
        processor.postProcessEnvironment(environment, application);

        // Then
        String retrievedValue = environment.getProperty(testAlias);
        assertNotNull(retrievedValue);
        assertTrue(retrievedValue instanceof String);
    }
}
