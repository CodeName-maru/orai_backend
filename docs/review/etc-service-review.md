# Etc Service 코드 리뷰

**리뷰 날짜**: 2026-01-21
**리뷰어**: Claude Code
**서비스 버전**: 0.0.1-SNAPSHOT

---

## 1. 개요

etc-service는 실시간 푸시 알림(SSE)과 첨부파일 관리를 담당하는 마이크로서비스입니다. Redis Pub/Sub을 통해 다른 서비스로부터 알림 이벤트를 수신하고, SSE를 통해 클라이언트에게 실시간으로 전달합니다.

### 기술 스택
- Java 17
- Spring Boot 3.3.7
- Spring Cloud 2023.0.5
- Spring Security (JWT 기반)
- Spring Data JPA (MySQL)
- Spring Data Redis
- SSE (Server-Sent Events)
- AWS S3 (주석 처리됨)
- OpenAPI/Swagger

---

## 2. 코드 구조

```
etc-service/
├── src/main/java/com/ovengers/etcservice/
│   ├── EtcServiceApplication.java
│   ├── common/
│   │   ├── auth/
│   │   │   ├── JwtAuthFilter.java
│   │   │   └── TokenUserInfo.java
│   │   ├── configs/
│   │   │   ├── AwsS3Config.java (주석 처리됨)
│   │   │   ├── RedisConfig.java
│   │   │   ├── SecurityConfig.java
│   │   │   ├── SwaggerConfig.java
│   │   │   └── WebConfig.java
│   │   └── dto/
│   │       ├── CommonErrorDto.java
│   │       └── CommonResDto.java
│   ├── controller/
│   │   ├── AttachmentController.java
│   │   └── NotificationController.java
│   ├── dto/
│   │   ├── NotificationEvent.java
│   │   ├── NotificationMessage.java
│   │   └── NotificationResDto.java
│   ├── entity/
│   │   ├── Attachment.java
│   │   └── Notification.java
│   ├── repository/
│   │   ├── AttachmentRepository.java
│   │   └── NotificationRepository.java
│   ├── service/
│   │   ├── AttachmentService.java
│   │   ├── NotificationService.java
│   │   └── SseConnectionService.java
│   └── util/
│       ├── NotificationEventParser.java
│       └── NotificationSubscriber.java
└── src/main/resources/
    └── bootstrap.yml
```

**분석**: 표준적인 계층형 아키텍처를 따르고 있으며, 패키지 구조가 명확합니다.

---

## 3. 주요 컴포넌트 리뷰

### 3.1 SecurityConfig.java

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/**")  // 모든 URL
    .permitAll()  // 인증 없이 접근 가능
    .anyRequest().authenticated())
```

| 항목 | 평가 | 상세 |
|------|------|------|
| CSRF 비활성화 | ✅ 적절 | REST API + JWT 사용 시 적절 |
| 세션 관리 | ✅ 양호 | STATELESS 설정 |
| URL 보안 | 🔴 심각 | **모든 URL이 permitAll로 설정됨** |

**심각한 보안 이슈**: `requestMatchers("/**").permitAll()`로 인해 JWT 필터가 있어도 모든 엔드포인트가 인증 없이 접근 가능합니다.

---

### 3.2 JwtAuthFilter.java

```java
String userRole = departmentId.contains("team9") ? "ADMIN" : "USER";
```

| 항목 | 평가 | 상세 |
|------|------|------|
| 헤더 기반 인증 | ✅ 양호 | Gateway에서 전달된 헤더 활용 |
| 권한 부여 로직 | ⚠️ 문제 | "team9" 하드코딩은 유지보수 어려움 |
| Null 처리 | ⚠️ 주의 | userId가 null이면 인증 없이 통과 |

---

### 3.3 SseConnectionService.java

```java
Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
    // heartbeat 전송
}, 30, 30, TimeUnit.SECONDS);
```

| 항목 | 평가 | 상세 |
|------|------|------|
| SSE 구현 | ✅ 양호 | 기본 SSE 패턴 준수 |
| 인스턴스 ID 생성 | ✅ 양호 | UUID로 고유성 보장 |
| Redis 연결 관리 | ✅ 양호 | Hash 기반 연결 정보 저장 |
| 리소스 누수 | 🔴 심각 | **ScheduledExecutorService가 종료되지 않음** |
| 예외 처리 | ⚠️ 주의 | heartbeat 실패 시 emitter 미정리 |

**메모리 누수 위험**: 매 SSE 연결마다 새로운 `ScheduledThreadPool`이 생성되지만, 연결 종료 시 shutdown되지 않습니다.

---

### 3.4 NotificationService.java

```java
public List<NotificationResDto> getNotification(String userId) {
    List<Notification> allByUserId = notificationRepository.findAllByUserId(userId);
    List<NotificationResDto> list = allByUserId.stream()
        .map(notification -> notification.toDto(notification)).toList();
    updateNotificationIsRead(allByUserId);
    return list;
}
```

| 항목 | 평가 | 상세 |
|------|------|------|
| 비즈니스 로직 | ✅ 양호 | 알림 조회/생성 로직 적절 |
| 트랜잭션 관리 | ❌ 미비 | `@Transactional` 누락 |
| N+1 문제 | ⚠️ 주의 | 알림마다 개별 save 호출 |
| 메서드 설계 | ⚠️ 주의 | `toDto(notification)` 호출 시 파라미터 불필요 |

---

### 3.5 NotificationController.java

```java
private final Map<String, SseEmitter> clients = new ConcurrentHashMap<>();
```

| 항목 | 평가 | 상세 |
|------|------|------|
| API 설계 | ✅ 양호 | RESTful 패턴 준수 |
| SSE 엔드포인트 | ✅ 양호 | MediaType 적절히 설정 |
| 사용되지 않는 필드 | ⚠️ 주의 | `clients` 맵이 선언만 되고 미사용 |
| 응답 일관성 | ⚠️ 주의 | `createNotification`만 `CommonResDto` 미사용 |

---

### 3.6 AttachmentController.java / AttachmentService.java

| 항목 | 평가 | 상세 |
|------|------|------|
| CRUD 구현 | ✅ 양호 | 기본 CRUD 패턴 준수 |
| 트랜잭션 관리 | ✅ 양호 | `@Transactional` 적용 |
| 입력 검증 | ❌ 미비 | Request 객체에 Entity 직접 사용 |
| 파일 업로드 | ❌ 미비 | 실제 파일 업로드 로직 없음 (메타데이터만 저장) |

**설계 문제**: Controller에서 Entity를 직접 받고 반환하는 것은 API 계층과 도메인 계층의 결합도를 높입니다.

---

### 3.7 NotificationEventParser.java

```java
public static NotificationEvent parseNotificationEvent(String jsonString) {
    // 수동 JSON 파싱
    JSONObject jsonObject = new JSONObject(jsonString);
    // ...
}
```

| 항목 | 평가 | 상세 |
|------|------|------|
| JSON 파싱 | ⚠️ 비효율 | Jackson ObjectMapper 미사용, 수동 파싱 |
| 에러 처리 | ✅ 양호 | Null/Empty 체크 존재 |
| 필드 누락 처리 | ❌ 미비 | 필수 필드 누락 시 예외 발생 |

---

### 3.8 Entity 클래스들

#### Notification.java
| 항목 | 평가 | 상세 |
|------|------|------|
| JPA 매핑 | ✅ 양호 | 적절한 어노테이션 사용 |
| UUID 전략 | ✅ 양호 | 분산 환경에 적합 |
| toDto 메서드 | ⚠️ 문제 | Entity에 변환 로직 포함 (SRP 위반) |

#### Attachment.java
| 항목 | 평가 | 상세 |
|------|------|------|
| JPA 매핑 | ✅ 양호 | 적절한 어노테이션 사용 |
| Enum 사용 | ✅ 양호 | Type enum으로 타입 안전성 확보 |

---

### 3.9 RedisConfig.java

| 항목 | 평가 | 상세 |
|------|------|------|
| 다중 DB 사용 | ✅ 양호 | 용도별 DB 분리 (1번: user, 2번: sse) |
| Qualifier 사용 | ✅ 양호 | 빈 구분 명확 |
| Pub/Sub 설정 | ✅ 양호 | MessageListener 적절히 설정 |
| 연결 풀 설정 | ⚠️ 주의 | 기본 설정 사용, 튜닝 필요 가능 |

---

### 3.10 기타 설정 파일

#### SwaggerConfig.java
| 항목 | 평가 | 상세 |
|------|------|------|
| JWT 설정 | ✅ 양호 | Bearer 토큰 스키마 설정 |
| Server URL | ❌ 오류 | `/user-service`로 잘못 설정됨 (etc-service여야 함) |
| API 정보 | ⚠️ 부족 | 제목/설명이 기본값 사용 |

#### WebConfig.java
| 항목 | 평가 | 상세 |
|------|------|------|
| PasswordEncoder | ⚠️ 불필요 | etc-service에서 사용되지 않는 빈 |

#### AwsS3Config.java
| 항목 | 평가 | 상세 |
|------|------|------|
| 상태 | ℹ️ 정보 | 전체 코드가 주석 처리됨 (미사용) |

---

## 4. 보안 점검

### 4.1 발견된 보안 이슈

| 심각도 | 이슈 | 위치 | 권장 조치 |
|--------|------|------|-----------|
| 🔴 심각 | 모든 엔드포인트 permitAll | SecurityConfig.java:29 | 인증 필요 엔드포인트 명시 |
| 🔴 심각 | 인증 없이 알림 생성 가능 | NotificationController:56 | 서비스 간 인증 추가 |
| 🟡 중간 | 권한 로직 하드코딩 | JwtAuthFilter.java:35 | 설정 외부화 또는 DB 조회 |
| 🟡 중간 | Entity 직접 노출 | AttachmentController | DTO 사용 |

### 4.2 잘 된 부분
- Gateway 헤더 기반 인증 (X-User-Id)
- CSRF 비활성화 (REST API 적합)
- Stateless 세션 관리

---

## 5. 성능 및 안정성 점검

### 5.1 메모리 누수 위험

**SseConnectionService.java:63**
```java
Executors.newScheduledThreadPool(1).scheduleAtFixedRate(...)
```
- 매 연결마다 새 스레드 풀 생성
- 연결 종료 시 스레드 풀 미종료
- **해결**: 공유 ScheduledExecutorService 사용 또는 종료 시 shutdown 호출

### 5.2 데이터베이스 성능

**NotificationService.java:72-80**
```java
event.getUserIds().forEach(userId -> {
    // 개별 save 호출 (N+1 문제)
    notificationRepository.save(notification);
});
```
- **해결**: `saveAll()` 배치 저장 사용

### 5.3 트랜잭션 누락

**NotificationService.java:30-35**
- `getNotification` 메서드에서 조회 후 업데이트하지만 `@Transactional` 없음
- 동시 요청 시 데이터 불일치 가능

---

## 6. 코드 품질 이슈

### 6.1 사용되지 않는 코드
| 파일 | 이슈 |
|------|------|
| NotificationController.java:33 | `clients` 맵 미사용 |
| WebConfig.java | `PasswordEncoder` 빈 미사용 |
| AwsS3Config.java | 전체 클래스 주석 처리 |
| NotificationSubscriber.java:19 | `objectMapper` 필드 미사용 |

### 6.2 네이밍 컨벤션
| 파일 | 이슈 |
|------|------|
| TokenUserInfo.java:12 | `Role` 필드 - 소문자로 시작해야 함 (`role`) |
| build.gradle:47 | `spring-cloud-starter-config` 중복 선언 |

### 6.3 설계 문제
| 이슈 | 위치 | 권장 |
|------|------|------|
| Entity에 toDto 로직 | Notification.java:43 | 별도 Mapper 클래스 분리 |
| Entity를 API에 직접 사용 | AttachmentController | Request/Response DTO 분리 |
| 수동 JSON 파싱 | NotificationEventParser | ObjectMapper 활용 |

---

## 7. 개선 권장 사항

### 7.1 필수 (Critical)

1. **SecurityConfig 수정**
   ```java
   .authorizeHttpRequests(auth -> auth
       .requestMatchers("/api/notifications/subscribe").authenticated()
       .requestMatchers("/api/notifications/**").authenticated()
       .requestMatchers("/api/attachments/**").authenticated()
       .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
       .anyRequest().authenticated())
   ```

2. **SSE 스레드 풀 메모리 누수 수정**
   ```java
   // 클래스 레벨에 공유 스케줄러 선언
   private final ScheduledExecutorService scheduler =
       Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

   // emitter 종료 시 task 취소
   ```

3. **Notification 생성 시 배치 저장**
   ```java
   List<Notification> notifications = event.getUserIds().stream()
       .map(userId -> createNotificationEntity(userId, message))
       .toList();
   notificationRepository.saveAll(notifications);
   ```

### 7.2 권장 (Recommended)

4. **Attachment API에 DTO 적용**
   - `AttachmentCreateRequest`, `AttachmentResponse` DTO 생성
   - Entity 직접 노출 제거

5. **트랜잭션 추가**
   ```java
   @Transactional
   public List<NotificationResDto> getNotification(String userId) { ... }
   ```

6. **SwaggerConfig 수정**
   ```java
   .addServersItem(new Server().url("/etc-service"));
   ```

7. **사용되지 않는 코드 정리**
   - `clients` 맵 제거
   - `PasswordEncoder` 빈 제거
   - `AwsS3Config` 파일 삭제 또는 활성화

### 7.3 선택 (Optional)

8. **NotificationEventParser Jackson 활용**
   ```java
   private final ObjectMapper objectMapper;
   public NotificationEvent parse(String json) {
       return objectMapper.readValue(json, NotificationEvent.class);
   }
   ```

9. **Eureka 클라이언트 등록**
   - 서비스 디스커버리 연동

10. **테스트 코드 추가**
    - SSE 연결 테스트
    - Redis Pub/Sub 통합 테스트

---

## 8. 종합 평가

| 카테고리 | 점수 | 평가 |
|----------|------|------|
| 코드 품질 | 6/10 | 구조는 양호하나 미사용 코드 존재 |
| 보안 | 3/10 | **모든 엔드포인트가 인증 없이 접근 가능** |
| 성능 | 5/10 | 메모리 누수 위험, N+1 문제 존재 |
| 안정성 | 5/10 | 트랜잭션 누락, 예외 처리 미흡 |
| 테스트 | 3/10 | 기본 테스트만 존재 |
| 문서화 | 4/10 | Swagger 설정 오류 |
| **종합** | **4.3/10** | 핵심 기능은 동작하나 보안 및 안정성 개선 시급 |

---

## 9. 결론

etc-service는 SSE 기반 실시간 알림의 핵심 기능을 구현하고 있으나, 다음 사항을 **긴급히** 개선해야 합니다:

### 최우선 수정 사항

1. **🔴 SecurityConfig의 permitAll 제거** - 현재 모든 API가 인증 없이 접근 가능하여 심각한 보안 취약점
2. **🔴 SSE 스레드 풀 메모리 누수 수정** - 연결마다 스레드 풀이 누적되어 OOM 위험
3. **🟡 NotificationService 트랜잭션 추가** - 데이터 일관성 보장

SSE와 Redis Pub/Sub을 활용한 실시간 알림 아키텍처는 적절하게 설계되었으나, 프로덕션 운영을 위해서는 위 보안 및 안정성 이슈를 반드시 해결해야 합니다.
