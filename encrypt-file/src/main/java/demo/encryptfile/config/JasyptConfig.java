package demo.encryptfile.config;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import demo.encryptfile.service.KeyStoreService;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import jakarta.annotation.PostConstruct;

/**
 * JASYPT PKCS#12 키스토어 설정
 * 개인키 기반 암호화 구조로 마이그레이션 완료 (v2.0)
 */
@Slf4j
@Configuration
@EnableEncryptableProperties
public class JasyptConfig {

    @Autowired
    private KeyStoreService keyStoreService;

    @Value("${spring.jasypt.encryptor.key-store.location}")
    private String keystoreLocation;

    @Value("${spring.jasypt.encryptor.key-store.password}")
    private String keystorePassword;

    @Value("${spring.jasypt.encryptor.key-store.alias}")
    private String keystoreAlias;

    @Value("${spring.jasypt.encryptor.algorithm}")
    private String algorithm;

    @Value("${spring.jasypt.encryptor.key-obtention-iterations}")
    private int iterations;

    @Value("${spring.jasypt.encryptor.pool-size}")
    private int poolSize;

    @PostConstruct
    public void validateConfiguration() {
        log.info("🔐 JASYPT 설정 초기화 중... (SecretKey 기반 v2.0)");
        log.info("키스토어 위치: {}", keystoreLocation);
        log.info("키스토어 별칭: {}", keystoreAlias);
        log.info("암호화 알고리즘: {}", algorithm);
        log.info("반복 횟수: {}", iterations);
        log.info("풀 크기: {}", poolSize);
        
        if (keystorePassword == null || keystorePassword.trim().isEmpty()) {
            throw new IllegalStateException("KEYSTORE_PASSWORD 환경변수가 설정되지 않았습니다. " +
                    "키스토어 비밀번호를 환경변수로 설정해주세요.");
        }
        
        // 키스토어 검증 (KeyStoreService 사용)
        try {
            keyStoreService.validateKeyStore(keystoreLocation, keystorePassword, keystoreAlias);
            log.info("✅ 키스토어 검증 완료 (KeyStoreService 사용)");
        } catch (Exception e) {
            log.error("❌ 키스토어 검증 실패: {}", e.getMessage());
            throw new IllegalStateException("키스토어 설정이 올바르지 않습니다: " + e.getMessage(), e);
        }
    }

    @Bean(name = "jasyptStringEncryptor")
    @Primary
    public StringEncryptor stringEncryptor() {
        log.info("🔧 JASYPT StringEncryptor 빈 생성 중... (SecretKey 기반)");
        
        // 🔑 핵심 변경: 키스토어에서 SecretKey를 추출하여 JASYPT 비밀번호로 사용
        String secretKeyPassword = keyStoreService.extractSecretKeyAsPassword(
            keystoreLocation, keystorePassword, keystoreAlias);
        
        log.info("🔐 SecretKey 기반 암호화 키 추출 완료 (키스토어 비밀번호와 분리됨)");
        
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        
        // SecretKey 기반 암호화 설정
        config.setPassword(secretKeyPassword);  // 🔑 키스토어 비밀번호가 아닌 SecretKey 사용
        config.setAlgorithm(algorithm);
        config.setKeyObtentionIterations(iterations);
        config.setPoolSize(poolSize);
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
        config.setStringOutputType("base64");
        
        encryptor.setConfig(config);
        
        // 초기화 테스트
        try {
            String testValue = "test";
            String encrypted = encryptor.encrypt(testValue);
            String decrypted = encryptor.decrypt(encrypted);
            
            if (!testValue.equals(decrypted)) {
                throw new IllegalStateException("암호화/복호화 테스트 실패");
            }
            
            log.info("✅ JASYPT StringEncryptor 초기화 완료 및 테스트 성공 (SecretKey 기반)");
            log.info("🔐 키 분리 완료: 키스토어 비밀번호 ≠ JASYPT 암호화 키");
        } catch (Exception e) {
            log.error("❌ JASYPT StringEncryptor 초기화 실패: {}", e.getMessage());
            throw new IllegalStateException("JASYPT 암호화 설정 실패", e);
        }
        
        return encryptor;
    }

    /**
     * 암호화 유틸리티 메서드
     */
    @Bean
    public EncryptionService encryptionService(StringEncryptor stringEncryptor) {
        return new EncryptionService(stringEncryptor);
    }

    /**
     * 암호화/복호화 서비스
     */
    public static class EncryptionService {
        private final StringEncryptor encryptor;

        public EncryptionService(StringEncryptor encryptor) {
            this.encryptor = encryptor;
        }

        public String encrypt(String plainText) {
            if (plainText == null || plainText.trim().isEmpty()) {
                return plainText;
            }
            return encryptor.encrypt(plainText);
        }

        public String decrypt(String encryptedText) {
            if (encryptedText == null || encryptedText.trim().isEmpty()) {
                return encryptedText;
            }
            
            // ENC() 형식 처리
            if (encryptedText.startsWith("ENC(") && encryptedText.endsWith(")")) {
                String actualEncryptedText = encryptedText.substring(4, encryptedText.length() - 1);
                return encryptor.decrypt(actualEncryptedText);
            }
            
            return encryptor.decrypt(encryptedText);
        }

        public String encryptWithFormat(String plainText) {
            if (plainText == null || plainText.trim().isEmpty()) {
                return plainText;
            }
            return "ENC(" + encrypt(plainText) + ")";
        }
    }
}
