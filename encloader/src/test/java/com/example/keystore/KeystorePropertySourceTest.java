package com.example.keystore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class KeystorePropertySourceTest {

    @TempDir
    Path tempDir;
    
    private Path keystorePath;
    private final String keystorePassword = "test-password";
    private final String keyPassword = "key-password";
    private final String testAlias = "TEST_KEY";

    @BeforeEach
    void setUp() throws Exception {
        keystorePath = tempDir.resolve("test-keystore.p12");
        createTestKeystore();
    }

    private void createTestKeystore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        // 테스트용 비밀키 생성 (AES 키를 시뮬레이션)
        byte[] testSecretBytes = "test-secret-value".getBytes();
        
        // SecretKeySpec을 사용하여 테스트 키 생성
        javax.crypto.spec.SecretKeySpec secretKey = 
            new javax.crypto.spec.SecretKeySpec(testSecretBytes, "AES");

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
    void testFromFileResource() {
        // Given
        String location = "file:" + keystorePath.toString();

        // When
        KeystorePropertySource propertySource = KeystorePropertySource.from(location, keystorePassword);

        // Then
        assertNotNull(propertySource);
        assertTrue(propertySource.containsProperty(testAlias));
        
        Object value = propertySource.getProperty(testAlias);
        assertNotNull(value);
        assertTrue(value instanceof String);
        
        // Base64로 인코딩된 값인지 확인
        String base64Value = (String) value;
        assertDoesNotThrow(() -> Base64.getDecoder().decode(base64Value));
    }

    @Test
    void testGetPropertyNames() {
        // Given
        String location = "file:" + keystorePath.toString();
        KeystorePropertySource propertySource = KeystorePropertySource.from(location, keystorePassword);

        // When
        String[] propertyNames = propertySource.getPropertyNames();

        // Then
        assertNotNull(propertyNames);
        assertEquals(1, propertyNames.length);
        assertEquals(testAlias, propertyNames[0]);
    }

    @Test
    void testInvalidKeystorePath() {
        // Given
        String invalidLocation = "file:/non/existent/path.p12";

        // When & Then
        assertThrows(IllegalStateException.class, () -> 
            KeystorePropertySource.from(invalidLocation, keystorePassword));
    }

    @Test
    void testInvalidPassword() {
        // Given
        String location = "file:" + keystorePath.toString();
        String wrongPassword = "wrong-password";

        // When & Then
        assertThrows(IllegalStateException.class, () -> 
            KeystorePropertySource.from(location, wrongPassword));
    }
}
