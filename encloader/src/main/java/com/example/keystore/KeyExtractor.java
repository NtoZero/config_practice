package com.example.keystore;

import java.security.Key;
import java.security.KeyStore;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * PKCS#12 KeyStore에서 키를 추출하는 유틸리티 클래스
 */
public class KeyExtractor {

    /**
     * KeyStore에서 모든 key entry를 추출하여 Base64 인코딩된 문자열로 반환
     * 
     * @param keyStore PKCS#12 KeyStore
     * @param password 키 패스워드
     * @return alias -> Base64 encoded key 맵
     * @throws Exception 키 추출 실패 시
     */
    public static Map<String, String> extractKeys(KeyStore keyStore, char[] password) throws Exception {
        Map<String, String> keyMap = new HashMap<>();
        
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            
            // key entry만 처리 (certificate entry는 제외)
            if (keyStore.isKeyEntry(alias)) {
                Key key = keyStore.getKey(alias, password);
                if (key != null) {
                    byte[] encoded = key.getEncoded();
                    if (encoded != null) {
                        String base64Key = Base64.getEncoder().encodeToString(encoded);
                        keyMap.put(alias, base64Key);
                    }
                }
            }
        }
        
        return keyMap;
    }

    /**
     * 특정 alias의 키를 Base64 문자열로 추출
     * 
     * @param keyStore PKCS#12 KeyStore
     * @param alias 키 별칭
     * @param password 키 패스워드
     * @return Base64 encoded key, 없으면 null
     * @throws Exception 키 추출 실패 시
     */
    public static String extractKey(KeyStore keyStore, String alias, char[] password) throws Exception {
        if (!keyStore.isKeyEntry(alias)) {
            return null;
        }
        
        Key key = keyStore.getKey(alias, password);
        if (key == null) {
            return null;
        }
        
        byte[] encoded = key.getEncoded();
        return encoded != null ? Base64.getEncoder().encodeToString(encoded) : null;
    }
}
