package com.example.keystore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import javax.crypto.spec.SecretKeySpec;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.*;

class KeystorePropertySourceIntegrationTest {

    @TempDir
    Path tempDir;
    
    private Path keystorePath;
    private final String keystorePassword = "integration-test-password";
    private final String keyPassword = "key-password";
    private final String testAlias = "JASYPT_PASSWORD";

    @SpringBootApplication
    static class TestApplication {
        // 테스트용 최소 스프링 부트 애플리케이션
    }

    @BeforeEach
    void setUp() throws Exception {
        keystorePath = tempDir.resolve("integration-test-keystore.p12");
        createTestKeystore();
    }

    private void createTestKeystore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        // 테스트용 비밀키 생성
        byte[] testSecretBytes = "integration-test-secret-password".getBytes();
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
    void testKeystorePropertySourceIntegration() {
        // Given
        System.setProperty("keystore.path", "file:" + keystorePath.toString());
        System.setProperty("keystore.password", keystorePassword);
        
        SpringApplication app = new SpringApplication(TestApplication.class);
        app.setAdditionalProfiles("test");

        try (ConfigurableApplicationContext context = app.run()) {
            // When
            Environment environment = context.getEnvironment();

            // Then
            assertTrue(environment.containsProperty(testAlias));
            String retrievedValue = environment.getProperty(testAlias);
            assertNotNull(retrievedValue);
            assertTrue(retrievedValue.length() > 0);
            
            // 애플리케이션이 정상적으로 시작되었는지 확인
            assertTrue(context.isRunning());
            
        } finally {
            // Clean up system properties
            System.clearProperty("keystore.path");
            System.clearProperty("keystore.password");
        }
    }

    @Test
    void testKeystorePropertySourceWithPlaceholderResolution() {
        // Given - placeholder를 사용하는 설정
        System.setProperty("keystore.path", "file:" + keystorePath.toString());
        System.setProperty("keystore.password", keystorePassword);
        System.setProperty("test.encrypted.value", "${" + testAlias + "}");
        
        SpringApplication app = new SpringApplication(TestApplication.class);
        app.setAdditionalProfiles("test");

        try (ConfigurableApplicationContext context = app.run()) {
            // When
            Environment environment = context.getEnvironment();

            // Then - placeholder가 keystore 값으로 해석되는지 확인
            String placeholderValue = environment.getProperty("test.encrypted.value");
            String directValue = environment.getProperty(testAlias);
            
            assertNotNull(placeholderValue);
            assertNotNull(directValue);
            assertEquals(directValue, placeholderValue);
            
        } finally {
            // Clean up system properties
            System.clearProperty("keystore.path");
            System.clearProperty("keystore.password");
            System.clearProperty("test.encrypted.value");
        }
    }
}
