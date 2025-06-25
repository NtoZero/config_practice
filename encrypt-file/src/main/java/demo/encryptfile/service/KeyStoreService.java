package demo.encryptfile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.security.KeyStore;
import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Base64;

/**
 * 키스토어 관리 서비스
 * PKCS#12 키스토어에서 개인키를 추출하여 JASYPT 암호화에 활용
 */
@Slf4j
@Service
public class KeyStoreService {

    /**
     * 키스토어에서 SecretKey를 추출하여 JASYPT 암호화용 비밀번호로 변환
     * 
     * @param keystorePath 키스토어 파일 경로
     * @param keystorePassword 키스토어 비밀번호
     * @param alias 키 별칭
     * @return SecretKey 기반 암호화 비밀번호
     */
    public String extractSecretKeyAsPassword(String keystorePath, String keystorePassword, String alias) {
        log.info("키스토어에서 SecretKey 추출 시작: path={}, alias={}", keystorePath, alias);
        
        try {
            // 키스토어 파일 경로 정리 (file: 프로토콜 제거)
            String actualPath = keystorePath.replace("file:", "");
            
            // 키스토어 로드
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(actualPath)) {
                keyStore.load(fis, keystorePassword.toCharArray());
            }
            
            // SecretKey 추출
            Key key = keyStore.getKey(alias, keystorePassword.toCharArray());
            if (key == null) {
                throw new IllegalStateException("키를 찾을 수 없습니다. 별칭: " + alias);
            }
            
            if (!(key instanceof SecretKey)) {
                throw new IllegalStateException("키가 SecretKey 타입이 아닙니다. 실제 타입: " + key.getClass().getSimpleName());
            }
            
            SecretKey secretKey = (SecretKey) key;
            
            // SecretKey를 Base64로 인코딩하여 비밀번호로 사용
            byte[] secretKeyBytes = secretKey.getEncoded();
            String secretKeyPassword = Base64.getEncoder().encodeToString(secretKeyBytes);
            
            log.info("SecretKey 추출 완료. 키 길이: {} bytes", secretKeyBytes.length);
            
            // 보안상 이유로 키 내용은 로그에 출력하지 않음
            return secretKeyPassword;
            
        } catch (Exception e) {
            log.error("SecretKey 추출 실패: {}", e.getMessage());
            throw new IllegalStateException("키스토어에서 SecretKey 추출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 키스토어 유효성 검증
     * 
     * @param keystorePath 키스토어 파일 경로
     * @param keystorePassword 키스토어 비밀번호
     * @param alias 키 별칭
     */
    public void validateKeyStore(String keystorePath, String keystorePassword, String alias) {
        log.info("키스토어 검증 시작: path={}, alias={}", keystorePath, alias);
        
        try {
            // 키스토어 파일 경로 정리
            String actualPath = keystorePath.replace("file:", "");
            
            // 키스토어 로드 및 검증
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(actualPath)) {
                keyStore.load(fis, keystorePassword.toCharArray());
            }
            
            // 별칭 존재 확인
            if (!keyStore.containsAlias(alias)) {
                throw new IllegalStateException("키스토어에서 별칭 '" + alias + "'를 찾을 수 없습니다.");
            }
            
            // SecretKey 존재 확인
            Key key = keyStore.getKey(alias, keystorePassword.toCharArray());
            if (key == null) {
                throw new IllegalStateException("별칭 '" + alias + "'에 대한 키를 찾을 수 없습니다.");
            }
            
            if (!(key instanceof SecretKey)) {
                throw new IllegalStateException("키가 SecretKey 타입이 아닙니다. 실제 타입: " + key.getClass().getSimpleName());
            }
            
            SecretKey secretKey = (SecretKey) key;
            
            log.info("키스토어 검증 완료");
            log.info("   - 키스토어 타입: PKCS12");
            log.info("   - 별칭: {}", alias);
            log.info("   - 키 알고리즘: {}", secretKey.getAlgorithm());
            log.info("   - 키 포맷: {}", secretKey.getFormat());
            
        } catch (Exception e) {
            log.error("키스토어 검증 실패: {}", e.getMessage());
            throw new IllegalStateException("키스토어 검증 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 키스토어 정보 출력 (디버깅용)
     * 
     * @param keystorePath 키스토어 파일 경로
     * @param keystorePassword 키스토어 비밀번호
     */
    public void printKeyStoreInfo(String keystorePath, String keystorePassword) {
        log.info("키스토어 정보 조회: {}", keystorePath);
        
        try {
            String actualPath = keystorePath.replace("file:", "");
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            
            try (FileInputStream fis = new FileInputStream(actualPath)) {
                keyStore.load(fis, keystorePassword.toCharArray());
            }
            
            log.info("키스토어 정보:");
            log.info("  - 타입: {}", keyStore.getType());
            log.info("  - 제공자: {}", keyStore.getProvider().getName());
            log.info("  - 엔트리 수: {}", keyStore.size());
            
            // 모든 별칭 출력
            log.info("  - 별칭 목록:");
            keyStore.aliases().asIterator().forEachRemaining(alias -> {
                try {
                    log.info("    * {}", alias);
                    if (keyStore.isKeyEntry(alias)) {
                        log.info("      - 키 엔트리 포함");
                    }
                    if (keyStore.isCertificateEntry(alias)) {
                        log.info("      - 인증서 엔트리 포함");
                    }
                } catch (Exception e) {
                    log.warn("      - 별칭 '{}' 정보 조회 실패: {}", alias, e.getMessage());
                }
            });
            
        } catch (Exception e) {
            log.error("키스토어 정보 조회 실패: {}", e.getMessage());
        }
    }
}
