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
    "spring.jasypt.encryptor.key-store.password=QmQ8Xu1WAy3wZ+Z77Y0JXi6FU0X0ERneF36jbTR4LVg=",
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
        String dbPassword = "ChangeMeRoot!";  // 실제 비밀번호로 변경
        
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
            ""
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

    /**
     * 복호화 전용 메서드
     * 이미 암호화된 값들을 복호화하여 원본 값을 확인할 때 사용
     */
    @Test
    void 암호화된_값_복호화() {
        // 복호화하고 싶은 암호화된 값들을 여기에 추가하세요
        // ENC() 형태나 순수 암호화 문자열 모두 가능
        String[] encryptedValues = {
            "ENC(your-encrypted-value-here)",  // ENC() 형태
            "another-encrypted-value",         // 순수 암호화 문자열
            // 실제 복호화할 값들을 여기에 추가하세요
                "ENC(DHMROhTp1w/dUH1GxuiyfAKH+jTbTwzBYOoVZi9E6ouinsGL3RK2KRHP6xRl+QyDBKd2XqhjvqVBJaWdUZ/eck7LWmg+lAePyaSi+Nm0f1M=)",
                "ENC(HZ+9mUVFXqXqrqUBe3QjpFx7Loy40375JC/DFBkJgrAt2HXrlNrOQHmvtA2vNvGJ)",
                "ENC(MV3eWU0ANwBeDmHbd/az+XpV9+knIiSRtZTa7D61IUWZrtgq1A9VJ2KIspOEQPWCa70EHARmFCxze7tzAojSyQ==)"
        };
        
        System.out.println("🔓 암호화된 값들 복호화:");
        System.out.println("=====================================");
        
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
                System.out.println("   원인: 잘못된 암호화 값이거나 키가 다를 수 있습니다.");
            }
            
            System.out.println("-------------------------------------");
        }
        
        System.out.println();
        System.out.println("💡 사용법:");
        System.out.println("1. 복호화할 암호화된 값을 encryptedValues 배열에 추가하세요");
        System.out.println("2. ENC(암호화값) 형태나 순수 암호화 문자열 모두 가능합니다");
        System.out.println("3. 테스트를 실행하면 복호화된 원본 값을 확인할 수 있습니다");
    }
}
