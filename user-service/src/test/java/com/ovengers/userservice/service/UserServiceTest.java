package com.ovengers.userservice.service;

import com.ovengers.common.auth.TokenUserInfo;
import com.ovengers.userservice.common.auth.JwtTokenProvider;
import com.ovengers.userservice.dto.UserRequestDto;
import com.ovengers.userservice.dto.UserResponseDto;
import com.ovengers.userservice.entity.Position;
import com.ovengers.userservice.entity.User;
import com.ovengers.userservice.entity.UserState;
import com.ovengers.userservice.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceTest.class);

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private User testUser;
    private UserRequestDto userRequestDto;

    @BeforeEach
    void setUp() {
        logger.info("===== 테스트 데이터 초기화 =====");

        testUser = User.builder()
                .userId("user-123")
                .email("test@example.com")
                .password("encodedPassword")
                .name("테스트유저")
                .profileImage("profile.jpg")
                .position(Position.EMPLOYEE)
                .phoneNum("010-1234-5678")
                .accountActive(true)
                .state(UserState.ACTIVE)
                .departmentId("dept-001")
                .mfaSecret("test-mfa-secret")
                .build();

        userRequestDto = UserRequestDto.builder()
                .email("test@example.com")
                .password("Password1!")
                .name("테스트유저")
                .phoneNum("010-1234-5678")
                .position(Position.EMPLOYEE)
                .departmentId("dept-001")
                .build();

        logger.info("테스트 사용자 초기화 완료 - ID: {}, Email: {}", testUser.getUserId(), testUser.getEmail());
    }

    @Nested
    @DisplayName("회원가입 테스트")
    class CreateUserTest {

        @Test
        @DisplayName("회원가입 성공")
        void 회원가입_성공() {
            logger.info("===== 회원가입 성공 테스트 시작 =====");

            // given
            given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());
            given(encoder.encode(anyString())).willReturn("encodedPassword");
            given(userRepository.save(any(User.class))).willReturn(testUser);

            // when
            logger.info("회원가입 실행 - 이메일: {}", userRequestDto.getEmail());
            UserResponseDto result = userService.createUser(userRequestDto);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(testUser.getEmail());
            assertThat(result.getName()).isEqualTo(testUser.getName());

            then(userRepository).should(times(1)).findByEmail(anyString());
            then(userRepository).should(times(1)).save(any(User.class));

            logger.info("회원가입 성공 테스트 완료 - 생성된 사용자: {}", result.getEmail());
        }

        @Test
        @DisplayName("회원가입 실패 - 이메일 중복")
        void 회원가입_실패_이메일_중복() {
            logger.info("===== 이메일 중복 테스트 시작 =====");

            // given
            given(userRepository.findByEmail(anyString())).willReturn(Optional.of(testUser));

            // when & then
            logger.info("중복 이메일로 회원가입 시도: {}", userRequestDto.getEmail());
            assertThatThrownBy(() -> userService.createUser(userRequestDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("이미 존재하는 이메일입니다.");

            then(userRepository).should(never()).save(any(User.class));
            logger.info("이메일 중복 예외 발생 확인 완료");
        }
    }

    @Nested
    @DisplayName("로그인 테스트")
    class LoginTest {

        @Test
        @DisplayName("로그인 성공")
        void 로그인_성공() {
            logger.info("===== 로그인 성공 테스트 시작 =====");

            // given
            given(userRepository.findByEmail(anyString())).willReturn(Optional.of(testUser));
            given(encoder.matches(anyString(), anyString())).willReturn(true);
            given(jwtTokenProvider.createToken(anyString(), anyString(), anyString())).willReturn("access-token");
            given(jwtTokenProvider.createRefreshToken(anyString(), anyString())).willReturn("refresh-token");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            // when
            logger.info("로그인 실행 - 이메일: {}", userRequestDto.getEmail());
            UserResponseDto result = userService.login(userRequestDto);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(testUser.getEmail());
            assertThat(result.getToken()).isEqualTo("access-token");

            then(jwtTokenProvider).should(times(1)).createToken(anyString(), anyString(), anyString());
            then(jwtTokenProvider).should(times(1)).createRefreshToken(anyString(), anyString());

            logger.info("로그인 성공 테스트 완료 - 토큰 발급됨");
        }

        @Test
        @DisplayName("로그인 실패 - 존재하지 않는 이메일")
        void 로그인_실패_이메일_없음() {
            logger.info("===== 이메일 없음 테스트 시작 =====");

            // given
            given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

            // when & then
            logger.info("존재하지 않는 이메일로 로그인 시도");
            assertThatThrownBy(() -> userService.login(userRequestDto))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessage("이메일을 찾을 수 없습니다.");

            logger.info("EntityNotFoundException 발생 확인 완료");
        }

        @Test
        @DisplayName("로그인 실패 - 비밀번호 불일치")
        void 로그인_실패_비밀번호_불일치() {
            logger.info("===== 비밀번호 불일치 테스트 시작 =====");

            // given
            given(userRepository.findByEmail(anyString())).willReturn(Optional.of(testUser));
            given(encoder.matches(anyString(), anyString())).willReturn(false);

            // when & then
            logger.info("잘못된 비밀번호로 로그인 시도");
            assertThatThrownBy(() -> userService.login(userRequestDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("비밀번호가 일치하지 않습니다.");

            then(jwtTokenProvider).should(never()).createToken(anyString(), anyString(), anyString());
            logger.info("비밀번호 불일치 예외 발생 확인 완료");
        }
    }

    @Nested
    @DisplayName("사용자 조회 테스트")
    class GetUserTest {

        @Test
        @DisplayName("내 정보 조회 성공")
        void 내_정보_조회_성공() {
            logger.info("===== 내 정보 조회 테스트 시작 =====");

            // given
            TokenUserInfo tokenUserInfo = TokenUserInfo.builder()
                    .id("user-123")
                    .departmentId("dept-001")
                    .build();

            Authentication authentication = mock(Authentication.class);
            SecurityContext securityContext = mock(SecurityContext.class);

            given(securityContext.getAuthentication()).willReturn(authentication);
            given(authentication.getPrincipal()).willReturn(tokenUserInfo);
            SecurityContextHolder.setContext(securityContext);

            given(userRepository.findById("user-123")).willReturn(Optional.of(testUser));

            // when
            logger.info("내 정보 조회 실행");
            UserResponseDto result = userService.getMyInfo();

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(testUser.getUserId());
            assertThat(result.getEmail()).isEqualTo(testUser.getEmail());

            logger.info("내 정보 조회 성공 - 사용자: {}", result.getEmail());

            // cleanup
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("ID로 사용자 조회 성공")
        void ID로_사용자_조회_성공() {
            logger.info("===== ID로 사용자 조회 테스트 시작 =====");

            // given
            String userId = "user-123";
            given(userRepository.findByUserId(userId)).willReturn(Optional.of(testUser));

            // when
            logger.info("사용자 조회 실행 - ID: {}", userId);
            UserResponseDto result = userService.getUserById(userId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);

            logger.info("사용자 조회 성공 - 이름: {}", result.getName());
        }

        @Test
        @DisplayName("ID로 사용자 조회 실패 - 존재하지 않음")
        void ID로_사용자_조회_실패_존재하지_않음() {
            logger.info("===== 존재하지 않는 사용자 조회 테스트 시작 =====");

            // given
            String userId = "non-existent";
            given(userRepository.findByUserId(userId)).willReturn(Optional.empty());

            // when & then
            logger.info("존재하지 않는 사용자 조회 시도 - ID: {}", userId);
            assertThatThrownBy(() -> userService.getUserById(userId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessage("사용자를 찾을 수 없습니다.");

            logger.info("EntityNotFoundException 발생 확인 완료");
        }

        @Test
        @DisplayName("여러 ID로 사용자 목록 조회")
        void 여러_ID로_사용자_목록_조회() {
            logger.info("===== 사용자 목록 조회 테스트 시작 =====");

            // given
            List<String> userIds = Arrays.asList("user-1", "user-2");
            User user1 = User.builder().userId("user-1").email("user1@test.com").name("유저1").build();
            User user2 = User.builder().userId("user-2").email("user2@test.com").name("유저2").build();

            given(userRepository.findAllById(userIds)).willReturn(Arrays.asList(user1, user2));

            // when
            logger.info("사용자 목록 조회 실행 - IDs: {}", userIds);
            List<UserResponseDto> result = userService.getUsersByIds(userIds);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getUserId()).isEqualTo("user-1");
            assertThat(result.get(1).getUserId()).isEqualTo("user-2");

            logger.info("사용자 목록 조회 성공 - 조회된 수: {}", result.size());
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 테스트")
    class ChangePasswordTest {

        @Test
        @DisplayName("비밀번호 변경 성공")
        void 비밀번호_변경_성공() {
            logger.info("===== 비밀번호 변경 성공 테스트 시작 =====");

            // given
            String userId = "user-123";
            String currentPassword = "oldPassword1!";
            String newPassword = "newPassword1!";

            given(userRepository.findByUserId(userId)).willReturn(Optional.of(testUser));
            given(encoder.matches(currentPassword, testUser.getPassword())).willReturn(true);
            given(encoder.encode(newPassword)).willReturn("encodedNewPassword");

            // when
            logger.info("비밀번호 변경 실행 - 사용자: {}", userId);
            userService.changePassword(userId, currentPassword, newPassword);

            // then
            then(userRepository).should(times(1)).save(any(User.class));
            logger.info("비밀번호 변경 성공 테스트 완료");
        }

        @Test
        @DisplayName("비밀번호 변경 실패 - 현재 비밀번호 불일치")
        void 비밀번호_변경_실패_현재_비밀번호_불일치() {
            logger.info("===== 비밀번호 변경 실패 테스트 시작 =====");

            // given
            String userId = "user-123";
            String wrongPassword = "wrongPassword1!";
            String newPassword = "newPassword1!";

            given(userRepository.findByUserId(userId)).willReturn(Optional.of(testUser));
            given(encoder.matches(wrongPassword, testUser.getPassword())).willReturn(false);

            // when & then
            logger.info("잘못된 현재 비밀번호로 변경 시도");
            assertThatThrownBy(() -> userService.changePassword(userId, wrongPassword, newPassword))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("현재 비밀번호가 일치하지 않습니다.");

            then(userRepository).should(never()).save(any(User.class));
            logger.info("비밀번호 변경 실패 테스트 완료");
        }
    }

    @Nested
    @DisplayName("이메일 중복 체크 테스트")
    class EmailDuplicateTest {

        @Test
        @DisplayName("이메일 중복 - true 반환")
        void 이메일_중복_확인() {
            logger.info("===== 이메일 중복 체크 테스트 시작 =====");

            // given
            String email = "existing@test.com";
            given(userRepository.findByEmail(email)).willReturn(Optional.of(testUser));

            // when
            boolean result = userService.isEmailDuplicate(email);

            // then
            assertThat(result).isTrue();
            logger.info("이메일 중복 확인 완료 - 결과: true");
        }

        @Test
        @DisplayName("이메일 미중복 - false 반환")
        void 이메일_미중복_확인() {
            logger.info("===== 이메일 미중복 체크 테스트 시작 =====");

            // given
            String email = "new@test.com";
            given(userRepository.findByEmail(email)).willReturn(Optional.empty());

            // when
            boolean result = userService.isEmailDuplicate(email);

            // then
            assertThat(result).isFalse();
            logger.info("이메일 미중복 확인 완료 - 결과: false");
        }
    }

    @Nested
    @DisplayName("MFA 시크릿 조회 테스트")
    class MfaSecretTest {

        @Test
        @DisplayName("이메일로 MFA 시크릿 조회 성공")
        void 이메일로_MFA_시크릿_조회_성공() {
            logger.info("===== MFA 시크릿 조회 테스트 시작 =====");

            // given
            String email = "test@example.com";
            given(userRepository.findByEmail(email)).willReturn(Optional.of(testUser));

            // when
            String secret = userService.getUserSecret(email);

            // then
            assertThat(secret).isEqualTo("test-mfa-secret");
            logger.info("MFA 시크릿 조회 성공");
        }

        @Test
        @DisplayName("userId로 MFA 시크릿 조회 성공")
        void userId로_MFA_시크릿_조회_성공() {
            logger.info("===== userId로 MFA 시크릿 조회 테스트 시작 =====");

            // given
            String userId = "user-123";
            given(userRepository.findByUserId(userId)).willReturn(Optional.of(testUser));

            // when
            String secret = userService.getUserSecretByUserId(userId);

            // then
            assertThat(secret).isEqualTo("test-mfa-secret");
            logger.info("userId로 MFA 시크릿 조회 성공");
        }
    }
}
