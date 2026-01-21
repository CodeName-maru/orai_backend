package com.ovengers.chatservice.mongodb.service;

import com.ovengers.chatservice.client.UserResponseDto;
import com.ovengers.chatservice.client.UserServiceClient;
import com.ovengers.chatservice.mongodb.document.Message;
import com.ovengers.chatservice.mongodb.dto.MessageDto;
import com.ovengers.chatservice.mongodb.repository.MessageRepository;
import com.ovengers.chatservice.mysql.repository.ChatRoomRepository;
import com.ovengers.chatservice.mysql.repository.UserChatRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService 단위 테스트")
class MessageServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(MessageServiceTest.class);

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private UserChatRoomRepository userChatRoomRepository;
    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private MessageService messageService;

    private UserResponseDto testUser;
    private Message testMessage;
    private final Long chatRoomId = 1L;
    private final String userId = "user1";
    private final String userName = "테스트유저";
    private final String messageId = "message1";

    @BeforeEach
    void setUp() {
        logger.info("===== 테스트 데이터 초기화 시작 =====");
        testUser = new UserResponseDto();
        testUser.setUserId(userId);
        testUser.setName(userName);
        testUser.setProfileImage("profile.jpg");
        logger.info("테스트 사용자 초기화 완료 - ID: {}", testUser.getUserId());

        testMessage = Message.builder()
                .messageId(messageId)
                .chatRoomId(chatRoomId)
                .content("테스트 메시지")
                .senderId(userId)
                .senderName(userName)
                .senderImage("profile.jpg")
                .type("CHAT")
                .createdAt(LocalDateTime.now())
                .build();
        logger.info("테스트 메시지 초기화 - ID: {}, 내용: {}, 발신자: {}",
                testMessage.getMessageId(), testMessage.getContent(), testMessage.getSenderName());
        logger.info("===== 테스트 데이터 초기화 완료 =====");
    }

    @Test
    @DisplayName("메시지 전송 성공 테스트")
    void sendMessageSuccess() {
        logger.info("===== 메시지 전송 테스트 시작 =====");

        // given
        String content = "테스트 메시지";
        logger.info("테스트 데이터 - 채팅방 ID: {}, 발신자 ID: {}, 메시지 내용: {}",
                chatRoomId, userId, content);

        when(chatRoomRepository.existsById(chatRoomId)).thenReturn(true);
        when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(true);
        when(userServiceClient.getUserById(userId)).thenReturn(testUser);
        when(messageRepository.save(any(Message.class))).thenReturn(Mono.just(testMessage));

        // when
        logger.info("메시지 전송 실행...");
        Mono<MessageDto> result = messageService.sendMessage(chatRoomId, content, userId, userName);

        // then
        logger.info("검증 단계 시작...");
        StepVerifier.create(result)
                .expectNextMatches(messageDto -> {
                    logger.info("전송된 메시지 정보:");
                    logger.info("- 내용: {}", messageDto.getContent());
                    logger.info("- 발신자 ID: {}", messageDto.getSenderId());
                    logger.info("- 메시지 타입: {}", messageDto.getType());
                    return messageDto.getContent().equals(content) &&
                            messageDto.getSenderId().equals(userId) &&
                            messageDto.getType().equals("CHAT");
                })
                .verifyComplete();
        logger.info("메시지 전송 테스트 성공");
        logger.info("============================");
    }

    @Test
    @DisplayName("메시지 삭제 성공 테스트")
    void deleteMessageSuccess() {
        logger.info("===== 메시지 삭제 테스트 시작 =====");

        // given
        logger.info("테스트 데이터 - 채팅방 ID: {}, 메시지 ID: {}, 사용자 ID: {}",
                chatRoomId, messageId, userId);

        Message deletedMessage = Message.builder()
                .content("메시지가 삭제되었습니다.")
                .type("DELETE")
                .build();
        logger.info("삭제될 메시지 생성 완료");

        when(chatRoomRepository.existsById(chatRoomId)).thenReturn(true);
        when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(true);
        when(messageRepository.findByMessageId(messageId)).thenReturn(Mono.just(testMessage));
        when(messageRepository.save(any(Message.class))).thenReturn(Mono.just(deletedMessage));

        // when
        logger.info("메시지 삭제 실행...");
        Mono<MessageDto> result = messageService.deleteMessage(chatRoomId, messageId, userId);

        // then
        logger.info("검증 단계 시작...");
        StepVerifier.create(result)
                .expectNextMatches(messageDto -> {
                    logger.info("삭제된 메시지 정보:");
                    logger.info("- 내용: {}", messageDto.getContent());
                    logger.info("- 메시지 타입: {}", messageDto.getType());
                    return messageDto.getContent().equals("메시지가 삭제되었습니다.") &&
                            messageDto.getType().equals("DELETE");
                })
                .verifyComplete();
        logger.info("메시지 삭제 테스트 성공");
        logger.info("============================");
    }

    @Test
    @DisplayName("권한 없는 메시지 수정 실패 테스트")
    void updateMessageWithoutPermissionFail() {
        logger.info("===== 권한 없는 메시지 수정 테스트 시작 =====");

        // given
        String otherUserId = "user2";
        String newContent = "수정된 메시지";
        logger.info("테스트 데이터:");
        logger.info("- 채팅방 ID: {}", chatRoomId);
        logger.info("- 메시지 ID: {}", messageId);
        logger.info("- 원래 작성자 ID: {}", userId);
        logger.info("- 수정 시도자 ID: {}", otherUserId);
        logger.info("- 수정할 내용: {}", newContent);

        when(chatRoomRepository.existsById(chatRoomId)).thenReturn(true);
        when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, otherUserId)).thenReturn(true);
        when(userServiceClient.getUserById(otherUserId)).thenReturn(new UserResponseDto());
        when(messageRepository.findByMessageId(messageId)).thenReturn(Mono.just(testMessage));

        // when & then
        logger.info("권한 없는 메시지 수정 시도...");
        StepVerifier.create(messageService.updateMessage(chatRoomId, messageId, newContent, otherUserId))
                .expectError(IllegalAccessException.class)
                .verify();
        logger.error("예상된 예외 발생: IllegalAccessException");
        logger.info("권한 없는 메시지 수정 실패 테스트 성공");
        logger.info("=====================================");
    }

    @Nested
    @DisplayName("메시지 전송 추가 테스트")
    class SendMessageAdditionalTest {

        @Test
        @DisplayName("빈 메시지 전송 실패")
        void 빈_메시지_전송_실패() {
            logger.info("===== 빈 메시지 전송 테스트 시작 =====");

            // given
            String emptyContent = "   ";

            when(chatRoomRepository.existsById(chatRoomId)).thenReturn(true);
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(true);

            // when & then
            logger.info("빈 메시지 전송 시도");
            assertThatThrownBy(() -> messageService.sendMessage(chatRoomId, emptyContent, userId, userName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("메시지 내용이 비어 있습니다");

            logger.info("IllegalArgumentException 발생 확인");
        }

        @Test
        @DisplayName("존재하지 않는 채팅방에 메시지 전송 실패")
        void 존재하지_않는_채팅방에_메시지_전송_실패() {
            logger.info("===== 존재하지 않는 채팅방 테스트 시작 =====");

            // given
            Long nonExistentChatRoomId = 999L;
            String content = "테스트 메시지";

            when(chatRoomRepository.existsById(nonExistentChatRoomId)).thenReturn(false);

            // when & then
            logger.info("존재하지 않는 채팅방에 메시지 전송 시도");
            assertThatThrownBy(() -> messageService.sendMessage(nonExistentChatRoomId, content, userId, userName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("채팅방은 존재하지 않습니다");

            logger.info("IllegalArgumentException 발생 확인");
        }

        @Test
        @DisplayName("구독하지 않은 채팅방에 메시지 전송 실패")
        void 구독하지_않은_채팅방에_메시지_전송_실패() {
            logger.info("===== 구독하지 않은 채팅방 메시지 전송 테스트 시작 =====");

            // given
            String content = "테스트 메시지";

            when(chatRoomRepository.existsById(chatRoomId)).thenReturn(true);
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(false);

            // when & then
            logger.info("구독하지 않은 채팅방에 메시지 전송 시도");
            assertThatThrownBy(() -> messageService.sendMessage(chatRoomId, content, userId, userName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("구독되어 있지 않습니다");

            logger.info("IllegalArgumentException 발생 확인");
        }
    }

    @Nested
    @DisplayName("메시지 조회 테스트")
    class GetMessagesTest {

        @Test
        @DisplayName("메시지 목록 조회 성공")
        void 메시지_목록_조회_성공() {
            logger.info("===== 메시지 목록 조회 테스트 시작 =====");

            // given
            Message message1 = Message.builder()
                    .messageId("msg1")
                    .chatRoomId(chatRoomId)
                    .content("첫 번째 메시지")
                    .senderId(userId)
                    .senderName(userName)
                    .type("CHAT")
                    .createdAt(LocalDateTime.now().minusMinutes(5))
                    .build();

            Message message2 = Message.builder()
                    .messageId("msg2")
                    .chatRoomId(chatRoomId)
                    .content("두 번째 메시지")
                    .senderId(userId)
                    .senderName(userName)
                    .type("CHAT")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(userServiceClient.getUserById(userId)).thenReturn(testUser);
            when(chatRoomRepository.existsById(chatRoomId)).thenReturn(true);
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(true);
            when(messageRepository.findByChatRoomIdOrderByCreatedAtAsc(chatRoomId))
                    .thenReturn(Flux.just(message1, message2));

            // when
            logger.info("메시지 목록 조회 실행");
            Flux<MessageDto> result = messageService.getMessages(chatRoomId, userId);

            // then
            StepVerifier.create(result)
                    .expectNextMatches(dto -> dto.getContent().equals("첫 번째 메시지"))
                    .expectNextMatches(dto -> dto.getContent().equals("두 번째 메시지"))
                    .verifyComplete();

            logger.info("메시지 목록 조회 성공 테스트 완료");
        }
    }

    @Nested
    @DisplayName("메시지 수정 추가 테스트")
    class UpdateMessageAdditionalTest {

        @Test
        @DisplayName("존재하지 않는 메시지 수정 실패")
        void 존재하지_않는_메시지_수정_실패() {
            logger.info("===== 존재하지 않는 메시지 수정 테스트 시작 =====");

            // given
            String nonExistentMessageId = "non-existent";
            String newContent = "수정된 내용";

            when(chatRoomRepository.existsById(chatRoomId)).thenReturn(true);
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(true);
            when(userServiceClient.getUserById(userId)).thenReturn(testUser);
            when(messageRepository.findByMessageId(nonExistentMessageId)).thenReturn(Mono.empty());

            // when & then
            logger.info("존재하지 않는 메시지 수정 시도");
            StepVerifier.create(messageService.updateMessage(chatRoomId, nonExistentMessageId, newContent, userId))
                    .expectError(IllegalArgumentException.class)
                    .verify();

            logger.info("IllegalArgumentException 발생 확인");
        }

        @Test
        @DisplayName("동일 내용으로 메시지 수정 실패")
        void 동일_내용으로_메시지_수정_실패() {
            logger.info("===== 동일 내용 메시지 수정 테스트 시작 =====");

            // given
            String sameContent = "테스트 메시지"; // 기존 메시지와 동일

            when(chatRoomRepository.existsById(chatRoomId)).thenReturn(true);
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(true);
            when(userServiceClient.getUserById(userId)).thenReturn(testUser);
            when(messageRepository.findByMessageId(messageId)).thenReturn(Mono.just(testMessage));

            // when & then
            logger.info("동일 내용으로 메시지 수정 시도");
            StepVerifier.create(messageService.updateMessage(chatRoomId, messageId, sameContent, userId))
                    .expectError(IllegalArgumentException.class)
                    .verify();

            logger.info("IllegalArgumentException 발생 확인");
        }

        @Test
        @DisplayName("메시지 수정 성공")
        void 메시지_수정_성공() {
            logger.info("===== 메시지 수정 성공 테스트 시작 =====");

            // given
            String newContent = "수정된 메시지 내용";

            Message updatedMessage = Message.builder()
                    .messageId(messageId)
                    .chatRoomId(chatRoomId)
                    .content(newContent)
                    .senderId(userId)
                    .senderName(userName)
                    .type("EDIT")
                    .build();

            when(chatRoomRepository.existsById(chatRoomId)).thenReturn(true);
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(true);
            when(userServiceClient.getUserById(userId)).thenReturn(testUser);
            when(messageRepository.findByMessageId(messageId)).thenReturn(Mono.just(testMessage));
            when(messageRepository.save(any(Message.class))).thenReturn(Mono.just(updatedMessage));

            // when & then
            logger.info("메시지 수정 실행");
            StepVerifier.create(messageService.updateMessage(chatRoomId, messageId, newContent, userId))
                    .expectNextMatches(dto -> {
                        logger.info("수정된 메시지 내용: {}, 타입: {}", dto.getContent(), dto.getType());
                        return dto.getContent().equals(newContent) && dto.getType().equals("EDIT");
                    })
                    .verifyComplete();

            logger.info("메시지 수정 성공 테스트 완료");
        }
    }

    @Nested
    @DisplayName("메시지 삭제 추가 테스트")
    class DeleteMessageAdditionalTest {

        @Test
        @DisplayName("권한 없는 메시지 삭제 실패")
        void 권한_없는_메시지_삭제_실패() {
            logger.info("===== 권한 없는 메시지 삭제 테스트 시작 =====");

            // given
            String otherUserId = "other-user";

            when(chatRoomRepository.existsById(chatRoomId)).thenReturn(true);
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, otherUserId)).thenReturn(true);
            when(messageRepository.findByMessageId(messageId)).thenReturn(Mono.just(testMessage));

            // when & then
            logger.info("권한 없는 사용자의 메시지 삭제 시도");
            StepVerifier.create(messageService.deleteMessage(chatRoomId, messageId, otherUserId))
                    .expectError(IllegalAccessException.class)
                    .verify();

            logger.info("IllegalAccessException 발생 확인");
        }

        @Test
        @DisplayName("메시지 삭제 후 내용 변경 확인")
        void 메시지_삭제_후_내용_변경_확인() {
            logger.info("===== 메시지 삭제 후 내용 변경 테스트 시작 =====");

            // given
            Message deletedMessage = Message.builder()
                    .messageId(messageId)
                    .chatRoomId(chatRoomId)
                    .content("메시지가 삭제되었습니다.")
                    .senderId(userId)
                    .senderName(userName)
                    .type("DELETE")
                    .build();

            when(chatRoomRepository.existsById(chatRoomId)).thenReturn(true);
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(true);
            when(messageRepository.findByMessageId(messageId)).thenReturn(Mono.just(testMessage));
            when(messageRepository.save(any(Message.class))).thenReturn(Mono.just(deletedMessage));

            // when & then
            logger.info("메시지 삭제 실행 및 내용 변경 확인");
            StepVerifier.create(messageService.deleteMessage(chatRoomId, messageId, userId))
                    .expectNextMatches(dto -> {
                        logger.info("삭제된 메시지 정보 - 내용: {}, 타입: {}", dto.getContent(), dto.getType());
                        boolean contentChanged = dto.getContent().equals("메시지가 삭제되었습니다.");
                        boolean typeChanged = dto.getType().equals("DELETE");
                        return contentChanged && typeChanged;
                    })
                    .verifyComplete();

            logger.info("메시지 삭제 내용 변경 확인 완료");
        }
    }

    @Nested
    @DisplayName("사용자 정보 조회 테스트")
    class GetUserInfoTest {

        @Test
        @DisplayName("사용자 정보 조회 실패 - null 반환")
        void 사용자_정보_조회_실패() {
            logger.info("===== 사용자 정보 조회 실패 테스트 시작 =====");

            // given
            String unknownUserId = "unknown-user";
            when(userServiceClient.getUserById(unknownUserId)).thenReturn(null);

            // when & then
            logger.info("존재하지 않는 사용자 정보 조회 시도");
            assertThatThrownBy(() -> messageService.getUserInfo(unknownUserId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("사용자 정보를 가져오는 데 실패했습니다");

            logger.info("RuntimeException 발생 확인");
        }

        @Test
        @DisplayName("사용자 정보 조회 성공")
        void 사용자_정보_조회_성공() {
            logger.info("===== 사용자 정보 조회 성공 테스트 시작 =====");

            // given
            when(userServiceClient.getUserById(userId)).thenReturn(testUser);

            // when
            logger.info("사용자 정보 조회 실행");
            UserResponseDto result = messageService.getUserInfo(userId);

            // then
            assertThat(result).isNotNull();
            verify(userServiceClient, times(1)).getUserById(userId);
            logger.info("사용자 정보 조회 성공");
        }
    }
}