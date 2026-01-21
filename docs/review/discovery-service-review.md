# Discovery Service 코드 리뷰

**리뷰 일자**: 2026-01-21
**리뷰어**: Claude Code
**서비스 버전**: 0.0.1-SNAPSHOT

---

## 1. 개요

Discovery Service는 Netflix Eureka Server를 사용한 서비스 레지스트리로, 마이크로서비스 아키텍처에서 서비스 디스커버리를 담당합니다.

### 기술 스택
- Java 17
- Spring Boot 3.3.6
- Spring Cloud 2023.0.3
- Netflix Eureka Server

---

## 2. 파일별 리뷰

### 2.1 build.gradle

**현재 상태**: 양호

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.6'
    id 'io.spring.dependency-management' version '1.1.6'
}
```

| 항목 | 평가 | 비고 |
|------|------|------|
| Java 버전 | ✅ 적절 | Java 17 LTS 사용 |
| Spring Boot 버전 | ✅ 적절 | 3.3.6 안정 버전 |
| Spring Cloud 버전 | ✅ 적절 | 2023.0.3 (Spring Boot 3.3.x 호환) |
| 의존성 관리 | ✅ 적절 | 최소한의 필요한 의존성만 포함 |

**개선 제안**: 없음 (현재 상태가 적절함)

---

### 2.2 Dockerfile

**현재 상태**: 개선 필요

```dockerfile
FROM openjdk:17-jdk-slim
COPY build/libs/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

| 항목 | 평가 | 비고 |
|------|------|------|
| 베이스 이미지 | ⚠️ 개선 가능 | JDK 대신 JRE 사용 권장 |
| 보안 | ❌ 미흡 | non-root 사용자 미설정 |
| 헬스체크 | ❌ 미설정 | HEALTHCHECK 지시어 없음 |
| 멀티스테이지 빌드 | ❌ 미사용 | 이미지 크기 최적화 가능 |
| JVM 옵션 | ❌ 미설정 | 메모리 설정 등 부재 |

**개선 제안**:

```dockerfile
# 권장되는 Dockerfile 구조
FROM eclipse-temurin:17-jre-alpine

# 보안: non-root 사용자 생성
RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser

WORKDIR /app

COPY build/libs/*.jar app.jar

# 소유권 변경
RUN chown -R appuser:appgroup /app
USER appuser

# 헬스체크 추가
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget -q --spider http://localhost:8761/actuator/health || exit 1

# JVM 최적화 옵션
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

**심각도**: 중간 - 프로덕션 환경에서 보안 및 안정성 이슈 발생 가능

---

### 2.3 DiscoveryServiceApplication.java

**현재 상태**: 양호

```java
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }
}
```

| 항목 | 평가 | 비고 |
|------|------|------|
| 구조 | ✅ 적절 | 표준적인 Spring Boot 애플리케이션 구조 |
| 어노테이션 | ✅ 적절 | 필요한 어노테이션만 사용 |
| 코드 스타일 | ✅ 적절 | 깔끔하고 간결함 |

**개선 제안**: 없음 (Discovery Service는 단순한 구조가 적절함)

---

### 2.4 application.yml

**현재 상태**: 개선 필요

```yaml
server:
  port: 8761

spring:
  application:
    name: discovery-service

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
```

| 항목 | 평가 | 비고 |
|------|------|------|
| 기본 설정 | ✅ 적절 | Eureka 서버 기본 설정 올바름 |
| 포트 설정 | ✅ 적절 | 표준 Eureka 포트(8761) 사용 |
| 보안 설정 | ❌ 미흡 | 인증/인가 설정 없음 |
| 서버 설정 | ⚠️ 개선 가능 | 고급 설정 부재 |
| 프로파일 분리 | ❌ 미흡 | 환경별 설정 분리 없음 |
| 로깅 설정 | ❌ 미흡 | 로깅 레벨 미설정 |

**개선 제안**:

```yaml
server:
  port: 8761

spring:
  application:
    name: discovery-service

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    # 자가 보존 모드 (프로덕션에서는 true 권장)
    enable-self-preservation: true
    # 서비스 정리 간격 (ms)
    eviction-interval-timer-in-ms: 60000
    # 응답 캐시 갱신 간격
    response-cache-update-interval-ms: 30000

# 로깅 설정
logging:
  level:
    com.netflix.eureka: INFO
    com.netflix.discovery: INFO

# Actuator 설정 (헬스체크용)
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

**심각도**: 중간 - 프로덕션 환경에서 모니터링 및 운영 이슈 발생 가능

---

### 2.5 테스트 코드

**현재 상태**: 개선 필요

```java
@SpringBootTest
class DiscoveryServiceApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

| 항목 | 평가 | 비고 |
|------|------|------|
| 테스트 커버리지 | ⚠️ 최소 | contextLoads만 존재 |
| 통합 테스트 | ❌ 없음 | Eureka 서버 기능 테스트 부재 |

**개선 제안**: Discovery Service는 단순한 서비스이므로 현재 수준이 수용 가능하나, 다음 테스트 추가를 고려할 수 있음:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServiceApplicationTests {

    @Autowired
    private EurekaServerContext eurekaServerContext;

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
        assertNotNull(eurekaServerContext);
    }

    @Test
    void eurekaServerIsRunning() {
        // Eureka 대시보드 접근 테스트
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate
            .getForEntity("http://localhost:" + port + "/", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
```

**심각도**: 낮음 - 단순 서비스이므로 기본 테스트로 충분

---

## 3. 종합 평가

### 3.1 점수 요약

| 카테고리 | 점수 | 가중치 |
|----------|------|--------|
| 코드 품질 | 9/10 | 20% |
| 보안 | 5/10 | 25% |
| 설정 완성도 | 6/10 | 25% |
| 운영 준비도 | 5/10 | 20% |
| 테스트 | 6/10 | 10% |
| **종합** | **6.2/10** | 100% |

### 3.2 강점

1. **간결한 코드**: 불필요한 복잡성 없이 핵심 기능만 구현
2. **적절한 버전 선택**: Java 17, Spring Boot 3.3.6 등 안정적인 버전 사용
3. **표준 패턴 준수**: Spring Cloud 표준 패턴을 따름

### 3.3 개선 필요 사항

| 우선순위 | 항목 | 설명 |
|----------|------|------|
| **높음** | Dockerfile 보안 | non-root 사용자, 헬스체크 추가 |
| **높음** | application.yml 보강 | Actuator, 로깅, Eureka 서버 설정 추가 |
| **중간** | 프로파일 분리 | 개발/스테이징/프로덕션 환경별 설정 |
| **낮음** | 테스트 보강 | 통합 테스트 추가 (선택적) |

---

## 4. 권장 조치 사항

### 4.1 즉시 조치 (1주 이내)

1. **Dockerfile 개선**
   - non-root 사용자 설정
   - 헬스체크 추가
   - JRE 기반 이미지로 변경

2. **application.yml 개선**
   - Spring Boot Actuator 의존성 추가 및 설정
   - 로깅 레벨 설정

### 4.2 단기 조치 (1개월 이내)

1. **환경별 설정 분리**
   - application-dev.yml
   - application-prod.yml

2. **Eureka 서버 고급 설정**
   - 자가 보존 모드 설정
   - 캐시 및 정리 간격 조정

### 4.3 장기 고려 사항

1. **고가용성 구성**
   - Eureka 서버 클러스터링 (피어 복제)
   - 프로덕션 환경에서 최소 2대 이상 운영

2. **보안 강화**
   - Eureka 대시보드 인증 추가
   - TLS/SSL 설정

---

## 5. 결론

Discovery Service는 기본적인 Eureka Server 구현으로서 핵심 기능은 올바르게 구현되어 있습니다. 그러나 프로덕션 환경 배포를 위해서는 **Dockerfile 보안 강화**와 **설정 파일 보강**이 필요합니다.

현재 상태는 개발 및 테스트 환경에서는 사용 가능하나, 프로덕션 배포 전에 위에서 제안한 개선 사항들을 적용하는 것을 권장합니다.
