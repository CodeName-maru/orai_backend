package com.ovengers.chatservice.mongodb.repository;

import com.ovengers.chatservice.mongodb.document.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface MessageRepository extends ReactiveMongoRepository<Message, String> {
    Flux<Message> findByChatRoomIdOrderByCreatedAtAsc(Long chatRoomId);

    Mono<Message> findByMessageId(String messageId);

    Flux<Message> findByChatRoomIdAndTypeNotOrderByCreatedAtAsc(Long chatRoomId, String type);

    // 페이징 지원: 최신 메시지부터 size개 조회
    Flux<Message> findByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId, Pageable pageable);

    // 커서 기반 페이징: 특정 시간 이전의 메시지 조회 (이전 메시지 로드)
    Flux<Message> findByChatRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(Long chatRoomId, LocalDateTime cursor, Pageable pageable);

    // 커서 기반 페이징: 특정 시간 이후의 메시지 조회 (새 메시지 로드)
    Flux<Message> findByChatRoomIdAndCreatedAtAfterOrderByCreatedAtAsc(Long chatRoomId, LocalDateTime cursor);

    // 채팅방 메시지 총 개수
    Mono<Long> countByChatRoomId(Long chatRoomId);
}
