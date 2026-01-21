package com.ovengers.chatservice.mysql.service;

import com.ovengers.chatservice.client.UserResponseDto;
import com.ovengers.chatservice.client.UserServiceClient;
import com.ovengers.chatservice.mongodb.document.Message;
import com.ovengers.chatservice.mongodb.repository.MessageRepository;
import com.ovengers.chatservice.mysql.dto.ChatRoomDto;
import com.ovengers.chatservice.mysql.dto.ChatRoomInvitationDto;
import com.ovengers.chatservice.mysql.dto.CompositeChatRoomDto;
import com.ovengers.chatservice.mysql.entity.ChatRoom;
import com.ovengers.chatservice.mysql.entity.Invitation;
import com.ovengers.chatservice.mysql.entity.UserChatRoom;
import com.ovengers.common.exception.BusinessException;
import com.ovengers.common.exception.ErrorCode;
import com.ovengers.chatservice.mysql.repository.ChatRoomReadRepository;
import com.ovengers.chatservice.mysql.repository.ChatRoomRepository;
import com.ovengers.chatservice.mysql.repository.InvitationRepository;
import com.ovengers.chatservice.mysql.repository.UserChatRoomRepository;
import io.micrometer.common.util.StringUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Getter
@Transactional
public class ChatRoomService {
    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final InvitationRepository invitationRepository;
    private final UserServiceClient userServiceClient;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final MessageRepository messageRepository;
    private final ChatRoomReadRepository chatRoomReadRepository;

    public UserResponseDto getUserInfo(String userId) {
        UserResponseDto userById = userServiceClient.getUserById(userId);

        if (userById == null) {
            throw new RuntimeException("사용자 정보를 가져오는 데 실패했습니다: " + userId);
        }

        return userById;
    }

    public List<UserResponseDto> getAllUsers(List<String> userIds) {
        return userServiceClient.getUsersByIds(userIds);
    }

    // 유효성 검사 메서드
    private void validateChatRoomName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new BusinessException(ErrorCode.INVALID_CHAT_ROOM_NAME, "채팅방 이름은 공백만으로 지정할 수 없습니다.");
        }
    }

    // 채팅방 존재 여부 및 사용자 구독 확인을 위한 공통 메서드
    private ChatRoom validateChatRoomAndUser(Long chatRoomId, String userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new EntityNotFoundException(chatRoomId + "번 채팅방은 존재하지 않습니다."));

        if (!userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)) {
            throw new IllegalArgumentException(chatRoomId + "번 채팅방에 구독되어 있지 않습니다.");
        }

        return chatRoom;
    }

    // 채팅방 생성자 권한 확인을 위한 공통 메서드
    private void validateCreatorPermission(ChatRoom chatRoom, String userId) {
        if (!chatRoom.getCreatorId().equals(userId)) {
            throw new SecurityException("채팅방 생성자만 이 작업을 수행할 수 있습니다.");
        }
    }

    // 채팅방 구독 취소
    private void removeUserFromChatRoomCommon(Long chatRoomId, String userIdToRemove) {
        userChatRoomRepository.deleteByChatRoomIdAndUserId(chatRoomId, userIdToRemove);
        invitationRepository.findByChatRoomIdAndUserId(chatRoomId, userIdToRemove)
                .ifPresent(invitationRepository::delete);
    }

    // 채팅방 생성자는 막음
    private void validateCreatorOperation(ChatRoom chatRoom, String targetUserId, boolean isRemoval) {
        if (chatRoom.getCreatorId().equals(targetUserId)) {
            String errorMessage = isRemoval ?
                    "채팅방 생성자는 내보낼 수 없습니다." :
                    "채팅방 생성자는 채팅방을 나갈 수 없습니다.";
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void removeChatRoomRead(Long chatRoomId, String userId) {
        chatRoomReadRepository.deleteByChatRoomIdAndUserId(chatRoomId, userId);
    }

    // 시스템 메시지 생성 및 전송을 위한 공통 메서드
    private void sendSystemMessage(Long chatRoomId, String content) {
        Message systemMessage = Message.builder()
                .chatRoomId(chatRoomId)
                .type("SYSTEM")
                .content(content)
                .build();

        messageRepository.save(systemMessage)
                .subscribe(savedMessage -> {
                    simpMessagingTemplate.convertAndSend(
                            "/sub/" + chatRoomId + "/chat",
                            savedMessage.toDto()
                    );
                });
    }

    // 채팅방 입장 알림(채팅방 생성 시(생성자))
    private void sendEnterChatRoom(Long chatRoomId, String userId) {
        UserResponseDto userInfo = getUserInfo(userId);
        ChatRoomDto chatRoomInfo = chatRoomRepository.findByChatRoomId(chatRoomId).toDto();
        String content = userInfo.getName() + "님이 " + chatRoomInfo.getName() + " 채팅방에 입장했습니다.";
        sendSystemMessage(chatRoomId, content);
    }

    // 채팅방 입장 알림(채팅방 생성 또는 초대 시(유저들))
    private void sendEnterUsers(Long chatRoomId, List<String> userIds) {
        ChatRoomDto chatRoomInfo = chatRoomRepository.findByChatRoomId(chatRoomId).toDto();
        List<UserResponseDto> userInfos = getAllUsers(userIds);
        String userNames = userInfos.stream()
                .map(UserResponseDto::getName)
                .collect(Collectors.joining(", "));
        String content = userNames + "님이 " + chatRoomInfo.getName() + " 채팅방에 입장했습니다.";
        sendSystemMessage(chatRoomId, content);
    }

    // 채팅방 수정 알림
    private void sendChatRoomUpdatedNotification(Long chatRoomId) {
        sendSystemMessage(chatRoomId, "채팅방 정보가 수정되었습니다.");
    }

    // 채팅방 퇴장 알림(채팅방 나가기)
    private void sendExitChatRoom(Long chatRoomId, String userId) {
        UserResponseDto userInfo = getUserInfo(userId);
        ChatRoomDto chatRoomInfo = chatRoomRepository.findByChatRoomId(chatRoomId).toDto();
        String content = userInfo.getName() + "님이 " + chatRoomInfo.getName() + " 채팅방에서 퇴장했습니다.";
        sendSystemMessage(chatRoomId, content);
    }

    // 채팅방 강퇴 알림(채팅방 내보내기)
    private void sendExportChatRoom(Long chatRoomId, String userId, String creatorId) {
        UserResponseDto userInfo = getUserInfo(userId);
        UserResponseDto creatorInfo = getUserInfo(creatorId);
        ChatRoomDto chatRoomInfo = chatRoomRepository.findByChatRoomId(chatRoomId).toDto();
        String content = creatorInfo.getName() + "님이 " + userInfo.getName() + "님을 " +
                chatRoomInfo.getName() + " 채팅방에서 내보냈습니다.";
        sendSystemMessage(chatRoomId, content);
    }

    // 구독 유저들에게 채팅방 삭제 알림을 보내는 메서드
    private void sendChatRoomDeletedNotification(List<String> userIds, ChatRoom chatRoom, String removerId) {
        UserResponseDto removerInfo = getUserInfo(removerId);
        String removerName = removerInfo.getName();

        userIds.forEach(userId -> simpMessagingTemplate.convertAndSendToUser(
                userId,
                "/queue",
                ChatRoomInvitationDto.builder()
                        .chatRoomId(chatRoom.getChatRoomId())
                        .name(chatRoom.getName())
                        .message(removerName + "님이 " + chatRoom.getName() + " 채팅방을 삭제했습니다.")
                        .build()
        ));
    }

    // ChatRoom 및 UserChatRoom 생성
    public CompositeChatRoomDto createChatRoom(String image, String name, String userId, List<String> userIds) {
        validateChatRoomName(name.trim());

        // 유저 목록을 FeignClient를 통해 확인
        List<UserResponseDto> validUsers = userServiceClient.getUsersByIds(userIds);

        // 유저 ID가 유효한지 확인 (존재하지 않는 유저 ID가 있으면 예외 처리)
        List<String> validUserIds = validUsers.stream()
                .map(UserResponseDto::getUserId)
                .toList();

        for (String user : userIds) {
            if (!validUserIds.contains(user)) {
                throw new IllegalArgumentException("유효하지 않은 유저 ID: " + user);
            }
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .name(name)
                .image(image)
                .creatorId(userId)
                .build();
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom); // 엔티티 저장

        UserChatRoom creator = UserChatRoom.builder()
                .chatRoomId(savedChatRoom.getChatRoomId())
                .userId(savedChatRoom.getCreatorId())
                .build();
        userChatRoomRepository.save(creator);

        // 생성자를 제외한 초대 유저 목록 생성
        List<String> inviteeIds = validUserIds.stream()
                .filter(id -> !id.equals(userId)) // 생성자 자신을 제외
                .toList();

        // 초대할 유저에 대한 초대 처리 (Invitation 생성)
        List<Invitation> invitations = inviteeIds.stream()
                .map(getUserId -> Invitation.builder()
                        .inviterId(savedChatRoom.getCreatorId())
                        .chatRoomId(savedChatRoom.getChatRoomId())
                        .userId(getUserId)
                        .accepted(true)
                        .build())
                .toList();
        invitationRepository.saveAll(invitations);

        List<UserChatRoom> users = inviteeIds.stream()
                .map(getUserId -> UserChatRoom.builder()
                        .chatRoomId(savedChatRoom.getChatRoomId())
                        .userId(getUserId)
                        .build())
                .toList();
        userChatRoomRepository.saveAll(users);

        // 생성자가 채팅방에 입장했을 때 입장 메시지 전송
        sendEnterChatRoom(savedChatRoom.getChatRoomId(), userId);  // 생성자가 입장했음을 알림
        sendEnterUsers(savedChatRoom.getChatRoomId(), inviteeIds);

        return CompositeChatRoomDto.builder()
                .chatRoomDto(savedChatRoom.toDto())
                .userChatRoomDto(List.of(creator.toDto())) // 생성자만 포함
                .build();
    }

    // UserId 별 ChatRoomList 조회
    public List<ChatRoomDto> getChatRooms(String userId) {
        // UserChatRoom에서 해당 사용자의 ChatRoomId 리스트 가져오기
        List<Long> chatRoomIds = userChatRoomRepository.findAllByUserId(userId)
                .stream()
                .map(UserChatRoom::getChatRoomId) // chatRoomId 추출
                .collect(Collectors.toList());

        // ChatRoomId가 없을 경우 빈 리스트 반환
        if (chatRoomIds.isEmpty()) {
            return Collections.emptyList();
        }

        // ChatRoomId에 해당하는 ChatRoom 엔티티를 조회하고 DTO로 변환
        return chatRoomRepository.findAllById(chatRoomIds)
                .stream()
                .map(ChatRoom::toDto)
                .collect(Collectors.toList());
    }

    public ChatRoomDto getChatRoom(Long chatRoomId, String userId) {
        ChatRoom chatRoom = validateChatRoomAndUser(chatRoomId, userId);
        return chatRoom.toDto();
    }

    public List<UserResponseDto> getSubUsers(Long chatRoomId, String userId) {
        validateChatRoomAndUser(chatRoomId, userId);

        List<String> userIds = userChatRoomRepository.findAllByChatRoomId(chatRoomId)
                .stream()
                .map(UserChatRoom::getUserId)
                .toList();

        // N+1 쿼리 방지: 배치 API 사용
        return userServiceClient.getUsersByIds(userIds);
    }

    // 채팅방에 초대
    @Transactional
    public void inviteUsers(Long chatRoomId, String inviterId, List<String> inviteUserIds) {

        validateChatRoomAndUser(chatRoomId, inviterId);

        if (inviteUserIds == null || inviteUserIds.isEmpty()) {
            throw new IllegalArgumentException("초대할 사용자 목록이 비어있습니다.");
        }

        // 기존 채팅방 유저 목록 조회
        List<String> existingUserIds = userChatRoomRepository.findAllByChatRoomId(chatRoomId)
                .stream()
                .map(UserChatRoom::getUserId)
                .toList();

        // 초대 유저 목록 중 기존 유저에 포함되지 않은 유저 필터링
        List<String> newUserIds = inviteUserIds.stream()
                .filter(userId -> !existingUserIds.contains(userId))
                .toList();

        if (newUserIds.isEmpty()) {
            throw new IllegalArgumentException("모든 초대 대상 유저가 이미 채팅방에 속해 있습니다.");
        }

        // FeignClient로 초대할 유저들이 유효한지 확인
        List<UserResponseDto> validUsers = userServiceClient.getUsersByIds(newUserIds);
        List<String> validUserIds = validUsers.stream()
                .map(UserResponseDto::getUserId)
                .toList();

        // 초대 대상 중 유효하지 않은 유저 ID가 있다면 예외 처리
        for (String userId : newUserIds) {
            if (!validUserIds.contains(userId)) {
                throw new IllegalArgumentException("존재하지 않는 유저 ID: " + userId);
            }
        }

        // 초대 처리
        List<Invitation> invitations = validUserIds.stream()
                .filter(inviteeId -> {
                    // 이미 초대된 유저인지 확인
                    boolean alreadyInvited = invitationRepository.existsByChatRoomIdAndUserIdAndAcceptedFalse(chatRoomId, inviteeId);
                    if (alreadyInvited) {
                        log.debug("이미 초대된 유저: {}", inviteeId);
                    }
                    return !alreadyInvited;
                })
                .map(inviteeId -> Invitation.builder()
                        .inviterId(inviterId)
                        .chatRoomId(chatRoomId)
                        .userId(inviteeId)
                        .accepted(true)
                        .build())
                .toList();

        List<UserChatRoom> users = validUserIds.stream()
                .map(getUserId -> UserChatRoom.builder()
                        .chatRoomId(chatRoomId)
                        .userId(getUserId)
                        .build())
                .toList();

        // 초대와 구독
        // 동시성 처리를 위한 락 획득
        Lock lock = new ReentrantLock();
        lock.lock();
        try {
            invitationRepository.saveAll(invitations);
            userChatRoomRepository.saveAll(users);
            sendEnterUsers(chatRoomId, validUserIds);
        } finally {
            lock.unlock();
        }
//        sendInvitationsToUsers(validUserIds, chatRoom, inviterId);       // 초대 유저들에게 알림
    }

/*    // 초대 수락 시 UserChatRoom 저장
    @Transactional
    public void acceptInvitation(Long chatRoomId, String userId) {
        // 초대받은 유저인지 확인
        Invitation invitation = invitationRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 채팅방에 초대받은 기록이 없습니다."));

        // 이미 초대를 수락한 경우 예외 처리
        if (invitation.isAccepted()) {
            throw new IllegalArgumentException("이미 초대를 수락했습니다.");
        }

        // 초대 상태를 수락으로 변경
        invitation.setAccepted(true);
        invitationRepository.save(invitation);

        // UserChatRoom에 추가
        UserChatRoom userChatRoom = UserChatRoom.builder()
                .chatRoomId(chatRoomId)
                .userId(userId)
                .build();
        userChatRoomRepository.save(userChatRoom);

        sendEnterChatRoom(chatRoomId, userId);  // 유저가 입장했음을 알림
    }*/

/*    // 초대 거절 시 Invitation 삭제
    @Transactional
    public void refusalInvitation(Long chatRoomId, String userId) {
        // 초대받은 유저인지 확인
        Invitation invitation = invitationRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 채팅방에 초대받은 기록이 없습니다."));

        // 이미 초대를 수락한 경우 예외 처리
        if (invitation.isAccepted()) {
            throw new IllegalArgumentException("이미 초대를 수락했습니다.");
        }

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다."));

        String inviterId = invitation.getInviterId();

        UserResponseDto inviterInfo = getUserInfo(userId);
        String refusalName = inviterInfo.getName(); // 초대를 거절한 유저의 이름

        // 초대 거절 알림 메시지 전송
        simpMessagingTemplate.convertAndSendToUser(
                inviterId,                // 초대한 유저에게 보냄
                "/queue/notifications",  // 개인 큐 경로
                "사용자 " + refusalName + "님이 " + chatRoom.getName() + " 채팅방 초대를 거절했습니다."
        );

        invitationRepository.deleteByChatRoomIdAndUserId(chatRoomId, userId);
    }*/

    // 채팅방 생성자만 채팅방 이미지 및 이름 수정
    @Transactional
    public ChatRoomDto updateChatRoom(Long chatRoomId, String newImage, String newName, String userId) {
        ChatRoom chatRoom = validateChatRoomAndUser(chatRoomId, userId);
        validateCreatorPermission(chatRoom, userId);

        boolean isUpdated = false;

        if (!newImage.trim().isEmpty() && !newImage.equals(chatRoom.getImage())) {
            chatRoom.setImage(newImage);
            isUpdated = true;
        }

        if (newName != null && !newName.trim().isEmpty() && !newName.equals(chatRoom.getName())) {
            validateChatRoomName(newName.trim());
            chatRoom.setName(newName);
            isUpdated = true;
        }

        if (!isUpdated) {
            throw new IllegalArgumentException("변경된 이미지나 이름이 없습니다.");
        }

        sendChatRoomUpdatedNotification(chatRoomId);
        return chatRoom.toDto();
    }

    // 채팅방 생성자만 채팅방 삭제 및 해당 채팅방 구독자 전체 삭제
    public void deleteChatRoom(Long chatRoomId, String userId) {
        ChatRoom chatRoom = validateChatRoomAndUser(chatRoomId, userId);
        validateCreatorPermission(chatRoom, userId);

        List<String> existingUserIds = userChatRoomRepository.findAllByChatRoomId(chatRoom.getChatRoomId())
                .stream()
                .map(UserChatRoom::getUserId)
                .toList();

        sendChatRoomDeletedNotification(existingUserIds, chatRoom, userId);

        chatRoomRepository.delete(chatRoom);
        userChatRoomRepository.deleteByChatRoomId(chatRoom.getChatRoomId());
        invitationRepository.deleteByChatRoomId(chatRoom.getChatRoomId());
        chatRoomReadRepository.deleteByChatRoomId(chatRoom.getChatRoomId());
    }

    // 채팅방에서 나가기
    public void disconnectChatRoom(Long chatRoomId, String userId) {
        ChatRoom chatRoom = validateChatRoomAndUser(chatRoomId, userId);
        validateCreatorOperation(chatRoom, userId, false);

        removeUserFromChatRoomCommon(chatRoomId, userId);
        sendExitChatRoom(chatRoomId, userId);
        removeChatRoomRead(chatRoomId, userId);
    }

    // 채팅방에서 내보내기(채팅방 생성자만)
    @Transactional
    public void removeUserFromChatRoom(Long chatRoomId, String userIdToRemove, String userId) {
        ChatRoom chatRoom = validateChatRoomAndUser(chatRoomId, userId);
        validateCreatorPermission(chatRoom, userId);
        validateCreatorOperation(chatRoom, userIdToRemove, true);

        if (!userChatRoomRepository.existsByChatRoomIdAndUserId(chatRoomId, userIdToRemove)) {
            throw new IllegalArgumentException("해당 유저는 채팅방에 속해 있지 않습니다.");
        }

        removeUserFromChatRoomCommon(chatRoomId, userIdToRemove);
        sendExportChatRoom(chatRoomId, userIdToRemove, userId);
        removeChatRoomRead(chatRoomId, userIdToRemove);
    }
}
