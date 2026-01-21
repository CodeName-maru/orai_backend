# CHAT-SERVICE 코드 리뷰

**리뷰 날짜**: 2026-01-21
**대상 서비스**: chat-service
**리뷰어**: Claude Code

---

## 요약 (Executive Summary)

chat-service는 MongoDB(메시지)와 MySQL(메타데이터)을 사용하는 하이브리드 Spring Boot 마이크로서비스입니다. 아키텍처는 적절하나, **즉각적인 조치가 필요한 심각한 보안 취약점**, 성능 문제, 코드 품질 이슈가 발견되었습니다.

### 발견된 이슈 요약

| 심각도 | 개수 | 주요 항목 |
|--------|------|-----------|
| CRITICAL | 3 | 인증 우회, JWT 검증 누락, 인증되지 않은 WebSocket |
| HIGH | 8 | CORS, Race condition, N+1 쿼리 |
| MEDIUM | 6 | 입력 검증, 에러 처리, 로깅 |
| LOW | 5 | i18n, 코드 정리, 테스트 커버리지 |

---

## 1. 아키텍처 & 구조

### 긍정적 측면
- MongoDB(메시지)와 MySQL(메타데이터) 레이어 분리 명확
- 프로젝트 컨벤션을 따르는 패키지 구조
- 메시지 작업에 대한 리액티브 프로그래밍 적절한 사용 (Project Reactor)
- OpenFeign 클라이언트를 통한 마이크로서비스 통신 (UserServiceClient)

### 발견된 이슈

#### 1.1 동기/비동기 패턴 혼재
- **파일**: `src/main/java/com/ovengers/chatservice/mysql/service/ChatService.java`
- **라인**: 31-71
- **문제**: `getChatRoomsWithUnreadCount()`가 Flux/Mono를 사용하면서 블로킹 동기 리포지토리 작업 호출 (`chatRoomRepository.findById()`)
- **영향**: 스레드 풀 고갈, 성능 저하 가능
- **권장**: 완전히 리액티브 리포지토리 사용 또는 완전히 블로킹 방식으로 전환

#### 1.2 레이어 위반
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/service/MessageService.java`
- **라인**: 47-52
- **문제**: Service 레이어가 별도 서비스에 위임하지 않고 MySQL 리포지토리에 직접 접근 (`ChatRoomRepository`, `UserChatRoomRepository`)
- **영향**: 서비스 책임이 레이어 간에 혼재

---

## 2. 컨트롤러

### 2.1 [CRITICAL] 보안 - 약한 인가 설정
- **파일**: `src/main/java/com/ovengers/chatservice/common/config/SecurityConfig.java`
- **라인**: 28-31
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/**")  // 모든 엔드포인트
    .permitAll()             // 인증 불필요
    .anyRequest().authenticated())
```
- **문제**: 모든 엔드포인트(`/**`)가 `permitAll()`로 설정되어 `anyRequest().authenticated()`가 도달 불가
- **심각도**: CRITICAL - 완전한 인증 우회
- **영향**: JWT 필터 설정에도 불구하고 REST 엔드포인트에 보안 없음
- **권장**: `/**`에서 `permitAll()` 제거하고 특정 public 엔드포인트만 정의

### 2.2 입력 검증 미흡
- **파일**: `src/main/java/com/ovengers/chatservice/mysql/controller/ChatRoomController.java`
- **라인**: 31-39
```java
public String cleanInput(String input) {
    if (input == null) {
        return null;
    }
    return input.startsWith("\"") && input.endsWith("\"")
            ? input.substring(1, input.length() - 1)
            : input;
}
```
- **문제**: 불충분한 입력 정제; 따옴표만 제거, XSS/인젝션 보호 없음
- **라인**: 57-60 - `image` multipart 파일 검증 없음 (크기, 타입)
- **권장**: 파일 크기 제한, MIME 타입 검증, 콘텐츠 스캔 추가

### 2.3 요청 검증 어노테이션 없음
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/controller/MessageController.java`
- **라인**: 34-40
```java
@GetMapping("/{chatRoomId}/messageList")
public Flux<MessageDto> getMessages(@PathVariable Long chatRoomId,
                                    @AuthenticationPrincipal TokenUserInfo tokenUserInfo)
```
- **문제**: `chatRoomId`에 `@NotNull`, `@Positive` 검증 없음
- **권장**: Bean Validation 어노테이션 추가 (`@Valid`, `@NotNull`, `@Positive`)

### 2.4 안전하지 않은 에러 메시지 노출
- **파일**: `src/main/java/com/ovengers/chatservice/common/handler/GlobalExceptionHandler.java`
- **라인**: 20-21
```java
return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    .body("An error occurred: " + ex.getMessage());
```
- **문제**: Raw 예외 메시지가 클라이언트에 노출 (정보 유출 가능성)
- **권장**: 예외를 내부적으로 로깅하고 클라이언트에는 일반적인 에러 반환

---

## 3. 서비스

### 3.1 [CRITICAL] Unread Count의 Race Condition
- **파일**: `src/main/java/com/ovengers/chatservice/mysql/service/ChatService.java`
- **라인**: 88-106
```java
@Transactional
public void incrementUnreadCount(Long chatRoomId, String senderId) {
    List<UserChatRoom> subscribers = userChatRoomRepository.findAllByChatRoomId(chatRoomId);

    subscribers.stream()
        .filter(subscriber -> !subscriber.getUserId().equals(senderId))
        .forEach(subscriber -> {
            ChatRoomRead chatRoomRead = chatRoomReadRepository
                    .findByChatRoomIdAndUserId(chatRoomId, subscriber.getUserId())
                    .orElseGet(() -> ChatRoomRead.builder()...build());

            chatRoomRead.setUnreadCount(chatRoomRead.getUnreadCount() + 1);
            chatRoomReadRepository.save(chatRoomRead);
        });
}
```
- **문제**:
  - 비원자적 read-modify-write 패턴
  - 동시 메시지로 인한 업데이트 손실
  - 동기화 없이 리액티브 코드에서 호출
- **영향**: 동시 부하 시 읽지 않은 개수가 부정확해짐
- **권장**: 데이터베이스 레벨 원자적 증가 사용 (`UPDATE SET unreadCount = unreadCount + 1`)

### 3.2 [HIGH] 잘못된 메시지 정렬
- **파일**: `src/main/java/com/ovengers/chatservice/mysql/service/ChatService.java`
- **라인**: 47-49
```java
Mono<Message> lastMessageMono = messageRepository
    .findByChatRoomIdOrderByCreatedAtAsc(chatRoomId)
    .last();
```
- **문제**: **오름차순** 정렬 후 `.last()` 호출 - 기술적으로는 동작하지만 의미상 혼란
- **권장**: 명확성을 위해 `OrderByCreatedAtDesc` 사용

### 3.3 [HIGH] 인가 우회 위험
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/service/MessageService.java`
- **라인**: 56-58
```java
public Mono<MessageDto> sendMessage(Long chatRoomId, String content, String userId, String userName) {
    validateChatRoomAndUser(chatRoomId, userId);
    validateMessageContent(content.trim());
```
- **문제**:
  - `userName` 파라미터가 클라이언트에서 검증 없이 전달됨
  - 인증된 토큰이나 user service에서 가져와야 함
  - 클라이언트가 발신자 이름을 위조할 수 있음
- **영향**: 가짜 메시지 귀속
- **권장**: 인증된 사용자 컨텍스트에서 userName 가져오기

### 3.4 Null Pointer Exception 위험
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/service/MessageService.java`
- **라인**: 25-32
```java
public UserResponseDto getUserInfo(String userId) {
    UserResponseDto userById = userServiceClient.getUserById(userId);

    if (userById == null) {
        throw new RuntimeException("사용자 정보를 가져오는 데 실패했습니다: " + userId);
    }
    return userById;
}
```
- **문제**: null 체크에 의존하지 않고 네트워크 오류에 대해 `FeignException`을 catch해야 함
- **권장**: Feign 예외에 대한 try-catch 추가

### 3.5 과도한 Lock 생성
- **파일**: `src/main/java/com/ovengers/chatservice/mysql/service/ChatRoomService.java`
- **라인**: 350-359
```java
Lock lock = new ReentrantLock();
lock.lock();
try {
    invitationRepository.saveAll(invitations);
    userChatRoomRepository.saveAll(users);
    sendEnterUsers(chatRoomId, validUserIds);
} finally {
    lock.unlock();
}
```
- **문제**:
  - 매번 새 lock 생성 (동기화 이점 없음)
  - 데이터베이스 레벨 제약조건이나 버전 제어 사용해야 함
  - JPA `@Transactional`이면 충분
- **권장**: 수동 lock 제거, 데이터베이스 제약조건 사용

### 3.6 페이징 누락
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/repository/MessageRepository.java`
- **라인**: 9
```java
Flux<Message> findByChatRoomIdOrderByCreatedAtAsc(Long chatRoomId/*Pageable pageable*/);
```
- **문제**: 페이징이 주석 처리됨; 대용량 채팅방의 모든 메시지 로딩
- **영향**: OOM, 성능 저하
- **권장**: 커서 기반 방식으로 페이징 재구현

---

## 4. 리포지토리

### 4.1 N+1 쿼리 문제
- **파일**: `src/main/java/com/ovengers/chatservice/mysql/service/ChatRoomService.java`
- **라인**: 273-284
```java
public List<UserResponseDto> getSubUsers(Long chatRoomId, String userId) {
    validateChatRoomAndUser(chatRoomId, userId);

    List<String> userIds = userChatRoomRepository.findAllByChatRoomId(chatRoomId)
            .stream()
            .map(UserChatRoom::getUserId)
            .toList();

    return userIds.stream()
            .map(userServiceClient::getUserById)  // 각 사용자 ID별 호출
            .collect(Collectors.toList());
}
```
- **문제**: 각 `getUserById()`가 별도 Feign 호출
- **영향**: 방에 100명의 사용자 = 100개의 네트워크 호출
- **권장**: 배치 엔드포인트 `getUsersByIds()` 사용

### 4.2 쿼리 최적화 누락
- **파일**: `src/main/java/com/ovengers/chatservice/mysql/repository/ChatRoomReadRepository.java`
- **문제**: 대량 업데이트를 위한 커스텀 쿼리 정의 없음
- **권장**: 배치 업데이트 메서드 추가: `void deleteUnreadCountByChatRoomId(Long chatRoomId)`

---

## 5. 엔티티/도큐먼트

### 5.1 Cascade Delete 미설정
- **파일**: `src/main/java/com/ovengers/chatservice/mysql/entity/UserChatRoom.java`
- **라인**: 23-24
```java
@JoinColumn(name = "chat_room_id", nullable = false)
private Long chatRoomId;
```
- **문제**: `@ManyToOne`이나 cascade 설정 없음; 고아 레코드 가능
- **권장**: 명시적 관계 추가: `@ManyToOne @JoinColumn(... onDelete=CASCADE)`

### 5.2 인덱스 누락
- **파일**: 여러 엔티티
- **문제**: 자주 쿼리되는 컬럼에 `@Index` 어노테이션 없음
- **예시**:
  - `ChatRoom.creatorId` - 자주 쿼리됨
  - `UserChatRoom.userId` - 사용자 채팅방 목록에서 쿼리됨
  - `ChatRoomRead.userId` - 읽지 않은 개수에서 쿼리됨
- **권장**: 데이터베이스 인덱스 추가

### 5.3 일관성 없는 DateTime 포매팅
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/document/Message.java`
- **라인**: 51-63
```java
public MessageDto toDto() {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    return MessageDto.builder()
            .messageId(messageId)
            ...
            .createdAt(createdAt != null ? createdAt.format(formatter) : "메시지생성시간없음")
            .updatedAt(updatedAt != null ? updatedAt.format(formatter) : "메시지수정시간없음")
            .build();
}
```
- **문제**:
  - ISO 8601 대신 DTO에서 String 타임스탬프
  - 반복적인 formatter 생성 (비효율)
  - 영문 코드에 한국어 에러 메시지
- **권장**: ISO 8601 포맷 사용, static formatter 생성

### 5.4 Message Type 하드코딩
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/document/Message.java`
- **라인**: 37
```java
private String type = "CHAT"; // CHAT, SYSTEM, EDIT, DELETE, ERROR 등
```
- **문제**: 매직 스트링, enum 없음
- **권장**: `MessageType` enum 생성

---

## 6. DTOs

### 6.1 검증 어노테이션 누락
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/dto/MessageRequestDto.java`
- **문제**: DTO에 검증 제약조건 없음
- **권장**: `@NotBlank`, `@NotNull` 어노테이션 추가

---

## 7. 설정

### 7.1 [HIGH] CORS 모든 Origin 허용
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/config/WebSocketStompConfig.java`
- **라인**: 24
```java
.setAllowedOriginPatterns("*")  // 모든 ORIGIN 허용
```
- **심각도**: HIGH
- **문제**: CORS 보호 없음; CSRF 유사 공격에 취약
- **권장**: 알려진 도메인으로 제한: `.allowedOriginPatterns("https://domain1.com", "https://domain2.com")`

### 7.2 WebSocket 보호 없이 CSRF 비활성화
- **파일**: `src/main/java/com/ovengers/chatservice/common/config/SecurityConfig.java`
- **라인**: 26
```java
.csrf(csrf -> csrf.disable())
```
- **문제**: CSRF가 전역적으로 비활성화됨; WebSocket 엔드포인트 보호 안됨
- **권장**: API 엔드포인트에만 CSRF 비활성화, 웹에는 활성화

### 7.3 Stateless Session이지만 HTTP Filter에서 JWT 검증 안함
- **파일**: `src/main/java/com/ovengers/chatservice/common/auth/JwtAuthFilter.java`
- **라인**: 30-51
- **문제**:
  - gateway가 `X-User-Id` 헤더를 설정하는 것에 의존
  - 이 서비스에서 JWT 검증 없음
  - gateway를 맹목적으로 신뢰
  - gateway가 손상되면 chat-service도 손상됨
- **권장**: JWT 서명을 독립적으로 검증

### 7.4 JWT Utils 예외 처리 없음
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/config/JwtUtils.java`
- **라인**: 23-33
```java
public Claims extractClaims(String token) {
    try {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    } catch (Exception e) {
        log.error("JWT validation failed: {}", e.getMessage());
        return null;  // 예외 대신 NULL 반환
    }
}
```
- **문제**: 조용히 null 반환; 호출자가 null 체크해야 함
- **라인**: 41-42
```java
public String getUserIdFromToken(String token) {
    return extractClaims(token).getSubject();  // 토큰 무효 시 NPE
}
```
- **영향**: 토큰이 무효하면 NullPointerException
- **권장**: null 반환 대신 `JwtException` throw

### 7.5 [HIGH] WebSocket 인증 미강제
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/config/JwtChannelInterceptor.java`
- **라인**: 28-46
```java
if ("CONNECT".equals(accessor.getCommand().name())) {
    String jwtToken = accessor.getFirstNativeHeader("Authorization");
    if (jwtToken != null && jwtToken.startsWith("Bearer ")) {
        // ...인증 로직
    }
    // ELSE 없음 - 인증되지 않은 연결 허용
}
```
- **문제**: 토큰이 제공되지 않으면 연결이 조용히 허용됨
- **권장**: 유효한 토큰이 없으면 예외 throw

---

## 8. 코드 품질

### 8.1 [CRITICAL] 프로덕션 코드에 System.out.println
- **파일**: `src/main/java/com/ovengers/chatservice/mysql/service/ChatRoomService.java`
- **라인**: 330
```java
System.out.println("이미 초대된 유저: " + inviteeId);
```
- **문제**: 프로덕션에 디버그 출력 남아있음
- **권장**: 제거하거나 logger 사용

### 8.2 Dead Code / 주석 처리된 코드
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/controller/WebSocketStompController.java`
- **라인**: 47-96 (전체 메서드 주석 처리)
- **문제**: 대량의 dead code로 유지보수성 저하
- **권장**: 삭제하거나 브랜치 히스토리로 이동

### 8.3 일반적인 Exception Catching
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/config/JwtChannelInterceptor.java`
- **라인**: 43-45
```java
} catch (Exception e) {
    throw new IllegalArgumentException("Invalid JWT Token: " + e.getMessage());
}
```
- **문제**: 너무 광범위한 예외 타입 catch; 스택 트레이스 손실
- **권장**: 특정 예외 catch (JwtException, MalformedJwtException)

### 8.4 하드코딩된 문자열
- 여러 파일에서 하드코딩된 한국어 메시지와 에러 문자열 사용
- **권장**: 메시지 properties 파일 사용 (i18n)

### 8.5 Reactive 경계에서 로깅 없음
- **파일**: `src/main/java/com/ovengers/chatservice/mongodb/controller/MessageController.java`
- **라인**: 50-51
```java
.doOnSuccess(updatedMessage -> messagingTemplate.convertAndSend("/sub/" + chatRoomId + "/chat", updatedMessage));
```
- **문제**: 전송 실패 시 에러 로깅 없음
- **권장**: 로깅과 함께 `.doOnError()` 추가

---

## 9. 보안 취약점 (OWASP Top 10)

### A01: Broken Access Control
1. **모든 엔드포인트 permit all** (SecurityConfig.java:29)
2. **WebSocket이 인증되지 않은 연결 허용** (JwtChannelInterceptor.java:28-46)
3. **Row-level security 없음** - ID를 추측하면 다른 사용자의 채팅방 접근 가능

### A02: Cryptographic Failures
1. **서비스에서 JWT 검증 안함** - gateway 신뢰 (JwtAuthFilter.java)
2. **Properties 파일에 Secrets** - 자격 증명 암호화 안됨

### A03: Injection
1. **SQL Injection 위험**: 파라미터화된 쿼리 미표시 (JPA가 완화)
2. **입력 정제 없음** - 따옴표 제거만 (ChatRoomController.java:31-39)

### A04: Insecure Design
1. WebSocket이나 REST 엔드포인트에 **Rate limiting 없음**
2. 메시지 작업에 **Idempotency keys 없음**

### A05: Security Misconfiguration
1. **CORS가 모든 origin 허용** (WebSocketStompConfig.java:24)
2. **CSRF 전역 비활성화** (SecurityConfig.java:26)
3. **디버그 로깅 활성화** (라인 330의 System.out.println)

### A07: Authentication Failures
1. **Gateway 신뢰 모델** - 독립적 검증 없음
2. **세션 타임아웃 미설정**

### A08: Insufficient Logging & Monitoring
1. 메시지 수정에 대한 **감사 추적 없음**
2. 예외에서 **제한된 에러 컨텍스트**
3. Rate limiting을 위한 **메트릭 없음**

---

## 10. 성능 이슈

### 10.1 무제한 메시지 쿼리
- **위치**: `MessageRepository.java:9`
- 페이징 없음; 모든 메시지 로딩
- **해결**: 페이징/커서 기반 로딩 구현

### 10.2 getSubUsers의 N+1 문제
- **위치**: ChatRoomService.java:273-284
- 사용자 정보에 대해 사용자당 하나의 쿼리
- **해결**: 배치 엔드포인트 사용

### 10.3 비효율적인 Unread Count 계산
- **위치**: ChatService.java:108-123
- 모든 채팅방에 대해 마지막 메시지 쿼리
- **해결**: 비정규화 또는 데이터베이스 트리거 사용

### 10.4 Reactive-Blocking 혼합
- **위치**: ChatService.java:40-70
- 블로킹 리포지토리를 호출하는 리액티브 코드
- **해결**: 완전히 리액티브한 MongoDB 리포지토리 사용

---

## 11. 테스트 커버리지

### 상태: 최소
- **위치**: `src/test/java`
- 4개의 테스트 파일만, 제한된 커버리지
- **테스트 파일**:
  - `ChatRoomServiceTest.java` - 3개 테스트
  - `MessageServiceTest.java` - 3개 테스트
  - `ChatServiceTest.java` - (비어있을 가능성)
  - `ChatServiceApplicationTests.java` - (비어있을 가능성)

### 부족한 부분
1. 통합 테스트 없음
2. WebSocket 테스트 없음
3. 보안 테스트 없음
4. 성능 테스트 없음
5. Mock된 의존성만 - 실제 데이터베이스 테스트 없음

### 권장 사항
- MySQL/MongoDB용 TestContainers 추가
- WebSocketClient 테스트 추가
- 인가를 위한 보안 테스트 추가
- 70%+ 커버리지 목표

---

## 12. 에러 처리

### 12.1 일관성 없는 예외 타입
- 사용자 찾을 수 없음에 RuntimeException 사용 (MessageService.java:29)
- 다른 곳에서는 IllegalArgumentException 사용
- 일부 경로에서 EntityNotFoundException
- **권장**: 커스텀 예외 생성

### 12.2 클라이언트에 일반적인 에러 메시지
- GlobalExceptionHandler.java:20-21이 스택 트레이스 노출
- **권장**: 에러 코드 반환, 세부 사항은 서버 측에서 로깅

### 12.3 Feign 에러에 대한 Fallback 누락
- FallbackFactory나 circuit breaker 없음
- **권장**: 장애 허용을 위한 Resilience4j 추가

---

## 13. 로깅 관행

### 13.1 일관성 없는 로그 레벨
- 디버그 정보에 `log.info()` 사용 (ChatRoomService.java:66, 157)
- `log.debug()` 사용해야 함

### 13.2 Bare System.out.println
- ChatRoomService.java:330
- logger 사용해야 함

### 13.3 구조화된 로깅 없음
- 파라미터화된 로깅 대신 문자열 연결 사용
- 예시: `log.info("request Url: {}", request.getRequestURI())`

---

## 핵심 권장 사항 - 우선순위 순

### P0 (Critical - 즉시 수정):
1. SecurityConfig에서 인증 요구하도록 수정 (라인 28-31)
2. chat-service에 독립적인 JWT 검증 추가
3. WebSocket 연결 인증 수정 (JwtChannelInterceptor)
4. unread count race condition 수정 (원자적 업데이트)
5. 입력 검증 및 정제 추가

### P1 (High - 스프린트 내 수정):
1. CORS를 알려진 origin으로 제한
2. 메시지에 페이징 구현
3. getSubUsers의 N+1 쿼리 수정
4. System.out.println 제거 (라인 330)
5. Feign 호출에 circuit breaker 구현
6. 포괄적인 보안 테스트 추가

### P2 (Medium - 곧 수정):
1. 메시지 타입 enum 구현
2. reactive/blocking 혼합 수정
3. 요청 검증 어노테이션 추가
4. 에러 처리 중앙화
5. 감사 로깅 구현

### P3 (Low - 있으면 좋음):
1. 국제화 (i18n)
2. 성능 최적화
3. 코드 커버리지 개선
4. API 문서 개선

---

## 파일 참조 요약

| 카테고리 | 파일 경로 | 이슈 개수 |
|----------|-----------|----------|
| Security | SecurityConfig.java | 2 CRITICAL |
| Security | JwtAuthFilter.java | 1 HIGH |
| Security | JwtChannelInterceptor.java | 2 HIGH |
| Config | WebSocketStompConfig.java | 1 HIGH |
| Services | ChatRoomService.java | 3 HIGH, 1 MEDIUM |
| Services | ChatService.java | 2 HIGH |
| Services | MessageService.java | 2 HIGH |
| Controllers | ChatRoomController.java | 2 MEDIUM |
| Controllers | MessageController.java | 1 MEDIUM |
| Repositories | ChatRoomService.java (calls) | 1 HIGH |
| Config | JwtUtils.java | 1 MEDIUM |
| Code Quality | ChatRoomService.java | 1 CRITICAL (System.out) |

---

*이 리뷰는 효율적인 수정을 위해 특정 라인 번호와 파일 경로로 조치 가능한 이슈를 식별합니다.*
