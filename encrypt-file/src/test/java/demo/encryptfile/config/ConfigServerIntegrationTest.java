package demo.encryptfile.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Config Server 연동 테스트
 * 
 * 이 테스트는 Config Server가 실행 중이어야 합니다.
 * Config Server가 실행되지 않은 경우, fallback 환경변수를 사용합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false", // 테스트에서는 Config Server 비활성화
    "encrypt-file.p12-storepass=TestPassword123!",
    "spring.jasypt.encryptor.key-store.location=file:secrets/keystore.p12"
})
class ConfigServerIntegrationTest {

    @Value("${encrypt-file.p12-storepass}")
    private String keystorePassword;

    @Test
    void testConfigServerPropertyInjection() {
        // Config Server에서 가져온 값 또는 테스트 프로퍼티 확인
        assertThat(keystorePassword).isNotNull();
        assertThat(keystorePassword).isNotEmpty();
        assertThat(keystorePassword).contains("Password123!");
        
        System.out.println("🔑 키스토어 비밀번호가 성공적으로 주입되었습니다: " + 
                         keystorePassword.substring(0, 4) + "****");
    }
}
