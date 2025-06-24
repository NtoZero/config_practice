package demo.encryptfile.config;

import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JASYPT 설정 테스트
 */
@SpringBootTest
@ActiveProfiles("local")
class JasyptConfigTest {

    @Autowired
    private StringEncryptor stringEncryptor;

    @Autowired
    private JasyptConfig.EncryptionService encryptionService;

    @Test
    void 암호화_복호화_테스트() {
        // Given
        String plainText = "Hello JASYPT!";

        // When
        String encrypted = stringEncryptor.encrypt(plainText);
        String decrypted = stringEncryptor.decrypt(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plainText);
        assertThat(encrypted).isNotEqualTo(plainText);
    }

    @Test
    void 암호화_서비스_테스트() {
        // Given
        String plainText = "Test Secret Value";

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
        String plainText = "Secret Password";
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
        String plainText = "Multi Encryption Test";

        // When - 같은 평문을 여러 번 암호화
        String encrypted1 = stringEncryptor.encrypt(plainText);
        String encrypted2 = stringEncryptor.encrypt(plainText);

        // Then - 암호화 결과는 다르지만 복호화하면 같은 결과
        assertThat(encrypted1).isNotEqualTo(encrypted2); // Salt로 인해 매번 다른 결과
        assertThat(stringEncryptor.decrypt(encrypted1)).isEqualTo(plainText);
        assertThat(stringEncryptor.decrypt(encrypted2)).isEqualTo(plainText);
    }
}
