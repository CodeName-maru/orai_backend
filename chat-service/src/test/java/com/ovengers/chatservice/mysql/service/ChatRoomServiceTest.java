package com.ovengers.chatservice.mysql.service;

import com.ovengers.chatservice.client.UserResponseDto;
import com.ovengers.chatservice.client.UserServiceClient;
import com.ovengers.chatservice.mongodb.document.Message;
import com.ovengers.chatservice.mongodb.repository.MessageRepository;
import com.ovengers.chatservice.mysql.dto.ChatRoomDto;
import com.ovengers.chatservice.mysql.dto.CompositeChatRoomDto;
import com.ovengers.chatservice.mysql.entity.ChatRoom;
import com.ovengers.chatservice.mysql.entity.UserChatRoom;
import com.ovengers.common.exception.BusinessException;
import com.ovengers.chatservice.mysql.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatRoomService 단위 테스트")
class ChatRoomServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(ChatRoomServiceTest.class);

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private UserChatRoomRepository userChatRoomRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private InvitationRepository invitationRepository;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;
    @Mock
    private ChatRoomReadRepository chatRoomReadRepository;

    @InjectMocks
    private ChatRoomService chatRoomService;

    private UserResponseDto testUser1;
    private UserResponseDto testUser2;
    private UserResponseDto testUser3;
    private ChatRoom testChatRoom;


    @BeforeEach
    void setUp() {
        logger.info("===== 테스트 데이터 초기화 시작 =====");
        testUser1 = new UserResponseDto();
        testUser1.setUserId("user1");
        testUser1.setName("테스트유저1");

        testUser2 = new UserResponseDto();
        testUser2.setUserId("user2");
        testUser2.setName("테스트유저2");

        testUser3 = new UserResponseDto();
        testUser3.setUserId("user3");
        testUser3.setName("테스트유저3");

        testChatRoom = ChatRoom.builder()
                .chatRoomId(1L)
                .name("테스트 채팅방")
                .image("test.jpg")
                .creatorId("user1")
                .build();

        logger.info("테스트 사용자 데이터 초기화 완료");
    }

    @Test
    @DisplayName("채팅방 수정 성공 테스트")
    void updateChatRoomSuccess() {
        logger.info("===== 채팅방 수정 테스트 시작 =====");

        // given
        Long chatRoomId = 1L;
        String userId = "user1";
        String newImage = "new.jpg";
        String newName = "수정된 채팅방";

        logger.info("테스트 데이터 설정 - 채팅방 ID: {}, 사용자 ID: {}", chatRoomId, userId);
        logger.info("수정할 정보 - 이름: {}, 이미지: {}", newName, newImage);

        ChatRoom chatRoom = ChatRoom.builder()
                .chatRoomId(chatRoomId)
                .name("기존 채팅방")
                .image("old.jpg")
                .creatorId(userId)
                .build();

        when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(chatRoom));
        when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(true);
        when(messageRepository.save(any())).thenReturn(Mono.just(new Message()));

        // when
        logger.info("채팅방 수정 실행...");
        ChatRoomDto result = chatRoomService.updateChatRoom(chatRoomId, newImage, newName, userId);

        // then
        logger.info("검증 단계 시작...");
        assertNotNull(result);
        assertEquals(newName, result.getName());
        assertEquals(newImage, result.getImage());
        verify(chatRoomRepository).findById(chatRoomId);
        logger.info("채팅방 수정 결과 - 이름: {}, 이미지: {}", result.getName(), result.getImage());
        logger.info("채팅방 수정 테스트 성공");
    }

    @Test
    @DisplayName("채팅방 생성 실패 - 잘못된 이름")
    void createChatRoomFailWithInvalidName() {
        logger.info("===== 채팅방 생성 실패 테스트 시작 =====");

        // given
        String image = "test.jpg";
        String invalidName = "   ";
        String userId = "user1";
        List<String> userIds = Arrays.asList("user1", "user2");

        logger.info("테스트 데이터 - 잘못된 채팅방 이름: '{}', 사용자 ID: {}", invalidName, userId);
        logger.info("참여 사용자 목록: {}", userIds);

        // when & then
        logger.info("잘못된 이름으로 채팅방 생성 시도...");
        Exception exception = assertThrows(BusinessException.class, () ->
                chatRoomService.createChatRoom(image, invalidName, userId, userIds)
        );
        logger.error("예상된 예외 발생: {}", exception.getClass().getSimpleName());
        logger.info("채팅방 생성 실패 테스트 성공");
    }

    @Test
    @DisplayName("채팅방 생성자 나가기 실패 테스트")
    void disconnectChatRoomFailForCreator() {
        logger.info("===== 채팅방 생성자 나가기 실패 테스트 시작 =====");

        // given
        Long chatRoomId = 1L;
        String creatorId = "user1";

        logger.info("테스트 데이터 - 채팅방 ID: {}, 생성자 ID: {}", chatRoomId, creatorId);

        ChatRoom chatRoom = ChatRoom.builder()
                .chatRoomId(chatRoomId)
                .name("테스트 채팅방")
                .creatorId(creatorId)
                .build();

        when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(chatRoom));
        when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, creatorId)).thenReturn(true);

        // when & then
        logger.info("채팅방 생성자의 채팅방 나가기 시도...");
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                chatRoomService.disconnectChatRoom(chatRoomId, creatorId)
        );
        logger.error("예상된 예외 발생: {}", exception.getClass().getSimpleName());
        logger.info("채팅방 생성자 나가기 실패 테스트 성공");
    }

    @Nested
    @DisplayName("채팅방 조회 테스트")
    class GetChatRoomTest {

        @Test
        @DisplayName("사용자의 채팅방 목록 조회 성공")
        void 사용자의_채팅방_목록_조회_성공() {
            logger.info("===== 채팅방 목록 조회 테스트 시작 =====");

            // given
            String userId = "user1";
            UserChatRoom userChatRoom1 = UserChatRoom.builder().chatRoomId(1L).userId(userId).build();
            UserChatRoom userChatRoom2 = UserChatRoom.builder().chatRoomId(2L).userId(userId).build();

            ChatRoom chatRoom1 = ChatRoom.builder().chatRoomId(1L).name("채팅방1").creatorId(userId).build();
            ChatRoom chatRoom2 = ChatRoom.builder().chatRoomId(2L).name("채팅방2").creatorId(userId).build();

            when(userChatRoomRepository.findAllByUserId(userId))
                    .thenReturn(Arrays.asList(userChatRoom1, userChatRoom2));
            when(chatRoomRepository.findAllById(Arrays.asList(1L, 2L)))
                    .thenReturn(Arrays.asList(chatRoom1, chatRoom2));

            // when
            logger.info("사용자 {} 의 채팅방 목록 조회", userId);
            List<ChatRoomDto> result = chatRoomService.getChatRooms(userId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("채팅방1");
            assertThat(result.get(1).getName()).isEqualTo("채팅방2");
            logger.info("채팅방 목록 조회 성공 - 조회된 수: {}", result.size());
        }

        @Test
        @DisplayName("채팅방 목록 없음 - 빈 리스트 반환")
        void 채팅방_목록_없음() {
            logger.info("===== 채팅방 없음 테스트 시작 =====");

            // given
            String userId = "user1";
            when(userChatRoomRepository.findAllByUserId(userId)).thenReturn(Collections.emptyList());

            // when
            List<ChatRoomDto> result = chatRoomService.getChatRooms(userId);

            // then
            assertThat(result).isEmpty();
            logger.info("빈 채팅방 목록 반환 확인");
        }

        @Test
        @DisplayName("특정 채팅방 조회 성공")
        void 특정_채팅방_조회_성공() {
            logger.info("===== 특정 채팅방 조회 테스트 시작 =====");

            // given
            Long chatRoomId = 1L;
            String userId = "user1";

            when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(testChatRoom));
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(true);

            // when
            logger.info("채팅방 조회 - ID: {}, 사용자: {}", chatRoomId, userId);
            ChatRoomDto result = chatRoomService.getChatRoom(chatRoomId, userId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getChatRoomId()).isEqualTo(chatRoomId);
            assertThat(result.getName()).isEqualTo("테스트 채팅방");
            logger.info("특정 채팅방 조회 성공");
        }

        @Test
        @DisplayName("존재하지 않는 채팅방 조회 실패")
        void 존재하지_않는_채팅방_조회_실패() {
            logger.info("===== 존재하지 않는 채팅방 조회 테스트 시작 =====");

            // given
            Long chatRoomId = 999L;
            String userId = "user1";

            when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.empty());

            // when & then
            logger.info("존재하지 않는 채팅방 조회 시도 - ID: {}", chatRoomId);
            assertThatThrownBy(() -> chatRoomService.getChatRoom(chatRoomId, userId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("채팅방은 존재하지 않습니다");

            logger.info("EntityNotFoundException 발생 확인");
        }

        @Test
        @DisplayName("구독하지 않은 채팅방 조회 실패")
        void 구독하지_않은_채팅방_조회_실패() {
            logger.info("===== 구독하지 않은 채팅방 조회 테스트 시작 =====");

            // given
            Long chatRoomId = 1L;
            String userId = "user3"; // 구독하지 않은 사용자

            when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(testChatRoom));
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(false);

            // when & then
            logger.info("구독하지 않은 사용자의 채팅방 조회 시도");
            assertThatThrownBy(() -> chatRoomService.getChatRoom(chatRoomId, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("구독되어 있지 않습니다");

            logger.info("IllegalArgumentException 발생 확인");
        }
    }

    @Nested
    @DisplayName("채팅방 삭제 테스트")
    class DeleteChatRoomTest {

        @Test
        @DisplayName("채팅방 삭제 성공 - 생성자만 삭제 가능")
        void 채팅방_삭제_성공() {
            logger.info("===== 채팅방 삭제 테스트 시작 =====");

            // given
            Long chatRoomId = 1L;
            String creatorId = "user1";

            UserChatRoom userChatRoom = UserChatRoom.builder()
                    .chatRoomId(chatRoomId)
                    .userId(creatorId)
                    .build();

            when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(testChatRoom));
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, creatorId)).thenReturn(true);
            when(userChatRoomRepository.findAllByChatRoomId(chatRoomId))
                    .thenReturn(Collections.singletonList(userChatRoom));
            when(userServiceClient.getUserById(creatorId)).thenReturn(testUser1);

            // when
            logger.info("채팅방 삭제 실행 - 생성자: {}", creatorId);
            chatRoomService.deleteChatRoom(chatRoomId, creatorId);

            // then
            verify(chatRoomRepository, times(1)).delete(testChatRoom);
            verify(userChatRoomRepository, times(1)).deleteByChatRoomId(chatRoomId);
            verify(invitationRepository, times(1)).deleteByChatRoomId(chatRoomId);
            logger.info("채팅방 삭제 테스트 성공");
        }

        @Test
        @DisplayName("채팅방 삭제 실패 - 생성자가 아닌 사용자")
        void 채팅방_삭제_실패_생성자가_아님() {
            logger.info("===== 생성자가 아닌 사용자의 채팅방 삭제 테스트 시작 =====");

            // given
            Long chatRoomId = 1L;
            String nonCreatorId = "user2"; // 생성자가 아닌 사용자

            when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(testChatRoom));
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, nonCreatorId)).thenReturn(true);

            // when & then
            logger.info("생성자가 아닌 사용자의 채팅방 삭제 시도");
            assertThatThrownBy(() -> chatRoomService.deleteChatRoom(chatRoomId, nonCreatorId))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("채팅방 생성자만");

            verify(chatRoomRepository, never()).delete(any());
            logger.info("SecurityException 발생 확인");
        }
    }

    @Nested
    @DisplayName("채팅방 나가기/내보내기 테스트")
    class LeaveChatRoomTest {

        @Test
        @DisplayName("일반 사용자 채팅방 나가기 성공")
        void 일반_사용자_채팅방_나가기_성공() {
            logger.info("===== 일반 사용자 채팅방 나가기 테스트 시작 =====");

            // given
            Long chatRoomId = 1L;
            String userId = "user2"; // 생성자가 아닌 사용자

            when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(testChatRoom));
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(true);
            when(userServiceClient.getUserById(userId)).thenReturn(testUser2);
            when(chatRoomRepository.findByChatRoomId(chatRoomId)).thenReturn(testChatRoom);
            when(messageRepository.save(any(Message.class))).thenReturn(Mono.just(new Message()));

            // when
            logger.info("일반 사용자 {} 채팅방 나가기", userId);
            chatRoomService.disconnectChatRoom(chatRoomId, userId);

            // then
            verify(userChatRoomRepository, times(1)).deleteByChatRoomIdAndUserId(chatRoomId, userId);
            logger.info("일반 사용자 채팅방 나가기 테스트 성공");
        }

        @Test
        @DisplayName("채팅방 생성자 내보내기 실패")
        void 채팅방_생성자_내보내기_실패() {
            logger.info("===== 채팅방 생성자 내보내기 실패 테스트 시작 =====");

            // given
            Long chatRoomId = 1L;
            String creatorId = "user1";
            String requesterId = "user1"; // 생성자 자신

            when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(testChatRoom));
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, requesterId)).thenReturn(true);

            // when & then
            logger.info("생성자 자신을 내보내기 시도");
            assertThatThrownBy(() -> chatRoomService.removeUserFromChatRoom(chatRoomId, creatorId, requesterId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("채팅방 생성자는 내보낼 수 없습니다");

            logger.info("IllegalArgumentException 발생 확인");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 내보내기 실패")
        void 존재하지_않는_사용자_내보내기_실패() {
            logger.info("===== 존재하지 않는 사용자 내보내기 테스트 시작 =====");

            // given
            Long chatRoomId = 1L;
            String userIdToRemove = "user3";
            String creatorId = "user1";

            when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(testChatRoom));
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, creatorId)).thenReturn(true);
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userIdToRemove)).thenReturn(false);

            // when & then
            logger.info("채팅방에 없는 사용자 내보내기 시도");
            assertThatThrownBy(() -> chatRoomService.removeUserFromChatRoom(chatRoomId, userIdToRemove, creatorId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("채팅방에 속해 있지 않습니다");

            logger.info("IllegalArgumentException 발생 확인");
        }
    }

    @Nested
    @DisplayName("채팅방 수정 추가 테스트")
    class UpdateChatRoomAdditionalTest {

        @Test
        @DisplayName("채팅방 이름만 수정 성공")
        void 채팅방_이름만_수정_성공() {
            logger.info("===== 채팅방 이름만 수정 테스트 시작 =====");

            // given
            Long chatRoomId = 1L;
            String userId = "user1";
            String newName = "새로운 채팅방 이름";
            String sameImage = "test.jpg"; // 기존 이미지와 동일

            when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(testChatRoom));
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(true);
            when(messageRepository.save(any())).thenReturn(Mono.just(new Message()));

            // when
            logger.info("채팅방 이름 수정: {}", newName);
            ChatRoomDto result = chatRoomService.updateChatRoom(chatRoomId, sameImage, newName, userId);

            // then
            assertThat(result.getName()).isEqualTo(newName);
            logger.info("채팅방 이름 수정 성공");
        }

        @Test
        @DisplayName("변경 사항 없을 때 실패")
        void 변경_사항_없을_때_실패() {
            logger.info("===== 변경 사항 없을 때 실패 테스트 시작 =====");

            // given
            Long chatRoomId = 1L;
            String userId = "user1";
            String sameName = "테스트 채팅방"; // 기존 이름과 동일
            String sameImage = "test.jpg"; // 기존 이미지와 동일

            when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(testChatRoom));
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(true);

            // when & then
            logger.info("기존과 동일한 정보로 수정 시도");
            assertThatThrownBy(() -> chatRoomService.updateChatRoom(chatRoomId, sameImage, sameName, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("변경된 이미지나 이름이 없습니다");

            logger.info("IllegalArgumentException 발생 확인");
        }

        @Test
        @DisplayName("생성자가 아닌 사용자 수정 실패")
        void 생성자가_아닌_사용자_수정_실패() {
            logger.info("===== 생성자가 아닌 사용자 수정 실패 테스트 시작 =====");

            // given
            Long chatRoomId = 1L;
            String nonCreatorId = "user2";
            String newName = "새로운 이름";

            when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(testChatRoom));
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, nonCreatorId)).thenReturn(true);

            // when & then
            logger.info("생성자가 아닌 사용자의 채팅방 수정 시도");
            assertThatThrownBy(() -> chatRoomService.updateChatRoom(chatRoomId, "new.jpg", newName, nonCreatorId))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("채팅방 생성자만");

            logger.info("SecurityException 발생 확인");
        }
    }

    @Nested
    @DisplayName("사용자 초대 테스트")
    class InviteUsersTest {

        @Test
        @DisplayName("빈 초대 목록으로 초대 실패")
        void 빈_초대_목록으로_초대_실패() {
            logger.info("===== 빈 초대 목록 테스트 시작 =====");

            // given
            Long chatRoomId = 1L;
            String inviterId = "user1";
            List<String> emptyUserIds = Collections.emptyList();

            when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(testChatRoom));
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, inviterId)).thenReturn(true);

            // when & then
            logger.info("빈 목록으로 초대 시도");
            assertThatThrownBy(() -> chatRoomService.inviteUsers(chatRoomId, inviterId, emptyUserIds))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("초대할 사용자 목록이 비어있습니다");

            logger.info("IllegalArgumentException 발생 확인");
        }

        @Test
        @DisplayName("이미 채팅방에 있는 사용자 초대 실패")
        void 이미_채팅방에_있는_사용자_초대_실패() {
            logger.info("===== 이미 있는 사용자 초대 테스트 시작 =====");

            // given
            Long chatRoomId = 1L;
            String inviterId = "user1";
            List<String> existingUserIds = Arrays.asList("user1", "user2");

            UserChatRoom userChatRoom1 = UserChatRoom.builder().chatRoomId(chatRoomId).userId("user1").build();
            UserChatRoom userChatRoom2 = UserChatRoom.builder().chatRoomId(chatRoomId).userId("user2").build();

            when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(testChatRoom));
            when(userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, inviterId)).thenReturn(true);
            when(userChatRoomRepository.findAllByChatRoomId(chatRoomId))
                    .thenReturn(Arrays.asList(userChatRoom1, userChatRoom2));

            // when & then
            logger.info("이미 채팅방에 있는 사용자 초대 시도");
            assertThatThrownBy(() -> chatRoomService.inviteUsers(chatRoomId, inviterId, existingUserIds))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("모든 초대 대상 유저가 이미 채팅방에 속해 있습니다");

            logger.info("IllegalArgumentException 발생 확인");
        }
    }
}

