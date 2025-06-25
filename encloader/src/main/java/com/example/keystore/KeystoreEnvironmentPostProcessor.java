package com.example.keystore;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

@Slf4j
public class KeystoreEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String path = env.getProperty("keystore.path");
        if (path == null || path.isEmpty()) {
            // 경로가 지정되지 않았으면 로드하지 않고 종료
            log.info("keystore.path is not configured. Skipping keystore loading.");
            return;
        }
        String password = env.getProperty("keystore.password");
        if (password == null || password.isEmpty()) {
            log.info("keystore.password is not configured. Skipping keystore loading.");
            return;   // 비밀번호 없으면 패스
        }

        KeystorePropertySource src = KeystorePropertySource.from(path, password);
        env.getPropertySources().addFirst(src); // 가장 먼저 삽입
    }

    @Override
    public int getOrder() {
        // application.yml 로더보다 뒤에 실행되어야 함. Ordered.HIGHEST_PRECEDENCE + 10
        return Ordered.HIGHEST_PRECEDENCE + 11;
    }
}
