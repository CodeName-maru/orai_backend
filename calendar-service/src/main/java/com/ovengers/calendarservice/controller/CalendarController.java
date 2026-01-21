package com.ovengers.calendarservice.controller;

import com.ovengers.common.auth.TokenUserInfo;
import com.ovengers.calendarservice.dto.request.ScheduleRequestDto;
import com.ovengers.calendarservice.dto.response.ScheduleResponseDto;
import com.ovengers.calendarservice.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
@Slf4j
public class CalendarController {

    private final CalendarService calendarService;

    @Value("${app.admin.department-id:team9}")
    private String adminDepartmentId;

    /**
     * 사용자 정보 유효성 검증
     */
    private void validateUserInfo(TokenUserInfo info) {
        if (info == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        if (info.getId() == null || info.getId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 ID가 없습니다.");
        }
        if (info.getDepartmentId() == null || info.getDepartmentId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "부서 ID가 없습니다. X-User-DepartmentId 헤더를 확인하세요.");
        }
    }

    // 전체 일정 조회
    @GetMapping("")
    public ResponseEntity<List<ScheduleResponseDto>> getAllSchedules(@AuthenticationPrincipal TokenUserInfo info) {
        validateUserInfo(info);

        String departmentId = info.getDepartmentId();
        log.debug("TokenUserInfo: {}", info);

        List<ScheduleResponseDto> schedules;

        try {
            if (adminDepartmentId.equals(departmentId)) {
                // 관리자 부서이면 전체 일정 조회
                schedules = calendarService.getAllSchedules();
            } else {
                // 사용자의 팀 및 상위 부서 일정 조회
                schedules = calendarService.getSchedulesForUser(departmentId);
            }
        } catch (Exception e) {
            log.error("Error fetching schedules for department: {}", departmentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }

        log.info("Fetched schedules: {}", schedules.size());

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(schedules);
    }

    // 일정 생성
    @PostMapping("/create-schedule")
    public ResponseEntity<ScheduleResponseDto> addSchedule(
            @Valid @RequestBody ScheduleRequestDto scheduleRequestDto,
            @AuthenticationPrincipal TokenUserInfo userInfo) {
        validateUserInfo(userInfo);
        log.debug("User Info: {}", userInfo);
        log.debug("ScheduleRequestDto: {}", scheduleRequestDto);

        ScheduleResponseDto createdSchedule = calendarService.createSchedule(userInfo, scheduleRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSchedule);
    }

    // 일정 수정
    @PutMapping("/modify-schedule/{id}")
    public ResponseEntity<ScheduleResponseDto> modifySchedule(
            @Valid @RequestBody ScheduleRequestDto scheduleRequestDto,
            @PathVariable("id") String scheduleId) {

        ScheduleResponseDto modifySchedule = calendarService.updateSchedule(scheduleId, scheduleRequestDto);

        return ResponseEntity.ok(modifySchedule);
    }

    @DeleteMapping("/delete-schedule")
    public ResponseEntity<Void> deleteSchedule(@RequestParam String scheduleId) {
        if (scheduleId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduleId is required");
        }

        calendarService.deleteSchedule(scheduleId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}