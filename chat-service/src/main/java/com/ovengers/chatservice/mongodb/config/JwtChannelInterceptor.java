package com.ovengers.chatservice.mongodb.config;

import com.ovengers.common.auth.TokenUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils; // JwtUtils 주입

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (accessor.getCommand() != null && "CONNECT".equals(accessor.getCommand().name())) {
            String jwtToken = accessor.getFirstNativeHeader("Authorization");

            // JWT 토큰이 없거나 Bearer 형식이 아니면 연결 거부
            if (jwtToken == null || !jwtToken.startsWith("Bearer ")) {
                log.warn("WebSocket connection rejected: Missing or invalid Authorization header");
                throw new IllegalArgumentException("WebSocket 연결에 유효한 JWT 토큰이 필요합니다");
            }

            jwtToken = jwtToken.substring(7); // "Bearer " 제거

            try {
                // JwtUtils를 사용하여 토큰 검증 및 정보 추출
                String userId = jwtUtils.getUserIdFromToken(jwtToken);
                String departmentId = jwtUtils.getDepartmentFromToken(jwtToken);

                if (userId == null || userId.isBlank()) {
                    throw new IllegalArgumentException("토큰에서 사용자 정보를 추출할 수 없습니다");
                }

                // 인증 정보 설정
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        new TokenUserInfo(userId, departmentId), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("WebSocket authenticated for userId: {}", userId);
            } catch (Exception e) {
                log.error("WebSocket authentication failed: {}", e.getMessage());
                throw new IllegalArgumentException("Invalid JWT Token: " + e.getMessage());
            }
        }
        return message;
    }
}
