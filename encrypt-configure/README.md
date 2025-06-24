# Spring Cloud Config Server

Simple Spring Cloud Config Server implementation based on the playbook.

## Quick Start

### 1. Local Development

```bash
# Start Config Server
./gradlew bootRun

# Or use script
chmod +x scripts/start-config-server.sh
./scripts/start-config-server.sh
```

### 2. Test Endpoints

```bash
# Health check
curl http://localhost:8888/actuator/health

# Get orders-service dev configuration
curl http://localhost:8888/orders-service/dev

# Get orders-service prod configuration  
curl http://localhost:8888/orders-service/prod

# Get user-service dev configuration
curl http://localhost:8888/user-service/dev

# Or use test script
chmod +x scripts/test-config-endpoints.sh
./scripts/test-config-endpoints.sh
```

### 3. Docker Deployment

```bash
# Build and run with Docker Compose
chmod +x scripts/build-and-run.sh
./scripts/build-and-run.sh

# Or manually
./gradlew bootJar
docker build -t config-server .
docker-compose up -d
```

## Configuration

### Environment Variables

For production use, set these environment variables:

```bash
# Git repository configuration
export SPRING_CLOUD_CONFIG_SERVER_GIT_URI=https://github.com/your-username/config-repo
export SPRING_CLOUD_CONFIG_SERVER_GIT_USERNAME=your-username
export SPRING_CLOUD_CONFIG_SERVER_GIT_PASSWORD=your-token

# Server configuration
export SERVER_PORT=8888
```

### Application Profiles

- `default`: Uses GitHub repository (requires configuration)
- `test`: Uses local classpath config files for testing

## Client Integration

### Dependencies

Add to your client application's `build.gradle`:

```gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-config'
    implementation 'org.springframework.cloud:spring-cloud-starter-bootstrap'
}
```

### Configuration

Add to `application.yml`:

```yaml
spring:
  application:
    name: your-service-name
  profiles:
    active: dev
  config:
    import: "configserver:http://localhost:8888"
  cloud:
    config:
      uri: http://localhost:8888
      fail-fast: true
```

### Usage Example

```java
@RestController
@RefreshScope
public class ConfigController {
    
    @Value("${app.config.message:Default}")
    private String message;
    
    @GetMapping("/config")
    public String getConfig() {
        return message;
    }
}
```

## Available Endpoints

| URL | Description |
|-----|-------------|
| `http://localhost:8888/{service}/{profile}` | Get service configuration |
| `http://localhost:8888/actuator/health` | Health check |
| `http://localhost:8888/actuator/info` | Server information |
| `http://localhost:8888/actuator/env` | Environment properties |

## Configuration Files Structure

```
config-repo/
├── application.yml              # Common configuration
├── application-dev.yml          # Development common
├── application-prod.yml         # Production common
├── orders-service.yml           # Orders service default
├── orders-service-dev.yml       # Orders service dev
├── orders-service-prod.yml      # Orders service prod
└── user-service.yml            # User service default
```

## Testing

```bash
# Run unit tests
./gradlew test

# Run integration tests
./gradlew integrationTest
```

## Monitoring

Health check endpoint provides detailed information:

```bash
curl http://localhost:8888/actuator/health
```

## Troubleshooting

### Common Issues

1. **Config Server won't start**
   - Check Git repository URL and credentials
   - Verify network connectivity

2. **Client can't connect**
   - Ensure Config Server is running on port 8888
   - Check client configuration

3. **Configuration not updating**
   - Use `/actuator/refresh` endpoint on client
   - Check Git repository for latest changes

### Logs

Enable debug logging:

```yaml
logging:
  level:
    org.springframework.cloud.config: DEBUG
```

## Next Steps

1. Set up Git repository with your configuration files
2. Configure security (Basic Auth or OAuth2)
3. Add encryption for sensitive data
4. Set up webhooks for automatic refresh
5. Implement high availability setup

For detailed implementation guide, see [docs/simple_config_server_playbook.md](docs/simple_config_server_playbook.md)
