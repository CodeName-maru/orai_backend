# Calendar Service 코드 리뷰

**리뷰 일자**: 2026-01-21
**대상 서비스**: calendar-service
**리뷰어**: Claude Code

---

## 1. 개요

calendar-service는 Orai 메신저 시스템의 일정 관리 마이크로서비스입니다. 일정 생성/수정/삭제, 부서별 일정 조회, 알림 발송 등의 기능을 제공합니다.

### 기술 스택
- Java 17, Spring Boot 3.x
- Spring Cloud (Eureka, OpenFeign)
- JPA + MySQL
- Redis (캐싱 및 Pub/Sub)
- AWS S3

---

## 2. 아키텍처 분석

### 2.1 패키지 구조 (양호)
```
com.ovengers.calendarservice/
├── client/          # Feign 클라이언트
├── common/          # 공통 DTO, 인증
├── configs/         # 설정 클래스
├── controller/      # REST 컨트롤러
├── dto/             # 요청/응답 DTO
├── entity/          # JPA 엔티티
├── repository/      # 데이터 접근
└── service/         # 비즈니스 로직
```

계층 분리가 명확하고 표준적인 Spring 패키지 구조를 따르고 있습니다.

---

## 3. 주요 이슈 (심각도별 분류)

### 3.1 Critical (즉시 수정 필요)

#### [C-01] 예외 처리 일관성 부족
**위치**: 전역
**문제**: `RuntimeException`, `IllegalArgumentException` 등을 무분별하게 사용하여 클라이언트에게 적절한 HTTP 상태 코드를 반환하지 못함.

```java
// CalendarService.java:85
throw new RuntimeException("Department not found: " + userInfo.getDepartmentId());

// DepartmentService.java:28
throw new IllegalArgumentException("Department not found");
```

**권장 사항**:
- 커스텀 예외 클래스 생성 (e.g., `ScheduleNotFoundException`, `DepartmentNotFoundException`)
- `@RestControllerAdvice`를 통한 전역 예외 핸들러 구현
- 적절한 HTTP 상태 코드 매핑 (404 Not Found, 400 Bad Request 등)

---

#### [C-02] 인증 헤더 검증 미흡
**위치**: `JwtAuthFilter.java:42`
**문제**: `X-User-Id` 헤더만 검증하고 `X-User-DepartmentId`는 검증하지 않음. 악의적 사용자가 조작된 헤더를 보낼 경우 문제 발생 가능.

```java
if (userId != null) {
    // departmentId가 null이어도 인증이 성공함
    Authentication auth = new UsernamePasswordAuthenticationToken(
            new TokenUserInfo(userId, departmentId), // departmentId가 null일 수 있음
            "", authorities
    );
}
```

**권장 사항**:
- `departmentId`도 필수 검증 추가
- Gateway에서 전달되는 헤더의 무결성 검증 로직 추가

---

#### [C-03] SQL Injection 가능성
**위치**: `CalendarRepository.java:17, 21, 26`
**문제**: Native Query 사용 시 파라미터 바인딩이 올바르게 적용되었지만, JPQL과 Native Query가 혼용되어 있음.

**권장 사항**:
- 가능한 JPQL 또는 QueryDSL 사용으로 통일
- Native Query가 필요한 경우 (재귀 쿼리 등) 명시적 문서화

---

### 3.2 High (조속한 수정 권장)

#### [H-01] 트랜잭션 관리 불완전
**위치**: `CalendarService.java`
**문제**: `createSchedule` 메서드에 `@Transactional` 누락. 알림 생성 실패 시 일정 생성 롤백되지 않음.

```java
// @Transactional 없음
public ScheduleResponseDto createSchedule(TokenUserInfo userInfo, ScheduleRequestDto scheduleRequestDto) {
    // ...
    Schedule savedSchedule = calendarRepository.save(schedule);
    // createNotification(savedSchedule); // 주석 처리됨
    return toDto(savedSchedule);
}
```

**권장 사항**:
- 서비스 메서드에 적절한 `@Transactional` 어노테이션 추가
- 읽기 전용 메서드에는 `@Transactional(readOnly = true)` 사용

---

#### [H-02] 하드코딩된 값들
**위치**: 여러 파일
**문제**:

| 위치 | 하드코딩된 값 |
|------|-------------|
| `CalendarController.java:40` | `"team9"` (관리자 부서 ID) |
| `CalendarService.java:110-118` | `"team"`, `"dept"`, `"org"` 접두사 |
| `UserServiceClient.java:13` | 서비스 URL |

```java
// CalendarController.java:40
if ("team9".equals(departmentId)) {
    schedules = calendarService.getAllSchedules();
}
```

**권장 사항**:
- 환경 변수 또는 설정 파일로 외부화
- Enum 또는 상수 클래스로 관리

---

#### [H-03] Feign Client URL 하드코딩
**위치**: `UserServiceClient.java:13`, `EtcServiceClient.java:9`
**문제**: Kubernetes 내부 DNS 주소가 하드코딩되어 있어 로컬 개발 환경에서 문제 발생.

```java
@FeignClient(name = "user-service", url = "http://user-service.default.svc.cluster.local:8081")
```

**권장 사항**:
- `url` 속성 제거하고 Eureka 서비스 디스커버리 활용
- 또는 `${feign.client.config.user-service.url}` 형태로 프로파일별 설정

---

#### [H-04] N+1 쿼리 문제 가능성
**위치**: `DepartmentResDto.java:25`
**문제**: `parent` 관계가 `LAZY` 로딩이지만, DTO 변환 시 `getParent()`를 호출하여 추가 쿼리 발생.

```java
public DepartmentResDto(Department department) {
    // ...
    this.parentId = department.getParent() == null ? "" : department.getParent().getDepartmentId();
}
```

**권장 사항**:
- `@EntityGraph` 사용하여 필요한 연관 관계 즉시 로딩
- 또는 DTO 프로젝션 쿼리 사용

---

### 3.3 Medium (개선 권장)

#### [M-01] 응답 DTO 일관성 부족
**위치**: `CalendarController.java`, `DepartmentController.java`
**문제**: 응답 형식이 일관되지 않음.

| 메서드 | 응답 형식 |
|--------|----------|
| `CalendarController.getAllSchedules()` | `List<ScheduleResponseDto>` 직접 반환 |
| `DepartmentController.getAllDepartments()` | `CommonResDto`로 래핑 |
| `DepartmentController.getDepartmentById()` | `Department` 엔티티 직접 반환 |

**권장 사항**:
- 모든 응답을 `CommonResDto`로 통일
- 엔티티를 직접 반환하지 않고 항상 DTO 사용

---

#### [M-02] 입력 유효성 검사 누락
**위치**: `ScheduleRequestDto.java`, `DepartmentRequestDto.java`
**문제**: `@Valid`, `@NotNull`, `@Size` 등 Bean Validation 어노테이션 누락.

```java
// ScheduleRequestDto.java
@Getter
@Setter
@Builder
public class ScheduleRequestDto {
    private String title;  // @NotBlank 필요
    private String description;
    // ...
}
```

**권장 사항**:
- 모든 요청 DTO에 적절한 검증 어노테이션 추가
- 컨트롤러에서 `@Valid` 어노테이션 사용

---

#### [M-03] 로깅 개선 필요
**위치**: `ScheduleTaskService.java`
**문제**: `System.out.println()` 사용.

```java
System.out.println("스케줄 조회 작업 실행: " + today);
```

**권장 사항**:
- SLF4J 로거 사용 (`@Slf4j`)
- 적절한 로그 레벨 설정 (INFO, DEBUG, ERROR)

---

#### [M-04] 미사용 코드
**위치**: 여러 파일
**문제**:

| 파일 | 내용 |
|------|------|
| `CalendarController.java:99-121` | 주석 처리된 파일 업로드 코드 |
| `CalendarService.java:129-155` | 주석 처리된 메서드들 |
| `CalendarService.java:183-185` | 빈 구현체 (`return null`) |
| `ScheduledNotificationService.java` | 전체가 주석 처리됨 |
| `WebConfig.java` | 주석 처리된 CORS 설정 |

**권장 사항**:
- 미사용 코드 제거 또는 별도 브랜치로 관리
- `TODO` 주석 추가하여 추후 구현 의도 명시

---

#### [M-05] 네이밍 컨벤션 위반
**위치**: `ScheduleResponseDto.java:19`, `TokenUserInfo.java:15`
**문제**:

```java
// ScheduleResponseDto.java - 대문자로 시작
private String ScheduleId;  // scheduleId여야 함

// TokenUserInfo.java - 대문자로 시작
private String Role;  // role이어야 함
```

**권장 사항**:
- Java 네이밍 컨벤션 준수 (필드명은 camelCase)

---

#### [M-06] 날짜 타입 혼용
**위치**: `Schedule.java`, `CalendarRepository.java`
**문제**: `LocalDate`와 `LocalDateTime` 혼용으로 타입 불일치 가능성.

```java
// Schedule.java - LocalDate 사용
private LocalDate startTime;
private LocalDate endTime;

// CalendarRepository.java:14 - LocalDateTime 파라미터
List<Schedule> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
```

**권장 사항**:
- 비즈니스 요구사항에 맞는 타입으로 통일
- 시간이 필요 없으면 `LocalDate`, 필요하면 `LocalDateTime` 일관 사용

---

### 3.4 Low (권장 사항)

#### [L-01] 테스트 코드 부재
**문제**: 단위 테스트, 통합 테스트 코드 확인 불가.

**권장 사항**:
- 서비스 계층 단위 테스트 추가
- 컨트롤러 통합 테스트 추가 (MockMvc)
- Repository 테스트 추가 (@DataJpaTest)

---

#### [L-02] API 문서화 개선
**위치**: 컨트롤러 클래스들
**문제**: Swagger 어노테이션 (`@Operation`, `@ApiResponse` 등) 누락.

**권장 사항**:
- OpenAPI 어노테이션 추가하여 API 문서 자동 생성

---

#### [L-03] 매직 넘버
**위치**: `ScheduleTaskService.java:18`
**문제**: 스케줄 cron 표현식이 코드에 직접 작성됨.

```java
@Scheduled(cron = "0 0 8 * * MON-FRI")
```

**권장 사항**:
- `@Value("${schedule.notification.cron}")` 형태로 설정 파일에서 읽기

---

## 4. 보안 검토

### 4.1 양호한 점
- JWT 기반 인증 사용
- CSRF 비활성화 (API 서버로서 적절)
- Stateless 세션 관리

### 4.2 개선 필요 사항
| 항목 | 현재 상태 | 권장 사항 |
|------|----------|----------|
| 인증 헤더 검증 | `X-User-Id`만 검증 | 모든 필수 헤더 검증 |
| Rate Limiting | 미구현 | API Gateway 또는 서비스 레벨에서 구현 |
| 입력 검증 | 일부 누락 | Bean Validation 전면 적용 |
| 감사 로깅 | 기본 로깅만 | 보안 이벤트 로깅 추가 |

---

## 5. 성능 검토

### 5.1 잠재적 병목 지점

| 위치 | 문제 | 영향 |
|------|------|------|
| `DepartmentRepository.findAllParentDepartmentIds()` | 재귀 쿼리 | 깊은 계층 구조에서 성능 저하 |
| `CalendarService.getAllSchedules()` | 전체 조회 | 데이터 증가 시 문제 |
| `DepartmentResDto` 생성 시 | N+1 쿼리 | parent 로딩 |

### 5.2 권장 개선 사항
- 재귀 쿼리 결과 캐싱 (부서 구조는 자주 변경되지 않음)
- 페이지네이션 적용 (전체 일정 조회)
- 배치 조회를 위한 `@EntityGraph` 사용

---

## 6. 개선 우선순위

### 즉시 (1주 내)
1. [C-01] 예외 처리 통일 및 전역 핸들러 구현
2. [C-02] 인증 헤더 검증 강화
3. [H-01] `@Transactional` 적용

### 단기 (1개월 내)
4. [H-02] 하드코딩된 값 외부화
5. [H-03] Feign Client 설정 개선
6. [M-01] 응답 형식 통일
7. [M-02] 입력 유효성 검사 추가

### 중기 (분기 내)
8. [M-04] 미사용 코드 정리
9. [H-04] N+1 문제 해결
10. [L-01] 테스트 코드 작성

---

## 7. 코드 품질 점수

| 항목 | 점수 (10점 만점) | 비고 |
|------|-----------------|------|
| 아키텍처 | 8 | 표준적인 구조, 계층 분리 양호 |
| 코드 가독성 | 7 | 전반적으로 깔끔하나 일부 네이밍 이슈 |
| 예외 처리 | 4 | 체계적인 예외 처리 부재 |
| 보안 | 6 | 기본 인증은 있으나 검증 미흡 |
| 성능 | 6 | 잠재적 병목 존재 |
| 테스트 | N/A | 테스트 코드 확인 불가 |
| 문서화 | 5 | Swagger 설정은 있으나 상세 문서 부족 |

**종합 점수**: 6.0 / 10

---

## 8. 결론

calendar-service는 기본적인 마이크로서비스 아키텍처를 잘 따르고 있으며, 핵심 기능은 정상적으로 구현되어 있습니다. 그러나 예외 처리, 입력 검증, 트랜잭션 관리 등 안정성과 관련된 부분에서 개선이 필요합니다.

가장 시급한 개선 사항은:
1. 예외 처리 체계화
2. 인증 강화
3. 트랜잭션 관리

위 사항들을 개선하면 서비스의 안정성과 유지보수성이 크게 향상될 것입니다.
