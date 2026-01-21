# User Service 코드 리뷰

**리뷰 일자**: 2026-01-21
**리뷰어**: Claude Code
**서비스 버전**: 0.0.1-SNAPSHOT

---

## 1. 개요

User Service는 사용자 인증, 관리, MFA/2FA, 휴가/근태 관리를 담당하는 핵심 마이크로서비스입니다.

### 기술 스택
- Java 17
- Spring Boot 3.3.7
- Spring Security + JWT
- Spring Data JPA + QueryDSL
- MySQL, Redis
- AWS S3 (프로필 이미지)
- Google Authenticator (TOTP MFA)
- OpenFeign (서비스 간 통신)

### 파일 구조
```
user-service/src/main/java/com/ovengers/userservice/
├── controllers/     (6개)
├── service/         (5개)
├── repository/      (4개)
├── entity/          (8개)
├── dto/             (11개)
├── common/auth/     (4개)
├── common/configs/  (6개)
└── common/util/     (2개)
```

---

## 2. 심각도별 이슈 요약

### 2.1 Critical (즉시 수정 필요)

| # | 파일 | 라인 | 이슈 | 설명 |
|---|------|------|------|------|
| 1 | UserController.java | 42 | **MFA Secret 로깅** | MFA 시크릿 키가 로그에 출력됨 |
| 2 | UserController.java | 64-65 | **MFA Secret 노출** | MFA 시크릿이 클라이언트에 반환됨 |
| 3 | SecurityConfig.java | 32 | **과도한 permitAll** | `/api/users/**` 와일드카드로 모든 엔드포인트 인증 우회 |
| 4 | JwtAuthFilter.java | 35 | **하드코딩된 권한** | "team9" 문자열로 ADMIN 권한 판단 |

### 2.2 High (빠른 수정 권장)

| # | 파일 | 라인 | 이슈 | 설명 |
|---|------|------|------|------|
| 5 | JwtTokenProvider.java | 42 | **개발용 코드** | 만료시간에 600 곱셈 (개발 편의용) |
| 6 | UserService.java | 70 | **토큰 저장 키 불일치** | email로 저장, userId로 조회 |
| 7 | VacationService.java | 103 | **NPE 위험** | findSupervisor()가 null 반환 가능 |
| 8 | build.gradle | 전체 | **중복 의존성** | 4개 의존성이 중복 선언됨 |

### 2.3 Medium (개선 권장)

| # | 파일 | 이슈 |
|---|------|------|
| 9 | MfaController.java | 전체 주석 처리 (데드 코드) |
| 10 | TotpUtil.java | 전체 주석 처리 (데드 코드) |
| 11 | AdminService.java:282 | 불필요한 null 체크 |
| 12 | Dockerfile | 보안 설정 부재 |

---

## 3. 상세 분석

### 3.1 Critical 이슈 상세

#### Issue #1: MFA Secret 로깅 (UserController.java:42)

```java
// 현재 코드 - 보안 취약
log.info("Generated MFA secret for user {}: {}", userRequestDto.getEmail(), mfaSecret);
```

**문제점**: MFA 시크릿 키가 평문으로 로그에 기록됩니다. 로그 파일이 유출되면 공격자가 사용자의 MFA를 우회할 수 있습니다.

**권장 수정**:
```java
log.info("MFA secret generated for user: {}", userRequestDto.getEmail());
// 시크릿 키는 절대 로깅하지 않음
```

---

#### Issue #2: MFA Secret 클라이언트 노출 (UserController.java:64-65)

```java
// 현재 코드 - 보안 취약
String secret = userService.getUserSecret(userRequestDto.getEmail());
Map<String, Object> result = new HashMap<>();
result.put("secret", secret);
```

**문제점**: MFA 시크릿이 API 응답으로 클라이언트에 전송됩니다. 이는 MFA의 보안 목적을 완전히 무력화합니다.

**권장 수정**:
```java
// 시크릿 대신 세션 토큰 또는 MFA 챌린지 ID 반환
String mfaChallengeId = mfaService.createChallenge(userRequestDto.getEmail());
result.put("challengeId", mfaChallengeId);
result.put("mfaRequired", true);
```

---

#### Issue #3: 과도한 permitAll (SecurityConfig.java:31-33)

```java
// 현재 코드 - 보안 취약
.requestMatchers("/v3/api-docs/**","/api/users/create", "/api/users/login",
    "/refresh","/health-check", "/actuator/**", "/findByEmail", "/users/email",
    "/api/users/**","/api/users/validate-mfa", ...)
.permitAll()
```

**문제점**: `/api/users/**` 와일드카드가 모든 사용자 관련 엔드포인트의 인증을 우회하게 합니다. `changePassword`, `getMyInfo` 등 민감한 API도 인증 없이 접근 가능합니다.

**권장 수정**:
```java
.requestMatchers(
    "/v3/api-docs/**",
    "/api/users/create",
    "/api/users/login",
    "/api/users/devLogin",
    "/api/users/validate-mfa",
    "/api/users/mfa/validate-code",
    "/api/users/check-email",
    "/api/users/refresh",
    "/health-check",
    "/actuator/health"
).permitAll()
.anyRequest().authenticated()
```

---

#### Issue #4: 하드코딩된 권한 로직 (JwtAuthFilter.java:35)

```java
// 현재 코드 - 보안 취약
String userRole = departmentId.contains("team9") ? "ADMIN" : "USER";
```

**문제점**:
1. "team9"이 하드코딩되어 있어 변경 시 코드 수정 필요
2. departmentId 조작으로 ADMIN 권한 획득 가능
3. 역할 관리가 DB나 설정이 아닌 코드에 의존

**권장 수정**:
```java
// 설정 파일 또는 DB에서 역할 조회
@Value("${security.admin.department-ids}")
private List<String> adminDepartmentIds;

String userRole = adminDepartmentIds.contains(departmentId) ? "ADMIN" : "USER";

// 또는 별도의 role 테이블에서 조회
// String userRole = roleService.getUserRole(userId);
```

---

### 3.2 High 이슈 상세

#### Issue #5: 개발용 코드 (JwtTokenProvider.java:42)

```java
// 현재 코드
.setExpiration(new Date(now.getTime() + expiration * 600 * 1000L))
// 주석: 개발할 때 로그인 다시하기 귀찮으니까 10배 늘려놓음
```

**문제점**: 토큰 만료 시간이 설정값의 10배로 적용됩니다. 프로덕션에서 보안 위험.

**권장 수정**:
```java
.setExpiration(new Date(now.getTime() + expiration * 1000L))
```

---

#### Issue #6: 토큰 저장/조회 키 불일치 (UserService.java)

```java
// 저장 시 (line 70)
redisTemplate.opsForValue().set(user.getEmail(), refreshToken, 240, TimeUnit.HOURS);

// 조회 시 (UserController.java line 137)
Object obj = redisTemplate.opsForValue().get(user.getUserId());
```

**문제점**: Refresh Token을 email로 저장하지만 userId로 조회하여 항상 null 반환.

**권장 수정**:
```java
// 일관된 키 사용 (userId 권장)
redisTemplate.opsForValue().set(user.getUserId(), refreshToken, 240, TimeUnit.HOURS);
```

---

#### Issue #7: NPE 위험 (VacationService.java:103)

```java
public User findSupervisor(User user) {
    if (user.getPosition().toString().equals("EMPLOYEE")) {
        return userRepository.findByPositionAndDepartmentId(...);
    } else if (...) {
        return ...;
    }
    return null;  // CEO인 경우 null 반환
}
```

**문제점**: CEO가 휴가 신청 시 null 반환으로 NPE 발생.

**권장 수정**:
```java
public User findSupervisor(User user) {
    Position position = user.getPosition();

    return switch (position) {
        case EMPLOYEE -> userRepository.findByPositionAndDepartmentId(
            Position.TEAM_LEADER, user.getDepartmentId())
            .orElseThrow(() -> new BusinessException("상위 결재자를 찾을 수 없습니다."));
        case TEAM_LEADER -> userRepository.findByPositionAndDepartmentId(
            Position.MANAGER, user.getDepartmentId())
            .orElseThrow(...);
        case MANAGER -> userRepository.findByPositionAndDepartmentId(
            Position.CEO, user.getDepartmentId())
            .orElseThrow(...);
        case CEO -> throw new BusinessException("CEO는 휴가 결재 대상이 아닙니다.");
    };
}
```

---

#### Issue #8: 중복 의존성 (build.gradle)

```gradle
// 중복 선언된 의존성들
implementation 'org.springframework.boot:spring-boot-starter-security'  // 3번 중복
implementation 'org.springframework.boot:spring-boot-starter-data-redis'  // 2번 중복
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui'  // 2번 중복
implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'  // 2번 중복
implementation 'org.springframework.boot:spring-boot-starter-actuator'  // 2번 중복
runtimeOnly 'com.mysql:mysql-connector-j'  // 2번 중복
```

**권장 수정**: 중복 제거 후 정리된 build.gradle 사용

---

### 3.3 코드 품질 이슈

#### 데드 코드
- `MfaController.java`: 전체 파일이 주석 처리됨
- `TotpUtil.java`: 전체 파일이 주석 처리됨
- 사용하지 않는 코드는 버전 관리에서 삭제 권장

#### DI 미사용
```java
// UserController.java:33 - new 키워드 직접 사용
private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

// 권장: Spring Bean으로 등록
@Bean
public GoogleAuthenticator googleAuthenticator() {
    return new GoogleAuthenticator();
}
```

#### 불필요한 null 체크 (AdminService.java:282)
```java
User user = userRepository.findById(userId).orElseThrow(...);
if(user==null) return false;  // 이 조건은 절대 true가 될 수 없음
```

---

### 3.4 Dockerfile 분석

```dockerfile
FROM openjdk:17-jdk-slim
COPY build/libs/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

**문제점**:
- non-root 사용자 미설정
- HEALTHCHECK 미설정
- JRE 대신 JDK 사용
- JVM 메모리 옵션 미설정

**권장 Dockerfile**:
```dockerfile
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -D appuser
WORKDIR /app
COPY build/libs/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

---

## 4. 아키텍처 분석

### 4.1 강점

| 항목 | 설명 |
|------|------|
| **마이크로서비스 패턴** | OpenFeign을 통한 서비스 간 통신 구현 |
| **QueryDSL 사용** | 동적 쿼리 생성을 위한 적절한 도구 선택 |
| **Redis 활용** | Refresh Token 관리에 Redis 사용 |
| **MFA 구현** | Google Authenticator TOTP 지원 |
| **S3 통합** | 프로필 이미지 저장을 위한 AWS S3 연동 |

### 4.2 개선 필요 영역

| 항목 | 현재 상태 | 권장 사항 |
|------|----------|----------|
| **예외 처리** | 일반 Exception 사용 | 커스텀 예외 클래스 도입 |
| **입력 검증** | @Valid 일부만 적용 | 모든 DTO에 검증 적용 |
| **API 버저닝** | 버전 없음 | `/api/v1/users` 형태 권장 |
| **Rate Limiting** | 미구현 | 로그인 시도 제한 필요 |
| **비밀번호 정책** | 미구현 | 복잡도 검증 추가 필요 |

---

## 5. 종합 평가

### 5.1 점수 요약

| 카테고리 | 점수 | 가중치 | 비고 |
|----------|------|--------|------|
| 보안 | 3/10 | 30% | Critical 이슈 4개 |
| 코드 품질 | 5/10 | 20% | 중복 코드, 데드 코드 |
| 아키텍처 | 7/10 | 20% | 기본 구조 양호 |
| 테스트 | 4/10 | 15% | 테스트 커버리지 낮음 |
| 운영 준비도 | 4/10 | 15% | Dockerfile, 로깅 개선 필요 |
| **종합** | **4.5/10** | 100% | |

### 5.2 위험 수준

```
[##########] CRITICAL - 즉시 조치 필요
```

보안 관련 Critical 이슈가 다수 존재하여 **프로덕션 배포 전 반드시 수정 필요**.

---

## 6. 권장 조치 사항

### 6.1 즉시 조치 (1주 이내) - P0

| # | 작업 | 파일 |
|---|------|------|
| 1 | MFA 시크릿 로깅 제거 | UserController.java:42 |
| 2 | MFA 시크릿 API 응답에서 제거 | UserController.java:64-65 |
| 3 | SecurityConfig permitAll 범위 축소 | SecurityConfig.java |
| 4 | ADMIN 권한 로직 개선 | JwtAuthFilter.java:35 |
| 5 | JWT 만료시간 개발 코드 제거 | JwtTokenProvider.java:42 |
| 6 | Refresh Token 키 불일치 수정 | UserService.java:70 |

### 6.2 단기 조치 (1개월 이내) - P1

| # | 작업 |
|---|------|
| 1 | build.gradle 중복 의존성 정리 |
| 2 | 데드 코드 파일 삭제 (MfaController, TotpUtil) |
| 3 | Dockerfile 보안 강화 |
| 4 | 비밀번호 복잡도 검증 추가 |
| 5 | Rate Limiting 구현 (로그인 시도 제한) |
| 6 | VacationService NPE 수정 |

### 6.3 장기 고려 사항 - P2

| # | 작업 |
|---|------|
| 1 | 커스텀 예외 클래스 도입 |
| 2 | API 버저닝 도입 |
| 3 | 테스트 커버리지 향상 (최소 70%) |
| 4 | OAuth2 지원 검토 |
| 5 | Audit 로깅 추가 |

---

## 7. 결론

User Service는 기본적인 사용자 관리 및 인증 기능을 갖추고 있으나, **보안 관련 Critical 이슈가 다수 존재**합니다.

특히:
- MFA 시크릿 노출 문제
- 과도한 인증 우회 설정
- 하드코딩된 권한 로직

이러한 이슈들은 **프로덕션 환경에서 심각한 보안 침해로 이어질 수 있으므로 즉시 수정이 필요**합니다.

현재 상태는 **개발/테스트 환경에서도 주의가 필요**하며, 프로덕션 배포 전 최소 P0, P1 항목들의 수정을 완료해야 합니다.
