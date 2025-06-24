package demo.encryptfile.config;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * JASYPT PKCS#12 키스토어 설정
 * 플레이북 v0.6 기준으로 구현
 */
@Slf4j
@Configuration
@EnableEncryptableProperties
public class JasyptConfig {

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
        log.info("🔐 JASYPT 설정 초기화 중...");
        log.info("키스토어 위치: {}", keystoreLocation);
        log.info("키스토어 별칭: {}", keystoreAlias);
        log.info("암호화 알고리즘: {}", algorithm);
        log.info("반복 횟수: {}", iterations);
        log.info("풀 크기: {}", poolSize);
        
        if (keystorePassword == null || keystorePassword.trim().isEmpty()) {
            throw new IllegalStateException("JASYPT_STOREPASS 환경변수가 설정되지 않았습니다. " +
                    "키스토어 비밀번호를 환경변수로 설정해주세요.");
        }
        
        // 키스토어 파일 존재 확인
        try {
            String path = keystoreLocation.replace("file:", "");
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(path)) {
                keyStore.load(fis, keystorePassword.toCharArray());
                if (!keyStore.containsAlias(keystoreAlias)) {
                    throw new IllegalStateException("키스토어에서 별칭 '" + keystoreAlias + "'를 찾을 수 없습니다.");
                }
                log.info("✅ 키스토어 검증 완료");
            }
        } catch (Exception e) {
            log.error("❌ 키스토어 검증 실패: {}", e.getMessage());
            throw new IllegalStateException("키스토어 설정이 올바르지 않습니다: " + e.getMessage(), e);
        }
    }

    @Bean(name = "jasyptStringEncryptor")
    @Primary
    public StringEncryptor stringEncryptor() {
        log.info("🔧 JASYPT StringEncryptor 빈 생성 중...");
        
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        
        // PKCS#12 키스토어 설정
        config.setPassword(keystorePassword);
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
            
            log.info("✅ JASYPT StringEncryptor 초기화 완료 및 테스트 성공");
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
