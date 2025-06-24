package demo.encryptconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.profiles.active=native",
    "spring.cloud.config.server.native.search-locations=classpath:/config-repo/"
})
class ConfigServerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        // Config Server가 정상적으로 로딩되는지 확인
    }

    @Test
    void healthEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void ordersServiceDevConfig() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/orders-service/dev", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("orders-service");
        assertThat(response.getBody()).contains("Development Environment");
    }

    @Test
    void ordersServiceProdConfig() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/orders-service/prod", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("orders-service");
        assertThat(response.getBody()).contains("Production Environment");
    }

    @Test
    void userServiceConfig() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/user-service/dev", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("user-service");
    }

    @Test
    void applicationDefaultConfig() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/application/default", String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("application");
    }
}
