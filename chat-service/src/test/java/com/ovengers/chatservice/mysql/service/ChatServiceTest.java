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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(ChatRoomServiceTest.class);

    @Mock
    private UserChatRoomRepository userChatRoomRepository;
    @Mock
    private ChatRoomReadRepository chatRoomReadRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;

    @InjectMocks
    private ChatService chatService;

    private ChatRoom testChatRoom;
    private UserChatRoom testUserChatRoom;
    private Message testMessage;
    private ChatRoomRead testChatRoomRead;

    @BeforeEach
    void setUp() {
        logger.info("===== 테스트 데이터 초기화 시작 =====");

        testChatRoom = ChatRoom.builder()
                .chatRoomId(1L)
                .name("테스트 채팅방")
                .image("test.jpg")
                .build();
        logger.info("채팅방 데이터 초기화: {}", testChatRoom);

        testUserChatRoom = UserChatRoom.builder()
                .chatRoomId(1L)
                .userId("user1")
                .build();
        logger.info("사용자 채팅방 데이터 초기화: {}", testUserChatRoom);

        testMessage = Message.builder()
                .messageId("message1")
                .chatRoomId(1L)
                .content("테스트 메시지")
                .type("CHAT")
                .createdAt(LocalDateTime.now())
                .build();
        logger.info("메시지 데이터 초기화: {}", testMessage);

        testChatRoomRead = ChatRoomRead.builder()
                .chatRoomId(1L)
                .userId("user1")
                .lastReadMessageId("message1")
                .unreadCount(0L)
                .build();
        logger.info("채팅방 읽음 데이터 초기화: {}", testChatRoomRead);

        logger.info("===== 테스트 데이터 초기화 완료 =====");
    }

    @Test
    @DisplayName("채팅방 목록과 읽지 않은 메시지 수 조회 성공 테스트")
    void getChatRoomsWithUnreadCountSuccess() {
        logger.info("===== 채팅방 목록 조회 테스트 시작 =====");

        // given
        String userId = "user1";
        List<UserChatRoom> userChatRooms = Collections.singletonList(testUserChatRoom);
        logger.info("테스트 데이터 - 사용자 ID: {}", userId);

        when(userChatRoomRepository.findAllByUserId(userId)).thenReturn(userChatRooms);
        when(chatRoomRepository.findById(anyLong())).thenReturn(Optional.of(testChatRoom));
        when(messageRepository.findByChatRoomIdOrderByCreatedAtAsc(anyLong()))
                .thenReturn(Flux.just(testMessage));
        when(messageRepository.findByChatRoomIdAndTypeNotOrderByCreatedAtAsc(anyLong(), eq("SYSTEM")))
                .thenReturn(Flux.just(testMessage));

        // when
        logger.info("채팅방 목록 조회 실행...");
        Mono<List<ChatRoomUnreadDto>> result = chatService.getChatRoomsWithUnreadCount(userId);

        // then
        logger.info("검증 단계 시작...");
        StepVerifier.create(result)
                .expectNextMatches(chatRooms -> {
                    ChatRoomUnreadDto dto = chatRooms.get(0);
                    logger.info("조회된 채팅방 정보 - ID: {}, 이름: {}, 마지막 메시지: {}",
                            dto.getChatRoomId(), dto.getName(), dto.getLastMessage());
                    return dto.getChatRoomId().equals(1L) &&
                            dto.getName().equals("테스트 채팅방") &&
                            dto.getImage().equals("test.jpg") &&
                            dto.getLastMessage().equals("테스트 메시지");
                })
                .verifyComplete();
        logger.info("채팅방 목록 조회 테스트 성공");
    }

    @Test
    @DisplayName("마지막으로 읽은 메시지 업데이트 성공 테스트")
    void updateLastReadMessageSuccess() {
        logger.info("===== 마지막 읽은 메시지 업데이트 테스트 시작 =====");

        // given
        Long chatRoomId = 1L;
        String userId = "user1";
        String messageId = "message1";
        logger.info("테스트 데이터 - 채팅방 ID: {}, 사용자 ID: {}, 메시지 ID: {}",
                chatRoomId, userId, messageId);

        when(chatRoomReadRepository.findByChatRoomIdAndUserId(chatRoomId, userId))
                .thenReturn(Optional.of(testChatRoomRead));

        // when
        logger.info("마지막 읽은 메시지 업데이트 실행...");
        chatService.updateLastReadMessage(chatRoomId, userId, messageId);

        // then
        logger.info("검증 단계 시작...");
        verify(chatRoomReadRepository).save(any(ChatRoomRead.class));
        logger.info("마지막 읽은 메시지 업데이트 테스트 성공");
    }

    @Test
    @DisplayName("읽지 않은 메시지 수 증가 성공 테스트")
    void incrementUnreadCountSuccess() {
        logger.info("===== 읽지 않은 메시지 수 증가 테스트 시작 =====");

        // given
        Long chatRoomId = 1L;
        String senderId = "user1";
        String receiverId = "user2";
        logger.info("테스트 데이터 - 채팅방 ID: {}, 발신자 ID: {}, 수신자 ID: {}",
                chatRoomId, senderId, receiverId);

        UserChatRoom receiverChatRoom = UserChatRoom.builder()
                .chatRoomId(chatRoomId)
                .userId(receiverId)
                .build();

        List<UserChatRoom> subscribers = Arrays.asList(testUserChatRoom, receiverChatRoom);
        logger.info("채팅방 구독자 수: {}", subscribers.size());

        ChatRoomRead receiverChatRoomRead = ChatRoomRead.builder()
                .chatRoomId(chatRoomId)
                .userId(receiverId)
                .unreadCount(0L)
                .build();

        when(userChatRoomRepository.findAllByChatRoomId(chatRoomId)).thenReturn(subscribers);
        when(chatRoomReadRepository.findByChatRoomIdAndUserId(chatRoomId, receiverId))
                .thenReturn(Optional.of(receiverChatRoomRead));

        // when
        logger.info("읽지 않은 메시지 수 증가 실행...");
        chatService.incrementUnreadCount(chatRoomId, senderId);

        // then
        logger.info("검증 단계 시작...");
        verify(chatRoomReadRepository).save(argThat(chatRoomRead -> {
            logger.info("저장된 읽지 않은 메시지 수: {}", chatRoomRead.getUnreadCount());
            return chatRoomRead.getUserId().equals(receiverId) &&
                    chatRoomRead.getUnreadCount() == 1L;
        }));
        logger.info("읽지 않은 메시지 수 증가 테스트 성공");
    }

    @Test
    @DisplayName("새로운 ChatRoomRead 생성 테스트")
    void createNewChatRoomReadSuccess() {
        logger.info("===== 새로운 ChatRoomRead 생성 테스트 시작 =====");

        // given
        Long chatRoomId = 1L;
        String userId = "user1";
        String messageId = "message1";
        logger.info("테스트 데이터 - 채팅방 ID: {}, 사용자 ID: {}, 메시지 ID: {}",
                chatRoomId, userId, messageId);

        when(chatRoomReadRepository.findByChatRoomIdAndUserId(chatRoomId, userId))
                .thenReturn(Optional.empty());

        // when
        logger.info("새로운 ChatRoomRead 생성 실행...");
        chatService.updateLastReadMessage(chatRoomId, userId, messageId);

        // then
        logger.info("검증 단계 시작...");
        verify(chatRoomReadRepository).save(argThat(chatRoomRead -> {
            logger.info("생성된 ChatRoomRead 정보:");
            logger.info("- 채팅방 ID: {}", chatRoomRead.getChatRoomId());
            logger.info("- 사용자 ID: {}", chatRoomRead.getUserId());
            logger.info("- 마지막 읽은 메시지 ID: {}", chatRoomRead.getLastReadMessageId());
            logger.info("- 읽지 않은 메시지 수: {}", chatRoomRead.getUnreadCount());
            return chatRoomRead.getChatRoomId().equals(chatRoomId) &&
                    chatRoomRead.getUserId().equals(userId) &&
                    chatRoomRead.getLastReadMessageId().equals(messageId) &&
                    chatRoomRead.getUnreadCount() == 0L;
        }));
        logger.info("새로운 ChatRoomRead 생성 테스트 성공");
    }

    @Test
    @DisplayName("구독한 채팅방이 없을 때 빈 결과 반환 테스트")
    void getEmptyChatRoomsSuccess() {
        logger.info("===== 빈 채팅방 목록 조회 테스트 시작 =====");

        // given
        String userId = "user1";
        logger.info("테스트 데이터 - 사용자 ID: {}", userId);
        when(userChatRoomRepository.findAllByUserId(userId)).thenReturn(Collections.emptyList());

        // when
        logger.info("빈 채팅방 목록 조회 실행...");
        Mono<List<ChatRoomUnreadDto>> result = chatService.getChatRoomsWithUnreadCount(userId);

        // then
        logger.info("검증 단계 시작...");
        StepVerifier.create(result)
                .verifyComplete();
        logger.info("빈 채팅방 목록 조회 테스트 성공");
    }
}