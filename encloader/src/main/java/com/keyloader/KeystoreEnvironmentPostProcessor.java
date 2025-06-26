package com.keyloader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

@Slf4j
public class KeystoreEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private final static int APPLICATION_LOADER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {

        // p12encload 플래그 확인 (기본값: false)
        String p12encloadFlag = env.getProperty("p12loader.enable", "false");
        boolean isEnabled = Boolean.parseBoolean(p12encloadFlag);

        if (!isEnabled) {
            log.info("Keystore loading skipped: p12encload flag is disabled");
            return; // 플래그가 false이면 전체 기능 비활성화
        }

        String path = env.getProperty("p12loader.keystore.path");
        if (path == null || path.isEmpty()) {
            // 경로가 지정되지 않았으면 로드하지 않고 종료
            log.info("keystore.path is not configured. Skipping keystore loading.");
            return;
        }
        String password = env.getProperty("p12loader.keystore.password");
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
        return APPLICATION_LOADER_ORDER + 1;
    }
}
