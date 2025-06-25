package demo.encryptfile.config;

import demo.encryptfile.service.KeyStoreService;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JASYPT 설정 테스트 (개인키 기반 v2.0)
 */
@SpringBootTest
@ActiveProfiles("test")
class JasyptConfigTest {

    @Autowired
    private StringEncryptor stringEncryptor;

    @Autowired
    private JasyptConfig.EncryptionService encryptionService;

    @Autowired
    private KeyStoreService keyStoreService;

    @Value("${spring.jasypt.encryptor.key-store.location}")
    private String keystoreLocation;

    @Value("${spring.jasypt.encryptor.key-store.password}")
    private String keystorePassword;

    @Value("${spring.jasypt.encryptor.key-store.alias}")
    private String keystoreAlias;

    @Test
    void 키스토어에서_개인키_추출_테스트() {
        // When - 개인키 추출
        String privateKeyPassword = keyStoreService.extractPrivateKeyAsPassword(
            keystoreLocation, keystorePassword, keystoreAlias);

        // Then
        assertThat(privateKeyPassword).isNotNull();
        assertThat(privateKeyPassword).isNotEmpty();
        assertThat(privateKeyPassword).isNotEqualTo(keystorePassword); // 🔑 키 분리 확인
        
        System.out.println("🔐 키 분리 확인:");
        System.out.println("키스토어 비밀번호: " + keystorePassword);
        System.out.println("개인키 기반 비밀번호: " + privateKeyPassword.substring(0, 20) + "...");
        System.out.println("✅ 키스토어 비밀번호 ≠ JASYPT 암호화 키");
    }

    @Test
    void 키스토어_검증_테스트() {
        // When & Then - 예외가 발생하지 않으면 성공
        keyStoreService.validateKeyStore(keystoreLocation, keystorePassword, keystoreAlias);
        
        System.out.println("✅ 키스토어 검증 완료");
    }

    @Test
    void 암호화_복호화_테스트() {
        // Given
        String plainText = "Hello JASYPT v2.0!";

        // When
        String encrypted = stringEncryptor.encrypt(plainText);
        String decrypted = stringEncryptor.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plainText);
        assertThat(encrypted).isNotEqualTo(plainText);
        
        System.out.println("🔐 암호화/복호화 테스트:");
        System.out.println("원본: " + plainText);
        System.out.println("암호화: " + encrypted);
        System.out.println("복호화: " + decrypted);
        System.out.println("✅ 개인키 기반 암호화 성공");
    }

    @Test
    void 암호화_서비스_테스트() {
        // Given
        String plainText = "Test Secret Value v2.0";

        // When
        String encrypted = encryptionService.encrypt(plainText);
        String decrypted = encryptionService.decrypt(encrypted);
        String encryptedWithFormat = encryptionService.encryptWithFormat(plainText);

        // Then
        assertThat(decrypted).isEqualTo(plainText);
        assertThat(encrypted).isNotEqualTo(plainText);
        assertThat(encryptedWithFormat).startsWith("ENC(");
        assertThat(encryptedWithFormat).endsWith(")");
    }

    @Test
    void ENC_형식_복호화_테스트() {
        // Given
        String plainText = "Secret Password v2.0";
        String encryptedWithFormat = encryptionService.encryptWithFormat(plainText);

        // When
        String decrypted = encryptionService.decrypt(encryptedWithFormat);

        // Then
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    void 빈_문자열_처리_테스트() {
        // Given & When & Then
        assertThat(encryptionService.encrypt(null)).isNull();
        assertThat(encryptionService.encrypt("")).isEmpty();
        assertThat(encryptionService.encrypt("   ")).isEqualTo("   ");
        
        assertThat(encryptionService.decrypt(null)).isNull();
        assertThat(encryptionService.decrypt("")).isEmpty();
    }

    @Test
    void 다중_암호화_테스트() {
        // Given
        String plainText = "Multi Encryption Test v2.0";

        // When - 같은 평문을 여러 번 암호화
        String encrypted1 = stringEncryptor.encrypt(plainText);
        String encrypted2 = stringEncryptor.encrypt(plainText);

        // Then - 암호화 결과는 다르지만 복호화하면 같은 결과
        assertThat(encrypted1).isNotEqualTo(encrypted2); // Salt로 인해 매번 다른 결과
        assertThat(stringEncryptor.decrypt(encrypted1)).isEqualTo(plainText);
        assertThat(stringEncryptor.decrypt(encrypted2)).isEqualTo(plainText);
    }

    @Test
    void 키스토어_정보_출력_테스트() {
        // When
        keyStoreService.printKeyStoreInfo(keystoreLocation, keystorePassword);
        
        // Then - 예외가 발생하지 않으면 성공
        System.out.println("✅ 키스토어 정보 출력 완료");
    }

    @Test
    void 마이그레이션_검증_테스트() {
        // Given
        String testData = "Migration Test Data";
        
        // When - 개인키 기반으로 암호화
        String privateKeyPassword = keyStoreService.extractPrivateKeyAsPassword(
            keystoreLocation, keystorePassword, keystoreAlias);
        String encrypted = stringEncryptor.encrypt(testData);
        String decrypted = stringEncryptor.decrypt(encrypted);
        
        // Then
        assertThat(decrypted).isEqualTo(testData);
        assertThat(privateKeyPassword).isNotEqualTo(keystorePassword);
        
        System.out.println("🎯 마이그레이션 검증 결과:");
        System.out.println("✅ 키 분리: " + !privateKeyPassword.equals(keystorePassword));
        System.out.println("✅ 암호화/복호화: " + testData.equals(decrypted));
        System.out.println("✅ 개인키 기반 JASYPT 동작 확인");
    }
}
