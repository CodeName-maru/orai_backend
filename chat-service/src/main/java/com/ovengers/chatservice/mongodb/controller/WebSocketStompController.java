package com.ovengers.chatservice.mongodb.controller;

import com.ovengers.chatservice.mongodb.dto.MessageDto;
import com.ovengers.chatservice.mongodb.service.MessageService;
import com.ovengers.chatservice.mysql.service.ChatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Slf4j
@Controller
@RequiredArgsConstructor
@Tag(name = "WebSocketStompController", description = "유튜브 참고")
public class WebSocketStompController {
    private final MessageService messageService;
    private final ChatService chatService;

    /**
     * stomp 통신
     */
    @MessageMapping("/{chatRoomId}/send")
    @SendTo("/sub/{chatRoomId}/chat")
    public Mono<MessageDto> broadcastMessage(
            @DestinationVariable Long chatRoomId,
            @Payload String content,
            @Header("userId") String userId,
            @Header("userName") String userName) {

        return messageService.sendMessage(chatRoomId, content, userId, userName)
                .doOnSuccess(messageDto -> {
                    // 메시지 전송 성공 시 읽지 않은 메시지 수 증가
                    chatService.incrementUnreadCount(chatRoomId, userId);
                })
                .onErrorResume(e -> {
                    log.error("메시지 전송 실패: {}", e.getMessage());
                    // 에러 메시지도 MessageDto 형식으로 반환
                    return Mono.just(MessageDto.builder()
                            .type("ERROR")
                            .content(e.getMessage())
                            .build());
                });
    }
}
