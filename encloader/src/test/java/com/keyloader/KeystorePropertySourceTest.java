package com.keyloader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.PropertySource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * KeystorePropertySource 리팩토링 검증 테스트
 * 
 * 주요 검증 사항:
 * 1. UTF-8 문자열이 올바르게 복원되는지
 * 2. Base64 인코딩 없이 원본 문자열이 반환되는지
 * 3. 다양한 문자 집합(한글, 특수문자 등) 지원
 */
class KeystorePropertySourceTest {

    @TempDir
    Path tempDir;
    
    private String keystorePassword = "testPassword123!";
    private Map<String, String> testSecrets;
    
    @BeforeEach
    void setUp() {
        testSecrets = Map.of(
            "JASYPT_PASSWORD", "my-super-secret-jasypt-password-123",
            "DEMO_SECRET", "this-is-a-demo-secret-value",
            "DB_PASSWORD", "database-production-password-456",
            "API_KEY", "api-key-abcdef1234567890",
            "KOREAN_SECRET", "한글비밀번호테스트",
            "SPECIAL_CHARS", "!@#$%^&*()_+-=[]{}|;:,.<>?"
        );
    }
    
    @Test
    void testKeystorePropertySourceCreation() throws Exception {
        // Given
        String keystorePath = tempDir.resolve("test-keystore.p12").toString();
        
        // KeystoreCreator로 테스트 키스토어 생성
        KeystoreCreator.createKeystore(keystorePath, keystorePassword, keystorePassword, testSecrets);
        
        // When
        PropertySource<?> propertySource = KeystorePropertySource.from("file:" + keystorePath, keystorePassword);
        
        // Then
        assertThat(propertySource).isNotNull();
        assertThat(propertySource.getName()).isEqualTo("keystore");
        
        // 모든 시크릿이 올바르게 로드되었는지 확인
        for (Map.Entry<String, String> entry : testSecrets.entrySet()) {
            String alias = entry.getKey();
            String expectedValue = entry.getValue();
            
            Object actualValue = propertySource.getProperty(alias);
            assertThat(actualValue)
                .as("Property %s should be loaded", alias)
                .isNotNull()
                .isEqualTo(expectedValue);
        }
    }
    
    @Test
    void testUtf8StringRestoration() throws Exception {
        // Given
        String keystorePath = tempDir.resolve("utf8-test-keystore.p12").toString();
        Map<String, String> utf8Secrets = Map.of(
            "ASCII_ONLY", "simple-ascii-password",
            "WITH_NUMBERS", "password123456789",
            "WITH_SPECIAL", "p@ssw0rd!@#$%^&*()",
            "KOREAN_TEXT", "안녕하세요비밀번호입니다",
            "MIXED_LANG", "Hello안녕하세요123!@#",
            "EMOJI_TEST", "🔐🔑🛡️비밀번호😀"
        );
        
        // KeystoreCreator로 테스트 키스토어 생성
        KeystoreCreator.createKeystore(keystorePath, keystorePassword, keystorePassword, utf8Secrets);
        
        // When
        PropertySource<?> propertySource = KeystorePropertySource.from("file:" + keystorePath, keystorePassword);
        
        // Then
        for (Map.Entry<String, String> entry : utf8Secrets.entrySet()) {
            String alias = entry.getKey();
            String expectedValue = entry.getValue();
            
            Object actualValue = propertySource.getProperty(alias);
            
            // 원본 문자열과 정확히 일치하는지 확인
            assertThat(actualValue)
                .as("UTF-8 string restoration failed for %s", alias)
                .isInstanceOf(String.class)
                .isEqualTo(expectedValue);
            
            // Base64 형태가 아닌지 확인
            String actualString = (String) actualValue;
            assertThat(isBase64Like(actualString))
                .as("Value for %s should not be Base64 encoded", alias)
                .isFalse();
            
            // 길이도 원본과 같아야 함
            assertThat(actualString.length())
                .as("String length should match for %s", alias)
                .isEqualTo(expectedValue.length());
        }
    }
    
    @Test
    void testPropertyNamesRetrieval() throws Exception {
        // Given
        String keystorePath = tempDir.resolve("names-test-keystore.p12").toString();
        KeystoreCreator.createKeystore(keystorePath, keystorePassword, keystorePassword, testSecrets);
        
        // When
        KeystorePropertySource propertySource = KeystorePropertySource.from("file:" + keystorePath, keystorePassword);
        String[] propertyNames = propertySource.getPropertyNames();
        
        // Then
        assertThat(propertyNames)
            .hasSize(testSecrets.size())
            .containsExactlyInAnyOrder(testSecrets.keySet().toArray(new String[0]));
    }
    
    @Test
    void testContainsProperty() throws Exception {
        // Given
        String keystorePath = tempDir.resolve("contains-test-keystore.p12").toString();
        KeystoreCreator.createKeystore(keystorePath, keystorePassword, keystorePassword, testSecrets);
        
        // When
        KeystorePropertySource propertySource = KeystorePropertySource.from("file:" + keystorePath, keystorePassword);
        
        // Then
        // 존재하는 속성들
        for (String alias : testSecrets.keySet()) {
            assertThat(propertySource.containsProperty(alias))
                .as("Should contain property %s", alias)
                .isTrue();
        }
        
        // 존재하지 않는 속성들
        assertThat(propertySource.containsProperty("NON_EXISTENT")).isFalse();
        assertThat(propertySource.containsProperty("")).isFalse();
        assertThat(propertySource.containsProperty(null)).isFalse();
    }
    
    @Test
    void testInvalidKeystorePath() {
        // When & Then
        assertThatThrownBy(() -> KeystorePropertySource.from("file:non-existent-keystore.p12", keystorePassword))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Keystore load error");
    }
    
    @Test
    void testInvalidKeystorePassword() throws Exception {
        // Given
        String keystorePath = tempDir.resolve("password-test-keystore.p12").toString();
        KeystoreCreator.createKeystore(keystorePath, keystorePassword, keystorePassword, testSecrets);
        
        // When & Then
        assertThatThrownBy(() -> KeystorePropertySource.from("file:" + keystorePath, "wrong-password"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Keystore load error");
    }
    
    /**
     * 문자열이 Base64처럼 보이는지 간단히 체크
     */
    private boolean isBase64Like(String value) {
        if (value == null || value.length() < 10) {
            return false;
        }
        
        // Base64 패턴과 길이 체크
        return value.matches("^[A-Za-z0-9+/]*={0,2}$") && 
               value.length() % 4 == 0 && 
               value.length() > 20;
    }
}
