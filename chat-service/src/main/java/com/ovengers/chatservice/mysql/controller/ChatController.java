package com.ovengers.chatservice.mysql.controller;

import com.ovengers.common.auth.TokenUserInfo;
import com.ovengers.chatservice.mysql.dto.ChatRoomUnreadDto;
import com.ovengers.chatservice.mysql.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    private final ChatService chatService;

    @Operation(summary = "채팅방 목록 및 읽지 않은 메시지 수 조회")
    @GetMapping("/rooms/unread")
    public Mono<ResponseEntity<List<ChatRoomUnreadDto>>> getChatRoomsWithUnreadCount(
            @AuthenticationPrincipal TokenUserInfo tokenUserInfo) {
        return chatService.getChatRoomsWithUnreadCount(tokenUserInfo.getId())
                .map(ResponseEntity::ok)
                .doOnError(e -> log.error("Failed to get chat rooms with unread count: {}", e.getMessage()));
    }

    @Operation(
            summary = "메시지 읽음 처리",
            description = "특정 채팅방의 메시지를 읽음 처리합니다."
    )
    @PostMapping("/rooms/{chatRoomId}/read")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "읽음 처리 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    public ResponseEntity<Void> markAsRead(
            @PathVariable
            @Parameter(description = "채팅방 ID", example = "1")
            Long chatRoomId,

            @AuthenticationPrincipal TokenUserInfo tokenUserInfo,

            @RequestBody
            @io.swagger.v3.oas.annotations.media.Schema(
                    description = "읽음 처리할 메시지 ID",
                    example = "{\"messageId\": \"message-123-456\"}"
            )
            Map<String, String> request
    ) {
        chatService.updateLastReadMessage(
                chatRoomId,
                tokenUserInfo.getId(),
                request.get("messageId")
        );
        return ResponseEntity.ok().build();
    }
}