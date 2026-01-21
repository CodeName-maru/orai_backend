# Gateway Service 코드 리뷰

**리뷰 일자**: 2026-01-21
**리뷰어**: Claude Code
**서비스 버전**: 0.0.1-SNAPSHOT

---

## 1. 개요

Gateway Service는 Spring Cloud Gateway 기반의 API Gateway로, 모든 클라이언트 요청의 진입점 역할을 하며 JWT 토큰 검증 및 라우팅을 담당합니다.

### 기술 스택
- Java 17
- Spring Boot 3.3.6
- Spring Cloud Gateway (WebFlux/Netty)
- Spring Cloud 2023.0.3
- JWT (jjwt 0.11.2)
- Spring Boot Actuator

### 파일 구조
```
gateway-service/src/main/java/com/ovengers/gatewayservice/
├── GatewayServiceApplication.java
├── configs/
│   ├── ActuatorHttpExchangesConfig.java
│   └── CorsConfig.java (주석 처리됨)
└── filter/
    ├── AuthorizationHeaderFilter.java
    └── GlobalFilter.java
```

---

## 2. 심각도별 이슈 요약

### 2.1 High (빠른 수정 권장)

| # | 파일 | 라인 | 이슈 | 설명 |
|---|------|------|------|------|
| 1 | AuthorizationHeaderFilter.java | 33 | **Actuator 노출** | `/actuator/**` 전체 허용으로 민감 정보 노출 가능 |
| 2 | AuthorizationHeaderFilter.java | 29-30 | **Secret Key 처리** | String 타입 직접 사용 (안전한 키 처리 필요) |
| 3 | build.gradle | 40-42 | **JWT 버전 불일치** | user-service(0.11.5)와 버전 다름 |

### 2.2 Medium (개선 권장)

| # | 파일 | 라인 | 이슈 | 설명 |
|---|------|------|------|------|
| 4 | AuthorizationHeaderFilter.java | 45 | **성능 이슈** | AntPathMatcher 매 요청마다 생성 |
| 5 | CorsConfig.java | 전체 | **데드 코드** | 파일 전체가 주석 처리됨 |
| 6 | CorsConfig.java | 15,18 | **CORS 설정 오류** | `*`와 credentials 동시 사용 불가 |
| 7 | ActuatorHttpExchangesConfig.java | 10-11 | **메모리 이슈** | InMemory 저장소 사용 |

### 2.3 Low (고려 사항)

| # | 파일 | 이슈 |
|---|------|------|
| 8 | bootstrap.yml | config-service URI 하드코딩 |
| 9 | Dockerfile | 보안 설정 부재 |
| 10 | 테스트 | contextLoads만 존재 |

---

## 3. 상세 분석

### 3.1 AuthorizationHeaderFilter.java

이 필터는 Gateway의 핵심 보안 컴포넌트로, JWT 토큰 검증과 사용자 정보 전달을 담당합니다.

#### Issue #1: Actuator 전체 노출 (라인 33)

```java
private final List<String> allowUrl = Arrays.asList(
    "/v3/api-docs/**", "/api/users/create", "/api/users/login", "/api/users/devLogin",
    "/refresh", "/", "/findByEmail", "/users/email", "/health-check",
    "/actuator/**",  // 모든 Actuator 엔드포인트 노출
    "/api/users/validate-mfa", "/api/users/mfa/validate-code/**"
);
```

**문제점**: `/actuator/**`가 인증 없이 접근 가능하여 다음 정보가 노출될 수 있습니다:
- `/actuator/env` - 환경 변수, 비밀 키
- `/actuator/beans` - 애플리케이션 구조
- `/actuator/configprops` - 설정 정보
- `/actuator/heapdump` - 힙 덤프 (메모리 내 민감 데이터)

**권장 수정**:
```java
private final List<String> allowUrl = Arrays.asList(
    "/v3/api-docs/**",
    "/api/users/create",
    "/api/users/login",
    "/api/users/devLogin",
    "/api/users/validate-mfa",
    "/api/users/mfa/validate-code/**",
    "/refresh",
    "/health-check",
    "/actuator/health",  // health만 허용
    "/actuator/info"     // info만 허용 (선택)
);
```

---

#### Issue #2: Secret Key 처리 (라인 29-30)

```java
@Value("${jwt.secretKey}")
private String secretKey;

// 사용 시 (라인 95)
.setSigningKey(secretKey)
```

**문제점**:
1. String 타입 시크릿 키 직접 사용은 deprecated 방식
2. 키 길이 검증 없음

**권장 수정**:
```java
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;

@Value("${jwt.secretKey}")
private String secretKeyString;

private SecretKey secretKey;

@PostConstruct
public void init() {
    byte[] keyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
        throw new IllegalStateException("JWT secret key must be at least 256 bits");
    }
    this.secretKey = Keys.hmacShaKeyFor(keyBytes);
}

// 사용 시
.setSigningKey(secretKey)
```

---

#### Issue #3: JWT 버전 불일치 (build.gradle 40-42)

```gradle
// gateway-service
implementation 'io.jsonwebtoken:jjwt-api:0.11.2'

// user-service
implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
```

**문제점**: 서비스 간 JWT 라이브러리 버전이 다르면 토큰 호환성 문제 발생 가능

**권장 수정**: 모든 서비스에서 동일한 버전 사용 (최신 안정 버전 0.11.5 권장)

---

#### Issue #4: 성능 이슈 - AntPathMatcher (라인 45)

```java
@Override
public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
        // 매 요청마다 새 인스턴스 생성
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        // ...
    };
}
```

**문제점**: `AntPathMatcher`는 불변 객체로 재사용 가능하나, 매 요청마다 새로 생성

**권장 수정**:
```java
// 필드로 선언하여 재사용
private final AntPathMatcher antPathMatcher = new AntPathMatcher();

@Override
public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
        String path = exchange.getRequest().getURI().getPath();
        boolean isAllowed = allowUrl.stream()
            .anyMatch(url -> antPathMatcher.match(url, path));
        // ...
    };
}
```

---

### 3.2 CorsConfig.java (데드 코드)

전체 파일이 주석 처리되어 있으며, 주석 내용에도 보안 이슈가 있습니다.

```java
// 주석 처리된 코드 내 문제
corsConfiguration.addAllowedOrigin("*");
corsConfiguration.setAllowCredentials(true);
```

**문제점**: `allowedOrigin("*")`와 `allowCredentials(true)`는 동시에 사용할 수 없습니다. CORS 스펙 위반으로 브라우저에서 에러 발생.

**권장 사항**:
1. 데드 코드 파일 삭제 또는
2. 올바른 CORS 설정으로 수정 후 활성화

```java
@Configuration
public class CorsConfig {
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("https://*.yourdomain.com");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
```

---

### 3.3 ActuatorHttpExchangesConfig.java

```java
@Bean
public HttpExchangeRepository httpExchangeRepository() {
    return new InMemoryHttpExchangeRepository();
}
```

**문제점**: `InMemoryHttpExchangeRepository`는 기본적으로 100개의 요청을 메모리에 저장합니다. 트래픽이 많은 환경에서는 메모리 사용량 증가.

**권장 수정**:
```java
@Bean
public HttpExchangeRepository httpExchangeRepository() {
    InMemoryHttpExchangeRepository repository = new InMemoryHttpExchangeRepository();
    repository.setCapacity(50);  // 필요에 따라 조정
    return repository;
}
```

또는 프로덕션에서는 외부 저장소(Redis 등) 사용 고려.

---

### 3.4 GlobalFilter.java

잘 구현된 필터입니다. 설정 기반으로 pre/post 로깅을 활성화/비활성화할 수 있습니다.

**장점**:
- 설정 가능한 로깅 (preLogger, postLogger)
- WebFlux 패턴 준수 (Mono 사용)
- 명확한 구조

**개선 가능 사항**:
```java
// 요청 ID 추가로 분산 추적 지원
@Override
public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        ServerHttpRequest request = exchange.getRequest()
            .mutate()
            .header("X-Request-Id", requestId)
            .build();

        if (config.isPreLogger()) {
            log.info("[{}] Request: {} {}", requestId,
                request.getMethod(), request.getURI());
        }
        // ...
    };
}
```

---

### 3.5 Dockerfile 분석

```dockerfile
FROM openjdk:17-jdk-slim
COPY build/libs/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

**문제점** (다른 서비스와 동일):
- non-root 사용자 미설정
- HEALTHCHECK 미설정
- JVM 옵션 미설정
- Gateway는 특히 네트워크 I/O가 많아 튜닝 필요

**권장 Dockerfile**:
```dockerfile
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser
WORKDIR /app
COPY build/libs/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# Gateway용 JVM 튜닝
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Dio.netty.leakDetection.level=disabled", \
  "-jar", "app.jar"]
```

---

### 3.6 bootstrap.yml

```yaml
spring:
  cloud:
    config:
      uri: http://config-service:8888
```

**문제점**: Config Server URI가 하드코딩되어 환경별 설정 변경이 어려움

**권장 수정**:
```yaml
spring:
  application:
    name: gateway-service
  cloud:
    config:
      uri: ${CONFIG_SERVER_URI:http://config-service:8888}
      fail-fast: true
      retry:
        max-attempts: 5
        initial-interval: 1000
```

---

## 4. 아키텍처 분석

### 4.1 강점

| 항목 | 설명 |
|------|------|
| **Spring Cloud Gateway** | WebFlux 기반 비동기 처리로 높은 처리량 |
| **JWT 검증 중앙화** | Gateway에서 토큰 검증하여 마이크로서비스 부담 감소 |
| **사용자 정보 헤더 전달** | X-User-Id, X-User-DepartmentId 헤더로 사용자 정보 전파 |
| **설정 가능한 필터** | GlobalFilter의 설정 기반 로깅 |
| **Swagger 통합** | springdoc-openapi-webflux 사용 |

### 4.2 개선 필요 영역

| 항목 | 현재 상태 | 권장 사항 |
|------|----------|----------|
| **Rate Limiting** | 미구현 | RequestRateLimiter 필터 추가 |
| **Circuit Breaker** | 미구현 | Resilience4j 통합 고려 |
| **요청 로깅** | 기본 | 분산 추적 ID 추가 (Sleuth/Micrometer) |
| **응답 캐싱** | 미구현 | 정적 리소스 캐싱 고려 |

---

## 5. 종합 평가

### 5.1 점수 요약

| 카테고리 | 점수 | 가중치 | 비고 |
|----------|------|--------|------|
| 보안 | 6/10 | 30% | Actuator 노출 이슈 |
| 코드 품질 | 7/10 | 25% | 전반적으로 양호, 일부 개선 필요 |
| 아키텍처 | 8/10 | 20% | Gateway 패턴 잘 구현 |
| 성능 | 6/10 | 15% | AntPathMatcher 재사용 필요 |
| 운영 준비도 | 5/10 | 10% | Dockerfile, 모니터링 개선 필요 |
| **종합** | **6.5/10** | 100% | |

### 5.2 위험 수준

```
[######----] HIGH - 빠른 수정 권장
```

Actuator 전체 노출이 주요 보안 우려사항입니다.

---

## 6. 권장 조치 사항

### 6.1 즉시 조치 (1주 이내) - P0

| # | 작업 | 파일 |
|---|------|------|
| 1 | Actuator 허용 범위 축소 (`/actuator/health`만) | AuthorizationHeaderFilter.java:33 |
| 2 | JWT 라이브러리 버전 통일 (0.11.5) | build.gradle |

### 6.2 단기 조치 (1개월 이내) - P1

| # | 작업 |
|---|------|
| 1 | AntPathMatcher 인스턴스 재사용 |
| 2 | Secret Key 안전한 처리 방식으로 변경 |
| 3 | 데드 코드(CorsConfig.java) 정리 |
| 4 | Dockerfile 보안 강화 |
| 5 | InMemoryHttpExchangeRepository 용량 제한 |

### 6.3 장기 고려 사항 - P2

| # | 작업 |
|---|------|
| 1 | Rate Limiting 구현 |
| 2 | Circuit Breaker 패턴 추가 |
| 3 | 분산 추적(Distributed Tracing) 도입 |
| 4 | 필터 단위 테스트 추가 |

---

## 7. 결론

Gateway Service는 **Spring Cloud Gateway를 활용한 표준적인 API Gateway 구현**으로, 기본 구조는 잘 갖춰져 있습니다.

**주요 강점**:
- JWT 토큰 검증 중앙화
- 사용자 정보 헤더 전달 패턴
- WebFlux 기반 비동기 처리

**즉시 조치 필요 사항**:
- `/actuator/**` 전체 허용 → `/actuator/health`만 허용으로 변경
- JWT 라이브러리 버전 통일

현재 상태는 **개발 환경에서는 사용 가능**하나, 프로덕션 배포 전 Actuator 노출 문제를 반드시 수정해야 합니다.
