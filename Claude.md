# Synaxic API: Consolidated Development & Operations Guide

## 1. Git Policy
**IMPORTANT**: Claude Code MUST NEVER perform git operations (commit, push, PRs, merge). These are manual user actions. Bash tools (`git`, `gh`, `gradlew`) and search queries are permitted.

## 2. Project Overview
**Synaxic API** is a distributed Spring Boot API hub for developer utilities.
*   **Tech Stack**: Spring Boot 3.5.4, Java 21, PostgreSQL, Redis (Redisson), Docker.
*   **Group/Package**: `dev.skillter` / `dev.skillter.synaxic`.
*   **Environment**: Dev on Windows 11 (IntelliJ), production on Linux Debian.

## 3. Architecture
### 3.1. Distributed Design
*   **Main VPS**: Runs primary PostgreSQL & Redis instances.
*   **Replica VPS**: Runs mirrored replicas.
*   **Application Servers**: Multiple stateless Spring Boot app instances.

### 3.2. Tiered Caching (L1/L2)
*   **L1 (Caffeine)**: In-memory, per-instance (500 entries, 10-min TTL).
*   **L2 (Redis)**: Distributed, shared cache.
*   **Invalidation**: Redis pub/sub broadcasts invalidation events to all instances to evict L1 entries, preventing stale reads.
*   **Implementation**: `TieredCacheManager` -> `TieredCache` -> `CacheInvalidationListener`.
*   **Caches (`CacheConfig`)**: `emailValidation` (24h), `geoIp` (1h), `apiKeyByPrefix` (30m), `mxRecords` (24h).

### 3.3. Rate Limiting
*   **Technology**: Bucket4j with Redis backend for distributed limiting.
*   **Limits**:
    *   Anonymous API: 1000 req/hour.
    *   API Key Users: 10000 req/hour.
    *   Static Resources: 50000 req/hour (DDoS protection).
*   **Note**: Counters reset on server restart (acceptable for v1).
*   **Implementation**: `RateLimitFilter` + `RateLimitService`.

### 3.4. Security & Authentication
*   **Onboarding**: Users sign in with Google OAuth2 to get API keys.
*   **Sessions**: Stored in Redis via Spring Session.
*   **API Keys**: `Authorization: ApiKey <key>`. Prefix is `key_live_...`; SHA-256 hash is stored in PostgreSQL.
*   **Auth Toggle**: Endpoints can be configured as public or API-key-required.

## 4. Build, Test & Run Commands
(Requires Java 21 in `PATH` or `JAVA_HOME`)
```bash
# Build project
./gradlew build

# Run all tests
./gradlew test

# Run specific test class/method
./gradlew test --tests "dev.skillter.synaxic.service.EmailValidationServiceTest"
./gradlew test --tests "dev.skillter.synaxic.service.EmailValidationServiceTest.testSpecificMethod"

# Run app locally (dev profile, H2 DB)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Run app with local Docker services (docker profile) - RECOMMENDED FOR DEVELOPMENT
./gradlew bootRun --args='--spring.profiles.active=docker'
```

## 5. Docker
```bash
# Start local dev environment (PostgreSQL + Redis)
docker-compose up -d

# Stop local dev environment
docker-compose down

# Production deployment
docker-compose -f docker-compose.prod.yml up -d
```

## 6. Production Server Management
### 6.1. SSH Access
*   **Command**: `ssh main@45.134.48.99` (private key auth).

### 6.2. ⚠️ Production Safety Rules
1.  **NEVER** run destructive commands (`rm -rf`, `docker rm -f`).
2.  **ALWAYS** check current directory before command execution.
3.  **READ** commands carefully before executing.
4.  **ASK** for confirmation if unsure.
5.  **BACKUP** config files before changing them.
6.  **DO NOT** restart services unnecessarily.

### 6.3. Troubleshooting Commands
```bash
# Check Docker container status
docker ps -a

# Check application logs (last 50 lines)
docker logs synaxic-app-main --tail 50

# Check system resources (disk, memory)
df -h && free -h

# Check application health endpoint
curl -f http://localhost:8080/actuator/health

# Check git status on server
cd ~/SynaxicAPI && git status && git branch

# Test manual deployment script
./scripts/deploy-node.sh

# Restart production services (use with caution)
docker-compose -f docker-compose.prod.yml restart

# View all container logs (last 20 lines)
docker-compose -f docker-compose.prod.yml logs --tail 20
```

### 6.4. Deployment Debugging
*   **Branch Mismatch**: Check `git branch` on VPS; should be `production`.
*   **Docker Build Failures**: Check disk space (`df -h`) and build logs.
*   **Health Check Failures**: Check `docker logs synaxic-app-main` for startup errors (e.g., DB connection).
*   **Config Issues**: Ensure `.env` files are present and `nginx/replica_ips.txt` is correct.

### 6.5. Emergency Procedures
*   **If Deployment Fails**: Check `docker ps -a` to ensure old containers are running. Review logs for errors. Avoid rapid re-deployments.
*   **If Services Are Down**: Check resources (`df -h`, `free -h`). Restart with `docker-compose -f docker-compose.prod.yml up -d`. Monitor with `watch docker ps`.

## 7. Development Details
### 7.1. Configuration & Database
*   **Profiles**: `application-dev` (H2), `application-docker` (localhost containers), `application-prod` (env vars).
*   **DB Migrations**: Flyway handles schema versioning from `src/main/resources/db/migration/`. Files must be named `V1__...`, `V2__...`.

### 7.2. Logging, Errors & Monitoring
*   **Logging**: Structured JSON (Logstash). Redacts headers: `Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key`, `Proxy-Authorization`. No body logging.
*   **Error Handling**: RFC 7807 `application/problem+json` responses via Zalando Problem library.
*   **Monitoring**: Micrometer + Prometheus. Endpoints: `/actuator/health`, `/actuator/prometheus`, `/actuator/metrics`. Visualized in Grafana.

### 7.3. Redis & Dev Tips
*   **Redis Client**: Redisson. Production requires encrypted/authenticated traffic (TLS on port 6380).
*   **API Docs**: Swagger UI at `/swagger-ui.html`, OpenAPI spec at `/v3/api-docs` (Springdoc OpenAPI 2.8.9).
*   **CORS**: Restrictive in production (no wildcard origins), configured in `WebConfig`.
*   **DevTools**: Enabled for fast restarts.
*   **Hot Reload**: The application supports hot-reloading when running with `bootRun`. Changes to Java files, HTML, CSS, and JavaScript are automatically reloaded.
*   **Docker**: Multi-stage build using Eclipse Temurin JRE 21, runs as non-root user (appuser:1001).

### 7.4. Testing
*   **Integration**: Extend `BaseIntegrationTest` which uses **Testcontainers** for isolated PostgreSQL/Redis instances.
*   **Unit**: Use Mockito.
*   **API**: REST Assured is available.
*   **Mocking**: Byte Buddy Agent is configured.

## 8. Core APIs & Implementation
1.  **IP Inspector**: `GET /v1/ip`, `GET /v1/whoami`, `POST /v1/echo`.
    *   *Impl*: `IpController`, `IpInspectorService`, `GeoIpService` (MaxMind).
2.  **Email Validation**: Disposable domain check, syntax validation, optional MX record lookup.
    *   *Impl*: `EmailValidatorController`, `EmailValidationService`, `DnsService` (dnsjava).
3.  **Converters**: Units (JSR-385), Bytes (SI/IEC), Colors (HEX/RGB/HSL, WCAG contrast).
    *   *Impl*: `UnitConverterController`, `ColorConverterController`, `ConversionService`.
4.  **Authentication**: `POST /v1/auth/login`, `POST /v1/auth/logout`, `GET /v1/auth/me`, `POST /v1/auth/api-key`.
    *   *Impl*: `AuthController`, Google OAuth2 integration, API key generation and management.
5.  **Admin & Analytics**: `GET /v1/admin/stats`, `GET /api/stats`.
    *   *Impl*: `AdminController` (authenticated), `StatsController` (public).
6.  **Web Interface**: Static HTML pages for dashboard, analytics, login, and legal pages.
    *   *Impl*: `ViewController` serves static resources from `src/main/resources/static/`.

## 9. Code Structure & Dependencies
```
src/main/java/dev/skillter/synaxic/
├── config/       # Spring configs
├── controller/v1/  # REST controllers
├── controller/   # View controllers, rate limiting
├── service/      # Business logic
├── repository/   # JPA repositories
├── cache/        # Tiered cache impl
├── model/        # DTOs and entities
└── SynaxicApplication.java
src/main/resources/
├── db/migration/ # Flyway migrations
├── static/       # HTML pages and web assets
├── application*.properties
└── GeoLite2-City.mmdb # Downloaded separately
```
*   **Key Libs**: Spring Boot 3.5.4, Springdoc OpenAPI 2.8.9, Redisson 3.51.0, Bucket4j 8.15.0, Caffeine 3.2.2, Indriya 2.2.3, MaxMind GeoIP2 4.3.1, dnsjava 3.6.3, Problem Spring Web 0.29.1, Testcontainers 1.21.3, JWT 0.12.7.