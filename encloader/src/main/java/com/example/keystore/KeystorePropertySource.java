package com.example.keystore;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;

@Slf4j
class KeystorePropertySource extends EnumerablePropertySource<KeyStore> {

    private final Map<String, Object> values = new HashMap<>();

    private KeystorePropertySource(String name, KeyStore ks, char[] pwd) throws Exception {
        super(name, ks);
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!ks.isKeyEntry(alias)) continue;
            byte[] secret = ks.getKey(alias, pwd).getEncoded();
            // 대칭성 확보: 원본 UTF-8 바이트 배열을 문자열로 복원
            // 대소문자 일관성을 위해 대문자로 키를 저장
            values.put(alias.toUpperCase(), new String(secret, StandardCharsets.UTF_8));
        }
    }

    /** 
     * location 예) file:secrets/keystore.p12, classpath:keystore.p12
     */
    static KeystorePropertySource from(String location, String password) {
        ResourceLoader loader = new DefaultResourceLoader();
        Resource resource = loader.getResource(location);

        try (var in = resource.getInputStream()) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, password.toCharArray());
            return new KeystorePropertySource("keystore", ks, password.toCharArray());
        } catch (Exception e) {
            // 실패 시 부팅을 중단하는 편이 안전
            log.error("p12 암호화 파일을 로드하는데 실패했습니다.");
            log.error(e.getMessage(), e);
            throw new IllegalStateException("Keystore load error from location: " + location, e);
        }
    }

    @Override
    public String[] getPropertyNames() { return values.keySet().toArray(String[]::new); }

    @Override
    public Object getProperty(String name) { 
        if (name == null) return null;
        return values.get(name.toUpperCase()); 
    }

    @Override
    public boolean containsProperty(String name) { 
        if (name == null) return false;
        return values.containsKey(name.toUpperCase()); 
    }
}
