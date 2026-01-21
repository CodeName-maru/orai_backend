package com.ovengers.etcservice.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SseConnectionService {

    @Value("${HOSTNAME:localhost}") // 쿠버네티스 환경에서 HOSTNAME 사용, 없으면 'localhost'로 대체
    private String hostname;

    @Getter
    private String instanceId;

    // 공유 스케줄러 - 모든 연결이 공유하여 메모리 누수 방지
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();

    @Qualifier("sse-template")
    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseConnectionService(@Qualifier("sse-template") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        this.instanceId = hostname + "-" + UUID.randomUUID();
        log.info("Generated Instance ID: {}", instanceId);
    }

    @PreDestroy
    public void destroy() {
        heartbeatScheduler.shutdown();
        try {
            if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public SseEmitter connect(String userId) {
        // 기존 연결이 있으면 정리
        removeEmitter(userId);

        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L); // 1시간

        // Redis Hash에 연결 정보 저장
        String connectionInfo = String.format("%s:%s", instanceId, emitter.hashCode());
        redisTemplate.opsForHash().put("user:connections", userId, connectionInfo);

        // 로컬 캐시에 저장
        emitters.put(userId, emitter);

        // 연결 종료 시 cleanup
        emitter.onCompletion(() -> removeEmitter(userId));
        emitter.onTimeout(() -> removeEmitter(userId));
        emitter.onError(e -> removeEmitter(userId));

        try {
            // 연결 성공 메시지 전송
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("Connected to notification service"));

            // 공유 스케줄러로 heartbeat 등록 (메모리 누수 방지)
            ScheduledFuture<?> heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
                try {
                    SseEmitter currentEmitter = emitters.get(userId);
                    if (currentEmitter != null) {
                        currentEmitter.send(SseEmitter.event()
                                .name("heartbeat")
                                .data("keep-alive"));
                    }
                } catch (IOException e) {
                    log.warn("Failed to send heartbeat, removing emitter for userId: {}", userId);
                    removeEmitter(userId);
                }
            }, 30, 30, TimeUnit.SECONDS);

            heartbeatTasks.put(userId, heartbeatTask);
        } catch (IOException e) {
            log.error("Failed to send connection message to user {}", userId);
            removeEmitter(userId);
        }

        return emitter;
    }

    public void removeEmitter(String userId) {
        emitters.remove(userId);
        redisTemplate.opsForHash().delete("user:connections", userId);

        // heartbeat 태스크 취소
        ScheduledFuture<?> heartbeatTask = heartbeatTasks.remove(userId);
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }

        log.debug("Removed emitter for user {}", userId);
    }

    public SseEmitter getEmitter(String userId) {
        return emitters.get(userId);
    }
}
