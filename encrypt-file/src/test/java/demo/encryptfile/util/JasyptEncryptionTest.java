package demo.encryptfile.util;

import demo.encryptfile.config.JasyptConfig;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * JASYPT 암호화 유틸리티 테스트
 * 실제 값들을 암호화하여 application.yml에 사용할 수 있는 형태로 출력
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jasypt.encryptor.key-store.password=MySecurePassword123!",
    "spring.jasypt.encryptor.key-store.location=file:secrets/keystore.p12"
})
class JasyptEncryptionTest {

    @Autowired
    private StringEncryptor stringEncryptor;

    @Autowired
    private JasyptConfig.EncryptionService encryptionService;

    @Test
    void 데이터베이스_URL_암호화() {
        // 암호화할 값들
        String[] valuesToEncrypt = {
            "jdbc:mysql://localhost:3306/demo",
            "root",
            "ChangeMeRoot!",
        };

        System.out.println("🔐 JASYPT 암호화 결과:");
        System.out.println("=====================================");
        
        for (String value : valuesToEncrypt) {
            String encrypted = stringEncryptor.encrypt(value);
            String encryptedWithFormat = encryptionService.encryptWithFormat(value);
            
            System.out.println("원본값: " + value);
            System.out.println("암호화: " + encrypted);
            System.out.println("ENC형식: " + encryptedWithFormat);
            System.out.println("-------------------------------------");
            
            // 복호화 테스트
            String decrypted = stringEncryptor.decrypt(encrypted);
            assert value.equals(decrypted) : "복호화 실패!";
        }
        
        System.out.println("✅ 모든 암호화/복호화 테스트 성공!");
    }

    @Test
    void MySQL_연결정보_암호화() {
        // MySQL 연결 정보
        String dbUrl = "jdbc:mysql://localhost:3306/demo";
        String dbUsername = "root";  // 실제 사용자명으로 변경
        String dbPassword = "password";  // 실제 비밀번호로 변경
        
        System.out.println("🗄️ MySQL 연결 정보 암호화:");
        System.out.println("=====================================");
        
        String encryptedUrl = encryptionService.encryptWithFormat(dbUrl);
        String encryptedUsername = encryptionService.encryptWithFormat(dbUsername);
        String encryptedPassword = encryptionService.encryptWithFormat(dbPassword);
        
        System.out.println("# application-prod.yml에 복사해서 사용하세요");
        System.out.println("spring:");
        System.out.println("  datasource:");
        System.out.println("    url: " + encryptedUrl);
        System.out.println("    username: " + encryptedUsername);
        System.out.println("    password: " + encryptedPassword);
        System.out.println("    driver-class-name: com.mysql.cj.jdbc.Driver");
        System.out.println();
        
        // 복호화 확인
        System.out.println("🔍 복호화 확인:");
        System.out.println("URL: " + encryptionService.decrypt(encryptedUrl));
        System.out.println("Username: " + encryptionService.decrypt(encryptedUsername));
        System.out.println("Password: " + encryptionService.decrypt(encryptedPassword));
    }

    @Test
    void 커스텀_값_암호화() {
        // 암호화하고 싶은 값들을 여기에 추가하세요
        String[] valuesToEncrypt = {
            "jdbc:mysql://localhost:3306/demo",
            "root",                    // DB 사용자명
            "your-db-password",        // DB 비밀번호
            "your-api-key",           // API 키
            "your-secret-value"       // 기타 비밀값
        };
        
        System.out.println("🔐 커스텀 값들 암호화:");
        System.out.println("=====================================");
        
        for (int i = 0; i < valuesToEncrypt.length; i++) {
            String value = valuesToEncrypt[i];
            String encrypted = encryptionService.encryptWithFormat(value);
            
            System.out.println((i + 1) + ". 원본: " + value);
            System.out.println("   암호화: " + encrypted);
            
            // 복호화 확인
            String decrypted = encryptionService.decrypt(encrypted);
            System.out.println("   복호화 확인: " + decrypted);
            System.out.println("   검증: " + (value.equals(decrypted) ? "✅ 성공" : "❌ 실패"));
            System.out.println("-------------------------------------");
        }
        
        System.out.println();
        System.out.println("📋 application.yml에 복사할 내용:");
        System.out.println("spring:");
        System.out.println("  datasource:");
        System.out.println("    url: " + encryptionService.encryptWithFormat(valuesToEncrypt[0]));
        if (valuesToEncrypt.length > 1) {
            System.out.println("    username: " + encryptionService.encryptWithFormat(valuesToEncrypt[1]));
        }
        if (valuesToEncrypt.length > 2) {
            System.out.println("    password: " + encryptionService.encryptWithFormat(valuesToEncrypt[2]));
        }
    }
}
