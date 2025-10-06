
Details of the project:

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
  description = 'Synaxic is a innovative API Hub for developers'
package to the main application class is dev.skillter.synaxic
Developing on JDK 21
Targeting Java 21
I'm using Intellij IDEA
Developing on Windows 11, but the server will be hosted on Linux Debian for production.
We want to use Redisson instead of Lettuce for Redis integration

The traffic of Redis between production servers needs to be encrypted and authenticated.
The production servers are gonna be hosted on 2 or more VPS servers. The main VPS server will run the shared Redis instance and the shared PostgreSQL. One of other VPS servers will run a mirrored replica Redis and mirrored replica PostgreSQL, just how industry standards are.


`application.properties` contains the base configuration shared across all environments, such as port numbers and API documentation settings.
`application-dev.properties` is used for local development without Docker, configuring an in-memory H2 database for quick startup.
`application-docker.properties` is for local development against services running in Docker, connecting to PostgreSQL and Redis on `localhost`.
`application-prod.properties` is for the final production deployment, overriding settings to use environment variables for database and Redis connections within the Docker network.

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
Gradle: ch.qos.logback:logback-classic:1.5.18
Gradle: ch.qos.logback:logback-core:1.5.18
Gradle: com.bucket4j:bucket4j_jdk17-core:8.15.0
Gradle: com.bucket4j:bucket4j_jdk17-redis-common:8.15.0
Gradle: com.bucket4j:bucket4j_jdk17-redisson:8.15.0
Gradle: com.esotericsoftware:kryo:5.6.2
Gradle: com.esotericsoftware:minlog:1.3.1
Gradle: com.esotericsoftware:reflectasm:1.11.9
Gradle: com.fasterxml.jackson.core:jackson-annotations:2.19.2
Gradle: com.fasterxml.jackson.core:jackson-core:2.19.2
Gradle: com.fasterxml.jackson.core:jackson-databind:2.19.2
Gradle: com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.19.2
Gradle: com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.2
Gradle: com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.19.2
Gradle: com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2
Gradle: com.fasterxml.jackson.module:jackson-module-parameter-names:2.19.2
Gradle: com.fasterxml:classmate:1.7.0
Gradle: com.github.ben-manes.caffeine:caffeine:3.2.2
Gradle: com.github.docker-java:docker-java-api:3.4.2
Gradle: com.github.docker-java:docker-java-transport-zerodep:3.4.2
Gradle: com.github.docker-java:docker-java-transport:3.4.2
Gradle: com.github.stephenc.jcip:jcip-annotations:1.0-1
Gradle: com.google.code.findbugs:jsr305:3.0.2
Gradle: com.google.errorprone:error_prone_annotations:2.40.0
Gradle: com.google.guava:failureaccess:1.0.3
Gradle: com.google.guava:guava:33.4.8-jre
Gradle: com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava
Gradle: com.google.j2objc:j2objc-annotations:3.0.0
Gradle: com.h2database:h2:2.3.232
Gradle: com.jayway.jsonpath:json-path:2.9.0
Gradle: com.maxmind.db:maxmind-db:3.2.0
Gradle: com.maxmind.geoip2:geoip2:4.3.1
Gradle: com.nimbusds:content-type:2.2
Gradle: com.nimbusds:lang-tag:1.7
Gradle: com.nimbusds:nimbus-jose-jwt:9.37.3
Gradle: com.nimbusds:oauth2-oidc-sdk:9.43.6
Gradle: com.redis:testcontainers-redis-common:2.2.4
Gradle: com.redis:testcontainers-redis:2.2.4
Gradle: com.sun.istack:istack-commons-runtime:4.1.2
Gradle: com.vaadin.external.google:android-json:0.0.20131108.vaadin1
Gradle: com.zaxxer:HikariCP:6.3.1
Gradle: commons-beanutils:commons-beanutils:1.11.0
Gradle: commons-codec:commons-codec:1.18.0
Gradle: commons-collections:commons-collections:3.2.2
Gradle: commons-digester:commons-digester:2.1
Gradle: commons-validator:commons-validator:1.10.0
Gradle: dnsjava:dnsjava:3.6.3
Gradle: io.jsonwebtoken:jjwt-api:0.12.7
Gradle: io.jsonwebtoken:jjwt-impl:0.12.7
Gradle: io.jsonwebtoken:jjwt-jackson:0.12.7
Gradle: io.lettuce:lettuce-core:6.6.0.RELEASE
Gradle: io.micrometer:micrometer-commons:1.15.2
Gradle: io.micrometer:micrometer-core:1.15.2
Gradle: io.micrometer:micrometer-jakarta9:1.15.2
Gradle: io.micrometer:micrometer-observation:1.15.2
Gradle: io.micrometer:micrometer-registry-prometheus:1.15.3
Gradle: io.netty:netty-buffer:4.1.123.Final
Gradle: io.netty:netty-codec-dns:4.1.123.Final
Gradle: io.netty:netty-codec:4.1.123.Final
Gradle: io.netty:netty-common:4.1.123.Final
Gradle: io.netty:netty-handler:4.1.123.Final
Gradle: io.netty:netty-resolver-dns:4.1.123.Final
Gradle: io.netty:netty-resolver:4.1.123.Final
Gradle: io.netty:netty-transport-native-unix-common:4.1.123.Final
Gradle: io.netty:netty-transport:4.1.123.Final
Gradle: io.projectreactor:reactor-core:3.7.8
Gradle: io.prometheus:prometheus-metrics-config:1.3.10
Gradle: io.prometheus:prometheus-metrics-core:1.3.10
Gradle: io.prometheus:prometheus-metrics-exposition-formats:1.3.10
Gradle: io.prometheus:prometheus-metrics-exposition-textformats:1.3.10
Gradle: io.prometheus:prometheus-metrics-model:1.3.10
Gradle: io.prometheus:prometheus-metrics-tracer-common:1.3.10
Gradle: io.reactivex.rxjava3:rxjava:3.1.11
Gradle: io.rest-assured:json-path:5.5.5
Gradle: io.rest-assured:rest-assured-common:5.5.5
Gradle: io.rest-assured:rest-assured:5.5.6
Gradle: io.rest-assured:xml-path:5.5.5
Gradle: io.smallrye:jandex:3.2.0
Gradle: io.swagger.core.v3:swagger-annotations-jakarta:2.2.30
Gradle: io.swagger.core.v3:swagger-core-jakarta:2.2.30
Gradle: io.swagger.core.v3:swagger-models-jakarta:2.2.30
Gradle: jakarta.activation:jakarta.activation-api:2.1.3
Gradle: jakarta.annotation:jakarta.annotation-api:2.1.1
Gradle: jakarta.inject:jakarta.inject-api:2.0.1
Gradle: jakarta.persistence:jakarta.persistence-api:3.1.0
Gradle: jakarta.transaction:jakarta.transaction-api:2.0.1
Gradle: jakarta.validation:jakarta.validation-api:3.0.2
Gradle: jakarta.xml.bind:jakarta.xml.bind-api:4.0.2
Gradle: javax.cache:cache-api:1.1.1
Gradle: javax.measure:unit-api:2.2
Gradle: junit:junit:4.13.2
Gradle: net.bytebuddy:byte-buddy-agent:1.17.6
Gradle: net.bytebuddy:byte-buddy:1.17.6
Gradle: net.java.dev.jna:jna:5.13.0
Gradle: net.logstash.logback:logstash-logback-encoder:8.1
Gradle: net.minidev:accessors-smart:2.5.2
Gradle: net.minidev:json-smart:2.5.2
Gradle: org.antlr:antlr4-runtime:4.13.0
Gradle: org.apache.commons:commons-compress:1.24.0
Gradle: org.apache.commons:commons-lang3:3.18.0
Gradle: org.apache.commons:commons-pool2:2.12.1
Gradle: org.apache.groovy:groovy-json:4.0.28
Gradle: org.apache.groovy:groovy-xml:4.0.28
Gradle: org.apache.groovy:groovy:4.0.28
Gradle: org.apache.httpcomponents:httpclient:4.5.13
Gradle: org.apache.httpcomponents:httpcore:4.4.16
Gradle: org.apache.httpcomponents:httpmime:4.5.13
Gradle: org.apache.logging.log4j:log4j-api:2.24.3
Gradle: org.apache.logging.log4j:log4j-to-slf4j:2.24.3
Gradle: org.apache.tomcat.embed:tomcat-embed-core:10.1.43
Gradle: org.apache.tomcat.embed:tomcat-embed-el:10.1.43
Gradle: org.apache.tomcat.embed:tomcat-embed-websocket:10.1.43
Gradle: org.apiguardian:apiguardian-api:1.1.2
Gradle: org.aspectj:aspectjweaver:1.9.24
Gradle: org.assertj:assertj-core:3.27.3
Gradle: org.awaitility:awaitility:4.2.2
Gradle: org.ccil.cowan.tagsoup:tagsoup:1.2.1
Gradle: org.checkerframework:checker-qual:3.49.3
Gradle: org.eclipse.angus:angus-activation:2.0.2
Gradle: org.flywaydb:flyway-core:11.7.2
Gradle: org.flywaydb:flyway-database-postgresql:11.7.2
Gradle: org.glassfish.jaxb:jaxb-core:4.0.5
Gradle: org.glassfish.jaxb:jaxb-runtime:4.0.5
Gradle: org.glassfish.jaxb:txw2:4.0.5
Gradle: org.hamcrest:hamcrest-core:3.0
Gradle: org.hamcrest:hamcrest:3.0
Gradle: org.hdrhistogram:HdrHistogram:2.2.2
Gradle: org.hibernate.common:hibernate-commons-annotations:7.0.3.Final
Gradle: org.hibernate.orm:hibernate-core:6.6.22.Final
Gradle: org.hibernate.validator:hibernate-validator:8.0.2.Final
Gradle: org.jboss.logging:jboss-logging:3.6.1.Final
Gradle: org.jetbrains:annotations:17.0.0
Gradle: org.jodd:jodd-util:6.3.0
Gradle: org.jspecify:jspecify:1.0.0
Gradle: org.junit.jupiter:junit-jupiter-api:5.12.2
Gradle: org.junit.jupiter:junit-jupiter-engine:5.12.2
Gradle: org.junit.jupiter:junit-jupiter-params:5.12.2
Gradle: org.junit.jupiter:junit-jupiter:5.12.2
Gradle: org.junit.platform:junit-platform-commons:1.12.2
Gradle: org.junit.platform:junit-platform-engine:1.12.2
Gradle: org.junit.platform:junit-platform-launcher:1.12.2
Gradle: org.latencyutils:LatencyUtils:2.0.3
Gradle: org.mockito:mockito-core:5.17.0
Gradle: org.mockito:mockito-junit-jupiter:5.17.0
Gradle: org.objenesis:objenesis:3.4
Gradle: org.opentest4j:opentest4j:1.3.0
Gradle: org.ow2.asm:asm:9.7.1
Gradle: org.postgresql:postgresql:42.7.7
Gradle: org.projectlombok:lombok:1.18.38
Gradle: org.reactivestreams:reactive-streams:1.0.4
Gradle: org.redisson:redisson-spring-boot-starter:3.51.0
Gradle: org.redisson:redisson-spring-data-35:3.51.0
Gradle: org.redisson:redisson:3.51.0
Gradle: org.rnorth.duct-tape:duct-tape:1.0.8
Gradle: org.skyscreamer:jsonassert:1.5.3
Gradle: org.slf4j:jul-to-slf4j:2.0.17
Gradle: org.slf4j:slf4j-api:2.0.17
Gradle: org.springdoc:springdoc-openapi-starter-common:2.8.9
Gradle: org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.9
Gradle: org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9
Gradle: org.springframework.boot:spring-boot-actuator-autoconfigure:3.5.4
Gradle: org.springframework.boot:spring-boot-actuator:3.5.4
Gradle: org.springframework.boot:spring-boot-autoconfigure:3.5.4
Gradle: org.springframework.boot:spring-boot-configuration-processor:3.5.4
Gradle: org.springframework.boot:spring-boot-devtools:3.5.4
Gradle: org.springframework.boot:spring-boot-starter-actuator:3.5.4
Gradle: org.springframework.boot:spring-boot-starter-cache:3.5.4
Gradle: org.springframework.boot:spring-boot-starter-data-jpa:3.5.4
Gradle: org.springframework.boot:spring-boot-starter-data-redis:3.5.4
Gradle: org.springframework.boot:spring-boot-starter-jdbc:3.5.4
Gradle: org.springframework.boot:spring-boot-starter-json:3.5.4
Gradle: org.springframework.boot:spring-boot-starter-logging:3.5.4
Gradle: org.springframework.boot:spring-boot-starter-oauth2-client:3.5.4
Gradle: org.springframework.boot:spring-boot-starter-security:3.5.4
Gradle: org.springframework.boot:spring-boot-starter-test:3.5.4
Gradle: org.springframework.boot:spring-boot-starter-tomcat:3.5.4
Gradle: org.springframework.boot:spring-boot-starter-validation:3.5.4
Gradle: org.springframework.boot:spring-boot-starter-web:3.5.4
Gradle: org.springframework.boot:spring-boot-starter:3.5.4
Gradle: org.springframework.boot:spring-boot-test-autoconfigure:3.5.4
Gradle: org.springframework.boot:spring-boot-test:3.5.4
Gradle: org.springframework.boot:spring-boot-testcontainers:3.5.4
Gradle: org.springframework.boot:spring-boot:3.5.4
Gradle: org.springframework.data:spring-data-commons:3.5.2
Gradle: org.springframework.data:spring-data-jpa:3.5.2
Gradle: org.springframework.data:spring-data-keyvalue:3.5.2
Gradle: org.springframework.data:spring-data-redis:3.5.2
Gradle: org.springframework.security:spring-security-config:6.5.2
Gradle: org.springframework.security:spring-security-core:6.5.2
Gradle: org.springframework.security:spring-security-crypto:6.5.2
Gradle: org.springframework.security:spring-security-oauth2-client:6.5.2
Gradle: org.springframework.security:spring-security-oauth2-core:6.5.2
Gradle: org.springframework.security:spring-security-oauth2-jose:6.5.2
Gradle: org.springframework.security:spring-security-test:6.5.2
Gradle: org.springframework.security:spring-security-web:6.5.2
Gradle: org.springframework.session:spring-session-core:3.5.1
Gradle: org.springframework.session:spring-session-data-redis:3.5.2
Gradle: org.springframework:spring-aop:6.2.9
Gradle: org.springframework:spring-aspects:6.2.9
Gradle: org.springframework:spring-beans:6.2.9
Gradle: org.springframework:spring-context-support:6.2.9
Gradle: org.springframework:spring-context:6.2.9
Gradle: org.springframework:spring-core:6.2.9
Gradle: org.springframework:spring-expression:6.2.9
Gradle: org.springframework:spring-jcl:6.2.9
Gradle: org.springframework:spring-jdbc:6.2.9
Gradle: org.springframework:spring-orm:6.2.9
Gradle: org.springframework:spring-oxm:6.2.9
Gradle: org.springframework:spring-test:6.2.9
Gradle: org.springframework:spring-tx:6.2.9
Gradle: org.springframework:spring-web:6.2.9
Gradle: org.springframework:spring-webmvc:6.2.9
Gradle: org.testcontainers:database-commons:1.21.3
Gradle: org.testcontainers:jdbc:1.21.3
Gradle: org.testcontainers:junit-jupiter:1.21.3
Gradle: org.testcontainers:postgresql:1.21.3
Gradle: org.testcontainers:testcontainers:1.21.3
Gradle: org.webjars:swagger-ui:5.21.0
Gradle: org.webjars:webjars-locator-lite:1.1.0
Gradle: org.xmlunit:xmlunit-core:2.10.3
Gradle: org.yaml:snakeyaml:2.4
Gradle: org.zalando:faux-pas:0.9.0
Gradle: org.zalando:problem-spring-common:0.29.1
Gradle: org.zalando:problem-spring-web:0.29.1
Gradle: org.zalando:problem-violations:0.29.1
Gradle: org.zalando:problem:0.27.1
Gradle: redis.clients.authentication:redis-authx-core:0.1.1-beta2
Gradle: si.uom:si-quantity:2.2.2
Gradle: si.uom:si-units:2.2.2
Gradle: systems.uom:systems-common:2.2
Gradle: tech.units:indriya:2.2.3
Gradle: tech.uom.lib:uom-lib-common:2.2

Perfect — since **Synaxic API will run on multiple servers** (distributed, horizontally scalable), we need to ensure proper state management and coordination across instances.

Here's your **updated roadmap and directory structure**, optimized for **multi-server deployment** — using Redis for distributed caching, rate limiting, and session management. Each server runs the same Spring Boot app.

---

## ✅ THE ROADMAP


### 🟢 Phase 0: Project Setup + Core Structure

---

### 🟢 Phase 1: “My IP” + Request Inspector APIs
✅ Still the perfect first feature. Lightweight, safe, high utility.

---

### 🟡 Phase 2: API Key Auth + Google OAuth Flow

    - ✅ Use Redis for session storage to share user state across servers
    - ✅ Store API keys in local PostgreSQL
    - ✅ Configure Spring Session Redis for automatic session replication
    - ✅ Google OAuth callback can directly create/update user in DB — no sync needed.

---

### 🟡 Phase 3: Rate Limiting with Bucket4j

- Use **Redis-backed JCache** for distributed rate limiting.
- Bucket4j + Redis ensures consistent rate limits across all servers.
- Per-IP and per-API-key buckets are shared and accurate across all instances.
- ✅ Redis is required for consistent rate limiting across servers.

> ⚠️ If you restart the server, rate limit counters reset — acceptable for v1. Document it.

---

### 🟡 Phase 4: Unit/Byte/Color Converters
✅ All conversions are deterministic and stateless

---

### 🟡 Phase 5: Disposable Email Detection

- Load disposable domain list into **in-memory Guava BloomFilter or HashSet** at startup.
- Use Redis caching for MX lookups — shared cache across all servers with TTL.
- DNS lookups (if enabled) are cached locally → fast, no network overhead.

> 💡 Optional: Warm up cache on startup with top 1000 domains.

---

### 🔵 Phase 6: Observability & Analytics 

- Use **Micrometer + Prometheus** — each server exposes metrics, aggregate in Prometheus.
- Expose `/actuator/prometheus` → scrape with local Prometheus via `docker-compose`.
- Grafana dashboards work exactly the same.
- Add simple **in-memory counters** for key usage, errors, geo-stats — no need for external TSDB unless you want long-term retention.

> 💡 Optional: Log analytics to file or stdout → ship to Loki/ELK later if needed.

---

### 🔵 Phase 7: Documentation Site + Examples
✅ Docs are static — serve them separately or embed Swagger UI.

---

### ⚫ Phase 8: Redis Configuration → ✅ REQUIRED

> ✅ **Redis is essential** for multi-server deployment.

- Caching: Use **Redis + Caffeine** two-tier caching strategy.
- Session storage: Spring Session Redis for shared sessions.
- Rate limiting: Distributed buckets via Redis.
- Consider Redis Sentinel or Cluster for HA in production.
- 
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
│       │               │   ├── CacheConfig.java          // Caffeine + Redis
│       │               │   ├── RateLimitConfig.java       // Bucket4j + Redis
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

