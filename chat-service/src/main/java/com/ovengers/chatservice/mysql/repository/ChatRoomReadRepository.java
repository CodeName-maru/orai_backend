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
     * 원자적 unreadCount 증가 - Race Condition 방지
     */
    @Modifying
    @Query("UPDATE ChatRoomRead c SET c.unreadCount = c.unreadCount + 1 WHERE c.chatRoomId = :chatRoomId AND c.userId = :userId")
    int incrementUnreadCount(@Param("chatRoomId") Long chatRoomId, @Param("userId") String userId);
}