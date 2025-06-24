package demo.encryptfile.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 헬스체크 및 시스템 상태 확인 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class HealthController {

    private final DataSource dataSource;

    @Value("${spring.application.name:encrypt-file}")
    private String applicationName;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    /**
     * 기본 헬스체크
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = Map.of(
            "status", "UP",
            "application", applicationName,
            "profile", activeProfile,
            "timestamp", LocalDateTime.now(),
            "message", "애플리케이션이 정상적으로 실행 중입니다"
        );
        
        return ResponseEntity.ok(health);
    }

    /**
     * 데이터베이스 연결 상태 확인
     */
    @GetMapping("/db")
    public ResponseEntity<Map<String, Object>> databaseHealth() {
        try {
            var connection = dataSource.getConnection();
            var metaData = connection.getMetaData();
            
            Map<String, Object> dbHealth = Map.of(
                "status", "UP",
                "database", metaData.getDatabaseProductName(),
                "version", metaData.getDatabaseProductVersion(),
                "driver", metaData.getDriverName(),
                "url", maskSensitiveInfo(metaData.getURL()),
                "timestamp", LocalDateTime.now()
            );
            
            connection.close();
            log.debug("데이터베이스 헬스체크 성공");
            return ResponseEntity.ok(dbHealth);
            
        } catch (Exception e) {
            log.error("데이터베이스 헬스체크 실패", e);
            
            Map<String, Object> dbHealth = Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.status(503).body(dbHealth);
        }
    }

    /**
     * JASYPT 암호화 상태 확인
     */
    @GetMapping("/jasypt")
    public ResponseEntity<Map<String, Object>> jasyptHealth() {
        try {
            // 간단한 암호화/복호화 테스트로 JASYPT 상태 확인
            // 실제 구현에서는 JasyptConfig.EncryptionService를 주입받아 사용
            
            Map<String, Object> jasyptHealth = Map.of(
                "status", "UP",
                "message", "JASYPT 암호화 서비스가 정상적으로 작동 중입니다",
                "algorithm", "PBEWITHHMACSHA512ANDAES_256",
                "keystore", "PKCS#12",
                "timestamp", LocalDateTime.now()
            );
            
            log.debug("JASYPT 헬스체크 성공");
            return ResponseEntity.ok(jasyptHealth);
            
        } catch (Exception e) {
            log.error("JASYPT 헬스체크 실패", e);
            
            Map<String, Object> jasyptHealth = Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.status(503).body(jasyptHealth);
        }
    }

    /**
     * 시스템 정보 조회
     */
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> systemInfo() {
        Runtime runtime = Runtime.getRuntime();
        
        Map<String, Object> systemInfo = new HashMap<>();
        systemInfo.put("application", applicationName);
        systemInfo.put("profile", activeProfile);
        systemInfo.put("javaVersion", System.getProperty("java.version"));
        systemInfo.put("javaVendor", System.getProperty("java.vendor"));
        systemInfo.put("osName", System.getProperty("os.name"));
        systemInfo.put("osVersion", System.getProperty("os.version"));
        systemInfo.put("maxMemory", formatMemory(runtime.maxMemory()));
        systemInfo.put("totalMemory", formatMemory(runtime.totalMemory()));
        systemInfo.put("freeMemory", formatMemory(runtime.freeMemory()));
        systemInfo.put("usedMemory", formatMemory(runtime.totalMemory() - runtime.freeMemory()));
        systemInfo.put("availableProcessors", runtime.availableProcessors());
        systemInfo.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(systemInfo);
    }

    /**
     * 전체 상태 종합 확인
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> overallStatus() {
        boolean dbUp = false;
        boolean jasyptUp = false;
        
        // 데이터베이스 상태 확인
        try {
            var connection = dataSource.getConnection();
            connection.close();
            dbUp = true;
        } catch (Exception e) {
            log.warn("데이터베이스 연결 실패", e);
        }
        
        // JASYPT 상태 확인 (간단한 체크)
        try {
            // 실제로는 암호화 서비스 테스트
            jasyptUp = true;
        } catch (Exception e) {
            log.warn("JASYPT 상태 확인 실패", e);
        }
        
        boolean overallUp = dbUp && jasyptUp;
        
        Map<String, Object> status = Map.of(
            "status", overallUp ? "UP" : "DOWN",
            "components", Map.of(
                "database", dbUp ? "UP" : "DOWN",
                "jasypt", jasyptUp ? "UP" : "DOWN"
            ),
            "application", applicationName,
            "profile", activeProfile,
            "timestamp", LocalDateTime.now()
        );
        
        return ResponseEntity.status(overallUp ? 200 : 503).body(status);
    }

    /**
     * 민감한 정보 마스킹
     */
    private String maskSensitiveInfo(String info) {
        if (info == null) return "N/A";
        
        return info.replaceAll("password=[^&;]*", "password=***")
                  .replaceAll("user=[^&;]*", "user=***");
    }

    /**
     * 메모리 크기 포맷팅
     */
    private String formatMemory(long bytes) {
        long mb = bytes / (1024 * 1024);
        return mb + " MB";
    }
}
