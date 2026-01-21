package com.ovengers.common.test;

import com.ovengers.common.auth.TokenUserInfo;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

/**
 * Mock 객체 생성 팩토리 클래스
 * 각 서비스에서 공통으로 사용할 수 있는 Mock 객체를 생성합니다.
 */
public final class MockFactory {

    private MockFactory() {
        // 유틸리티 클래스는 인스턴스화 방지
    }

    // ===== Security Context 설정 =====

    /**
     * SecurityContext에 인증된 사용자 설정
     */
    public static void setAuthenticatedUser(TokenUserInfo userInfo) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userInfo,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    /**
     * SecurityContext에 관리자 사용자 설정
     */
    public static void setAuthenticatedAdmin(TokenUserInfo userInfo) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userInfo,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                )
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    /**
     * SecurityContext 초기화
     */
    public static void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 기본 사용자로 SecurityContext 설정
     */
    public static TokenUserInfo setDefaultAuthenticatedUser() {
        TokenUserInfo userInfo = TestFixtures.createTokenUserInfo();
        setAuthenticatedUser(userInfo);
        return userInfo;
    }

    /**
     * 기본 관리자로 SecurityContext 설정
     */
    public static TokenUserInfo setDefaultAuthenticatedAdmin() {
        TokenUserInfo adminInfo = TestFixtures.createAdminTokenUserInfo();
        setAuthenticatedAdmin(adminInfo);
        return adminInfo;
    }

    /**
     * 지정된 사용자 ID로 SecurityContext 설정
     */
    public static TokenUserInfo setAuthenticatedUserWithId(String userId) {
        TokenUserInfo userInfo = TestFixtures.createTokenUserInfo(userId);
        setAuthenticatedUser(userInfo);
        return userInfo;
    }

    /**
     * 지정된 부서로 SecurityContext 설정
     */
    public static TokenUserInfo setAuthenticatedUserWithDepartment(String departmentId) {
        TokenUserInfo userInfo = TestFixtures.createTokenUserInfoWithDepartment(departmentId);
        setAuthenticatedUser(userInfo);
        return userInfo;
    }

    // ===== 인증 확인 유틸리티 =====

    /**
     * 현재 인증된 사용자 정보 반환
     */
    public static TokenUserInfo getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TokenUserInfo)) {
            return null;
        }
        return (TokenUserInfo) authentication.getPrincipal();
    }

    /**
     * 현재 사용자가 인증되었는지 확인
     */
    public static boolean isAuthenticated() {
        return getCurrentUser() != null;
    }

    /**
     * 현재 사용자가 관리자인지 확인
     */
    public static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }
}
