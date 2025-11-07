# Synaxic API - Enterprise Developer Utilities Hub

[![Production Status](https://img.shields.io/badge/Status-Production%20Ready-green)](https://synaxic.skillter.dev)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-brightgreen)](https://spring.io/projects/spring-boot)

**Synaxic API** is a distributed API platform for providing essential developer utilities .

**Live Production**: [https://synaxic.skillter.dev](https://synaxic.skillter.dev)

## What Makes This Different

Synaxic runs on distributed infrastructure with PostgreSQL replication, Redis clustering, and automatic failover. The service handles production workloads with analytics monitoring and built-in scaling.

## Currently implemented features:

### IP Detection & Analysis
- **IP Information**: IP Address, Geolocation, ISP
- **Metadata Inspector**: Useragent, system version, browser identifiers, redacted headers for privacy

### Email Validation & Security
- **Syntax Validation**: RFC-compliant email format checking
- **Disposable Domain Detection**: 10,000+ blocked disposable email domains
- **MX Record Verification**: Real-time DNS checks with Redis caching

### Unit & Conversion APIs
- **Physical Units**: Length, mass, temperature, volume, speed conversions using JSR-385 standards
- **Byte Converter**: SI (kB, MB) vs IEC (KiB, MiB) binary conversions
- **Color Tools**: Conversion between HEX, RGB and HSL with WCAG contrast ratio calculations
- **Deterministic**: Same input = same output. every time

### Authentication & Security
- **OAuth2 Authentication**: Google account login integration
- **API Key System**: SHA-256 hashed keys with prefix based lookup
- **Rate Limiting**: Distributed bucket4j implementation (50k/1k/10k req/hour tiers)
- **GDPR Compliant**: Privacy minded design with legal compliance

## Architecture Highlights

This isn't your typical REST API. Synaxic runs on fancy infrastructure to handle extreme traffic:

- **Multi-Node Setup**: Load distribution between multiple servers
- **Two-Tier Caching**: L1 Caffeine (in ram) + L2 Redis (on disk) for efficient caching
- **Database Replication**: PostgreSQL master-replica configuration for automatic fail over
- **Observability Stack**: Prometheus metrics + Grafana dashboards for monitoring
- **Ran in Containers**: Docker separation for hardened security and quick deployment

## Quick Start

All endpoints work without authentication (with lower rate limit):

```bash
# Get your IP address
curl https://synaxic.skillter.dev/v1/ip

# Validate an email
curl "https://synaxic.skillter.dev/v1/email/validate?email=test@example.com"

# Convert units
curl "https://synaxic.skillter.dev/v1/convert/units?from=mi&to=km&value=5"

# Analyze a request
curl -X POST https://synaxic.skillter.dev/v1/echo \
  -H "Content-Type: application/json" \
  -d '{"test": "data"}'
```

Use an API key for 10 times higher rate limit (**10k per hour**). Get yours for free through the dashboard after OAuth login.

## Performance & Reliability

- **Response Times**: <50ms average (cached), <200ms (uncached) *Will be better once I upgrade the servers*
- **Scalability**: Horizontal scaling with stateless application nodes
- **Rate Limits**: 1,000 req/hour (anonymous API) and 10,000 req/hour (authenticated)

## Tech Stack

- **Backend**: Spring Boot 3.5.4, Java 21, PostgreSQL 16, Redis 7, Docker
- **Caching**: Caffeine (L1) + Redisson (L2)
- **Security**: Spring Security, OAuth2, JWT tokens
- **Monitoring**: Prometheus + Grafana
- **Testing**: Testcontainers, REST Assured, JUnit 5

## For Developers

**Requirements**: install [JDK 21](https://adoptium.net/temurin/releases?version=21&os=any&arch=any) and [Docker](https://www.docker.com/get-started/)

```bash
git clone https://github.com/skillter/synaxicapi.git
cd synaxicapi
docker-compose up -d
./gradlew bootRun --args='--spring.profiles.active=docker'
```

### API Documentation
Interactive docs available at: [https://synaxic.skillter.dev/swagger-ui.html](https://synaxic.skillter.dev/swagger-ui.html)

## Terms

- Open source
- [Fair use](https://synaxic.skillter.dev/fair-use-policy) policy applies (no abuse, be reasonable)
- Terms of Service: [https://synaxic.skillter.dev/terms-of-service]()
- Privacy policy: [https://synaxic.skillter.dev/privacy-policy](https://synaxic.skillter.dev/privacy-policy)

---

**Built for developers who need reliable, scalable APIs without the enterprise price tag.**

*Production URL: [https://synaxic.skillter.dev](https://synaxic.skillter.dev)*