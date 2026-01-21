# Chat Service

실시간 채팅 기능을 제공하는 마이크로서비스입니다. WebSocket/STOMP 프로토콜을 사용합니다.

## 기술 스택

- **Framework**: Spring Boot 3.3.7
- **Database**:
  - MySQL (채팅방 메타데이터)
  - MongoDB (메시지 저장)
- **Cache**: Caffeine Cache
- **Messaging**: WebSocket + STOMP
- **Storage**: AWS S3 (채팅방 이미지)
- **API Docs**: SpringDoc OpenAPI (Swagger)

## 주요 기능

- 실시간 메시지 송수신 (WebSocket)
- 채팅방 CRUD
- 채팅방 참여자 관리
- 메시지 CRUD
- 읽지 않은 메시지 카운트
- 채팅방 이미지 관리

## API 엔드포인트

### 채팅방 API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/createChatRoom` | 채팅방 생성 |
| GET | `/chatRoomList` | 참여 중인 채팅방 목록 |
| GET | `/{chatRoomId}/chatRoom` | 채팅방 상세 조회 |
| PUT | `/{chatRoomId}/updateChatRoom` | 채팅방 수정 |
| DELETE | `/{chatRoomId}/deleteChatRoom` | 채팅방 삭제 |
| DELETE | `/{chatRoomId}/disconnect` | 채팅방 나가기 |

### 채팅방 참여자 API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/{chatRoomId}/users` | 채팅방 참여자 목록 |
| POST | `/{chatRoomId}/invite` | 사용자 초대 |
| DELETE | `/{chatRoomId}/{userId}/deleteUser` | 사용자 내보내기 |

### 메시지 API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/{chatRoomId}/messageList` | 메시지 목록 조회 |
| PUT | `/{chatRoomId}/{messageId}/updateMessage` | 메시지 수정 |
| DELETE | `/{chatRoomId}/{messageId}/deleteMessage` | 메시지 삭제 |

### 사용자 조회 API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/{userId}/profile` | 사용자 프로필 조회 |
| POST | `/userList` | 사용자 목록 조회 |

## WebSocket 엔드포인트

### 연결

```
ws://localhost:8084/ws-stomp
```

### Subscribe (메시지 수신)

```
/sub/{chatRoomId}/chat
```

### Publish (메시지 전송)

```
/pub/chat/message
```

### 메시지 형식

```json
{
  "chatRoomId": 1,
  "content": "Hello, World!",
  "senderId": "user-uuid"
}
```

## 환경 변수

```yaml
# MySQL
SPRING_DATASOURCE_URL: jdbc:mysql://localhost:3306/orai
SPRING_DATASOURCE_USERNAME: orai
SPRING_DATASOURCE_PASSWORD: orai

# MongoDB
SPRING_DATA_MONGODB_URI: mongodb://localhost:27017/orai_chat

# Redis
SPRING_DATA_REDIS_HOST: localhost
SPRING_DATA_REDIS_PORT: 6379

# AWS S3
AWS_S3_BUCKET: your-bucket-name

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
docker build -t chat-service:latest .
```

## 포트

- **Application**: 8084
- **WebSocket**: 8084/ws-stomp
- **Actuator**: 8084/actuator

## 의존 서비스

- **discovery-service**: 서비스 등록
- **config-service**: 설정 관리
- **user-service**: 사용자 정보 조회 (Feign Client)
