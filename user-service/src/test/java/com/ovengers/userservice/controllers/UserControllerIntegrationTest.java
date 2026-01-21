package com.ovengers.userservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.userservice.common.auth.JwtTokenProvider;
import com.ovengers.userservice.dto.LoginRequestDto;
import com.ovengers.userservice.dto.UserRequestDto;
import com.ovengers.userservice.dto.UserResponseDto;
import com.ovengers.userservice.entity.Position;
import com.ovengers.userservice.entity.User;
import com.ovengers.userservice.entity.UserState;
import com.ovengers.userservice.service.UserService;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@DisplayName("UserController 통합 테스트")
class UserControllerIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(UserControllerIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ValueOperations<String, Object> valueOperations;

    private UserRequestDto userRequestDto;
    private UserResponseDto userResponseDto;
    private User testUser;

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

        userResponseDto = new UserResponseDto(testUser);

        logger.info("테스트 데이터 초기화 완료");
    }

    @Nested
    @DisplayName("회원가입 API 테스트")
    class CreateUserApiTest {

        @Test
        @WithMockUser
        @DisplayName("회원가입 성공 - 201 Created")
        void 회원가입_성공() throws Exception {
            logger.info("===== 회원가입 API 테스트 시작 =====");

            // given
            given(userService.createUser(any(UserRequestDto.class))).willReturn(userResponseDto);

            // when
            logger.info("POST /api/users/create 요청");
            ResultActions result = mockMvc.perform(post("/api/users/create")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(userRequestDto)));

            // then
            result.andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value(userResponseDto.getEmail()))
                    .andExpect(jsonPath("$.name").value(userResponseDto.getName()));

            logger.info("회원가입 API 테스트 성공");
        }

        @Test
        @WithMockUser
        @DisplayName("회원가입 실패 - 유효성 검증 실패 (이메일 누락)")
        void 회원가입_실패_이메일_누락() throws Exception {
            logger.info("===== 회원가입 유효성 검증 테스트 시작 =====");

            // given
            UserRequestDto invalidDto = UserRequestDto.builder()
                    .email("") // 빈 이메일
                    .password("Password1!")
                    .name("테스트유저")
                    .departmentId("dept-001")
                    .build();

            // when
            logger.info("POST /api/users/create 요청 (이메일 누락)");
            ResultActions result = mockMvc.perform(post("/api/users/create")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidDto)));

            // then
            result.andDo(print())
                    .andExpect(status().isBadRequest());

            logger.info("유효성 검증 실패 테스트 성공");
        }

        @Test
        @WithMockUser
        @DisplayName("회원가입 실패 - 유효성 검증 실패 (잘못된 이메일 형식)")
        void 회원가입_실패_잘못된_이메일_형식() throws Exception {
            logger.info("===== 이메일 형식 검증 테스트 시작 =====");

            // given
            UserRequestDto invalidDto = UserRequestDto.builder()
                    .email("invalid-email")
                    .password("Password1!")
                    .name("테스트유저")
                    .departmentId("dept-001")
                    .build();

            // when
            logger.info("POST /api/users/create 요청 (잘못된 이메일 형식)");
            ResultActions result = mockMvc.perform(post("/api/users/create")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidDto)));

            // then
            result.andDo(print())
                    .andExpect(status().isBadRequest());

            logger.info("이메일 형식 검증 테스트 성공");
        }

        @Test
        @WithMockUser
        @DisplayName("회원가입 실패 - 유효성 검증 실패 (비밀번호 패턴 미충족)")
        void 회원가입_실패_비밀번호_패턴_미충족() throws Exception {
            logger.info("===== 비밀번호 패턴 검증 테스트 시작 =====");

            // given
            UserRequestDto invalidDto = UserRequestDto.builder()
                    .email("test@example.com")
                    .password("simplepassword") // 특수문자, 숫자 미포함
                    .name("테스트유저")
                    .departmentId("dept-001")
                    .build();

            // when
            logger.info("POST /api/users/create 요청 (비밀번호 패턴 미충족)");
            ResultActions result = mockMvc.perform(post("/api/users/create")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidDto)));

            // then
            result.andDo(print())
                    .andExpect(status().isBadRequest());

            logger.info("비밀번호 패턴 검증 테스트 성공");
        }
    }

    @Nested
    @DisplayName("로그인 API 테스트")
    class LoginApiTest {

        @Test
        @WithMockUser
        @DisplayName("로그인 성공 - MFA 요구 응답")
        void 로그인_성공_MFA_요구() throws Exception {
            logger.info("===== 로그인 API 테스트 시작 =====");

            // given
            UserResponseDto loginResponse = new UserResponseDto(testUser);
            given(userService.login(any(UserRequestDto.class))).willReturn(loginResponse);

            // when
            logger.info("POST /api/users/login 요청");
            ResultActions result = mockMvc.perform(post("/api/users/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(userRequestDto)));

            // then
            result.andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.userId").value(userResponseDto.getUserId()))
                    .andExpect(jsonPath("$.result.mfaRequired").value(true));

            logger.info("로그인 API 테스트 성공 - MFA 요구됨");
        }

        @Test
        @WithMockUser
        @DisplayName("개발용 로그인 성공")
        void 개발용_로그인_성공() throws Exception {
            logger.info("===== 개발용 로그인 API 테스트 시작 =====");

            // given
            LoginRequestDto loginDto = LoginRequestDto.builder()
                    .email("test@example.com")
                    .password("Password1!")
                    .build();

            UserResponseDto loginResponse = new UserResponseDto(testUser);
            given(userService.login(any(LoginRequestDto.class))).willReturn(loginResponse);

            // when
            logger.info("POST /api/users/devLogin 요청");
            ResultActions result = mockMvc.perform(post("/api/users/devLogin")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginDto)));

            // then
            result.andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200));

            logger.info("개발용 로그인 API 테스트 성공");
        }
    }

    @Nested
    @DisplayName("사용자 조회 API 테스트")
    class GetUserApiTest {

        @Test
        @WithMockUser
        @DisplayName("특정 사용자 조회 성공")
        void 특정_사용자_조회_성공() throws Exception {
            logger.info("===== 사용자 조회 API 테스트 시작 =====");

            // given
            String userId = "user-123";
            given(userService.getUserById(userId)).willReturn(userResponseDto);

            // when
            logger.info("GET /api/users/{} 요청", userId);
            ResultActions result = mockMvc.perform(get("/api/users/{userId}", userId)
                    .contentType(MediaType.APPLICATION_JSON));

            // then
            result.andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(userId))
                    .andExpect(jsonPath("$.email").value(userResponseDto.getEmail()))
                    .andExpect(jsonPath("$.name").value(userResponseDto.getName()));

            logger.info("사용자 조회 API 테스트 성공");
        }

        @Test
        @WithMockUser
        @DisplayName("사용자 목록 조회 성공")
        void 사용자_목록_조회_성공() throws Exception {
            logger.info("===== 사용자 목록 조회 API 테스트 시작 =====");

            // given
            List<String> userIds = Arrays.asList("user-1", "user-2");
            UserResponseDto user1 = new UserResponseDto();
            user1.setUserId("user-1");
            user1.setEmail("user1@test.com");
            user1.setName("유저1");

            UserResponseDto user2 = new UserResponseDto();
            user2.setUserId("user-2");
            user2.setEmail("user2@test.com");
            user2.setName("유저2");

            List<UserResponseDto> users = Arrays.asList(user1, user2);
            given(userService.getUsersByIds(userIds)).willReturn(users);

            // when
            logger.info("POST /api/users/list 요청");
            ResultActions result = mockMvc.perform(post("/api/users/list")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(userIds)));

            // then
            result.andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].userId").value("user-1"))
                    .andExpect(jsonPath("$[1].userId").value("user-2"));

            logger.info("사용자 목록 조회 API 테스트 성공");
        }
    }

    @Nested
    @DisplayName("이메일 중복 체크 API 테스트")
    class EmailCheckApiTest {

        @Test
        @WithMockUser
        @DisplayName("이메일 중복 - true 반환")
        void 이메일_중복_확인() throws Exception {
            logger.info("===== 이메일 중복 체크 API 테스트 시작 =====");

            // given
            String email = "existing@test.com";
            given(userService.isEmailDuplicate(email)).willReturn(true);

            // when
            logger.info("GET /api/users/check-email?email={} 요청", email);
            ResultActions result = mockMvc.perform(get("/api/users/check-email")
                    .param("email", email)
                    .contentType(MediaType.APPLICATION_JSON));

            // then
            result.andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));

            logger.info("이메일 중복 체크 API 테스트 성공 - 중복됨");
        }

        @Test
        @WithMockUser
        @DisplayName("이메일 미중복 - false 반환")
        void 이메일_미중복_확인() throws Exception {
            logger.info("===== 이메일 미중복 체크 API 테스트 시작 =====");

            // given
            String email = "new@test.com";
            given(userService.isEmailDuplicate(email)).willReturn(false);

            // when
            logger.info("GET /api/users/check-email?email={} 요청", email);
            ResultActions result = mockMvc.perform(get("/api/users/check-email")
                    .param("email", email)
                    .contentType(MediaType.APPLICATION_JSON));

            // then
            result.andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));

            logger.info("이메일 미중복 체크 API 테스트 성공 - 사용 가능");
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 API 테스트")
    class ChangePasswordApiTest {

        @Test
        @WithMockUser
        @DisplayName("비밀번호 변경 성공")
        void 비밀번호_변경_성공() throws Exception {
            logger.info("===== 비밀번호 변경 API 테스트 시작 =====");

            // given
            String userId = "user-123";
            String currentPassword = "OldPassword1!";
            String newPassword = "NewPassword1!";

            // when
            logger.info("PUT /api/users/change-password 요청");
            ResultActions result = mockMvc.perform(put("/api/users/change-password")
                    .with(csrf())
                    .param("userId", userId)
                    .param("currentPassword", currentPassword)
                    .param("newPassword", newPassword)
                    .contentType(MediaType.APPLICATION_JSON));

            // then
            result.andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().string("Password changed successfully."));

            logger.info("비밀번호 변경 API 테스트 성공");
        }
    }

    @Nested
    @DisplayName("토큰 갱신 API 테스트")
    class RefreshTokenApiTest {

        @Test
        @WithMockUser
        @DisplayName("토큰 갱신 성공")
        void 토큰_갱신_성공() throws Exception {
            logger.info("===== 토큰 갱신 API 테스트 시작 =====");

            // given
            String userId = "user-123";
            given(userService.getUserById(userId)).willReturn(userResponseDto);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(userId)).willReturn("valid-refresh-token");
            given(jwtTokenProvider.createToken(anyString(), anyString(), anyString()))
                    .willReturn("new-access-token");

            String requestBody = "{\"id\": \"" + userId + "\"}";

            // when
            logger.info("POST /api/users/refresh 요청");
            ResultActions result = mockMvc.perform(post("/api/users/refresh")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody));

            // then
            result.andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.token").value("new-access-token"));

            logger.info("토큰 갱신 API 테스트 성공");
        }

        @Test
        @WithMockUser
        @DisplayName("토큰 갱신 실패 - Refresh Token 만료")
        void 토큰_갱신_실패_Refresh_Token_만료() throws Exception {
            logger.info("===== Refresh Token 만료 테스트 시작 =====");

            // given
            String userId = "user-123";
            given(userService.getUserById(userId)).willReturn(userResponseDto);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(userId)).willReturn(null); // Redis에 토큰 없음

            String requestBody = "{\"id\": \"" + userId + "\"}";

            // when
            logger.info("POST /api/users/refresh 요청 (만료된 토큰)");
            ResultActions result = mockMvc.perform(post("/api/users/refresh")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody));

            // then
            result.andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.statusMessage").value("EXPIRED_RT"));

            logger.info("Refresh Token 만료 테스트 성공");
        }
    }
}
