package com.keyloader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KeyExtractorTest {

    @TempDir
    Path tempDir;
    
    private KeyStore keyStore;
    private final String keystorePassword = "keystore-password";
    private final String keyPassword = "keystore-password"; // 키스토어 패스워드와 동일하게 설정
    private final String testAlias1 = "test_key_1"; // PKCS#12는 별칭을 소문자로 저장함
    private final String testAlias2 = "test_key_2"; // PKCS#12는 별칭을 소문자로 저장함

    @BeforeEach
    void setUp() throws Exception {
        keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        // 테스트용 비밀키들 생성
        byte[] testSecret1 = "test-secret-value-1".getBytes();
        byte[] testSecret2 = "test-secret-value-2".getBytes();
        
        SecretKeySpec secretKey1 = new SecretKeySpec(testSecret1, "AES");
        SecretKeySpec secretKey2 = new SecretKeySpec(testSecret2, "AES");

        // KeyStore에 키들 저장
        KeyStore.SecretKeyEntry entry1 = new KeyStore.SecretKeyEntry(secretKey1);
        KeyStore.SecretKeyEntry entry2 = new KeyStore.SecretKeyEntry(secretKey2);
        KeyStore.PasswordProtection protection = 
            new KeyStore.PasswordProtection(keyPassword.toCharArray());
        
        keyStore.setEntry(testAlias1, entry1, protection);
        keyStore.setEntry(testAlias2, entry2, protection);
    }

    @Test
    void testExtractKeys() throws Exception {
        // When
        Map<String, String> keyMap = KeyExtractor.extractKeys(keyStore, keyPassword.toCharArray());

        // Then
        assertNotNull(keyMap);
        assertEquals(2, keyMap.size());
        assertTrue(keyMap.containsKey(testAlias1));
        assertTrue(keyMap.containsKey(testAlias2));
        
        // Base64로 인코딩되었는지 확인
        assertDoesNotThrow(() -> Base64.getDecoder().decode(keyMap.get(testAlias1)));
        assertDoesNotThrow(() -> Base64.getDecoder().decode(keyMap.get(testAlias2)));
    }

    @Test
    void testExtractSingleKey() throws Exception {
        // When
        String key1 = KeyExtractor.extractKey(keyStore, testAlias1, keyPassword.toCharArray());
        String key2 = KeyExtractor.extractKey(keyStore, testAlias2, keyPassword.toCharArray());

        // Then
        assertNotNull(key1);
        assertNotNull(key2);
        assertNotEquals(key1, key2);
        
        // Base64로 인코딩되었는지 확인
        assertDoesNotThrow(() -> Base64.getDecoder().decode(key1));
        assertDoesNotThrow(() -> Base64.getDecoder().decode(key2));
    }

    @Test
    void testExtractNonExistentKey() throws Exception {
        // When
        String result = KeyExtractor.extractKey(keyStore, "NON_EXISTENT_ALIAS", keyPassword.toCharArray());

        // Then
        assertNull(result);
    }

    @Test
    void testExtractKeysFromEmptyKeyStore() throws Exception {
        // Given
        KeyStore emptyKeyStore = KeyStore.getInstance("PKCS12");
        emptyKeyStore.load(null, null);

        // When
        Map<String, String> keyMap = KeyExtractor.extractKeys(emptyKeyStore, keyPassword.toCharArray());

        // Then
        assertNotNull(keyMap);
        assertTrue(keyMap.isEmpty());
    }
}
