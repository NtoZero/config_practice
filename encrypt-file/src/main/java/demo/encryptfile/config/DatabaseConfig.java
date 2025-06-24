package demo.encryptfile.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

/**
 * 데이터베이스 설정 및 연결 검증
 */
@Slf4j
@Configuration
public class DatabaseConfig {

    private final DataSource dataSource;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    public DatabaseConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void validateDatabaseConnection() {
        log.info("🗄️  데이터베이스 연결 검증 중... (Profile: {})", activeProfile);
        
        try {
            // 연결 테스트
            var connection = dataSource.getConnection();
            var databaseProductName = connection.getMetaData().getDatabaseProductName();
            var databaseProductVersion = connection.getMetaData().getDatabaseProductVersion();
            var url = connection.getMetaData().getURL();
            
            // URL에서 민감한 정보 마스킹
            String maskedUrl = maskUrl(url);
            
            log.info("✅ 데이터베이스 연결 성공");
            log.info("  - 데이터베이스: {} {}", databaseProductName, databaseProductVersion);
            log.info("  - URL: {}", maskedUrl);
            
            connection.close();
        } catch (Exception e) {
            log.error("❌ 데이터베이스 연결 실패: {}", e.getMessage());
            throw new IllegalStateException("데이터베이스 연결을 설정할 수 없습니다", e);
        }
    }

    private String maskUrl(String url) {
        if (url == null) return "N/A";
        
        // 비밀번호 마스킹
        return url.replaceAll("password=[^&;]*", "password=***")
                  .replaceAll("user=[^&;]*", "user=***");
    }
}
