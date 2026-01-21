package com.ovengers.userservice.service;

import com.ovengers.userservice.common.auth.JwtTokenProvider;
import com.ovengers.common.auth.TokenUserInfo;
import com.ovengers.userservice.dto.LoginRequestDto;
import com.ovengers.userservice.dto.UserRequestDto;
import com.ovengers.userservice.dto.UserResponseDto;
import com.ovengers.userservice.entity.User;
import com.ovengers.userservice.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityNotFoundException;
import com.ovengers.userservice.common.util.MfaSecretGenerator;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@AllArgsConstructor
@Transactional
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserResponseDto createUser(UserRequestDto dto) {
        // 이메일 중복 체크
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        // 사용자 엔티티 생성
        User user = dto.toEntity(encoder);

        // MFA 시크릿 키 생성 및 설정
        String mfaSecret = MfaSecretGenerator.generateSecret();
        user.setUserSecret(mfaSecret);

        // 사용자 저장
        User savedUser = userRepository.save(user);

        return new UserResponseDto(savedUser);
    }

    public UserResponseDto login(UserRequestDto dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("이메일을 찾을 수 없습니다."));

        if (!encoder.matches(dto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // JWT 토큰 발급 (부서 ID만 전달, 역할은 부서 정보로 대체)
        String token = jwtTokenProvider.createToken(user.getUserId(),user.getEmail(), user.getDepartmentId());

        // Refresh Token을 생성해 주겠다.
        // Access Token의 수명이 만료되었을 경우 Refresh Token을 확인해서 리프레시가 유효한 경우
        // 로그인 없이 Access Token을 재발급 해주는 용도로 사용.
        String refreshToken
                = jwtTokenProvider.createRefreshToken(user.getUserId(), user.getDepartmentId());

        // refresh Token을 DB에 저장하자. -> redis에 저장. (userId를 키로 사용)
        redisTemplate.opsForValue().set(user.getUserId(), refreshToken, 240, TimeUnit.HOURS);

        return new UserResponseDto(user, token);
    }
    public UserResponseDto login(LoginRequestDto dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("이메일을 찾을 수 없습니다."));

        if (!encoder.matches(dto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String token = jwtTokenProvider.createToken(user.getUserId(),user.getEmail(), user.getDepartmentId());
        return new UserResponseDto(user, token);
    }

    /**
     * 내 정보 조회
     */
    public UserResponseDto getMyInfo() {
        // 인증된 사용자 정보
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof TokenUserInfo)) {
            throw new IllegalArgumentException("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        TokenUserInfo userInfo = (TokenUserInfo) principal;
        User user = userRepository.findById(userInfo.getId())
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

        return new UserResponseDto(user);
    }
    /**
     * email 대신 secret으로 사용자 조회
     */
    public UserResponseDto getUserBySecret(String secret) {
        User user = userRepository.findByMfaSecret(secret)
                .orElseThrow(() -> new EntityNotFoundException("Secret으로 사용자를 찾을 수 없습니다."));

        return new UserResponseDto(user);
    }

    public String getUserSecret(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        return user.getUserSecret();
    }

    public String getUserSecretByUserId(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
        return user.getUserSecret();
    }

//    // 중복체크
    public boolean isEmailDuplicate(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
    /**
     * 모든 사용자 조회
     */
    public List<UserResponseDto> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(UserResponseDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 특정 ID로 사용자 조회
     * dto 로 리턴
     */
    public UserResponseDto getUserById(String userId) {  // 파라미터를 String으로 수정
        User user = userRepository.findByUserId(userId)  // findById 대신 findByUserId로 변경
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

        return new UserResponseDto(user);
    }

    /**
     *
     * @param userId 로 사용자 조회
     * @return user 엔티티로 리턴
     */
    public User findUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("그런 아이디의 사용자 없음"));
        return user;
    }

    /**
     * 사용자 비밀번호 변경
     */
    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {  // 파라미터를 String으로 수정
        User user = userRepository.findByUserId(userId)  // findById 대신 findByUserId로 변경
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

        // 현재 비밀번호 확인
        if (!encoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 새 비밀번호로 변경
        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);
    }


    public List<UserResponseDto> getUsersByIds(List<String> userIds) {
        List<User> users = userRepository.findAllById(userIds);
        return users.stream()
                .map(UserResponseDto::new)
                .collect(Collectors.toList());
    }
}
