package com.ovengers.common.test;

import com.ovengers.common.auth.TokenUserInfo;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 테스트용 Fixture 생성 유틸리티 클래스
 * 각 서비스에서 공통으로 사용할 수 있는 테스트 데이터를 생성합니다.
 */
public final class TestFixtures {

    private TestFixtures() {
        // 유틸리티 클래스는 인스턴스화 방지
    }

    // ===== TokenUserInfo Fixtures =====

    /**
     * 기본 TokenUserInfo 생성
     */
    public static TokenUserInfo createTokenUserInfo() {
        return TokenUserInfo.builder()
                .id(generateUserId())
                .departmentId("dept-001")
                .role("USER")
                .build();
    }

    /**
     * 지정된 ID로 TokenUserInfo 생성
     */
    public static TokenUserInfo createTokenUserInfo(String userId) {
        return TokenUserInfo.builder()
                .id(userId)
                .departmentId("dept-001")
                .role("USER")
                .build();
    }

    /**
     * 관리자 TokenUserInfo 생성
     */
    public static TokenUserInfo createAdminTokenUserInfo() {
        return TokenUserInfo.builder()
                .id(generateUserId())
                .departmentId("team9")
                .role("ADMIN")
                .build();
    }

    /**
     * 지정된 부서 ID로 TokenUserInfo 생성
     */
    public static TokenUserInfo createTokenUserInfoWithDepartment(String departmentId) {
        return TokenUserInfo.builder()
                .id(generateUserId())
                .departmentId(departmentId)
                .role("USER")
                .build();
    }

    // ===== ID 생성 유틸리티 =====

    /**
     * 랜덤 사용자 ID 생성
     */
    public static String generateUserId() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 랜덤 채팅방 ID 생성
     */
    public static Long generateChatRoomId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 10000);
    }

    /**
     * 랜덤 메시지 ID 생성
     */
    public static String generateMessageId() {
        return "msg-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 랜덤 부서 ID 생성
     */
    public static String generateDepartmentId() {
        return "dept-" + UUID.randomUUID().toString().substring(0, 4);
    }

    // ===== 테스트용 상수 =====

    public static final String TEST_EMAIL = "test@example.com";
    public static final String TEST_PASSWORD = "Password1!";
    public static final String TEST_NAME = "테스트유저";
    public static final String TEST_PHONE = "010-1234-5678";
    public static final String TEST_PROFILE_IMAGE = "profile.jpg";
    public static final String TEST_MFA_SECRET = "test-mfa-secret";

    public static final String ADMIN_EMAIL = "admin@example.com";
    public static final String ADMIN_DEPARTMENT = "team9";

    public static final String TEST_JWT_SECRET = "testSecretKeyForJwtTokenMustBeLongEnough12345";
    public static final int TEST_JWT_EXPIRATION = 3600;

    // ===== 날짜/시간 유틸리티 =====

    /**
     * 현재 시간 반환
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * 현재 시간에서 지정된 분 이전 시간 반환
     */
    public static LocalDateTime minutesAgo(int minutes) {
        return LocalDateTime.now().minusMinutes(minutes);
    }

    /**
     * 현재 시간에서 지정된 시간 이전 반환
     */
    public static LocalDateTime hoursAgo(int hours) {
        return LocalDateTime.now().minusHours(hours);
    }

    /**
     * 현재 시간에서 지정된 일 이전 반환
     */
    public static LocalDateTime daysAgo(int days) {
        return LocalDateTime.now().minusDays(days);
    }
}
