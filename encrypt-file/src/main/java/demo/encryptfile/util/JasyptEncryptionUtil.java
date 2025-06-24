package demo.encryptfile.util;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * JASYPT 암호화 유틸리티
 * standalone으로 값들을 암호화할 수 있는 유틸리티
 */
public class JasyptEncryptionUtil {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("사용법: java JasyptEncryptionUtil <암호화할값>");
            System.out.println("예시: java JasyptEncryptionUtil \"jdbc:mysql://localhost:3306/demo\"");
            return;
        }
        
        String valueToEncrypt = args[0];
        
        try {
            // 환경변수에서 키스토어 정보 가져오기
            String keystorePassword = System.getenv("JASYPT_STOREPASS");
            String keystoreLocation = System.getenv("KEYSTORE_LOCATION");
            
            if (keystorePassword == null) {
                throw new IllegalStateException("JASYPT_STOREPASS 환경변수가 설정되지 않았습니다.");
            }
            
            if (keystoreLocation == null) {
                keystoreLocation = "file:secrets/keystore.p12";
            }
            
            // 키스토어 파일 경로 처리
            String keystorePath = keystoreLocation.replace("file:", "");
            
            // 키스토어 검증
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                keyStore.load(fis, keystorePassword.toCharArray());
                if (!keyStore.containsAlias("jasypt-key")) {
                    throw new IllegalStateException("키스토어에서 별칭 'jasypt-key'를 찾을 수 없습니다.");
                }
            }
            
            // StringEncryptor 설정
            PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
            SimpleStringPBEConfig config = new SimpleStringPBEConfig();
            
            config.setPassword(keystorePassword);
            config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
            config.setKeyObtentionIterations("100000");
            config.setPoolSize("2");
            config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
            config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
            config.setStringOutputType("base64");
            
            encryptor.setConfig(config);
            
            // 암호화 실행
            String encrypted = encryptor.encrypt(valueToEncrypt);
            String encryptedWithFormat = "ENC(" + encrypted + ")";
            
            System.out.println("🔐 JASYPT 암호화 결과:");
            System.out.println("=====================================");
            System.out.println("원본값: " + valueToEncrypt);
            System.out.println("암호화: " + encrypted);
            System.out.println("ENC형식: " + encryptedWithFormat);
            System.out.println();
            System.out.println("application.yml에서 사용:");
            System.out.println("your-property: " + encryptedWithFormat);
            System.out.println();
            
            // 복호화 테스트
            String decrypted = encryptor.decrypt(encrypted);
            if (valueToEncrypt.equals(decrypted)) {
                System.out.println("✅ 암호화/복호화 검증 성공!");
            } else {
                System.out.println("❌ 암호화/복호화 검증 실패!");
            }
            
        } catch (Exception e) {
            System.err.println("❌ 암호화 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
