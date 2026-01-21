package com.ovengers.chatservice.mongodb.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker // WebSocket을 이용한 메시징 기능 사용
@RequiredArgsConstructor
public class WebSocketStompConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub", "/queue"); // 메시지 수신 (개인, 그룹)
        registry.setApplicationDestinationPrefixes("/pub"); // 메시지 발신
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/stomp") // STOMP 프로토콜을 사용하기 위한 엔드포인트 등록 "localhost:{port}/stomp"
                .setAllowedOrigins(allowedOrigins) // 허용된 도메인만 접근 가능
                .withSockJS(); // WebSocket이 지원되지 않는 브라우저에서도 대체 옵션을 제공
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor); // 인터셉터 추가
    }

}