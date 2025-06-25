package com.example.keystore;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;

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
            values.put(alias, new String(secret, StandardCharsets.UTF_8));
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
            throw new IllegalStateException("Keystore load error from location: " + location, e);
        }
    }

    @Override
    public String[] getPropertyNames() { return values.keySet().toArray(String[]::new); }

    @Override
    public Object getProperty(String name) { return values.get(name); }

    @Override
    public boolean containsProperty(String name) { return values.containsKey(name); }
}
