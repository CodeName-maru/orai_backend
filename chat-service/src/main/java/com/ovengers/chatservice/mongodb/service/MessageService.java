package com.ovengers.chatservice.mongodb.service;

import com.ovengers.chatservice.client.UserResponseDto;
import com.ovengers.chatservice.client.UserServiceClient;
import com.ovengers.chatservice.mongodb.dto.MessageDto;
import com.ovengers.chatservice.mongodb.document.Message;
import com.ovengers.chatservice.mongodb.repository.MessageRepository;
import com.ovengers.chatservice.mysql.repository.ChatRoomRepository;
import com.ovengers.chatservice.mysql.repository.UserChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final UserServiceClient userServiceClient;

    public UserResponseDto getUserInfo(String userId) {
        UserResponseDto userById = userServiceClient.getUserById(userId);

        if (userById == null) {
            throw new RuntimeException("사용자 정보를 가져오는 데 실패했습니다: " + userId);
        }

        return userById;
    }

    private void validateMessageContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("메시지 내용이 비어 있습니다.");
        }
    }

    private void validateChatRoomAndUser(Long chatRoomId, String userId) {
        if (!chatRoomRepository.existsById(chatRoomId)) {
            throw new IllegalArgumentException(chatRoomId + "번 채팅방은 존재하지 않습니다.");
        }

        if (!userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)) {
            throw new IllegalArgumentException(chatRoomId + "번 채팅방에 구독되어 있지 않습니다.");
        }
    }

    private Mono<Void> validateChatRoomAndUserReactive(Long chatRoomId, String userId) {
        return Mono.fromRunnable(() -> validateChatRoomAndUser(chatRoomId, userId));
    }

    // 메시지 전송
    public Mono<MessageDto> sendMessage(Long chatRoomId, String content, String userId, String userName) {
        validateChatRoomAndUser(chatRoomId, userId);
        validateMessageContent(content.trim());

        UserResponseDto userInfo = getUserInfo(userId);

        Message message = Message.builder()
                .chatRoomId(chatRoomId)
                .content(content)
                .senderId(userId)
                .senderImage(userInfo.getProfileImage())
                .senderName(userName)
                .build();

        message.setType("CHAT");
        log.debug("\n\n\n chatRoomId: {}, content: {}, senderId: {}\n\n\n", chatRoomId, content, userId);

        return messageRepository.save(message).map(Message::toDto);
    }

    // 메시지 조회
    public Flux<MessageDto> getMessages(Long chatRoomId, String userId) {
        UserResponseDto userInfo = getUserInfo(userId);

        return validateChatRoomAndUserReactive(chatRoomId, userInfo.getUserId())
                .thenMany(messageRepository.findByChatRoomIdOrderByCreatedAtAsc(chatRoomId)
                        .map(Message::toDto));
    }

    // 메시지 수정
    public Mono<MessageDto> updateMessage(Long chatRoomId, String messageId, String newContent, String userId) {
        validateChatRoomAndUser(chatRoomId, userId);
        UserResponseDto userInfo = getUserInfo(userId);

        return messageRepository.findByMessageId(messageId)
                .flatMap(existingMessage -> {
                    if (!existingMessage.getSenderId().equals(userInfo.getUserId())) {
                        return Mono.error(new IllegalAccessException("메시지를 수정할 권한이 없습니다."));
                    }

                    if (existingMessage.getContent().equals(newContent.trim())) {
                        return Mono.error(new IllegalArgumentException("메시지에 수정 사항이 없습니다."));
                    }

                    validateMessageContent(newContent.trim());
                    existingMessage.setContent(newContent.trim());
                    existingMessage.setType("EDIT");

                    return messageRepository.save(existingMessage).map(Message::toDto);
                })
                .switchIfEmpty(Mono.error(new IllegalArgumentException("메시지가 존재하지 않습니다.")));
    }

    // 메시지 삭제
    public Mono<MessageDto> deleteMessage(Long chatRoomId, String messageId, String userId) {
        validateChatRoomAndUser(chatRoomId, userId);

        return messageRepository.findByMessageId(messageId)
                .flatMap(message -> {
                    if (!message.getSenderId().equals(userId)) {
                        return Mono.error(new IllegalAccessException("메시지를 삭제할 권한이 없습니다."));
                    }
                    message.setContent("메시지가 삭제되었습니다.");
                    message.setType("DELETE");
                    return messageRepository.save(message).map(Message::toDto);
                });
    }

    // 페이징된 메시지 조회 (최신 메시지부터)
    public Flux<MessageDto> getMessagesWithPaging(Long chatRoomId, String userId, int size) {
        UserResponseDto userInfo = getUserInfo(userId);

        return validateChatRoomAndUserReactive(chatRoomId, userInfo.getUserId())
                .thenMany(messageRepository.findByChatRoomIdOrderByCreatedAtDesc(chatRoomId, PageRequest.of(0, size))
                        .collectList()
                        .flatMapMany(messages -> {
                            // 시간순 정렬로 반환 (오래된 메시지 -> 최신 메시지)
                            java.util.Collections.reverse(messages);
                            return Flux.fromIterable(messages);
                        })
                        .map(Message::toDto));
    }

    // 커서 기반 페이징: 이전 메시지 로드
    public Flux<MessageDto> getMessagesBefore(Long chatRoomId, String userId, LocalDateTime cursor, int size) {
        UserResponseDto userInfo = getUserInfo(userId);

        return validateChatRoomAndUserReactive(chatRoomId, userInfo.getUserId())
                .thenMany(messageRepository.findByChatRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                                chatRoomId, cursor, PageRequest.of(0, size))
                        .collectList()
                        .flatMapMany(messages -> {
                            // 시간순 정렬로 반환
                            java.util.Collections.reverse(messages);
                            return Flux.fromIterable(messages);
                        })
                        .map(Message::toDto));
    }

    // 커서 기반 페이징: 새 메시지 로드 (실시간 동기화용)
    public Flux<MessageDto> getMessagesAfter(Long chatRoomId, String userId, LocalDateTime cursor) {
        UserResponseDto userInfo = getUserInfo(userId);

        return validateChatRoomAndUserReactive(chatRoomId, userInfo.getUserId())
                .thenMany(messageRepository.findByChatRoomIdAndCreatedAtAfterOrderByCreatedAtAsc(chatRoomId, cursor)
                        .map(Message::toDto));
    }

    // 메시지 총 개수 조회
    public Mono<Long> getMessageCount(Long chatRoomId, String userId) {
        validateChatRoomAndUser(chatRoomId, userId);
        return messageRepository.countByChatRoomId(chatRoomId);
    }
}
