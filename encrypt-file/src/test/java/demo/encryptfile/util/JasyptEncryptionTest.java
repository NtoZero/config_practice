package demo.encryptfile.util;

import demo.encryptfile.config.JasyptConfig;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * JASYPT 암호화 유틸리티 테스트 (SecretKey 기반)
 * 실제 값들을 암호화하여 application.yml에 사용할 수 있는 형태로 출력
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jasypt.encryptor.key-store.password=+zBa4N3VU1/f52yiHnoLUarisaLY9d1TgZYSt89XH6M=",
    "spring.jasypt.encryptor.key-store.location=file:secrets/keystore.p12"
})
class JasyptEncryptionTest {

    @Autowired
    private StringEncryptor stringEncryptor;

    @Autowired
    private JasyptConfig.EncryptionService encryptionService;

    @Test
    void 데이터베이스_URL_암호화() {
        System.out.println("🔐 JASYPT 암호화 테스트 (SecretKey 기반):");
        System.out.println("=====================================");
        
        // 암호화할 값들
        String[] valuesToEncrypt = {
            "jdbc:mysql://localhost:3306/demo",
            "root",
            "ChangeMeRoot!",
        };

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
        
        System.out.println("✅ 모든 암호화/복호화 테스트 성공! (SecretKey 기반)");
    }

    @Test
    void MySQL_연결정보_암호화() {
        System.out.println("🗄️ MySQL 연결 정보 암호화 (SecretKey 기반):");
        System.out.println("=====================================");
        
        // MySQL 연결 정보
        String dbUrl = "jdbc:mysql://localhost:3306/demo";
        String dbUsername = "root";  // 실제 사용자명으로 변경
        String dbPassword = "ChangeMeRoot!";  // 실제 비밀번호로 변경
        
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
        System.out.println("✅ SecretKey 기반 암호화/복호화 성공!");
    }

    @Test
    void 커스텀_값_암호화() {
        System.out.println("🔐 커스텀 값들 암호화 (SecretKey 기반):");
        System.out.println("=====================================");
        
        // 암호화하고 싶은 값들을 여기에 추가하세요
        String[] valuesToEncrypt = {
            "jdbc:mysql://localhost:3306/demo",
            "root",                    // DB 사용자명
            "test-password-123"        // 테스트용 비밀번호
        };
        
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
        System.out.println("✅ SecretKey 기반 커스텀 암호화 완료!");
    }

    /**
     * 복호화 전용 메서드 (SecretKey 기반)
     * 이미 암호화된 값들을 복호화하여 원본 값을 확인할 때 사용
     */
    @Test
    void 암호화된_값_복호화() {
        System.out.println("🔓 암호화된 값들 복호화 (SecretKey 기반):");
        System.out.println("=====================================");
        
        // 복호화하고 싶은 암호화된 값들을 여기에 추가하세요
        // ENC() 형태나 순수 암호화 문자열 모두 가능
        String[] encryptedValues = {
            "ENC(your-encrypted-value-here)",  // ENC() 형태
            "another-encrypted-value",         // 순수 암호화 문자열
            // 실제 복호화할 값들을 여기에 추가하세요
            // 주의: 이전 PrivateKey 기반으로 암호화된 값들은 복호화되지 않습니다
        };
        
        for (int i = 0; i < encryptedValues.length; i++) {
            String encryptedValue = encryptedValues[i];
            
            System.out.println((i + 1) + ". 암호화된 값: " + encryptedValue);
            
            try {
                String decrypted = encryptionService.decrypt(encryptedValue);
                System.out.println("   복호화 결과: " + decrypted);
                System.out.println("   상태: ✅ 복호화 성공");
                
                // 복호화된 값을 다시 암호화하여 검증
                String reEncrypted = encryptionService.encryptWithFormat(decrypted);
                String reDecrypted = encryptionService.decrypt(reEncrypted);
                System.out.println("   재암호화 검증: " + (decrypted.equals(reDecrypted) ? "✅ 성공" : "❌ 실패"));
                
            } catch (Exception e) {
                System.out.println("   오류: ❌ 복호화 실패 - " + e.getMessage());
                System.out.println("   원인: SecretKey 기반으로 변경되어 이전 PrivateKey 기반 암호화 값은 복호화되지 않습니다.");
            }
            
            System.out.println("-------------------------------------");
        }
        
        System.out.println();
        System.out.println("💡 사용법:");
        System.out.println("1. 복호화할 암호화된 값을 encryptedValues 배열에 추가하세요");
        System.out.println("2. ENC(암호화값) 형태나 순수 암호화 문자열 모두 가능합니다");
        System.out.println("3. SecretKey 기반으로 변경되어 이전 PrivateKey 기반 암호화 값은 호환되지 않습니다");
        System.out.println("4. 새로운 값들은 SecretKey 기반으로 다시 암호화해야 합니다");
    }

    /**
     * 키스토어 정보 확인 테스트
     */
    @Test
    void 키스토어_정보_확인() {
        System.out.println("🔑 키스토어 정보 확인 (SecretKey 기반):");
        System.out.println("=====================================");
        
        try {
            // 간단한 암호화/복호화 테스트
            String testValue = "SecretKey-기반-테스트-" + System.currentTimeMillis();
            String encrypted = stringEncryptor.encrypt(testValue);
            String decrypted = stringEncryptor.decrypt(encrypted);
            
            System.out.println("테스트 값: " + testValue);
            System.out.println("암호화 결과: " + encrypted);
            System.out.println("복호화 결과: " + decrypted);
            System.out.println("검증: " + (testValue.equals(decrypted) ? "✅ 성공" : "❌ 실패"));
            
        } catch (Exception e) {
            System.out.println("❌ 테스트 실패: " + e.getMessage());
        }
    }
}
