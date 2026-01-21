# Orai 마이크로서비스 포트폴리오 리팩토링 계획

## 개요
취업 포트폴리오용으로 Orai 프로젝트를 리팩토링하여 코드 품질, 보안, 테스트, 문서화를 강화합니다.

---

## Phase 1: 보안 취약점 수정 (최우선)

### 1.1 MFA 시크릿 로깅 제거
- **파일**: `user-service/src/main/java/com/ovengers/userservice/controllers/UserController.java`
- **작업**: Line 42의 `log.info("Generated MFA secret...")` 제거

### 1.2 JWT 토큰 만료시간 버그 수정
- **파일**: `user-service/src/main/java/com/ovengers/userservice/common/auth/JwtTokenProvider.java`
- **작업**: Line 42, 58의 `expiration * 600` → `expiration` 수정 (개발용 임시 코드 제거)

### 1.3 입력 검증 추가
- **파일**: `user-service/src/main/java/com/ovengers/userservice/dto/UserRequestDto.java`
- **작업**: `@Email`, `@NotBlank`, `@Size`, `@Pattern` 검증 어노테이션 추가

### 1.4 SecurityConfig permitAll 범위 수정 (Critical)
- **파일**: `user-service/src/main/java/com/ovengers/userservice/common/configs/SecurityConfig.java`
- **작업**: `/api/users/**` 와일드카드 제거, 필요한 엔드포인트만 명시적으로 허용

### 1.5 하드코딩된 권한 로직 수정
- **파일**: `user-service/src/main/java/com/ovengers/userservice/common/auth/JwtAuthFilter.java:35`
- **작업**: `"team9"` 하드코딩 제거, 설정 파일 또는 DB 기반 역할 관리로 변경

### 1.6 Refresh Token 키 불일치 수정
- **파일**: `user-service/src/main/java/com/ovengers/userservice/service/UserService.java:70`
- **작업**: Redis에 email로 저장하고 userId로 조회하는 불일치 수정

### 1.7 VacationService NPE 수정
- **파일**: `user-service/src/main/java/com/ovengers/userservice/service/VacationService.java:103`
- **작업**: `findSupervisor()` null 반환 시 NPE 방지, 적절한 예외 처리 추가

### 1.8 Chat Service 보안 수정 (Critical)
- **파일**: `chat-service/src/main/java/com/ovengers/chatservice/common/config/SecurityConfig.java`
- **작업**: `/**` permitAll 제거, 인증 필요 엔드포인트 명시

### 1.9 WebSocket 인증 강화 (Critical)
- **파일**: `chat-service/src/main/java/com/ovengers/chatservice/mongodb/config/JwtChannelInterceptor.java`
- **작업**: JWT 토큰 없는 WebSocket 연결 차단

### 1.10 Chat Service Race Condition 수정
- **파일**: `chat-service/src/main/java/com/ovengers/chatservice/mysql/service/ChatService.java:88-106`
- **작업**: Unread count 원자적 업데이트 (`UPDATE SET unreadCount = unreadCount + 1`)

### 1.11 CORS 설정 수정
- **파일**: `chat-service/src/main/java/com/ovengers/chatservice/mongodb/config/WebSocketStompConfig.java:24`
- **작업**: `setAllowedOriginPatterns("*")` → 알려진 도메인으로 제한

### 1.12 Etc Service 보안 수정 (Critical)
- **파일**: `etc-service/src/main/java/com/ovengers/etcservice/common/configs/SecurityConfig.java`
- **작업**: `/**` permitAll 제거, 인증 필요 엔드포인트 명시

### 1.13 SSE 메모리 누수 수정 (Critical)
- **파일**: `etc-service/src/main/java/com/ovengers/etcservice/service/SseConnectionService.java:63`
- **작업**: 매 연결마다 생성되는 `ScheduledThreadPool` → 공유 스케줄러 사용

### 1.14 Gateway Actuator 노출 제한
- **파일**: `gateway-service/src/main/java/com/ovengers/gatewayservice/filter/AuthorizationHeaderFilter.java:33`
- **작업**: `/actuator/**` → `/actuator/health`만 허용

### 1.15 Calendar/Etc Service 예외 처리 통일
- **파일**: 여러 서비스
- **작업**: `RuntimeException`, `IllegalArgumentException` → 커스텀 예외 클래스로 통일

---

## Phase 2: 공통 라이브러리 모듈화

### 2.1 orai-common 모듈 생성
```
orai-common/
├── build.gradle
└── src/main/java/com/ovengers/common/
    ├── auth/
    │   ├── JwtAuthFilter.java
    │   └── TokenUserInfo.java
    ├── dto/
    │   ├── CommonResDto.java (Generic 타입 수정)
    │   └── CommonErrorDto.java
    ├── exception/
    │   ├── BusinessException.java
    │   ├── ErrorCode.java (enum)
    │   └── GlobalExceptionHandler.java
    └── config/
        └── BaseSecurityConfig.java
```

### 2.2 중복 코드 제거 대상
| 파일 | 현재 위치 | 조치 |
|------|----------|------|
| CommonResDto.java | user, chat, calendar, etc | orai-common으로 통합 |
| JwtAuthFilter.java | user, chat, calendar, etc | orai-common으로 통합 |
| TokenUserInfo.java | user, chat, calendar, etc | orai-common으로 통합 |

### 2.3 각 서비스 build.gradle 수정
- `implementation project(':orai-common')` 의존성 추가
- 중복 의존성 제거 (user-service: 5개 중복 발견)

---

## Phase 3: 버전 통일

### 3.1 Spring Boot/Cloud 버전 통일
| 서비스 | 현재 | 통일 버전 |
|--------|------|----------|
| gateway-service | 3.3.6 | 3.3.7 |
| calendar-service | 3.4.1 | 3.3.7 |
| discovery-service | 3.3.6 | 3.3.7 |
| 나머지 | 3.3.7 | 유지 |

- **Spring Cloud**: 모두 `2023.0.4`로 통일

### 3.2 JWT 라이브러리 버전 통일
| 서비스 | 현재 | 통일 버전 |
|--------|------|----------|
| gateway-service | 0.11.2 | 0.11.5 |
| user-service | 0.11.5 | 유지 |

### 3.3 Feign Client URL 하드코딩 제거
| 서비스 | 파일 | 작업 |
|--------|------|------|
| calendar-service | `UserServiceClient.java:13` | Kubernetes URL 하드코딩 제거, Eureka 서비스 디스커버리 활용 |
| calendar-service | `EtcServiceClient.java:9` | 동일 |

---

## Phase 4: 테스트 커버리지 확대

### 4.1 user-service 테스트
- `UserServiceTest.java` - 회원가입, 로그인, MFA 로직
- `JwtTokenProviderTest.java` - 토큰 생성/검증
- `UserControllerIntegrationTest.java` - API 통합 테스트

### 4.2 chat-service 테스트
- `ChatRoomServiceTest.java` (기존 확장) - 채팅방 CRUD
- `MessageServiceTest.java` (기존 확장) - 메시지 전송
- `WebSocketStompControllerTest.java` - WebSocket 통합 테스트

### 4.3 테스트 공통 유틸리티
- `orai-common/src/test/java/` 에 TestFixtures, MockFactory 추가

---

## Phase 5: 예외 처리 표준화

### 5.1 ErrorCode enum 생성
```java
public enum ErrorCode {
    USER_NOT_FOUND(404, "U001", "사용자를 찾을 수 없습니다"),
    INVALID_PASSWORD(401, "U002", "비밀번호가 일치하지 않습니다"),
    DUPLICATE_EMAIL(409, "U003", "이미 존재하는 이메일입니다"),
    CHAT_ROOM_NOT_FOUND(404, "C001", "채팅방을 찾을 수 없습니다"),
    // ...
}
```

### 5.2 GlobalExceptionHandler 추가
- user-service, calendar-service, etc-service에 추가 (현재 chat-service만 존재)

### 5.3 기존 예외 마이그레이션
- `IllegalArgumentException` → `BusinessException`
- `RuntimeException` → `BusinessException`

---

## Phase 6: Feign Client 복원력 강화

### 6.1 Resilience4j 적용
- **의존성**: `spring-cloud-starter-circuitbreaker-resilience4j`
- Circuit Breaker + Retry 설정

### 6.2 Fallback 구현
- `UserServiceClientFallback.java` (calendar-service, chat-service)
- `CalendarServiceClientFallback.java` (user-service)
- `EtcServiceClientFallback.java` (calendar-service)

### 6.3 Chat Service N+1 쿼리 수정
- **파일**: `chat-service/.../ChatRoomService.java:273-284`
- **작업**: `getSubUsers()`에서 사용자별 개별 Feign 호출 → 배치 엔드포인트 `getUsersByIds()` 사용

### 6.4 Calendar Service N+1 쿼리 수정
- **파일**: `calendar-service/.../DepartmentResDto.java:25`
- **작업**: parent LAZY 로딩 시 추가 쿼리 발생 → `@EntityGraph` 또는 DTO 프로젝션 사용

---

## Phase 7: Docker/Infra 최적화

### 7.1 멀티스테이지 Dockerfile
```dockerfile
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle clean build -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 7.2 Docker Compose 생성
- `docker-compose.yml` 신규 생성
- MySQL, MongoDB, Redis, 전체 서비스 포함
- 로컬 개발 환경 원클릭 실행 지원

---

## Phase 8: 문서화 강화

### 8.1 서비스별 README.md
- `user-service/README.md`
- `chat-service/README.md`
- `calendar-service/README.md`
- `etc-service/README.md`

### 8.2 API 문서 강화
- Controller에 `@Operation`, `@ApiResponse` 상세 추가
- DTO에 `@Schema` 필드 설명 추가

### 8.3 ADR(Architecture Decision Records) 작성
- `docs/adr/001-microservices-architecture.md`
- `docs/adr/002-jwt-authentication.md`
- `docs/adr/003-common-module-extraction.md`

---

## Phase 9: 로깅 표준화

### 9.1 System.out.println 제거
- `etc-service/SseConnectionService.java` (Line 35)
- `chat-service/ChatRoomService.java` (Line 330)
- `calendar-service/ScheduleTaskService.java` (Lines 22,29,32,36)

### 9.2 민감정보 로깅 마스킹
- 이메일, 토큰 정보 로깅 시 마스킹 처리

### 9.3 GlobalExceptionHandler 에러 메시지 개선
- **파일**: `chat-service/.../GlobalExceptionHandler.java:20-21`
- **작업**: Raw 예외 메시지 노출 제거, 내부 로깅 후 일반적인 에러 반환

---

## Phase 10: 트랜잭션 및 데이터 일관성

### 10.1 Calendar Service 트랜잭션 추가
- **파일**: `calendar-service/.../CalendarService.java`
- **작업**: `createSchedule` 등 메서드에 `@Transactional` 추가

### 10.2 Etc Service 트랜잭션 추가
- **파일**: `etc-service/.../NotificationService.java:30-35`
- **작업**: `getNotification` 메서드에 `@Transactional` 추가

### 10.3 Chat Service 메시지 페이징 구현
- **파일**: `chat-service/.../MessageRepository.java:9`
- **작업**: 주석 처리된 페이징 활성화, 커서 기반 페이징 구현

### 10.4 Notification 배치 저장
- **파일**: `etc-service/.../NotificationService.java:72-80`
- **작업**: 개별 `save()` → `saveAll()` 배치 저장으로 변경

---

## Phase 11: 코드 정리 및 품질 개선

### 11.1 데드 코드 제거
| 파일 | 내용 |
|------|------|
| `user-service/.../MfaController.java` | 전체 주석 처리됨 |
| `user-service/.../TotpUtil.java` | 전체 주석 처리됨 |
| `chat-service/.../WebSocketStompController.java:47-96` | 메서드 전체 주석 처리 |
| `calendar-service/.../ScheduledNotificationService.java` | 전체 주석 처리됨 |
| `calendar-service/.../WebConfig.java` | CORS 설정 주석 처리 |
| `calendar-service/.../CalendarController.java:99-121` | 파일 업로드 코드 주석 처리 |
| `gateway-service/.../CorsConfig.java` | 전체 주석 처리됨 |
| `etc-service/.../AwsS3Config.java` | 전체 주석 처리됨 |

### 11.2 미사용 필드/변수 제거
| 파일 | 내용 |
|------|------|
| `etc-service/.../NotificationController.java:33` | `clients` 맵 미사용 |
| `etc-service/.../WebConfig.java` | `PasswordEncoder` 빈 미사용 |
| `etc-service/.../NotificationSubscriber.java:19` | `objectMapper` 필드 미사용 |

### 11.3 네이밍 컨벤션 수정
| 파일 | 현재 | 수정 |
|------|------|------|
| `calendar-service/.../ScheduleResponseDto.java:19` | `ScheduleId` | `scheduleId` |
| `calendar-service/.../TokenUserInfo.java:15` | `Role` | `role` |
| `etc-service/.../TokenUserInfo.java:12` | `Role` | `role` |

### 11.4 중복 의존성 정리
- **user-service/build.gradle**: spring-boot-starter-security (3번), redis (2번), springdoc (2번), openfeign (2번), actuator (2번), mysql (2번)
- **etc-service/build.gradle**: spring-cloud-starter-config (2번)

### 11.5 DI 미사용 코드 수정
- **파일**: `user-service/.../UserController.java:33`
- **작업**: `new GoogleAuthenticator()` → Spring Bean으로 등록

### 11.6 불필요한 null 체크 제거
- **파일**: `user-service/.../AdminService.java:282`
- **작업**: `orElseThrow()` 이후 불필요한 null 체크 제거

---

## Phase 12: 입력 검증 강화

### 12.1 Chat Service 입력 검증
| 파일 | 작업 |
|------|------|
| `chat-service/.../ChatRoomController.java:31-39` | `cleanInput()` XSS/인젝션 보호 추가 |
| `chat-service/.../ChatRoomController.java:57-60` | 파일 업로드 크기/타입 검증 추가 |
| `chat-service/.../MessageController.java:34-40` | `@NotNull`, `@Positive` 검증 추가 |
| `chat-service/.../MessageRequestDto.java` | `@NotBlank`, `@NotNull` 어노테이션 추가 |

### 12.2 Calendar Service 입력 검증
| 파일 | 작업 |
|------|------|
| `calendar-service/.../ScheduleRequestDto.java` | `@NotBlank`, `@Size` 등 검증 추가 |
| `calendar-service/.../DepartmentRequestDto.java` | Bean Validation 어노테이션 추가 |

### 12.3 Calendar Service 인증 헤더 검증 강화
- **파일**: `calendar-service/.../JwtAuthFilter.java:42`
- **작업**: `X-User-DepartmentId` 필수 검증 추가

---

## Phase 13: 하드코딩 값 외부화

### 13.1 관리자 부서 ID 외부화
| 파일 | 현재 | 작업 |
|------|------|------|
| `calendar-service/.../CalendarController.java:40` | `"team9"` | 환경변수 또는 설정 파일로 |
| 각 서비스 `JwtAuthFilter.java` | `"team9"` | 동일 |

### 13.2 부서 접두사 외부화
- **파일**: `calendar-service/.../CalendarService.java:110-118`
- **작업**: `"team"`, `"dept"`, `"org"` → Enum 또는 상수 클래스

### 13.3 Config Service username 외부화
- **파일**: `config-service/src/main/resources/application.yml:12`
- **작업**: `username: CodeName-maru` → `username: ${GIT_USERNAME}`

### 13.4 Swagger Server URL 수정
- **파일**: `etc-service/.../SwaggerConfig.java`
- **작업**: `/user-service` → `/etc-service`

### 13.5 Cron 표현식 외부화
- **파일**: `calendar-service/.../ScheduleTaskService.java:18`
- **작업**: 하드코딩된 cron → `@Value("${schedule.notification.cron}")`

---

## Phase 14: 날짜/시간 타입 통일

### 14.1 Calendar Service 날짜 타입 통일
- **파일**: `calendar-service/.../Schedule.java`, `CalendarRepository.java`
- **문제**: `LocalDate` (Entity) vs `LocalDateTime` (Repository 파라미터)
- **작업**: 비즈니스 요구사항에 맞는 타입으로 통일

### 14.2 Chat Service DateTime 포매팅 표준화
- **파일**: `chat-service/.../Message.java:51-63`
- **작업**: ISO 8601 포맷 사용, static formatter 생성

---

## Phase 15: 인프라 서비스 개선

### 15.1 Config Service 보안 강화
- Spring Security 의존성 추가
- Actuator 엔드포인트 인증 설정
- beans 엔드포인트 프로덕션 제거

### 15.2 Config Service Eureka 등록
```yaml
eureka:
  client:
    service-url:
      defaultZone: http://discovery-service:8761/eureka
```

### 15.3 Discovery Service 설정 보강
```yaml
eureka:
  server:
    enable-self-preservation: true
    eviction-interval-timer-in-ms: 60000
    response-cache-update-interval-ms: 30000
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### 15.4 Gateway Service 성능 개선
- **파일**: `gateway-service/.../AuthorizationHeaderFilter.java:45`
- **작업**: `AntPathMatcher` 매 요청마다 생성 → 필드로 재사용

### 15.5 Gateway Service Secret Key 처리 개선
- **파일**: `gateway-service/.../AuthorizationHeaderFilter.java:29-30`
- **작업**: String → SecretKey 타입, 키 길이 검증 추가

### 15.6 HttpExchangeRepository 용량 제한
- **파일**: `gateway-service/.../ActuatorHttpExchangesConfig.java`
- **작업**: `InMemoryHttpExchangeRepository` 용량 50개로 제한

---

## 핵심 수정 파일 목록

### Critical (즉시 수정)
| 파일 | Phase | 작업 내용 |
|------|-------|----------|
| `user-service/.../SecurityConfig.java` | 1 | permitAll 범위 축소 |
| `user-service/.../JwtTokenProvider.java` | 1 | 토큰 만료시간 버그 수정 |
| `user-service/.../UserController.java` | 1 | MFA 시크릿 로깅/노출 제거 |
| `chat-service/.../SecurityConfig.java` | 1 | `/**` permitAll 제거 |
| `chat-service/.../JwtChannelInterceptor.java` | 1 | WebSocket 인증 강화 |
| `chat-service/.../ChatService.java` | 1 | Race condition 수정 |
| `etc-service/.../SecurityConfig.java` | 1 | `/**` permitAll 제거 |
| `etc-service/.../SseConnectionService.java` | 1 | 메모리 누수 수정 |
| `gateway-service/.../AuthorizationHeaderFilter.java` | 1 | Actuator 노출 제한 |

### High (조속히 수정)
| 파일 | Phase | 작업 내용 |
|------|-------|----------|
| `user-service/.../JwtAuthFilter.java` | 1 | 하드코딩 권한 로직 수정 |
| `user-service/.../UserService.java` | 1 | Redis 토큰 키 불일치 수정 |
| `user-service/.../VacationService.java` | 1 | NPE 수정 |
| `chat-service/.../WebSocketStompConfig.java` | 1 | CORS 제한 |
| `chat-service/.../ChatRoomService.java` | 6 | N+1 쿼리 수정 |
| `calendar-service/.../CalendarService.java` | 10 | 트랜잭션 추가 |
| `etc-service/.../NotificationService.java` | 10 | 트랜잭션/배치 저장 |
| `gateway-service/build.gradle` | 3 | JWT 버전 통일 |

### Medium (개선 권장)
| 파일 | Phase | 작업 내용 |
|------|-------|----------|
| `orai-common/` (신규) | 2 | 공통 모듈 생성 |
| 각 서비스 `build.gradle` | 2,3,11 | 의존성 정리/버전 통일 |
| `user-service/src/test/` | 4 | 테스트 추가 |
| `chat-service/src/test/` | 4 | 테스트 추가 |
| 각 서비스 Dockerfile | 7 | 멀티스테이지 빌드 |
| `docker-compose.yml` (신규) | 7 | 로컬 환경 구성 |
| 데드 코드 파일들 | 11 | 삭제 또는 활성화 |
| 각 서비스 DTO | 12 | 입력 검증 추가 |
| 하드코딩된 값들 | 13 | 외부화 |

---

## 검증 방법

### 빌드 검증
```bash
./gradlew clean build
```

### 테스트 실행
```bash
./gradlew test
```

### Docker Compose 로컬 실행
```bash
docker-compose up -d
```

### API 테스트
- Swagger UI: `http://localhost:8081/swagger-ui.html` (user-service)
- 로그인 → MFA 인증 → 토큰 발급 플로우 테스트

---

---

## 서비스별 종합 평가 (리뷰 기준)

| 서비스 | 보안 | 코드품질 | 아키텍처 | 테스트 | 운영준비도 | 종합 |
|--------|------|----------|----------|--------|------------|------|
| user-service | 3/10 | 5/10 | 7/10 | 4/10 | 4/10 | **4.5/10** |
| chat-service | 2/10 | 5/10 | 7/10 | 3/10 | 4/10 | **4.0/10** |
| calendar-service | 6/10 | 7/10 | 8/10 | N/A | 6/10 | **6.0/10** |
| etc-service | 3/10 | 6/10 | 7/10 | 3/10 | 5/10 | **4.3/10** |
| gateway-service | 6/10 | 7/10 | 8/10 | 3/10 | 5/10 | **6.5/10** |
| config-service | 5/10 | 8/10 | 8/10 | 4/10 | 6/10 | **5.2/10** |
| discovery-service | 5/10 | 9/10 | 8/10 | 6/10 | 5/10 | **6.2/10** |

### 주요 이슈 통계
- **Critical**: 15개 (보안 취약점, 메모리 누수)
- **High**: 16개 (트랜잭션, N+1, 버전 불일치)
- **Medium**: 20개+ (코드 품질, 입력 검증, 하드코딩)
- **Low**: 10개+ (테스트, 문서화, i18n)

### 가장 시급한 수정 사항
1. **모든 서비스 SecurityConfig** - `/**` permitAll 패턴으로 인증 완전 우회
2. **SSE 메모리 누수** - 연결마다 새 스레드 풀 생성
3. **MFA 시크릿 노출** - 로그 및 API 응답에 민감 정보 노출
4. **WebSocket 인증 미강제** - 토큰 없이 연결 허용
5. **JWT 만료시간 버그** - 개발용 코드가 프로덕션에 영향

---

## 면접 대비 핵심 포인트

1. **보안**: "OWASP Top 10 기반 보안 감사 후 민감정보 로깅 취약점 수정, SecurityConfig permitAll 범위 축소"
2. **DRY 원칙**: "4개 서비스에 중복된 인증 코드를 공통 모듈로 추출"
3. **테스트**: "Mockito 단위 테스트 + SpringBootTest 통합 테스트 전략"
4. **MSA 복원력**: "Resilience4j Circuit Breaker로 장애 전파 방지, Feign N+1 쿼리 최적화"
5. **DevOps**: "멀티스테이지 Docker 빌드로 이미지 크기 50% 감소"
6. **성능**: "Race condition 해결 (원자적 업데이트), 메모리 누수 수정, 페이징 구현"
7. **코드 품질**: "데드 코드 정리, 네이밍 컨벤션 준수, 하드코딩 값 외부화"
