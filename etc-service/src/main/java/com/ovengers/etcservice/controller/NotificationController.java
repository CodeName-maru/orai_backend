package com.ovengers.etcservice.controller;

import com.ovengers.common.auth.TokenUserInfo;
import com.ovengers.common.dto.CommonResDto;
import com.ovengers.etcservice.dto.NotificationEvent;
import com.ovengers.etcservice.dto.NotificationResDto;
import com.ovengers.etcservice.entity.Notification;
import com.ovengers.etcservice.service.NotificationService;
import com.ovengers.etcservice.service.SseConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final SseConnectionService connectionService;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal TokenUserInfo tokenUserInfo) {
        return connectionService.connect(tokenUserInfo.getId());
    }

    @GetMapping
    public ResponseEntity<?> getNotifications(@AuthenticationPrincipal TokenUserInfo tokenUserInfo) {
        List<NotificationResDto> notification = notificationService.getNotification(tokenUserInfo.getId());
        CommonResDto<?> commonResDto = new CommonResDto<>(HttpStatus.OK,"알림 조회 완료", notification);
        return ResponseEntity.ok(commonResDto);
    }
    //안 읽은 알림 갯수 세기
    @GetMapping("/count")
    public ResponseEntity<?> getNotificationCount(@AuthenticationPrincipal TokenUserInfo tokenUserInfo) {
        long notificationCount = notificationService.getNotificationCount(tokenUserInfo.getId());
        CommonResDto<?> commonResDto = new CommonResDto<>(HttpStatus.OK,"갯수 조회 완료", notificationCount);
        log.info("notification count: {}", notificationCount);
        return ResponseEntity.ok(commonResDto);
    }

    @PostMapping
    public ResponseEntity<?> createNotification(@RequestBody NotificationEvent event) {
        notificationService.createNotification(event);
        return ResponseEntity.ok(event);
    }


}
