package demo.encloader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class EncloaderApplication implements CommandLineRunner {

	private final Environment environment;
	
	@Value("${demo.encrypted-value:not-configured}")
	private String encryptedValue;
	
	@Value("${demo.database-password:not-configured}")
	private String databasePassword;
	
	@Value("${demo.api-key:not-configured}")
	private String apiKey;
	
	@Value("${spring.jasypt.encryptor.password:not-configured}")
	private String jasyptPassword;

	public EncloaderApplication(Environment environment) {
		this.environment = environment;
	}

	public static void main(String[] args) {
		SpringApplication.run(EncloaderApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("=".repeat(60));
		System.out.println("🔐 Keystore Property Source Demo");
		System.out.println("=".repeat(60));
		
		// Keystore 설정 정보 출력
		String keystorePath = environment.getProperty("keystore.path");
		String keystorePassword = environment.getProperty("keystore.password");
		
		System.out.println("📁 Keystore Configuration:");
		System.out.println("   Path: " + keystorePath);
		System.out.println("   Password: " + (keystorePassword != null ? "***" : "not set"));
		System.out.println();
		
		// 직접 keystore에서 로드된 속성들 확인
		System.out.println("🔑 Properties loaded from Keystore:");
		checkKeystoreProperty("JASYPT_PASSWORD");
		checkKeystoreProperty("DEMO_SECRET");
		checkKeystoreProperty("DB_PASSWORD");
		checkKeystoreProperty("API_KEY");
		System.out.println();
		
		// 플레이스홀더가 해석된 최종 값들
		System.out.println("🎯 Resolved Property Values:");
		System.out.println("   Jasypt Password: " + maskValue(jasyptPassword));
		System.out.println("   Demo Encrypted Value: " + maskValue(encryptedValue));
		System.out.println("   Database Password: " + maskValue(databasePassword));
		System.out.println("   API Key: " + maskValue(apiKey));
		System.out.println();
		
		// 성공/실패 상태 출력
		boolean keystoreLoaded = !jasyptPassword.equals("not-configured") && 
								!jasyptPassword.equals("default-password");
		
		if (keystoreLoaded) {
			System.out.println("✅ Keystore properties successfully loaded!");
		} else {
			System.out.println("❌ Keystore properties not loaded. Check keystore configuration.");
			System.out.println("💡 Usage:");
			System.out.println("   java -Dkeystore.path=file:path/to/keystore.p12 \\");
			System.out.println("        -Dkeystore.password=your-keystore-password \\");
			System.out.println("        -jar encloader.jar");
		}
		
		System.out.println("=".repeat(60));
	}
	
	private void checkKeystoreProperty(String propertyName) {
		String value = environment.getProperty(propertyName);
		if (value != null) {
			System.out.println("   ✓ " + propertyName + ": " + maskValue(value));
		} else {
			System.out.println("   ✗ " + propertyName + ": not found");
		}
	}
	
	private String maskValue(String value) {
		if (value == null || value.equals("not-configured") || value.equals("not-found")) {
			return value;
		}
		if (value.length() <= 8) {
			return "***";
		}
		return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
	}
}
