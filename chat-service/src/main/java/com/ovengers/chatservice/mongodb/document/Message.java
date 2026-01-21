package com.ovengers.chatservice.mongodb.document;

import com.ovengers.chatservice.mongodb.dto.MessageDto;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Document(collection = "message")
@Getter
@NoArgsConstructor
@ToString
@AllArgsConstructor
@Builder
public class Message {

    // ISO 8601 기반 표준 날짜/시간 포맷터 (스레드 안전)
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // 기존 포맷 (하위 호환성 유지용)
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Id
    private String messageId;

    @Setter
    private Long chatRoomId;

    @Setter
    private String senderId;

    @Setter
    private String senderImage;

    @Setter
    private String senderName;

    @Setter
    private String type = "CHAT"; // CHAT, SYSTEM, EDIT, DELETE, ERROR 등

    @Setter
    private String content;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @Setter
    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    public MessageDto toDto() {
        return MessageDto.builder()
                .messageId(messageId)
                .chatRoomId(chatRoomId)
                .senderId(senderId)
                .senderImage(senderImage)
                .senderName(senderName)
                .type(type)
                .content(content)
                .createdAt(formatDateTime(createdAt))
                .updatedAt(formatDateTime(updatedAt))
                .build();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DISPLAY_FORMATTER);
    }

    /**
     * ISO 8601 형식으로 날짜/시간을 포맷합니다.
     * API 응답이나 외부 시스템 연동 시 사용합니다.
     */
    public String getCreatedAtIso() {
        return createdAt != null ? createdAt.format(DATE_TIME_FORMATTER) : null;
    }

    public String getUpdatedAtIso() {
        return updatedAt != null ? updatedAt.format(DATE_TIME_FORMATTER) : null;
    }
}
