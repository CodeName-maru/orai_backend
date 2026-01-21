package com.ovengers.chatservice.mysql.service;

import com.ovengers.chatservice.mongodb.document.Message;
import com.ovengers.chatservice.mongodb.repository.MessageRepository;
import com.ovengers.chatservice.mysql.dto.ChatRoomUnreadDto;
import com.ovengers.chatservice.mysql.entity.ChatRoom;
import com.ovengers.chatservice.mysql.entity.ChatRoomRead;
import com.ovengers.chatservice.mysql.entity.UserChatRoom;
import com.ovengers.chatservice.mysql.repository.ChatRoomReadRepository;
import com.ovengers.chatservice.mysql.repository.ChatRoomRepository;
import com.ovengers.chatservice.mysql.repository.UserChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {
    private final UserChatRoomRepository userChatRoomRepository;
    private final ChatRoomReadRepository chatRoomReadRepository;
    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;

    public Mono<List<ChatRoomUnreadDto>> getChatRoomsWithUnreadCount(String userId) {
        // 사용자가 구독한 채팅방 목록 조회
        List<UserChatRoom> userChatRooms = userChatRoomRepository.findAllByUserId(userId);

        if (userChatRooms.isEmpty()) {
            return Mono.empty();
        }

        // 각 채팅방별 마지막 메시지와 읽지 않은 메시지 수 조회
        return Flux.fromIterable(userChatRooms)
                .flatMap(userChatRoom -> {
                    Long chatRoomId = userChatRoom.getChatRoomId();
                    ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                            .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다."));

                    // 마지막 메시지 조회
                    Mono<Message> lastMessageMono = messageRepository
                            .findByChatRoomIdOrderByCreatedAtAsc(chatRoomId)
                            .last();

                    // 읽지 않은 메시지 수 조회("SYSTEM"타입 제외)
                    Mono<Long> unreadCountMono = getUnreadCountExcludingSystem(chatRoomId, userId);

                    return Mono.zip(lastMessageMono, unreadCountMono)
                            .map(tuple -> {
                                Message lastMessage = tuple.getT1();
                                Long unreadCount = tuple.getT2();

                                return ChatRoomUnreadDto.builder()
                                        .chatRoomId(chatRoomId)
                                        .name(chatRoom.getName())
                                        .image(chatRoom.getImage())
                                        .unreadCount(unreadCount)
                                        .lastMessage(lastMessage.getContent())
                                        .lastMessageTime(lastMessage.getCreatedAt()
                                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                                        .build();
                            });
                })
                .collectList();
    }

    @Transactional
    public void updateLastReadMessage(Long chatRoomId, String userId, String messageId) {
        ChatRoomRead chatRoomRead = chatRoomReadRepository
                .findByChatRoomIdAndUserId(chatRoomId, userId)
                .orElseGet(() -> ChatRoomRead.builder()
                        .chatRoomId(chatRoomId)
                        .userId(userId)
                        .unreadCount(0L)
                        .build());

        chatRoomRead.setLastReadMessageId(messageId);
        chatRoomRead.setUnreadCount(0L);
        chatRoomReadRepository.save(chatRoomRead);
    }

    @Transactional
    public void incrementUnreadCount(Long chatRoomId, String senderId) {
        List<UserChatRoom> subscribers = userChatRoomRepository.findAllByChatRoomId(chatRoomId);

        subscribers.stream()
                .filter(subscriber -> !subscriber.getUserId().equals(senderId))
                .forEach(subscriber -> {
                    String userId = subscriber.getUserId();
                    // 원자적 업데이트 시도
                    int updatedRows = chatRoomReadRepository.incrementUnreadCount(chatRoomId, userId);

                    // 레코드가 없으면 새로 생성
                    if (updatedRows == 0) {
                        ChatRoomRead chatRoomRead = ChatRoomRead.builder()
                                .chatRoomId(chatRoomId)
                                .userId(userId)
                                .unreadCount(1L)
                                .build();
                        chatRoomReadRepository.save(chatRoomRead);
                    }
                });
    }

    private Mono<Long> getUnreadCountExcludingSystem(Long chatRoomId, String userId) {
        ChatRoomRead chatRoomRead = chatRoomReadRepository
                .findByChatRoomIdAndUserId(chatRoomId, userId)
                .orElse(null);

        if (chatRoomRead == null || chatRoomRead.getLastReadMessageId() == null) {
            return messageRepository.findByChatRoomIdAndTypeNotOrderByCreatedAtAsc(chatRoomId, "SYSTEM").count();
        }

        return messageRepository.findByMessageId(chatRoomRead.getLastReadMessageId())
                .flatMap(lastReadMessage ->
                        messageRepository.findByChatRoomIdAndTypeNotOrderByCreatedAtAsc(chatRoomId, "SYSTEM")
                                .filter(message -> message.getCreatedAt().isAfter(lastReadMessage.getCreatedAt()))
                                .count()
                );
    }
}