package demo.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RefreshScope  // 설정 갱신 지원
public class ConfigController {
    
    @Value("${app.config.message:Default Message}")
    private String message;
    
    @Value("${app.config.debug:false}")
    private boolean debug;
    
    @Value("${app.config.version:unknown}")
    private String version;
    
    @Value("${server.port:8080}")
    private int serverPort;
    
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("message", message);
        config.put("debug", debug);
        config.put("version", version);
        config.put("serverPort", serverPort);
        return config;
    }
    
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("message", "Client application is running");
        return status;
    }
}
