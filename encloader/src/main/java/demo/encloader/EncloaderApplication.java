package demo.encloader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

@Slf4j
@SpringBootApplication
public class EncloaderApplication implements CommandLineRunner {

	private final Environment environment;

	@Value("${p12loader.enable:false}")
	private boolean encloadEnabled;
	
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
		if (!encloadEnabled) {
			log.info("EncloaderApplication is disabled via p12encload.enable=false. Skipping demo logs.");
			return; // 즉시 종료
		}

		log.info("=".repeat(80));
		log.info("🔐 Keystore Property Source - Version 1.0");
		log.info("=".repeat(80));
		
		// Keystore 설정 정보 출력
		String keystorePath = environment.getProperty("p12loader.keystore.path");
		String keystorePassword = environment.getProperty("p12loader.keystore.password");
		
		log.info("📁 Keystore Configuration:");
		log.info("   Path: " + keystorePath);
		log.info("   Password: " + (keystorePassword != null ? "***" : "not set"));

		// 직접 keystore에서 로드된 속성들 확인
		log.info("🔑 Properties loaded from Keystore:");
		checkAllKeystoreProperties();
		
		log.info("=".repeat(80));
	}
	
	private void checkKeystoreProperty(String propertyName) {
		String value = environment.getProperty(propertyName);
		if (value != null) {
			log.info("   ✓ " + propertyName + ": " + maskValue(value) + 
							   " (length=" + value.length() + ")");
		} else {
			log.info("   ✗ " + propertyName + ": not found");
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
							log.info("   ⚠️  No properties found in keystore");
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
			log.info("   ⚠️  Keystore PropertySource not found - checking fallback properties");
		}
	}
	
	private void verifyStringIntegrity(String propertyName, String value) {
		if (value == null || value.equals("not-configured") || value.equals("not-found")) {
			log.info("   ⚠️  " + propertyName + ": not loaded from keystore");
			return;
		}
		
		// UTF-8 문자열 검증
		boolean isValidUtf8 = isValidUtf8String(value);
		boolean isBase64Like = isBase64Like(value);
		
		if (isValidUtf8 && !isBase64Like) {
			log.info("   ✅ " + propertyName + ": Valid UTF-8 string (not Base64)");
		} else if (isBase64Like) {
			log.info("   ❌ " + propertyName + ": Appears to be Base64 encoded! Data not properly restored!");
		} else {
			log.info("   ⚠️  " + propertyName + ": Uncertain format");
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
