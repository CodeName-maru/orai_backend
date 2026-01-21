# User Service

사용자 인증, 관리, MFA(Multi-Factor Authentication)를 담당하는 마이크로서비스입니다.

## 기술 스택

- **Framework**: Spring Boot 3.3.7
- **Database**: MySQL (JPA/Hibernate)
- **Cache**: Redis
- **Storage**: AWS S3 (프로필 이미지)
- **Authentication**: JWT + Google Authenticator TOTP
- **API Docs**: SpringDoc OpenAPI (Swagger)

## 주요 기능

- 사용자 인증 (로그인/로그아웃)
- MFA (2단계 인증) 지원
- JWT 토큰 발급 및 갱신
- 사용자 CRUD
- 관리자 기능 (사용자 관리, 근태 조회)
- 휴가 관리
- 출퇴근 관리

## API 엔드포인트

### 인증 API (`/api/users`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/create` | 회원가입 |
| POST | `/login` | 로그인 (MFA 코드 요청) |
| POST | `/devLogin` | 개발용 로그인 (MFA 없음) |
| POST | `/validate-mfa` | MFA 코드 검증 및 토큰 발급 |
| POST | `/mfa/validate-code` | MFA 코드 검증 |
| POST | `/refresh` | Access Token 갱신 |
| GET | `/me` | 내 정보 조회 |
| GET | `/{userId}` | 사용자 정보 조회 |
| PUT | `/change-password` | 비밀번호 변경 |
| GET | `/check-email` | 이메일 중복 확인 |
| POST | `/list` | 사용자 목록 조회 (ID 리스트) |

### 관리자 API (`/api/admin`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/users/list` | 사용자 목록 조회 |
| GET | `/users/page` | 사용자 페이징 조회 |
| POST | `/users` | 사용자 생성 |
| PUT | `/users/info` | 사용자 정보 수정 |
| PATCH | `/users/actives` | 사용자 활성화 토글 |
| PATCH | `/users/position` | 사용자 직급 변경 |
| DELETE | `/users` | 사용자 삭제 |
| GET | `/attitudes` | 근태 조회 |

### 휴가 API (`/api/vacations`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | 휴가 목록 조회 |
| POST | `/` | 휴가 신청 |
| PUT | `/{id}` | 휴가 수정 |
| DELETE | `/{id}` | 휴가 취소 |

### 근태 API (`/api/attitudes`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/check-in` | 출근 |
| POST | `/check-out` | 퇴근 |
| GET | `/today` | 오늘 근태 조회 |

## 환경 변수

```yaml
# Database
SPRING_DATASOURCE_URL: jdbc:mysql://localhost:3306/orai
SPRING_DATASOURCE_USERNAME: orai
SPRING_DATASOURCE_PASSWORD: orai

# Redis
SPRING_DATA_REDIS_HOST: localhost
SPRING_DATA_REDIS_PORT: 6379

# JWT
JWT_SECRET_KEY: your-secret-key
JWT_ACCESS_TOKEN_EXPIRATION: 3600000
JWT_REFRESH_TOKEN_EXPIRATION: 604800000

# AWS S3
AWS_S3_BUCKET: your-bucket-name
AWS_ACCESS_KEY: your-access-key
AWS_SECRET_KEY: your-secret-key

# Eureka
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://localhost:8761/eureka
```

## 실행 방법

```bash
# 로컬 실행
./gradlew bootRun

# 빌드
./gradlew clean build

# Docker 빌드
docker build -t user-service:latest .
```

## 포트

- **Application**: 8081
- **Actuator**: 8081/actuator

## 의존 서비스

- **discovery-service**: 서비스 등록
- **config-service**: 설정 관리
- **calendar-service**: 부서 정보 조회 (Feign Client)
