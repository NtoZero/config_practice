package demo.encryptfile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * JASYPT + PKCS#12 키스토어 기반 암호화 데모 애플리케이션
 * 
 * 플레이북 v0.6 기준으로 구현된 Spring Boot 애플리케이션
 * - PKCS#12 키스토어를 사용한 JASYPT 암호화
 * - MySQL 데이터베이스 연동
 * - REST API를 통한 암호화/복호화 기능
 * - 사용자 관리 시스템
 */
@Slf4j
@SpringBootApplication
public class EncryptFileApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(EncryptFileApplication.class, args);
        } catch (Exception e) {
            log.error("애플리케이션 시작 실패", e);
            System.exit(1);
        }
    }

    /**
     * 애플리케이션 시작 완료 후 실행되는 이벤트 리스너
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        
        String appName = env.getProperty("spring.application.name", "encrypt-file");
        String profile = String.join(", ", env.getActiveProfiles());
        String port = env.getProperty("server.port", "8080");
        
        log.info("🚀 ============================================================================");
        log.info("🚀 {} 애플리케이션이 성공적으로 시작되었습니다!", appName);
        log.info("🚀 ============================================================================");
        log.info("🔧 활성 프로파일: {}", profile.isEmpty() ? "default" : profile);
        log.info("🌐 서버 포트: {}", port);
        log.info("🔐 JASYPT 암호화: 활성화됨 (PKCS#12 키스토어)");
        log.info("📚 API 문서:");
        log.info("   - 헬스체크: http://localhost:{}/api/health", port);
        log.info("   - 사용자 관리: http://localhost:{}/api/users", port);
        log.info("   - 암호화 테스트: http://localhost:{}/api/encrypt", port);
        log.info("   - Actuator: http://localhost:{}/actuator/health", port);
        if ("local".equals(profile) || Arrays.asList(env.getActiveProfiles()).contains("local")) {
            log.info("   - H2 콘솔: http://localhost:{}/h2-console", port);
        }
        log.info("🚀 ============================================================================");
        
        // 환경변수 확인 로그 (민감한 정보는 마스킹)
        String keystoreLocation = env.getProperty("spring.jasypt.encryptor.key-store.location", "N/A");
        String hasStorepass = env.getProperty("encrypt-file.p12-storepass") != null ? "Config Server에서 설정됨" : 
                            env.getProperty("JASYPT_STOREPASS") != null ? "환경변수에서 설정됨" : "설정되지 않음";
        String configServerUri = env.getProperty("spring.cloud.config.uri", "미설정");
        
        log.info("🔑 키스토어 설정:");
        log.info("   - 위치: {}", keystoreLocation);
        log.info("   - 비밀번호: {}", hasStorepass);
        log.info("🌐 Config Server:");
        log.info("   - URI: {}", configServerUri);
        
        if ("설정되지 않음".equals(hasStorepass)) {
            log.warn("⚠️  키스토어 비밀번호가 설정되지 않았습니다!");
            log.warn("⚠️  Config Server에서 encrypt-file.p12-storepass 설정을 확인하거나");
            log.warn("⚠️  JASYPT_STOREPASS 환경변수를 설정해주세요:");
            log.warn("⚠️  Linux/macOS: export JASYPT_STOREPASS=$(cat secrets/.keystore_pass)");
            log.warn("⚠️  Windows: $env:JASYPT_STOREPASS = Get-Content secrets\\keystore_pass.txt");
        }
    }
}
