package com.ovengers.userservice.dto;

import com.ovengers.userservice.entity.Position;
import com.ovengers.userservice.entity.User;
import com.ovengers.userservice.entity.UserState;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRequestDto {

    @NotBlank(message = "이메일은 필수 입력값입니다")
    @Email(message = "유효한 이메일 형식이 아닙니다")
    @Size(max = 100, message = "이메일은 100자를 초과할 수 없습니다")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력값입니다")
    @Size(min = 8, max = 100, message = "비밀번호는 8~100자 사이여야 합니다")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]+$",
            message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다")
    private String password;

    @NotBlank(message = "이름은 필수 입력값입니다")
    @Size(min = 2, max = 50, message = "이름은 2~50자 사이여야 합니다")
    private String name;

    private String profileImage;

    @Pattern(regexp = "^01[0-9]-\\d{3,4}-\\d{4}$", message = "유효한 전화번호 형식이 아닙니다 (예: 010-1234-5678)")
    private String phoneNum;

    private Position position;
    private boolean accountActive;
    private UserState state;

    @NotBlank(message = "부서 ID는 필수 입력값입니다")
    private String departmentId;

    // MFA 시크릿 (서버에서 생성)
    private String mfaSecret;

    // User 엔티티로 변환하는 메서드
    public User toEntity(PasswordEncoder encoder) {
        return User.builder()
                .email(this.email)
                .password(encoder.encode(this.password)) // 비밀번호 암호화
                .name(this.name)
                .profileImage(this.profileImage)
                .phoneNum(this.phoneNum)
                .position(this.position)
                .accountActive(this.accountActive)
                .state(this.state)
                .departmentId(this.departmentId)
                .mfaSecret(this.mfaSecret) // 추가된 mfa 매핑
                .build();
    }
}
