package com.ovengers.chatservice.mongodb.controller;

import com.ovengers.common.auth.TokenUserInfo;
import com.ovengers.chatservice.mongodb.dto.MessageDto;
import com.ovengers.chatservice.mongodb.dto.MessageRequestDto;
import com.ovengers.chatservice.mongodb.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@Validated
@Tag(name = "MessageController", description = "단순히 MongoDB에 데이터를 저장하는 컨트롤러")
public class MessageController {
    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

//    @Operation(summary = "메시지 저장", description = "채팅방Id, 콘텐츠")
//    @PostMapping("/{chatRoomId}/saveMessage")
//    public Mono<MessageDto> saveMessage(@PathVariable Long chatRoomId,
//                                        @RequestBody MessageRequestDto messageRequestDto,
//                                        @AuthenticationPrincipal TokenUserInfo tokenUserInfo) {
//
//        return messageService.sendMessage(chatRoomId, messageRequestDto.getContent(), tokenUserInfo.getId());
////                .doOnSuccess(messageDto -> messagingTemplate.convertAndSend("/sub/" + chatRoomId + "/chat", messageDto));
//    }

    @Operation(summary = "채팅방의 메시지 조회 (전체)", description = "채팅방의 모든 메시지 조회")
    @GetMapping("/{chatRoomId}/messageList")
    public Flux<MessageDto> getMessages(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long chatRoomId,
            @AuthenticationPrincipal TokenUserInfo tokenUserInfo) {
        return messageService.getMessages(chatRoomId, tokenUserInfo.getId());
    }

    @Operation(summary = "채팅방의 최근 메시지 조회 (페이징)", description = "최근 메시지를 size개만큼 조회")
    @GetMapping("/{chatRoomId}/messages")
    public Flux<MessageDto> getMessagesWithPaging(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long chatRoomId,
            @AuthenticationPrincipal TokenUserInfo tokenUserInfo,
            @Parameter(description = "조회할 메시지 개수")
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return messageService.getMessagesWithPaging(chatRoomId, tokenUserInfo.getId(), size);
    }

    @Operation(summary = "이전 메시지 조회 (커서 기반)", description = "특정 시간 이전의 메시지를 size개만큼 조회")
    @GetMapping("/{chatRoomId}/messages/before")
    public Flux<MessageDto> getMessagesBefore(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long chatRoomId,
            @AuthenticationPrincipal TokenUserInfo tokenUserInfo,
            @Parameter(description = "커서 시간 (이 시간 이전 메시지 조회)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursor,
            @Parameter(description = "조회할 메시지 개수")
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return messageService.getMessagesBefore(chatRoomId, tokenUserInfo.getId(), cursor, size);
    }

    @Operation(summary = "새 메시지 조회 (커서 기반)", description = "특정 시간 이후의 새 메시지 조회")
    @GetMapping("/{chatRoomId}/messages/after")
    public Flux<MessageDto> getMessagesAfter(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long chatRoomId,
            @AuthenticationPrincipal TokenUserInfo tokenUserInfo,
            @Parameter(description = "커서 시간 (이 시간 이후 메시지 조회)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursor) {
        return messageService.getMessagesAfter(chatRoomId, tokenUserInfo.getId(), cursor);
    }

    @Operation(summary = "채팅방 메시지 총 개수 조회", description = "채팅방의 전체 메시지 개수")
    @GetMapping("/{chatRoomId}/messages/count")
    public Mono<Long> getMessageCount(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long chatRoomId,
            @AuthenticationPrincipal TokenUserInfo tokenUserInfo) {
        return messageService.getMessageCount(chatRoomId, tokenUserInfo.getId());
    }

    @Operation(summary = "메시지 수정", description = "채팅방Id, 메시지Id, 콘텐츠")
    @PutMapping("/{chatRoomId}/{messageId}/updateMessage")
    public Mono<MessageDto> updateMessage(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long chatRoomId,
            @PathVariable String messageId,
            @Valid @RequestBody MessageRequestDto messageRequestDto,
            @AuthenticationPrincipal TokenUserInfo tokenUserInfo) {

        return messageService.updateMessage(chatRoomId, messageId, messageRequestDto.getContent(), tokenUserInfo.getId())
                .doOnSuccess(updatedMessage -> messagingTemplate.convertAndSend("/sub/" + chatRoomId + "/chat", updatedMessage));
    }

    @Operation(summary = "메시지 삭제", description = "채팅방Id, 메시지Id")
    @DeleteMapping("/{chatRoomId}/{messageId}/deleteMessage")
    public Mono<MessageDto> deleteMessage(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long chatRoomId,
            @PathVariable String messageId,
            @AuthenticationPrincipal TokenUserInfo tokenUserInfo) {
        return messageService.deleteMessage(chatRoomId, messageId, tokenUserInfo.getId())
                .doOnSuccess(deletedMessage ->
                        messagingTemplate.convertAndSend("/sub/" + chatRoomId + "/chat", deletedMessage));
    }
}
