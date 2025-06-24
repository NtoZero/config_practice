package demo.encryptconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.profiles.active=native", 
    "spring.cloud.config.server.native.search-locations=classpath:/config-repo/"
})
class EncryptConfigureApplicationTests {

    @Test
    void contextLoads() {
    }

}
