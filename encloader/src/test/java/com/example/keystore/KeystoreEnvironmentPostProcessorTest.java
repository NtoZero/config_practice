package com.example.keystore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * KeystoreEnvironmentPostProcessor 통합 테스트
 * 
 * Spring Boot 환경에서 키스토어 속성이 올바르게 로드되고
 * 플레이스홀더 해석이 정상적으로 작동하는지 검증
 */
class KeystoreEnvironmentPostProcessorTest {

    @TempDir
    Path tempDir;
    
    private KeystoreEnvironmentPostProcessor postProcessor;
    private ConfigurableEnvironment environment;
    private SpringApplication application;
    private String keystorePassword = "integrationTestPassword123!";
    
    @BeforeEach
    void setUp() {
        postProcessor = new KeystoreEnvironmentPostProcessor();
        environment = new StandardEnvironment();
        application = new SpringApplication();
    }
    
    @Test
    void testPostProcessEnvironmentWithValidKeystore() throws Exception {
        // Given
        String keystorePath = tempDir.resolve("integration-test-keystore.p12").toString();
        Map<String, String> testSecrets = Map.of(
            "JASYPT_PASSWORD", "integration-jasypt-password-123",
            "DATABASE_URL", "jdbc:postgresql://localhost:5432/testdb",
            "API_SECRET", "super-secret-api-key-456"
        );
        
        // 테스트 키스토어 생성
        KeystoreCreator.createKeystore(keystorePath, keystorePassword, keystorePassword, testSecrets);
        
        // 환경 변수 설정
        System.setProperty("keystore.path", "file:" + keystorePath);
        System.setProperty("keystore.password", keystorePassword);
        
        try {
            // When
            postProcessor.postProcessEnvironment(environment, application);
            
            // Then
            // 키스토어 속성들이 환경에 추가되었는지 확인
            for (Map.Entry<String, String> entry : testSecrets.entrySet()) {
                String alias = entry.getKey();
                String expectedValue = entry.getValue();
                
                String actualValue = environment.getProperty(alias);
                assertThat(actualValue)
                    .as("Property %s should be available in environment", alias)
                    .isNotNull()
                    .isEqualTo(expectedValue);
            }
            
            // PropertySource가 최우선 순위로 추가되었는지 확인
            String firstPropertySourceName = environment.getPropertySources().iterator().next().getName();
            assertThat(firstPropertySourceName).isEqualTo("keystore");
            
        } finally {
            // 시스템 속성 정리
            System.clearProperty("keystore.path");
            System.clearProperty("keystore.password");
        }
    }
    
    @Test
    void testPostProcessEnvironmentWithoutKeystorePassword() {
        // Given - keystore.password가 설정되지 않음
        System.setProperty("keystore.path", "file:some-path.p12");
        
        try {
            // When
            postProcessor.postProcessEnvironment(environment, application);
            
            // Then - 아무 PropertySource도 추가되지 않아야 함
            boolean hasKeystorePropertySource = environment.getPropertySources()
                .stream()
                .anyMatch(ps -> "keystore".equals(ps.getName()));
                
            assertThat(hasKeystorePropertySource).isFalse();
            
        } finally {
            System.clearProperty("keystore.path");
        }
    }
    
    @Test
    void testPostProcessEnvironmentWithInvalidKeystore() {
        // Given
        System.setProperty("keystore.path", "file:non-existent-keystore.p12");
        System.setProperty("keystore.password", "somepassword");
        
        try {
            // When & Then
            assertThatThrownBy(() -> postProcessor.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Keystore load error");
                
        } finally {
            System.clearProperty("keystore.path");
            System.clearProperty("keystore.password");
        }
    }
    
    @Test
    void testPostProcessEnvironmentWithDefaultPath() throws Exception {
        // Given - keystore.path를 설정하지 않으면 기본값 사용
        String defaultKeystorePath = tempDir.resolve("secrets").resolve("keystore.p12").toString();
        
        // 기본 경로에 디렉토리 생성
        tempDir.resolve("secrets").toFile().mkdirs();
        
        Map<String, String> testSecrets = Map.of("TEST_KEY", "test-value");
        KeystoreCreator.createKeystore(defaultKeystorePath, keystorePassword, keystorePassword, testSecrets);
        
        // 기본 경로를 실제 경로로 설정
        System.setProperty("keystore.path", "file:" + defaultKeystorePath);
        System.setProperty("keystore.password", keystorePassword);
        
        try {
            // When
            postProcessor.postProcessEnvironment(environment, application);
            
            // Then
            assertThat(environment.getProperty("TEST_KEY")).isEqualTo("test-value");
            
        } finally {
            System.clearProperty("keystore.path");
            System.clearProperty("keystore.password");
        }
    }
    
    @Test
    void testOrderPrecedence() {
        // Given
        KeystoreEnvironmentPostProcessor processor = new KeystoreEnvironmentPostProcessor();
        
        // When
        int order = processor.getOrder();
        
        // Then
        // HIGHEST_PRECEDENCE + 10 이어야 함
        assertThat(order).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10);
    }
    
    @Test
    void testPlaceholderResolution() throws Exception {
        // Given
        String keystorePath = tempDir.resolve("placeholder-test-keystore.p12").toString();
        Map<String, String> testSecrets = Map.of(
            "JASYPT_PASSWORD", "placeholder-test-password"
        );
        
        KeystoreCreator.createKeystore(keystorePath, keystorePassword, keystorePassword, testSecrets);
        
        System.setProperty("keystore.path", "file:" + keystorePath);
        System.setProperty("keystore.password", keystorePassword);
        
        try {
            // When
            postProcessor.postProcessEnvironment(environment, application);
            
            // 플레이스홀더가 있는 속성을 환경에 추가
            System.setProperty("test.jasypt.password", "${JASYPT_PASSWORD}");
            
            // Then
            String resolvedValue = environment.getProperty("test.jasypt.password");
            assertThat(resolvedValue)
                .as("Placeholder should be resolved with keystore value")
                .isEqualTo("placeholder-test-password");
                
        } finally {
            System.clearProperty("keystore.path");
            System.clearProperty("keystore.password");
            System.clearProperty("test.jasypt.password");
        }
    }
}
