# Calendar Service

일정 관리 및 부서 관리 기능을 제공하는 마이크로서비스입니다.

## 기술 스택

- **Framework**: Spring Boot 3.3.7
- **Database**: MySQL (JPA/Hibernate)
- **Cache**: Redis
- **Storage**: AWS S3 (첨부파일)
- **API Docs**: SpringDoc OpenAPI (Swagger)

## 주요 기능

- 일정 CRUD
- 부서별 일정 조회
- 부서 관리
- 일정 알림 (etc-service 연동)

## API 엔드포인트

### 일정 API (`/api/schedules`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | 전체 일정 조회 (부서 기반) |
| POST | `/create-schedule` | 일정 생성 |
| PUT | `/modify-schedule/{id}` | 일정 수정 |
| DELETE | `/delete-schedule` | 일정 삭제 |

### 부서 API (`/api/departments`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | 전체 부서 목록 |
| GET | `/{id}` | 부서 상세 조회 |
| GET | `/map` | 부서 ID-이름 맵 조회 |
| POST | `/` | 부서 생성 |
| PUT | `/{id}` | 부서 전체 수정 |
| PATCH | `/{id}` | 부서 부분 수정 |
| DELETE | `/{id}` | 부서 삭제 |

## 데이터 모델

### Schedule (일정)

```json
{
  "scheduleId": "uuid",
  "title": "회의",
  "description": "주간 정기 회의",
  "startDate": "2024-01-15",
  "endDate": "2024-01-15",
  "departmentId": "team1",
  "createdBy": "user-uuid"
}
```

### Department (부서)

```json
{
  "departmentId": "team1",
  "name": "개발팀",
  "parentId": "dept1",
  "level": 2
}
```

## 일정 조회 로직

- **관리자 (team9)**: 전체 일정 조회
- **일반 사용자**: 본인 팀 + 상위 부서 일정 조회
  - team → dept → org 계층 구조

## 환경 변수

```yaml
# Database
SPRING_DATASOURCE_URL: jdbc:mysql://localhost:3306/orai
SPRING_DATASOURCE_USERNAME: orai
SPRING_DATASOURCE_PASSWORD: orai

# Redis
SPRING_DATA_REDIS_HOST: localhost
SPRING_DATA_REDIS_PORT: 6379

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
docker build -t calendar-service:latest .
```

## 포트

- **Application**: 8082
- **Actuator**: 8082/actuator

## 의존 서비스

- **discovery-service**: 서비스 등록
- **config-service**: 설정 관리
- **user-service**: 사용자 정보 조회 (Feign Client)
- **etc-service**: 알림 발송 (Feign Client)
