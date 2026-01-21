package com.ovengers.userservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class CalendarServiceClientFallback implements CalendarServiceClient {

    @Override
    public Map<String, String> getDepartmentMap() {
        log.warn("Fallback: calendar-service 부서 맵 조회 실패");
        return Collections.emptyMap();
    }
}
