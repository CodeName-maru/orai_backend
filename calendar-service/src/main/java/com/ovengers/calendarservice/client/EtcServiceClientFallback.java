package com.ovengers.calendarservice.client;

import com.ovengers.calendarservice.dto.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EtcServiceClientFallback implements EtcServiceClient {

    @Override
    public ResponseEntity<?> createNotification(NotificationEvent event) {
        log.warn("Fallback: etc-service 알림 생성 실패. event={}", event);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("etc-service unavailable");
    }
}
