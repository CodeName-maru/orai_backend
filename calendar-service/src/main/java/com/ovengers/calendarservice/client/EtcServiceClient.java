package com.ovengers.calendarservice.client;

import com.ovengers.calendarservice.dto.NotificationEvent;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "etc-service", fallback = EtcServiceClientFallback.class)
public interface EtcServiceClient {
    @PostMapping("api/notifications")
    ResponseEntity<?> createNotification(@RequestBody NotificationEvent event);
}
