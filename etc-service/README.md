# Etc Service

푸시 알림 및 첨부파일 관리 기능을 제공하는 마이크로서비스입니다.

## 기술 스택

- **Framework**: Spring Boot 3.3.7
- **Database**: MySQL (JPA/Hibernate)
- **Cache**: Redis (Pub/Sub)
- **Real-time**: Server-Sent Events (SSE)
- **API Docs**: SpringDoc OpenAPI (Swagger)

## 주요 기능

- 실시간 알림 (SSE)
- 알림 CRUD
- 첨부파일 관리
- Redis Pub/Sub 기반 알림 분산 처리

## API 엔드포인트

### 알림 API (`/api/notifications`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/subscribe` | SSE 알림 구독 |
| GET | `/` | 알림 목록 조회 |
| GET | `/count` | 읽지 않은 알림 개수 |
| POST | `/` | 알림 생성 |

### 첨부파일 API (`/api/attachments`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/` | 첨부파일 생성 |
| GET | `/{id}` | 첨부파일 조회 |
| PUT | `/{id}` | 첨부파일 수정 |
| DELETE | `/{id}` | 첨부파일 삭제 |

## SSE 연결

### 구독 (Subscribe)

```javascript
const eventSource = new EventSource('/api/notifications/subscribe', {
  headers: {
    'Authorization': 'Bearer ' + token
  }
});

eventSource.onmessage = (event) => {
  const notification = JSON.parse(event.data);
  console.log('New notification:', notification);
};

eventSource.onerror = (error) => {
  console.error('SSE Error:', error);
  eventSource.close();
};
```

### 알림 데이터 형식

```json
{
  "notificationId": "uuid",
  "userId": "user-uuid",
  "type": "SCHEDULE",
  "message": "새로운 일정이 등록되었습니다.",
  "isRead": false,
  "createdAt": "2024-01-15T10:30:00"
}
```

## 알림 타입

| Type | Description |
|------|-------------|
| SCHEDULE | 일정 관련 알림 |
| CHAT | 채팅 관련 알림 |
| VACATION | 휴가 관련 알림 |
| SYSTEM | 시스템 알림 |

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
docker build -t etc-service:latest .
```

## 포트

- **Application**: 8083
- **Actuator**: 8083/actuator

## 의존 서비스

- **discovery-service**: 서비스 등록
- **config-service**: 설정 관리

## 아키텍처

```
[Calendar/Chat/User Service]
         |
         v
    [Redis Pub/Sub]
         |
         v
    [Etc Service]
         |
         v
    [SSE Connection]
         |
         v
     [Client]
```

알림은 Redis Pub/Sub을 통해 수신되어 각 사용자의 SSE 연결로 전달됩니다.
