package com.ovengers.etcservice.util;

import com.ovengers.etcservice.dto.NotificationEvent;
import com.ovengers.etcservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSubscriber implements MessageListener {

    private final NotificationService notificationService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            NotificationEvent event = NotificationEventParser.parseNotificationEvent(json);
            event.getUserIds().forEach(notificationService::handleNotification);
            log.info("Received notification: {}", event);
        } catch (Exception e) {
            log.error("Failed to process notification message", e);
        }
    }

}
