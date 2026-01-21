# ADR 003: 공통 모듈(orai-common) 추출

## 상태

승인됨 (Accepted)

## 컨텍스트

현재 Orai 프로젝트에서 다음과 같은 코드가 여러 서비스에 중복으로 존재합니다:

| 파일 | 중복 서비스 |
|------|------------|
| `CommonResDto.java` | user, chat, calendar, etc |
| `CommonErrorDto.java` | user, chat, calendar, etc |
| `JwtAuthFilter.java` | user, chat, calendar, etc |
| `TokenUserInfo.java` | user, chat, calendar, etc |
| `GlobalExceptionHandler.java` | chat (others missing) |

이로 인한 문제점:

1. **유지보수 어려움**: 동일한 수정을 여러 서비스에 반복 적용
2. **불일치 위험**: 서비스별로 미세한 차이 발생 가능
3. **테스트 중복**: 동일한 코드에 대한 테스트 반복 작성

## 결정

**공통 코드를 `orai-common` 모듈로 추출합니다.**

### 모듈 구조

```
orai-common/
├── build.gradle
└── src/
    ├── main/java/com/ovengers/common/
    │   ├── auth/
    │   │   ├── JwtAuthFilter.java
    │   │   └── TokenUserInfo.java
    │   ├── dto/
    │   │   ├── CommonResDto.java
    │   │   └── CommonErrorDto.java
    │   ├── exception/
    │   │   ├── BusinessException.java
    │   │   ├── ErrorCode.java
    │   │   └── GlobalExceptionHandler.java
    │   └── config/
    │       └── BaseSecurityConfig.java
    └── test/java/com/ovengers/common/
        └── ...
```

### 의존성 설정

**orai-common/build.gradle**:
```groovy
plugins {
    id 'java-library'
    id 'org.springframework.boot' version '3.3.7' apply false
    id 'io.spring.dependency-management' version '1.1.4'
}

dependencyManagement {
    imports {
        mavenBom org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES
    }
}

dependencies {
    api 'org.springframework.boot:spring-boot-starter-web'
    api 'org.springframework.boot:spring-boot-starter-security'
    api 'io.jsonwebtoken:jjwt-api:0.11.5'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

**각 서비스 build.gradle**:
```groovy
dependencies {
    implementation project(':orai-common')
    // 기존 중복 의존성 제거
}
```

**settings.gradle (루트)**:
```groovy
rootProject.name = 'orai-backend'

include 'orai-common'
include 'user-service'
include 'chat-service'
include 'calendar-service'
include 'etc-service'
include 'gateway-service'
include 'config-service'
include 'discovery-service'
```

### CommonResDto 개선

**Before (서비스별로 다른 구현)**:
```java
// user-service
public class CommonResDto {
    private HttpStatus status;
    private String message;
    private Object data;
}

// chat-service
public class CommonResDto<T> {
    private int statusCode;
    private String msg;
    private T result;
}
```

**After (통합)**:
```java
@Getter
@NoArgsConstructor
public class CommonResDto<T> {
    private int statusCode;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    public CommonResDto(HttpStatus status, String message, T data) {
        this.statusCode = status.value();
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }
}
```

### ErrorCode Enum

```java
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Common
    INVALID_INPUT(400, "C001", "잘못된 입력입니다"),
    INTERNAL_ERROR(500, "C002", "서버 내부 오류입니다"),

    // User
    USER_NOT_FOUND(404, "U001", "사용자를 찾을 수 없습니다"),
    INVALID_PASSWORD(401, "U002", "비밀번호가 일치하지 않습니다"),
    DUPLICATE_EMAIL(409, "U003", "이미 존재하는 이메일입니다"),
    INVALID_MFA_CODE(401, "U004", "잘못된 MFA 코드입니다"),

    // Chat
    CHAT_ROOM_NOT_FOUND(404, "CH001", "채팅방을 찾을 수 없습니다"),
    NOT_CHAT_ROOM_MEMBER(403, "CH002", "채팅방 멤버가 아닙니다"),

    // Calendar
    SCHEDULE_NOT_FOUND(404, "CA001", "일정을 찾을 수 없습니다"),
    DEPARTMENT_NOT_FOUND(404, "CA002", "부서를 찾을 수 없습니다"),

    // Notification
    NOTIFICATION_NOT_FOUND(404, "N001", "알림을 찾을 수 없습니다");

    private final int status;
    private final String code;
    private final String message;
}
```

### BusinessException

```java
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
```

### GlobalExceptionHandler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonErrorDto> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
            .status(errorCode.getStatus())
            .body(new CommonErrorDto(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonErrorDto> handleValidationException(
            MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity
            .badRequest()
            .body(new CommonErrorDto(ErrorCode.INVALID_INPUT, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonErrorDto> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new CommonErrorDto(ErrorCode.INTERNAL_ERROR));
    }
}
```

## 대안 검토

### 1. 코드 복사 유지

**장점**:
- 서비스별 독립성 유지
- 빌드 의존성 없음

**단점**:
- 유지보수 부담
- 버전 불일치 위험

### 2. Maven Central 라이브러리 배포

**장점**:
- 완전한 버전 관리
- 외부 프로젝트에서도 사용 가능

**단점**:
- 배포 파이프라인 복잡
- 개발 중 빠른 반복에 부적합

### 3. Git Submodule

**장점**:
- 별도 저장소로 관리
- 버전 고정 가능

**단점**:
- Git 워크플로우 복잡
- IDE 지원 제한적

## 결과

### 긍정적 결과

1. **단일 진실 공급원**: 공통 코드 변경 시 한 곳만 수정
2. **일관성**: 모든 서비스에서 동일한 응답 형식
3. **테스트 효율**: 공통 코드는 한 번만 테스트
4. **의존성 정리**: 중복 의존성 제거

### 부정적 결과 (Trade-offs)

1. **빌드 의존성**: orai-common 변경 시 모든 서비스 재빌드 필요
2. **버전 관리**: 공통 모듈 버전과 서비스 버전 동기화 필요
3. **순환 의존성 주의**: 공통 모듈이 서비스 코드 참조 금지

### 완화 전략

- **Gradle 캐싱**: 변경 없는 경우 캐시된 빌드 사용
- **시맨틱 버저닝**: 공통 모듈 버전 관리
- **CI 파이프라인**: 공통 모듈 변경 시 전체 서비스 테스트

## 마이그레이션 계획

1. **Phase 1**: orai-common 모듈 생성 및 코드 이동
2. **Phase 2**: 각 서비스 build.gradle 수정
3. **Phase 3**: 기존 중복 파일 삭제
4. **Phase 4**: 테스트 및 검증

## 참조

- [Gradle Multi-Project Builds](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [Spring Boot Multi-Module Project](https://spring.io/guides/gs/multi-module/)
