package com.ovengers.common.util;

import java.util.regex.Pattern;

/**
 * 로그에서 민감한 정보를 마스킹하는 유틸리티 클래스.
 * 이메일, 전화번호, 토큰 등의 민감 정보를 안전하게 로깅할 수 있도록 지원합니다.
 */
public final class LogMaskingUtil {

    private LogMaskingUtil() {
        // 유틸리티 클래스는 인스턴스화 방지
    }

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(\\d{3})[-.]?(\\d{3,4})[-.]?(\\d{4})"
    );

    private static final Pattern JWT_PATTERN = Pattern.compile(
            "(eyJ[a-zA-Z0-9_-]*\\.eyJ[a-zA-Z0-9_-]*\\.)([a-zA-Z0-9_-]*)"
    );

    /**
     * 이메일 주소를 마스킹합니다.
     * 예: user@example.com -> us**@example.com
     *
     * @param email 마스킹할 이메일 주소
     * @return 마스킹된 이메일 주소
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "**" + domain;
        }

        return localPart.substring(0, 2) + "**" + domain;
    }

    /**
     * 전화번호를 마스킹합니다.
     * 예: 010-1234-5678 -> 010-****-5678
     *
     * @param phoneNumber 마스킹할 전화번호
     * @return 마스킹된 전화번호
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return phoneNumber;
        }

        return PHONE_PATTERN.matcher(phoneNumber)
                .replaceAll("$1-****-$3");
    }

    /**
     * JWT 토큰을 마스킹합니다.
     * 헤더와 페이로드 일부만 표시하고 시그니처는 완전히 마스킹합니다.
     * 예: eyJhbGci...payload...signature -> eyJhbGci...***
     *
     * @param token 마스킹할 JWT 토큰
     * @return 마스킹된 토큰
     */
    public static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return token;
        }

        if (token.length() <= 20) {
            return "***";
        }

        // JWT 토큰인 경우 시그니처 부분 마스킹
        if (token.startsWith("eyJ")) {
            return JWT_PATTERN.matcher(token).replaceAll("$1***");
        }

        // 일반 토큰의 경우 앞 10자만 표시
        return token.substring(0, 10) + "***";
    }

    /**
     * 사용자 ID를 마스킹합니다.
     * UUID 형식인 경우 앞 8자리만 표시합니다.
     * 예: 550e8400-e29b-41d4-a716-446655440000 -> 550e8400-****
     *
     * @param userId 마스킹할 사용자 ID
     * @return 마스킹된 사용자 ID
     */
    public static String maskUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return userId;
        }

        if (userId.length() <= 8) {
            return userId.substring(0, Math.min(4, userId.length())) + "****";
        }

        return userId.substring(0, 8) + "-****";
    }

    /**
     * 문자열 내의 모든 민감 정보를 마스킹합니다.
     * 이메일, 전화번호, JWT 토큰을 자동으로 감지하여 마스킹합니다.
     *
     * @param text 마스킹할 텍스트
     * @return 마스킹된 텍스트
     */
    public static String maskSensitiveData(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String masked = text;

        // 이메일 마스킹
        masked = EMAIL_PATTERN.matcher(masked)
                .replaceAll(match -> maskEmail(match.group()));

        // 전화번호 마스킹
        masked = PHONE_PATTERN.matcher(masked)
                .replaceAll("$1-****-$3");

        // JWT 토큰 마스킹
        masked = JWT_PATTERN.matcher(masked)
                .replaceAll("$1***");

        return masked;
    }
}
