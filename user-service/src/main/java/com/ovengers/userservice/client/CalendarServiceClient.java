package com.ovengers.userservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "calendar-service", fallback = CalendarServiceClientFallback.class)
public interface CalendarServiceClient {
    @GetMapping("/api/departments/map")
    Map<String, String> getDepartmentMap();
}
