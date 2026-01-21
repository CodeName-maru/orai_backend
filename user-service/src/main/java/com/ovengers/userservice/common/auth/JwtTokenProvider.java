package com.ovengers.userservice.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secretKey}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private int expiration;

    @Value("${jwt.secretKeyRt}")
    private String secretKeyRt;

    @Value("${jwt.expirationRt}")
    private int expirationRt;

    /**
     * 액세스 토큰 생성
     */
    public String createToken(String id, String email, String departmentId) {
        Claims claims = Jwts.claims().setSubject(id); // 사용자 ID를 subject로 설정
        claims.put("departmentId", departmentId); // 부서 ID 추가
        claims.put("email",email);
//        claims.get("departmentId", String.class);

        Date now = new Date();

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now) // 발급 시간
                .setExpiration(new Date(now.getTime() + expiration * 1000L)) // 만료 시간 (초 단위)
                .signWith(SignatureAlgorithm.HS256, secretKey) // 서명 알고리즘과 비밀 키 설정
                .compact();
    }

    /**
     * 리프레시 토큰 생성
     */
    public String createRefreshToken(String userId, String departmentId) {
        Claims claims = Jwts.claims().setSubject(userId); // 사용자 ID를 subject로 설정
        claims.put("departmentId", departmentId); // 부서 ID 추가
        Date now = new Date();

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now) // 발급 시간
                .setExpiration(new Date(now.getTime() + expirationRt * 1000L)) // 만료 시간 (초 단위)
                .signWith(SignatureAlgorithm.HS256, secretKeyRt) // 서명 알고리즘과 리프레시 비밀 키 설정
                .compact();
    }

    /**
     * 토큰에서 userId 추출
     */
    public String getUserIdFromToken(String token) {
        Claims claims = parseClaims(token); // JWT 토큰에서 클레임을 파싱
        return claims.getSubject(); // subject로 저장된 userId 반환
    }

    /**
     * 토큰에서 클레임 추출
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .setSigningKey(secretKey) // 액세스 토큰의 비밀 키
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 리프레시 토큰에서 userId 추출
     */
    public String getUserIdFromRefreshToken(String token) {
        Claims claims = parseClaimsForRefreshToken(token); // 리프레시 토큰에서 클레임을 파싱
        return claims.getSubject(); // subject로 저장된 userId 반환
    }

    /**
     * 리프레시 토큰에서 클레임 추출
     */
    private Claims parseClaimsForRefreshToken(String token) {
        return Jwts.parser()
                .setSigningKey(secretKeyRt) // 리프레시 토큰의 비밀 키
                .parseClaimsJws(token)
                .getBody();
    }
}
