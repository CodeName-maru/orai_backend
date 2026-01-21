package com.ovengers.chatservice.mysql.repository;

import com.ovengers.chatservice.mysql.entity.ChatRoomRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatRoomReadRepository extends JpaRepository<ChatRoomRead, Long> {
    Optional<ChatRoomRead> findByChatRoomIdAndUserId(Long chatRoomId, String userId);
    void deleteByChatRoomIdAndUserId(Long chatRoomId, String userId);
    void deleteByChatRoomId(Long chatRoomId);

    /**
     * 원자적 upsert - Race Condition 방지
     * INSERT하거나 이미 존재하면 unreadCount를 1 증가
     */
    @Modifying
    @Query(value = "INSERT INTO tbl_chat_room_read (chat_room_id, user_id, unread_count) " +
                   "VALUES (:chatRoomId, :userId, 1) " +
                   "ON DUPLICATE KEY UPDATE unread_count = unread_count + 1",
           nativeQuery = true)
    void upsertIncrementUnreadCount(@Param("chatRoomId") Long chatRoomId, @Param("userId") String userId);
}