package com.ovengers.calendarservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovengers.calendarservice.client.EtcServiceClient;
import com.ovengers.calendarservice.client.UserResponseDto;
import com.ovengers.calendarservice.client.UserServiceClient;
import com.ovengers.common.auth.TokenUserInfo;
import com.ovengers.calendarservice.dto.NotificationEvent;
import com.ovengers.calendarservice.dto.NotificationMessage;
import com.ovengers.calendarservice.dto.request.ScheduleRequestDto;
import com.ovengers.calendarservice.dto.response.ScheduleResponseDto;
import com.ovengers.calendarservice.entity.Department;
import com.ovengers.calendarservice.entity.Schedule;
import com.ovengers.calendarservice.repository.CalendarRepository;
import com.ovengers.calendarservice.repository.DepartmentRepository;
import com.ovengers.common.domain.DepartmentPrefix;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CalendarRepository calendarRepository;
    private final DepartmentRepository departmentRepository;
    private final UserServiceClient userServiceClient;
    private final EtcServiceClient etcServiceClient;

    @Qualifier("sse-template")
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void createNotification(Schedule schedule) {
        // 1. 부서 사용자 목록 조회
        Map<String,String> map = new HashMap<>();
        map.put("departmentId", schedule.getDepartment().getDepartmentId());
        @SuppressWarnings("unchecked")
        List<UserResponseDto> departmentUsers = (List<UserResponseDto>) userServiceClient.getUsersToList(map).getResult();
        List<String> departmentUsersIds = departmentUsers.stream()
                .map(UserResponseDto::getUserId)
                .toList();

        // 2. 알림 메시지 생성
        NotificationMessage message = NotificationMessage.builder()
                .type("SCHEDULE")
                .departmentId(schedule.getDepartment().getDepartmentId())
                .scheduleId(schedule.getScheduleId())
                .title(schedule.getTitle())
                .content("오늘 예정된 일정입니다:" + (schedule.getDescription()==null?schedule.getTitle():schedule.getDescription()))
                .createdAt(LocalDateTime.now())
                .build();

        NotificationEvent event = NotificationEvent.builder()
                .userIds(departmentUsersIds)
                .message(message)
                .build();

        // 3. 알림 저장
        etcServiceClient.createNotification(event);
        try {
            // 4. Redis pub/sub 채널에 발행
            String jsonMessage = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend("notifications", jsonMessage);
            log.info("Notification sent for schedule {}", schedule.getScheduleId());
        } catch (JsonProcessingException e) {
            log.error("Failed to send notification for schedule {}", schedule.getScheduleId(), e);
        }
    }

    // 일정 생성
    @Transactional
    public ScheduleResponseDto createSchedule(TokenUserInfo userInfo, ScheduleRequestDto scheduleRequestDto) {
        // Department 조회
        Department department = departmentRepository.findById(userInfo.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found: " + userInfo.getDepartmentId()));

        // departmentId 기반 일정 유형 설정
        Schedule.Type scheduleType = determineScheduleType(department.getDepartmentId());
        log.info("Determined schedule type: {} for departmentId: {}", scheduleType, userInfo.getDepartmentId());


        Schedule schedule = Schedule.builder()
                .title(scheduleRequestDto.getTitle())
                .description(scheduleRequestDto.getDescription())
                .userId(userInfo.getId())
                .type(scheduleType)
                .scheduleStatus(scheduleRequestDto.getScheduleStatus())
                .startTime(scheduleRequestDto.getStart())
                .endTime(scheduleRequestDto.getEnd())
                .department(department)
                .build();
        Schedule savedSchedule = calendarRepository.save(schedule);
//        createNotification(savedSchedule);

        return toDto(savedSchedule);
    }

    // departmentId에 따른 type 설정 로직
    private Schedule.Type determineScheduleType(String departmentId) {
        DepartmentPrefix prefix = DepartmentPrefix.fromDepartmentId(departmentId);
        return switch (prefix) {
            case TEAM -> Schedule.Type.TEAM;
            case DEPARTMENT -> Schedule.Type.GROUP;
            case ORGANIZATION -> Schedule.Type.DIVISION;
        };
    }

    // 전체 일정 조회
    @Transactional(readOnly = true)
    public List<ScheduleResponseDto> getAllSchedules() {
        return calendarRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

//    // 부서별 일정 조회
//    public List<ScheduleResponseDto> getScheduleByDepartment(String departmentId) {
//        return calendarRepository.findByDepartmentId(departmentId).stream()
//                .map(this::toDto)
//                .collect(Collectors.toList());
//    }
//
//    // 특정 일정 조회 by scheduleId
//    public ScheduleResponseDto getScheduleById(String scheduleId) {
//        Schedule schedule = calendarRepository.findById(scheduleId)
//                .orElseThrow(() -> new RuntimeException("Schedule not found"));
//        return toDto(schedule);
//    }
//
//    // 특정 일정 조회 by userId
//    public List<ScheduleResponseDto> getScheduleByUserId(String userId) {
//        return calendarRepository.findByUserId(userId).stream()
//                .map(this::toDto)
//                .collect(Collectors.toList());
//    }
//
//    // 날짜 범위 내 일정 조회
//    public List<ScheduleResponseDto> getSchedulesByDateRange(LocalDateTime start, LocalDateTime end) {
//        return calendarRepository.findByStartTimeBetween(start, end).stream()
//                .map(this::toDto)
//                .collect(Collectors.toList());
//    }

    // 일정 수정
    @Transactional
    public ScheduleResponseDto updateSchedule(String scheduleId, ScheduleRequestDto scheduleRequestDto) {
        Schedule oldSchedule = calendarRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));

        // 수정 요청 데이터를 기존 엔티티에 반영
        oldSchedule.setTitle(scheduleRequestDto.getTitle());
        oldSchedule.setDescription(scheduleRequestDto.getDescription());
        oldSchedule.setStartTime(scheduleRequestDto.getStart());
        oldSchedule.setScheduleStatus(scheduleRequestDto.getScheduleStatus());
        oldSchedule.setEndTime(scheduleRequestDto.getEnd());

        Schedule updatedSchedule = calendarRepository.save(oldSchedule);
        return toDto(updatedSchedule);
    }

    // 일정 삭제
    @Transactional
    public void deleteSchedule(String scheduleId) {
        if (!calendarRepository.existsById(scheduleId)) {
            throw new RuntimeException("Schedule not found for ID: " + scheduleId);
        }
        calendarRepository.deleteById(scheduleId);
    }

    // 
    public List<String> getUserIdsWithSchedulesForDate(LocalDate date) {
        return null;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponseDto> getSchedulesForUser(String departmentId) {

        // 사용자의 상위 부서 포함한 부서 ID 목록 조회
        List<String> departmentIds = departmentRepository.findAllParentDepartmentIds(departmentId);

        if (departmentIds.isEmpty()) {
            throw new RuntimeException("해당 부서의 상위 부서를 찾을 수 없습니다.");
        }

        // 해당 부서 ID 목록을 기반으로 일정 조회
        List<Schedule> schedules = calendarRepository.findByDepartment_DepartmentIdIn(departmentIds);

        return schedules.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ScheduleResponseDto toDto(Schedule schedule) {
        return ScheduleResponseDto.builder()
                .scheduleId(schedule.getScheduleId())
                .title(schedule.getTitle())
                .description(schedule.getDescription())
                .scheduleStatus(schedule.getScheduleStatus())
                .start(schedule.getStartTime().toString())
                .end(schedule.getEndTime().toString())
                .type(schedule.getType().name())
                .build();
    }
}
