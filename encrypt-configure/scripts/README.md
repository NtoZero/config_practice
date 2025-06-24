# Scripts Directory

This directory contains utility scripts for the Config Server.

## Available Scripts

### start-config-server.sh
Builds and starts the Config Server locally.

```bash
chmod +x scripts/start-config-server.sh
./scripts/start-config-server.sh
```

### test-config-endpoints.sh
Tests all Config Server endpoints. Requires `jq` for JSON formatting.

```bash
chmod +x scripts/test-config-endpoints.sh
./scripts/test-config-endpoints.sh
```

### build-and-run.sh
Builds Docker image and runs the complete stack with Docker Compose.

```bash
chmod +x scripts/build-and-run.sh
./scripts/build-and-run.sh
```

## Prerequisites

- Java 17
- Docker and Docker Compose
- `jq` (for endpoint testing script)
- `curl` (for testing)

## Windows Users

For Windows users, these scripts can be run using Git Bash or WSL.
