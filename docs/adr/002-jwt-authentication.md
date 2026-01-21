# ADR 002: JWT 기반 인증 및 MFA 도입

## 상태

승인됨 (Accepted)

## 컨텍스트

마이크로서비스 아키텍처에서 사용자 인증은 다음과 같은 요구사항이 있습니다:

1. **Stateless**: 각 서비스가 독립적으로 인증 검증 가능
2. **보안**: 기업용 시스템으로 높은 보안 수준 필요
3. **확장성**: 서비스 수가 증가해도 인증 부하 분산
4. **사용자 경험**: 세션 만료 시에도 원활한 토큰 갱신

## 결정

**JWT (JSON Web Token) 기반 인증과 MFA (Multi-Factor Authentication)를 도입합니다.**

### 인증 플로우

```
┌──────────┐     1. Login (email, password)      ┌──────────────┐
│  Client  │ ─────────────────────────────────► │ User Service │
│          │                                     │              │
│          │ ◄───────────────────────────────── │              │
│          │     2. MFA Required + userId        │              │
│          │                                     │              │
│          │     3. Validate MFA (userId, code)  │              │
│          │ ─────────────────────────────────► │              │
│          │                                     │              │
│          │ ◄───────────────────────────────── │              │
│          │     4. Access Token + Refresh Token │              │
└──────────┘                                     └──────────────┘
      │
      │ 5. API Request (Authorization: Bearer <token>)
      ▼
┌──────────────┐     6. Route + Validate JWT     ┌──────────────┐
│   Gateway    │ ─────────────────────────────► │ Other Service│
│   Service    │                                 │              │
└──────────────┘                                 └──────────────┘
```

### JWT 구조

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "user-uuid",
    "email": "user@example.com",
    "departmentId": "team1",
    "role": "USER",
    "iat": 1704067200,
    "exp": 1704070800
  }
}
```

### 토큰 전략

| 토큰 | 만료 시간 | 저장 위치 | 용도 |
|------|-----------|-----------|------|
| Access Token | 1시간 | 메모리/LocalStorage | API 요청 인증 |
| Refresh Token | 7일 | Redis | Access Token 갱신 |

### MFA 구현

- **방식**: Google Authenticator TOTP (Time-based One-Time Password)
- **라이브러리**: `com.warrenstrange:googleauth`
- **시크릿 저장**: 사용자별 암호화된 시크릿 DB 저장
- **검증**: 30초 간격 6자리 코드

### Gateway 인증 검증

```java
// AuthorizationHeaderFilter.java
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String token = extractToken(exchange);

    if (isExcludedPath(exchange.getRequest().getPath())) {
        return chain.filter(exchange);
    }

    if (!jwtTokenProvider.validateToken(token)) {
        return onError(exchange, HttpStatus.UNAUTHORIZED);
    }

    // 사용자 정보를 헤더에 추가하여 하위 서비스로 전달
    Claims claims = jwtTokenProvider.getClaims(token);
    ServerHttpRequest request = exchange.getRequest().mutate()
        .header("X-User-Id", claims.getSubject())
        .header("X-User-Email", claims.get("email", String.class))
        .header("X-User-DepartmentId", claims.get("departmentId", String.class))
        .build();

    return chain.filter(exchange.mutate().request(request).build());
}
```

### 각 서비스 인증 처리

```java
// JwtAuthFilter.java (각 서비스)
@Override
protected void doFilterInternal(HttpServletRequest request, ...) {
    String userId = request.getHeader("X-User-Id");
    String email = request.getHeader("X-User-Email");
    String departmentId = request.getHeader("X-User-DepartmentId");

    if (userId != null) {
        TokenUserInfo userInfo = new TokenUserInfo(userId, email, departmentId);
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userInfo, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
```

## 대안 검토

### 1. Session 기반 인증

**장점**:
- 서버 측 세션 무효화 용이
- 토큰 탈취 시 즉시 대응 가능

**단점**:
- Stateful하여 수평 확장 어려움
- 서비스 간 세션 공유 필요 (Sticky Session 또는 Redis Session)

### 2. OAuth 2.0 / OIDC

**장점**:
- 표준화된 프로토콜
- 외부 IdP 연동 용이

**단점**:
- 내부 시스템에 과도한 복잡도
- 추가 인프라 (Authorization Server) 필요

### 3. API Key 방식

**장점**:
- 구현 단순
- 서비스 간 통신에 적합

**단점**:
- 사용자 인증에는 부적합
- 키 갱신 메커니즘 필요

## 결과

### 긍정적 결과

1. **Stateless 인증**: Gateway에서 토큰 검증 후 각 서비스로 사용자 정보 전달
2. **보안 강화**: MFA로 계정 탈취 위험 감소
3. **확장성**: 토큰 검증은 로컬에서 수행, 별도 인증 서버 호출 불필요
4. **유연한 갱신**: Refresh Token으로 사용자 경험 개선

### 부정적 결과 (Trade-offs)

1. **토큰 무효화 어려움**: 발급된 토큰은 만료까지 유효
2. **토큰 크기**: Session ID 대비 큰 페이로드
3. **MFA 의존성**: Google Authenticator 앱 필수

### 완화 전략

- **토큰 블랙리스트**: Redis에 로그아웃된 토큰 저장
- **짧은 Access Token 만료**: 1시간으로 설정하여 탈취 영향 최소화
- **Refresh Token Rotation**: 갱신 시 새로운 Refresh Token 발급

## 보안 고려사항

1. **Secret Key**: 환경변수로 관리, 256비트 이상 사용
2. **HTTPS**: 모든 통신 TLS 암호화
3. **XSS 방지**: HttpOnly 쿠키 또는 메모리 저장
4. **CSRF 방지**: SameSite 쿠키 속성 활용

## 참조

- [JWT RFC 7519](https://tools.ietf.org/html/rfc7519)
- [TOTP RFC 6238](https://tools.ietf.org/html/rfc6238)
- [OWASP JWT Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
