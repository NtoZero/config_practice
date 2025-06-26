package demo.encloaderclient.config;

import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
* <p> </p>
* @author st.lee
* @version 0.1.0
* @since 2025-06-26
 */
@Slf4j
@Configuration
public class JasyptConfig {

    public static final String JASYPT_BEAN_NAME = "bapJasyptStringEncryptor";

    @Bean
    StringEncryptor stringEncryptor(@Value("${jasypt.encryptor.password}") String password){
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(password);
        config.setAlgorithm("PBEWithMD5AndDES");
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.NoIvGenerator");
        config.setStringOutputType("base64");
        encryptor.setConfig(config);

        log.info("{} is created", JASYPT_BEAN_NAME);
        return encryptor;
    }
}
