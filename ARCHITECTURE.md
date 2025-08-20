
For my project I want to use:

1. Tech stack and features:
- Spring boot
- Spring Security (for API keys)
- Spring Cache (with Redis and Caffeine)
- Docker (for easy deployment)
Caching: Caffeine (in-memory) + Redis
Rate limiting: Bucket4j filter; per-IP + API key support. Add user-friendly 429 body.
Versioned endpoints (/v1), semantic versioning in OpenAPI.
Create a sleek documentation site: examples in docs for each endpoint (curl + JS + Java).
Add a simple analytics dashboard
Live status page (uptime, latency, last data refresh).
Abuse controls: rate limits, token buckets, and fair use policy.
Implementation for per API toggleable key authentication, and you get the key after signing in with a google account on the website (OAuth integration)
Comprehensive error handling
Request/Response logging
CORS support
Health check endpoints
JUnit tests
The project is named Synaxic API.
rootProject.name = 'synaxic'
  group = 'dev.skillter'
  version = '0.0.1-SNAPSHOT'
  description = 'Synaxic is a innovative API Hub for developers'
package to the main application class is dev.skillter.synaxic

2. The actual APIs we want to implement, host and serve:
- "My IP" + lightweight request inspector api, don’t log sensitive data.
    Endpoints:
    GET /ip → { ip, ipVersion }
    GET /whoami → { ip, headers: {...}, ua: ... }
    POST /echo → { size, sha256, contentType }
- Disposable email detection, Email syntax validation

- Unit, byte-size, color conversions.  Deterministic converters: temperature, distance, storage units; color HEX/RGB/HSL; WCAG contrast ratio.
eg. Endpoints:
    GET /convert/units?from=mi&to=km&value=3.1
    GET /convert/bytes?from=MiB&to=MB&value=128
    GET /color/convert?from=hex&to=hsl&value=%23ffcc00
    GET /color/contrast?fg=%23000000&bg=%23ffffff

3. Architecture Considerations:

# Suggested project structure:
    request-inspector-api/
    ├── api-gateway/          # Rate limiting, auth
    ├── core-services/        # Your 3 APIs
    ├── auth-service/         # Google OAuth handling
    ├── analytics-service/    # Metrics collection
    └── docker-compose.yml    # Local dev environment

# Simplification Options
To reduce complexity without losing impact:
    - Analytics: Consider using Prometheus + Grafana instead of building custom dashboard
    - Redis: Start with just Caffeine, add Redis when you need distributed caching
    - Status Page: Use UptimeRobot or BetterUptime (free tiers) initially

# Rate Limiting Strategy
    - Anonymous users: 100 req/hour per core API and adjustable per API
    - API key users: 1000 req/hour per core API and adjustable per API

# API Key + Google OAuth Flow
    1. User visits your site
    2. "Sign in with Google" → OAuth flow
    3. Generate unique API key tied to Google ID
    4. Store in DB with usage limits
    5. User dashboard shows key + usage stats

# Documentation Site Structure
    docs.skillter.dev/
    ├── Getting Started
    ├── Authentication (Google OAuth flow)
    ├── Rate Limits
    ├── API Reference
    │   ├── IP Inspector
    │   ├── Email Validator  
    │   └── Unit Converter
    ├── Code Examples (curl, JS, Java, Python)
    ├── Status Page (embed)
    └── Pricing/Fair Use

# Monitoring & Analytics Must-Haves
    - Request count by endpoint
    - Response time percentiles (p50, p95, p99)
    - Error rates
    - API key usage breakdown
    - Geographic distribution

# API key format and storage
    - Return a token once, store only a hash (e.g., SHA-256) + prefix for lookup: key_live_abc123… (prefix helps support lookup and UX).
    - Accept in header: Authorization: ApiKey <key>

# Persistent store for API keys
    - Add Postgres (or SQLite to start) to store users, hashed API keys, quotas, and usage. 

# Per-endpoint auth toggle:
    - Use Spring Security matchers and a custom ApiKeyAuthFilter. Make open endpoints (e.g., /v1/ip, /v1/whoami) configurable via properties so you can flip to “API-key required” without redeploy.

# Rate limiting dimensions:
    - Apply per-IP for anonymous calls and per-API-key when present (prefer key over IP if both exist).
    - Return X-RateLimit-Limit, X-RateLimit-Remaining, Retry-After in 429 responses.
# Logging and privacy:
    - Log structured JSON with a requestId, path, status, duration, size; do not log bodies by default.
    - Redact headers: Authorization, Cookie, Set-Cookie, X-Api-Key, Proxy-Authorization.
# Error model:
    - Standardize on RFC 7807 problem+json
    - Configure Spring Boot to default to RFC 7807
    - Validate outgoing error responses against a JSON schema or type definitions.
    - Document the model in the docs, including example payloads.
# CORS:
Allow GET, POST, HEAD, OPTIONS; keep origins restricted (no wildcard) in prod.
# Docs:
    springdoc-openapi + Swagger UI for live; static docs site (Docusaurus/Redocly) for guides and copy-paste examples (curl, JS, Java).
# Legal/ops:
    Publish a Fair Use Policy and Privacy Policy (you’ll be collecting IPs and usage).
    Cloudflare in front for TLS, caching GETs, and extra rate limiting if needed.


4. API specifics and suggestions

# My IP + request inspector
    Keep these open initially; allow optional API key so users can test both flows.
    Redact sensitive headers in /whoami; don’t echo request body anywhere else.
# Disposable email detection + email syntax validation
    Data sources: aggregate public lists (e.g., ivolo/disposable-email-domains, andrewsdisposable, fzipi/disposable-domains). Deduplicate; store lowercased eTLD+1 suffixes.
    Matching: suffix match against domain and subdomains; handle IDN/punycode.
    Optional DNS checks: cache MX existence in Redis with short TTL; keep requests fast and degrade gracefully if DNS slow.
# Unit, byte-size, color conversions
    Units:
        Start with a curated set (length, mass, temperature, volume, speed) to avoid ambiguity.
        Consider JSR-385 (Indriya) or keep a small deterministic table and tests.
        GET /v1/convert/units?from=mi&to=km&value=3.1 → { from, to, value, result, precision }
    Byte sizes:
        Support SI (kB, MB) and IEC (KiB, MiB) and document both.
        GET /v1/convert/bytes?from=MiB&to=MB&value=128 → { result, ratio }
    Colors:
        HEX ⇄ RGB ⇄ HSL, plus WCAG contrast.
        GET /v1/color/convert?from=hex&to=hsl&value=%23ffcc00 → { h:…, s:…, l:… }
        GET /v1/color/contrast?fg=%23000000&bg=%23ffffff → { ratio: 21, aa: true, aaa: true }
        Watch out for rounding; add unit tests against known fixtures.

5. The dependencies we are using. Utilize them.
Spring Web - Build web, including RESTful, applications using Spring MVC. Uses Apache Tomcat as the default embedded container.
Spring Security - Highly customizable authentication and access-control framework for Spring applications.
OAuth2 Client - Spring Boot integration for Spring Security's OAuth2/OpenID Connect client features.
Validation - Bean Validation with Hibernate validator.
Spring Cache Abstraction - Provides cache-related operations, such as the ability to update the content of the cache, but does not provide the actual data store.
Spring Data Redis (Access+Driver) - Advanced and thread-safe Java Redis client for synchronous, asynchronous, and reactive usage. Supports Cluster, Sentinel, Pipelining, Auto-Reconnect, Codecs and much more.
Spring Data JPA - Persist data in SQL stores with Java Persistence API using Spring Data and Hibernate.
PostgreSQL Driver - A JDBC and R2DBC driver that allows Java programs to connect to a PostgreSQL database using standard, database independent Java code.
Spring Boot Actuator - Supports built in (or custom) endpoints that let you monitor and manage your application - such as application health, metrics, sessions, etc.
Lombok Developer - Java annotation library which helps to reduce boilerplate code.
Spring Boot DevTools Developer - Provides fast application restarts, LiveReload, and configurations for enhanced development experience.
Testcontainers - Provide lightweight, throwaway instances of common databases, Selenium web browsers, or anything else that can run in a Docker container.
Flyway Migration - Version control for your database so you can migrate from any version (incl. an empty database) to the latest version of the schema.
Spring Configuration Processor Developer - Generate metadata for developers to offer contextual help and "code completion" when working with custom configuration keys (ex.application.properties/.yml files).
H2 Database - Provides a fast in-memory database that supports JDBC API and R2DBC access, with a small (2mb) footprint. Supports embedded and server modes as well as a browser based console application.


Springdoc OpenAPI Starter WebMVC UI - Automates the generation of API documentation for Spring Boot projects.
Caffeine - A high-performance, near-optimal caching library for Java.
Bucket4j - A Java implementation of the token bucket algorithm for rate limiting.
Micrometer Registry Prometheus - A Micrometer registry implementation for Prometheus.
Commons Validator - Provides the building blocks for both client-side and server-side data validation.
dnsjava - An implementation of DNS in Java that supports all defined record types.
Google Guava - A set of core Java libraries from Google that includes new collection types, immutable collections, a graph library, and utilities for concurrency, I/O, hashing, primitives, strings, and more.
Indriya - The reference implementation of the Units of Measurement API (JSR 385).
REST Assured - A Java DSL for easy testing of REST services.
Testcontainers Redis - Provides a lightweight, throwaway instance of a Redis container for testing.
Problem Spring Web - A library that makes it easy to produce application/problem+json responses from a Spring application.
Logstash Logback Encoder - Provides Logback encoders, layouts, and appenders to log in JSON and other formats supported by Jackson.
JJWT API - A Java library for creating and parsing JSON Web Tokens (JWTs).
MaxMind GeoIP2 - A Java API for the GeoIP2 web services and databases.
Commons Pool 2 - An object-pooling library for Java.
Jackson Datatype JSR310 - An add-on module for the Jackson JSON processor to support the Java 8 Date and Time API (JSR-310) data types.
Apache Commons Lang - A package of Java utility classes for the classes that are in java.lang's hierarchy, or are considered to be so standard as to justify existence in java.lang
SpringDoc OpenAPI - Automated JSON API documentation for Spring Boot projects using OpenAPI 3.0 specification with integrated Swagger UI for interactive API exploration.
Caffeine Cache - High-performance, near-optimal caching library for Java providing an in-memory cache with a modern API inspired by Google Guava.
Bucket4j Rate Limiting - Java rate-limiting library based on token-bucket algorithm for implementing API throttling and request rate limiting with JCache integration.
JCache API - Standard Java caching API (JSR-107) that provides a common way to use caching in Java applications.
Micrometer Prometheus - Application metrics facade that provides Prometheus registry for collecting and exposing metrics in Prometheus format for monitoring.
Commons Validator - Provides validation framework with common validation routines such as email, credit card, and other format validations.
DNSJava - Implementation of DNS in Java supporting all defined record types, DNSSEC, and both synchronous and asynchronous resolution.
Google Guava - Core libraries from Google including collections, caching, primitives support, concurrency libraries, common annotations, string processing, and I/O utilities.
Indriya Units - Reference implementation of JSR 385 (Units of Measurement API) for handling physical quantities and their units with type safety.
REST Assured Testing - Java DSL for easy testing of REST services, providing a domain-specific language for writing powerful, readable API tests.
Testcontainers Redis Testing - Provides lightweight, throwaway Redis instances for integration testing using Docker containers.
Problem Spring Web - Implementation of RFC 7807 (Problem Details for HTTP APIs) for standardized error responses in Spring applications.
Logstash Logback Encoder - Logback encoder for outputting log events in JSON format, optimized for log aggregation systems like Logstash and ELK stack.
JJWT - Java library for creating and verifying JSON Web Tokens (JWTs) for secure API authentication and authorization.
MaxMind GeoIP2 - Java API for the GeoIP2 and GeoLite2 web services and databases for IP geolocation and geographical data lookup.
Apache Commons Pool - Object pooling library providing generic object pool implementations, commonly used for database and Redis connection pooling.
Jackson JSR310 - Jackson module for serializing and deserializing Java 8+ date and time types (java.time.*) to and from JSON.
Apache Commons Lang - Provides helper utilities for the java.lang API, including string manipulation, basic numerical methods, object reflection, and concurrency utilities.

More specific format of the Dependencies for you to use, pay attention to the library versions:
dependencies {
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'org.springframework.boot:spring-boot-starter-cache'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'
compileOnly 'org.projectlombok:lombok'
developmentOnly 'org.springframework.boot:spring-boot-devtools'
runtimeOnly 'com.h2database:h2'
runtimeOnly 'org.postgresql:postgresql'
annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'
annotationProcessor 'org.projectlombok:lombok'
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.springframework.security:spring-security-test'
testImplementation 'org.testcontainers:junit-jupiter'
testImplementation 'org.testcontainers:postgresql'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
// API Documentation (Swagger UI + OpenAPI)
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9'
// Caffeine for in-memory caching
implementation 'com.github.ben-manes.caffeine:caffeine:3.2.2'
// Bucket4j for rate limiting
implementation 'com.bucket4j:bucket4j_jdk17-core:8.14.0'
implementation 'com.bucket4j:bucket4j_jdk17-jcache:8.14.0'
implementation 'javax.cache:cache-api:1.1.1'
// Metrics for Prometheus
implementation 'io.micrometer:micrometer-registry-prometheus:1.15.3'
// Email validation utilities
implementation 'commons-validator:commons-validator:1.10.0'
// DNS querys
implementation 'dnsjava:dnsjava:3.6.3'
// Google Guava utilities
implementation 'com.google.guava:guava:33.4.8-jre'
// Units of Measurement API (for unit conversions)
implementation 'tech.units:indriya:2.2.3'
// Enhanced testing capabilities
testImplementation 'io.rest-assured:rest-assured:5.5.6'
testImplementation 'com.redis:testcontainers-redis:2.2.4'
// RFC 7807 problem+json for standardized error responses
implementation 'org.zalando:problem-spring-web:0.29.1'
// For proper request/response logging with JSON format
implementation 'net.logstash.logback:logstash-logback-encoder:8.1'
// For secure API key generation and validation
implementation 'io.jsonwebtoken:jjwt-api:0.12.7'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.7'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.7'
// IP Geolocation
implementation 'com.maxmind.geoip2:geoip2:4.3.1'
// For Redis connection pooling (recommended for production)
implementation 'org.apache.commons:commons-pool2:2.12.1'
// For better Jackson JSON handling with Java 8+ time types
implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2'
// Useful string/collection utilities
implementation 'org.apache.commons:commons-lang3:3.18.0'
}
plugins {
id 'java'
id 'org.springframework.boot' version '3.5.4'
id 'io.spring.dependency-management' version '1.1.7'
}

Perfect — since **Synaxic API will run on a single machine** (not distributed, not horizontally scalable), we can simplify several architectural decisions while keeping all the core functionality intact.

Here’s your **updated roadmap and directory structure**, optimized for **single-node deployment** — no need for Redis clustering, distributed locks, or complex service separation. Everything runs in one Spring Boot app.

---

## ✅ THE ROADMAP


### 🟢 Phase 0: Project Setup + Core Structure

---

### 🟢 Phase 1: “My IP” + Request Inspector APIs
✅ Still the perfect first feature. Lightweight, safe, high utility.

---

### 🟡 Phase 2: API Key Auth + Google OAuth Flow

    - ✅ No need to worry about session replication or distributed user state.
    - ✅ Store API keys in local PostgreSQL
    - ✅ Session affinity is automatic — you’re on one machine!
    - ✅ Google OAuth callback can directly create/update user in DB — no sync needed.

---

### 🟡 Phase 3: Rate Limiting with Bucket4j

- Use **Caffeine-backed JCache** only — **no Redis needed**.
- Bucket4j + Caffeine is perfect for single-node rate limiting.
- Since all requests hit one JVM, per-IP and per-API-key buckets are accurate and consistent.
- ✅ Skip Redis integration entirely unless you later want persistence across restarts (optional).

> ⚠️ If you restart the server, rate limit counters reset — acceptable for v1. Document it.

---

### 🟡 Phase 4: Unit/Byte/Color Converters
✅ All conversions are deterministic and stateless

---

### 🟡 Phase 5: Disposable Email Detection

- Load disposable domain list into **in-memory Guava BloomFilter or HashSet** at startup.
- Skip Redis caching for MX lookups — just use **local Caffeine cache** with TTL.
- DNS lookups (if enabled) are cached locally → fast, no network overhead.

> 💡 Optional: Warm up cache on startup with top 1000 domains.

---

### 🔵 Phase 6: Observability & Analytics 

- Still use **Micrometer + Prometheus** — single node doesn’t mean no metrics!
- Expose `/actuator/prometheus` → scrape with local Prometheus via `docker-compose`.
- Grafana dashboards work exactly the same.
- Add simple **in-memory counters** for key usage, errors, geo-stats — no need for external TSDB unless you want long-term retention.

> 💡 Optional: Log analytics to file or stdout → ship to Loki/ELK later if needed.

---

### 🔵 Phase 7: Documentation Site + Examples
✅ Docs are static — serve them separately or embed Swagger UI.

---

### ⚫ Phase 8: Redis? → ❌ SKIP ENTIRELY (Unless You Want Persistence)

> 🚫 **No Redis required** for single-node deployment.

- Caching: Use **Caffeine** for everything — email MX, expensive calcs, rate limit state.
- Persistence across restarts? Not needed for v1. If you want it later:
    - Serialize caches to disk on shutdown (advanced).
    - Or add Redis then — but it’s optional.

> ✅ You save complexity, memory, and ops overhead.

---

### ⚫ Phase 9: Abuse Controls + Dashboard

- Admin dashboard can query local DB + in-memory metrics → real-time stats.
- Suspensions, overrides, quota edits → all local, atomic, simple.

---

## 📁 DIRECTORY STRUCTURE

```
synaxic/
├── src/
│   └── main/
│       ├── java/
│       │   └── dev/
│       │       └── skillter/
│       │           └── synaxic/
│       │               ├── SynaxicApplication.java
│       │               │
│       │               ├── config/
│       │               │   ├── WebConfig.java
│       │               │   ├── SecurityConfig.java
│       │               │   ├── CacheConfig.java          // Caffeine only
│       │               │   ├── RateLimitConfig.java       // Bucket4j + Caffeine
│       │               │   └── OpenApiConfig.java
│       │               │
│       │               ├── controller/
│       │               │   └── v1/
│       │               │       ├── IpController.java
│       │               │       ├── EchoController.java
│       │               │       ├── UnitConverterController.java
│       │               │       ├── ColorConverterController.java
│       │               │       ├── EmailValidatorController.java
│       │               │       └── AuthController.java
│       │               │
│       │               ├── service/
│       │               │   ├── RateLimitService.java
│       │               │   ├── ApiKeyService.java
│       │               │   ├── EmailValidationService.java
│       │               │   ├── ConversionService.java
│       │               │   └── GeoIpService.java
│       │               │
│       │               ├── security/
│       │               │   ├── ApiKeyAuthFilter.java
│       │               │   └── RateLimitFilter.java
│       │               │
│       │               ├── model/
│       │               │   ├── entity/
│       │               │   │   ├── User.java
│       │               │   │   └── ApiKey.java
│       │               │   ├── dto/
│       │               │   │   └── ... (Response/Request DTOs)
│       │               │   └── exception/
│       │               │       └── ApiException.java
│       │               │
│       │               └── util/
│       │                   ├── HeaderRedactor.java
│       │                   ├── KeyGenerator.java
│       │                   ├── DomainSuffixMatcher.java
│       │                   └── LocalCacheUtil.java
│       │
│       └── resources/
│           ├── application.properties       
│           ├── application-dev.properties  
│           ├── application-prod.properties  
│           │
│           ├── static/                       ← For CSS/JS/images (future dashboard/docs assets)
│           ├── templates/                    ← For Thymeleaf (Google login page, admin UI)
│           │
│           ├── db/
│           │   ├──migration/                 ← Rename files to Flyway format!
│           │   └── V1__init.sql              ← e.g., create users, api_keys tables
│           │
│           ├── disposable-domains.txt        ← Embedded list of disposable domains
│           └── GeoLite2-City.mmdb            ← MaxMind DB file (download once, commit or .gitignore)
│
├── docker-compose.yml
├── build.gradle
└── README.md
```

