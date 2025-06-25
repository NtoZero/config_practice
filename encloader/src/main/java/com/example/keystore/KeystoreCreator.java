package com.example.keystore;

import javax.crypto.spec.SecretKeySpec;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;

/**
 * PKCS#12 keystore 생성 유틸리티
 * 개발 및 테스트 목적으로 keystore를 프로그래밍 방식으로 생성
 */
public class KeystoreCreator {

    /**
     * 주어진 키-값 쌍으로 PKCS#12 keystore 생성
     * 
     * @param keystorePath keystore 파일 경로
     * @param keystorePassword keystore 패스워드
     * @param keyPassword 개별 키 패스워드
     * @param secrets alias -> secret value 맵
     * @throws Exception keystore 생성 실패 시
     */
    public static void createKeystore(String keystorePath, String keystorePassword, 
                                    String keyPassword, Map<String, String> secrets) throws Exception {
        
        // 디렉토리 생성
        Path path = Paths.get(keystorePath);
        Path parentDir = path.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        
        // KeyStore 초기화
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        
        // 각 secret을 keystore에 추가
        KeyStore.PasswordProtection protection = 
            new KeyStore.PasswordProtection(keyPassword.toCharArray());
            
        for (Map.Entry<String, String> entry : secrets.entrySet()) {
            String alias = entry.getKey();
            String secretValue = entry.getValue();
            
            // 문자열을 바이트 배열로 변환하여 SecretKey 생성
            byte[] secretBytes = secretValue.getBytes("UTF-8");
            SecretKeySpec secretKey = new SecretKeySpec(secretBytes, "AES");
            
            // KeyStore에 secret key 저장
            KeyStore.SecretKeyEntry secretKeyEntry = new KeyStore.SecretKeyEntry(secretKey);
            keyStore.setEntry(alias, secretKeyEntry, protection);
        }
        
        // 파일에 keystore 저장
        try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
            keyStore.store(fos, keystorePassword.toCharArray());
        }
        
        System.out.println("✅ Keystore created successfully: " + keystorePath);
        System.out.println("   Entries: " + secrets.keySet());
    }
    
    /**
     * 데모용 keystore 생성
     * 
     * @param keystorePath keystore 파일 경로
     * @param keystorePassword keystore 패스워드
     * @throws Exception keystore 생성 실패 시
     */
    public static void createDemoKeystore(String keystorePath, String keystorePassword) throws Exception {
        Map<String, String> demoSecrets = Map.of(
            "JASYPT_PASSWORD", "my-super-secret-jasypt-password-123",
            "DEMO_SECRET", "this-is-a-demo-secret-value",
            "DB_PASSWORD", "database-production-password-456",
            "API_KEY", "api-key-abcdef1234567890"
        );
        
        createKeystore(keystorePath, keystorePassword, keystorePassword, demoSecrets);
    }
    
    /**
     * CLI 도구로 사용할 수 있는 main 메서드
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java KeystoreCreator <keystore-path> <keystore-password> [demo]");
            System.out.println("Example: java KeystoreCreator secrets/keystore.p12 mypassword demo");
            System.exit(1);
        }
        
        String keystorePath = args[0];
        String keystorePassword = args[1];
        boolean createDemo = args.length > 2 && "demo".equals(args[2]);
        
        try {
            if (createDemo) {
                createDemoKeystore(keystorePath, keystorePassword);
                System.out.println("\n🎯 Demo keystore created with the following entries:");
                System.out.println("   - JASYPT_PASSWORD: for Jasypt encryption");
                System.out.println("   - DEMO_SECRET: demo secret value");
                System.out.println("   - DB_PASSWORD: database password");
                System.out.println("   - API_KEY: API key");
                System.out.println("\n💡 To use this keystore:");
                System.out.println("   java -Dkeystore.path=file:" + keystorePath + " \\");
                System.out.println("        -Dkeystore.password=" + keystorePassword + " \\");
                System.out.println("        -jar your-app.jar");
            } else {
                System.out.println("Custom keystore creation not implemented yet.");
                System.out.println("Use 'demo' parameter to create demo keystore.");
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to create keystore: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
