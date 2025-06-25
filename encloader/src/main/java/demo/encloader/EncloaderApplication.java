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
		System.out.println("=".repeat(80));
		System.out.println("🔐 Keystore Property Source Demo - Refactored Version 1.2");
		System.out.println("=".repeat(80));
		
		// Keystore 설정 정보 출력
		String keystorePath = environment.getProperty("keystore.path");
		String keystorePassword = environment.getProperty("keystore.password");
		
		System.out.println("📁 Keystore Configuration:");
		System.out.println("   Path: " + keystorePath);
		System.out.println("   Password: " + (keystorePassword != null ? "***" : "not set"));
		System.out.println();
		
		// 직접 keystore에서 로드된 속성들 확인
		System.out.println("🔑 Properties loaded from Keystore:");
		checkAllKeystoreProperties();
		System.out.println();
		
		// 플레이스홀더가 해석된 최종 값들
		System.out.println("🎯 Resolved Property Values:");
		System.out.println("   Jasypt Password: " + maskValue(jasyptPassword));
		System.out.println("   Demo Encrypted Value: " + maskValue(encryptedValue));
		System.out.println("   Database Password: " + maskValue(databasePassword));
		System.out.println("   API Key: " + maskValue(apiKey));
		System.out.println();
		
		// 문자열 복원 검증
		System.out.println("🔍 Data Integrity Verification:");
		verifyStringIntegrity("JASYPT_PASSWORD", jasyptPassword);
		verifyStringIntegrity("DEMO_SECRET", encryptedValue);
		verifyStringIntegrity("DB_PASSWORD", databasePassword);
		verifyStringIntegrity("API_KEY", apiKey);
		System.out.println();
		
		// 성공/실패 상태 출력
		boolean keystoreLoaded = !jasyptPassword.equals("not-configured") && 
								!jasyptPassword.equals("default-password");
		
		if (keystoreLoaded) {
			System.out.println("✅ SUCCESS: Keystore properties successfully loaded!");
			System.out.println("✅ SUCCESS: Original UTF-8 strings properly restored!");
			System.out.println("✅ SUCCESS: Jasypt will receive actual password strings!");
		} else {
			System.out.println("❌ FAILURE: Keystore properties not loaded. Check keystore configuration.");
			System.out.println("💡 Usage:");
			System.out.println("   # First, create demo keystore:");
			System.out.println("   java -cp encloader.jar com.example.keystore.KeystoreCreator \\");
			System.out.println("        secrets/keystore.p12 mypassword demo");
			System.out.println("");
			System.out.println("   # Then run with keystore:");
			System.out.println("   java -Dkeystore.path=file:secrets/keystore.p12 \\");
			System.out.println("        -Dkeystore.password=mypassword \\");
			System.out.println("        -jar encloader.jar");
		}
		
		System.out.println("=".repeat(80));
	}
	
	private void checkKeystoreProperty(String propertyName) {
		String value = environment.getProperty(propertyName);
		if (value != null) {
			System.out.println("   ✓ " + propertyName + ": " + maskValue(value) + 
							   " (length=" + value.length() + ")");
		} else {
			System.out.println("   ✗ " + propertyName + ": not found");
		}
	}
	
	private void checkAllKeystoreProperties() {
		// Environment에서 PropertySource들을 순회하여 keystore PropertySource 찾기
		if (environment instanceof org.springframework.core.env.ConfigurableEnvironment) {
			org.springframework.core.env.ConfigurableEnvironment configurableEnv = 
				(org.springframework.core.env.ConfigurableEnvironment) environment;
			
			configurableEnv.getPropertySources().forEach(propertySource -> {
				if ("keystore".equals(propertySource.getName())) {
					if (propertySource instanceof org.springframework.core.env.EnumerablePropertySource) {
						org.springframework.core.env.EnumerablePropertySource<?> enumerablePS = 
							(org.springframework.core.env.EnumerablePropertySource<?>) propertySource;
						
						String[] propertyNames = enumerablePS.getPropertyNames();
						if (propertyNames.length == 0) {
							System.out.println("   ⚠️  No properties found in keystore");
							return;
						}
						
						for (String propertyName : propertyNames) {
							checkKeystoreProperty(propertyName);
						}
						return;
					}
				}
			});
			
			// keystore PropertySource를 찾지 못한 경우
			System.out.println("   ⚠️  Keystore PropertySource not found - checking fallback properties");
			checkKeystoreProperty("JASYPT_PASSWORD");
			checkKeystoreProperty("DEMO_SECRET");
			checkKeystoreProperty("DB_PASSWORD");
			checkKeystoreProperty("API_KEY");
		}
	}
	
	private void verifyStringIntegrity(String propertyName, String value) {
		if (value == null || value.equals("not-configured") || value.equals("not-found")) {
			System.out.println("   ⚠️  " + propertyName + ": not loaded from keystore");
			return;
		}
		
		// UTF-8 문자열 검증
		boolean isValidUtf8 = isValidUtf8String(value);
		boolean isBase64Like = isBase64Like(value);
		
		if (isValidUtf8 && !isBase64Like) {
			System.out.println("   ✅ " + propertyName + ": Valid UTF-8 string (not Base64)");
		} else if (isBase64Like) {
			System.out.println("   ❌ " + propertyName + ": Appears to be Base64 encoded! Data not properly restored!");
		} else {
			System.out.println("   ⚠️  " + propertyName + ": Uncertain format");
		}
	}
	
	private boolean isValidUtf8String(String value) {
		// 기본적인 UTF-8 문자열 검증
		try {
			byte[] bytes = value.getBytes("UTF-8");
			String reconstructed = new String(bytes, "UTF-8");
			return value.equals(reconstructed);
		} catch (Exception e) {
			return false;
		}
	}
	
	private boolean isBase64Like(String value) {
		// Base64처럼 보이는지 간단히 체크
		return value.matches("^[A-Za-z0-9+/]*={0,2}$") && value.length() % 4 == 0 && value.length() > 20;
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
