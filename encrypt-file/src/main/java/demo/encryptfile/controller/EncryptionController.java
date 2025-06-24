package demo.encryptfile.controller;

import demo.encryptfile.config.JasyptConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 암호화/복호화 테스트 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/encrypt")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EncryptionController {

    private final JasyptConfig.EncryptionService encryptionService;

    /**
     * 문자열 암호화
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> encryptText(@RequestBody Map<String, String> request) {
        String plainText = request.get("plainText");
        
        if (plainText == null || plainText.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "plainText는 필수입니다"));
        }
        
        log.info("암호화 요청 - 평문 길이: {}", plainText.length());
        
        try {
            String encrypted = encryptionService.encrypt(plainText);
            String encryptedWithFormat = encryptionService.encryptWithFormat(plainText);
            
            Map<String, String> response = Map.of(
                "plainText", plainText,
                "encrypted", encrypted,
                "encryptedWithFormat", encryptedWithFormat
            );
            
            log.info("암호화 완료");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("암호화 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "암호화 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 문자열 복호화
     */
    @PostMapping("/decrypt")
    public ResponseEntity<Map<String, String>> decryptText(@RequestBody Map<String, String> request) {
        String encryptedText = request.get("encryptedText");
        
        if (encryptedText == null || encryptedText.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "encryptedText는 필수입니다"));
        }
        
        log.info("복호화 요청 - 암호문 길이: {}", encryptedText.length());
        
        try {
            String decrypted = encryptionService.decrypt(encryptedText);
            
            Map<String, String> response = Map.of(
                "encryptedText", encryptedText,
                "decrypted", decrypted
            );
            
            log.info("복호화 완료");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("복호화 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "복호화 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 암호화/복호화 테스트
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testEncryption(@RequestBody Map<String, String> request) {
        String testText = request.getOrDefault("testText", "Hello JASYPT PKCS#12!");
        
        log.info("암호화/복호화 테스트 - 테스트 텍스트: {}", testText);
        
        try {
            // 암호화
            String encrypted = encryptionService.encrypt(testText);
            String encryptedWithFormat = encryptionService.encryptWithFormat(testText);
            
            // 복호화
            String decrypted1 = encryptionService.decrypt(encrypted);
            String decrypted2 = encryptionService.decrypt(encryptedWithFormat);
            
            // 결과 검증
            boolean isValid1 = testText.equals(decrypted1);
            boolean isValid2 = testText.equals(decrypted2);
            
            Map<String, Object> response = Map.of(
                "originalText", testText,
                "encrypted", encrypted,
                "encryptedWithFormat", encryptedWithFormat,
                "decrypted1", decrypted1,
                "decrypted2", decrypted2,
                "isValid1", isValid1,
                "isValid2", isValid2,
                "overallSuccess", isValid1 && isValid2
            );
            
            log.info("암호화/복호화 테스트 완료 - 성공: {}", isValid1 && isValid2);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("암호화/복호화 테스트 실패", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "테스트 실패: " + e.getMessage()));
        }
    }
}
