package com.example.keystore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

public class KeystoreEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String path     = env.getProperty("keystore.path", "file:secrets/keystore.p12");
        String password = env.getProperty("keystore.password");
        if (password == null)
            return;   // 비밀번호 없으면 패스

        KeystorePropertySource src = KeystorePropertySource.from(path, password);
        env.getPropertySources().addFirst(src); // 가장 먼저 삽입
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
