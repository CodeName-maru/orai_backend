package com.ovengers.userservice.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtTokenProvider 단위 테스트")
class JwtTokenProviderTest {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProviderTest.class);

    private JwtTokenProvider jwtTokenProvider;

    private static final String TEST_SECRET_KEY = "testSecretKeyForJwtTokenMustBeLongEnough12345";
    private static final String TEST_SECRET_KEY_RT = "testRefreshSecretKeyForJwtTokenMustBeLongEnough12345";
    private static final int TEST_EXPIRATION = 3600; // 1시간 (초 단위)
    private static final int TEST_EXPIRATION_RT = 86400; // 24시간 (초 단위)

    @BeforeEach
    void setUp() {
        logger.info("===== 테스트 환경 초기화 =====");

        jwtTokenProvider = new JwtTokenProvider();

        ReflectionTestUtils.setField(jwtTokenProvider, "secretKey", TEST_SECRET_KEY);
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKeyRt", TEST_SECRET_KEY_RT);
        ReflectionTestUtils.setField(jwtTokenProvider, "expiration", TEST_EXPIRATION);
        ReflectionTestUtils.setField(jwtTokenProvider, "expirationRt", TEST_EXPIRATION_RT);

        logger.info("JwtTokenProvider 초기화 완료");
        logger.info("- Access Token 만료시간: {}초", TEST_EXPIRATION);
        logger.info("- Refresh Token 만료시간: {}초", TEST_EXPIRATION_RT);
    }

    @Nested
    @DisplayName("Access Token 생성 테스트")
    class CreateAccessTokenTest {

        @Test
        @DisplayName("Access Token 생성 성공")
        void Access_Token_생성_성공() {
            logger.info("===== Access Token 생성 테스트 시작 =====");

            // given
            String userId = "user-123";
            String email = "test@example.com";
            String departmentId = "dept-001";

            // when
            logger.info("토큰 생성 - userId: {}, email: {}, departmentId: {}", userId, email, departmentId);
            String token = jwtTokenProvider.createToken(userId, email, departmentId);

            // then
            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();
            assertThat(token.split("\\.")).hasSize(3); // JWT 형식 검증 (header.payload.signature)

            logger.info("Access Token 생성 성공: {}...", token.substring(0, Math.min(50, token.length())));
        }

        @Test
        @DisplayName("Access Token에서 userId 추출 성공")
        void Access_Token에서_userId_추출_성공() {
            logger.info("===== Token에서 userId 추출 테스트 시작 =====");

            // given
            String userId = "user-123";
            String email = "test@example.com";
            String departmentId = "dept-001";
            String token = jwtTokenProvider.createToken(userId, email, departmentId);

            // when
            logger.info("생성된 토큰에서 userId 추출");
            String extractedUserId = jwtTokenProvider.getUserIdFromToken(token);

            // then
            assertThat(extractedUserId).isEqualTo(userId);
            logger.info("userId 추출 성공: {}", extractedUserId);
        }

        @Test
        @DisplayName("Access Token에 departmentId 포함 확인")
        void Access_Token에_departmentId_포함_확인() {
            logger.info("===== Token에 departmentId 포함 확인 테스트 시작 =====");

            // given
            String userId = "user-123";
            String email = "test@example.com";
            String departmentId = "dept-001";
            String token = jwtTokenProvider.createToken(userId, email, departmentId);

            // when
            Claims claims = Jwts.parser()
                    .setSigningKey(TEST_SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();

            // then
            assertThat(claims.get("departmentId", String.class)).isEqualTo(departmentId);
            assertThat(claims.get("email", String.class)).isEqualTo(email);

            logger.info("Token claims 검증 성공 - departmentId: {}, email: {}",
                    claims.get("departmentId"), claims.get("email"));
        }

        @Test
        @DisplayName("Access Token 만료시간 검증")
        void Access_Token_만료시간_검증() {
            logger.info("===== Token 만료시간 검증 테스트 시작 =====");

            // given
            String userId = "user-123";
            String email = "test@example.com";
            String departmentId = "dept-001";
            String token = jwtTokenProvider.createToken(userId, email, departmentId);

            // when
            Claims claims = Jwts.parser()
                    .setSigningKey(TEST_SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();

            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();

            // then
            long expirationDiff = (expiration.getTime() - issuedAt.getTime()) / 1000;
            assertThat(expirationDiff).isEqualTo(TEST_EXPIRATION);

            logger.info("Token 만료시간 검증 성공 - 발급시간: {}, 만료시간: {}, 차이: {}초",
                    issuedAt, expiration, expirationDiff);
        }
    }

    @Nested
    @DisplayName("Refresh Token 생성 테스트")
    class CreateRefreshTokenTest {

        @Test
        @DisplayName("Refresh Token 생성 성공")
        void Refresh_Token_생성_성공() {
            logger.info("===== Refresh Token 생성 테스트 시작 =====");

            // given
            String userId = "user-123";
            String departmentId = "dept-001";

            // when
            logger.info("Refresh Token 생성 - userId: {}, departmentId: {}", userId, departmentId);
            String refreshToken = jwtTokenProvider.createRefreshToken(userId, departmentId);

            // then
            assertThat(refreshToken).isNotNull();
            assertThat(refreshToken).isNotEmpty();
            assertThat(refreshToken.split("\\.")).hasSize(3);

            logger.info("Refresh Token 생성 성공: {}...", refreshToken.substring(0, Math.min(50, refreshToken.length())));
        }

        @Test
        @DisplayName("Refresh Token에서 userId 추출 성공")
        void Refresh_Token에서_userId_추출_성공() {
            logger.info("===== Refresh Token에서 userId 추출 테스트 시작 =====");

            // given
            String userId = "user-123";
            String departmentId = "dept-001";
            String refreshToken = jwtTokenProvider.createRefreshToken(userId, departmentId);

            // when
            logger.info("Refresh Token에서 userId 추출");
            String extractedUserId = jwtTokenProvider.getUserIdFromRefreshToken(refreshToken);

            // then
            assertThat(extractedUserId).isEqualTo(userId);
            logger.info("userId 추출 성공: {}", extractedUserId);
        }

        @Test
        @DisplayName("Refresh Token 만료시간 검증")
        void Refresh_Token_만료시간_검증() {
            logger.info("===== Refresh Token 만료시간 검증 테스트 시작 =====");

            // given
            String userId = "user-123";
            String departmentId = "dept-001";
            String refreshToken = jwtTokenProvider.createRefreshToken(userId, departmentId);

            // when
            Claims claims = Jwts.parser()
                    .setSigningKey(TEST_SECRET_KEY_RT)
                    .parseClaimsJws(refreshToken)
                    .getBody();

            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();

            // then
            long expirationDiff = (expiration.getTime() - issuedAt.getTime()) / 1000;
            assertThat(expirationDiff).isEqualTo(TEST_EXPIRATION_RT);

            logger.info("Refresh Token 만료시간 검증 성공 - 발급시간: {}, 만료시간: {}, 차이: {}초",
                    issuedAt, expiration, expirationDiff);
        }
    }

    @Nested
    @DisplayName("Token 검증 실패 테스트")
    class TokenValidationFailureTest {

        @Test
        @DisplayName("만료된 Access Token 검증 실패")
        void 만료된_Access_Token_검증_실패() {
            logger.info("===== 만료된 Token 검증 테스트 시작 =====");

            // given - 이미 만료된 토큰 생성
            Date now = new Date();
            Date expiredDate = new Date(now.getTime() - 1000); // 1초 전에 만료

            String expiredToken = Jwts.builder()
                    .setSubject("user-123")
                    .setIssuedAt(new Date(now.getTime() - 10000)) // 10초 전 발급
                    .setExpiration(expiredDate)
                    .signWith(SignatureAlgorithm.HS256, TEST_SECRET_KEY)
                    .compact();

            // when & then
            logger.info("만료된 토큰으로 userId 추출 시도");
            assertThatThrownBy(() -> jwtTokenProvider.getUserIdFromToken(expiredToken))
                    .isInstanceOf(ExpiredJwtException.class);

            logger.info("ExpiredJwtException 발생 확인 완료");
        }

        @Test
        @DisplayName("잘못된 서명의 Token 검증 실패")
        void 잘못된_서명의_Token_검증_실패() {
            logger.info("===== 잘못된 서명 Token 검증 테스트 시작 =====");

            // given - 다른 키로 서명된 토큰
            String wrongKeyToken = Jwts.builder()
                    .setSubject("user-123")
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                    .signWith(SignatureAlgorithm.HS256, "wrongSecretKeyForTestingPurposeMustBeLongEnough12345")
                    .compact();

            // when & then
            logger.info("잘못된 서명의 토큰으로 userId 추출 시도");
            assertThatThrownBy(() -> jwtTokenProvider.getUserIdFromToken(wrongKeyToken))
                    .isInstanceOf(Exception.class);

            logger.info("서명 검증 실패 예외 발생 확인 완료");
        }

        @Test
        @DisplayName("Access Token으로 Refresh Token 검증 실패")
        void Access_Token으로_Refresh_Token_검증_실패() {
            logger.info("===== Access Token으로 Refresh 추출 시도 테스트 시작 =====");

            // given
            String accessToken = jwtTokenProvider.createToken("user-123", "test@test.com", "dept-001");

            // when & then
            logger.info("Access Token으로 Refresh Token 파싱 시도");
            assertThatThrownBy(() -> jwtTokenProvider.getUserIdFromRefreshToken(accessToken))
                    .isInstanceOf(Exception.class);

            logger.info("다른 키로 서명된 토큰 파싱 실패 확인 완료");
        }
    }

    @Nested
    @DisplayName("Token 일관성 테스트")
    class TokenConsistencyTest {

        @Test
        @DisplayName("동일 정보로 생성한 토큰은 다른 값을 가짐")
        void 동일_정보로_생성한_토큰은_다른_값을_가짐() throws InterruptedException {
            logger.info("===== 토큰 고유성 테스트 시작 =====");

            // given
            String userId = "user-123";
            String email = "test@example.com";
            String departmentId = "dept-001";

            // when
            String token1 = jwtTokenProvider.createToken(userId, email, departmentId);
            Thread.sleep(1100); // JWT의 시간 단위가 초(second)이므로 1초 이상 대기
            String token2 = jwtTokenProvider.createToken(userId, email, departmentId);

            // then - 발급 시간이 달라지므로 토큰 값이 달라짐
            assertThat(token1).isNotEqualTo(token2);
            logger.info("토큰 고유성 검증 완료 - 동일 정보로 생성된 토큰이 서로 다름");
        }

        @Test
        @DisplayName("Access Token과 Refresh Token은 다른 서명을 가짐")
        void Access_Token과_Refresh_Token은_다른_서명을_가짐() {
            logger.info("===== Access/Refresh Token 분리 테스트 시작 =====");

            // given
            String userId = "user-123";
            String departmentId = "dept-001";

            // when
            String accessToken = jwtTokenProvider.createToken(userId, "test@test.com", departmentId);
            String refreshToken = jwtTokenProvider.createRefreshToken(userId, departmentId);

            // then
            assertThat(accessToken).isNotEqualTo(refreshToken);

            // 각각 올바른 키로 파싱 가능
            String accessUserId = jwtTokenProvider.getUserIdFromToken(accessToken);
            String refreshUserId = jwtTokenProvider.getUserIdFromRefreshToken(refreshToken);

            assertThat(accessUserId).isEqualTo(userId);
            assertThat(refreshUserId).isEqualTo(userId);

            logger.info("Access/Refresh Token 분리 검증 완료");
        }
    }
}
