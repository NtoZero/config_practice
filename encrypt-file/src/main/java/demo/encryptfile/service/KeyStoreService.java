package demo.encryptfile.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Base64;

/**
 * 키스토어 관리 서비스
 * PKCS#12 키스토어에서 개인키를 추출하여 JASYPT 암호화에 활용
 */
@Slf4j
@Service
public class KeyStoreService {

    /**
     * 키스토어에서 개인키를 추출하여 JASYPT 암호화용 비밀번호로 변환
     * 
     * @param keystorePath 키스토어 파일 경로
     * @param keystorePassword 키스토어 비밀번호
     * @param alias 키 별칭
     * @return 개인키 기반 암호화 비밀번호
     */
    public String extractPrivateKeyAsPassword(String keystorePath, String keystorePassword, String alias) {
        log.info("키스토어에서 개인키 추출 시작: path={}, alias={}", keystorePath, alias);
        
        try {
            // 키스토어 파일 경로 정리 (file: 프로토콜 제거)
            String actualPath = keystorePath.replace("file:", "");
            
            // 키스토어 로드
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(actualPath)) {
                keyStore.load(fis, keystorePassword.toCharArray());
            }
            
            // 개인키 추출
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keystorePassword.toCharArray());
            if (privateKey == null) {
                throw new IllegalStateException("개인키를 찾을 수 없습니다. 별칭: " + alias);
            }
            
            // 개인키를 Base64로 인코딩하여 비밀번호로 사용
            byte[] privateKeyBytes = privateKey.getEncoded();
            String privateKeyPassword = Base64.getEncoder().encodeToString(privateKeyBytes);
            
            log.info("개인키 추출 완료. 키 길이: {} bytes", privateKeyBytes.length);
            
            // 보안상 이유로 개인키 내용은 로그에 출력하지 않음
            return privateKeyPassword;
            
        } catch (Exception e) {
            log.error("개인키 추출 실패: {}", e.getMessage());
            throw new IllegalStateException("키스토어에서 개인키 추출 실패: " + e.getMessage(), e);
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
            
            // 개인키 존재 확인
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keystorePassword.toCharArray());
            if (privateKey == null) {
                throw new IllegalStateException("별칭 '" + alias + "'에 대한 개인키를 찾을 수 없습니다.");
            }
            
            // 인증서 존재 확인
            Certificate certificate = keyStore.getCertificate(alias);
            if (certificate == null) {
                throw new IllegalStateException("별칭 '" + alias + "'에 대한 인증서를 찾을 수 없습니다.");
            }
            
            log.info("키스토어 검증 완료");
            log.info("   - 키스토어 타입: PKCS12");
            log.info("   - 별칭: {}", alias);
            log.info("   - 개인키 알고리즘: {}", privateKey.getAlgorithm());
            log.info("   - 인증서 타입: {}", certificate.getType());
            
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
