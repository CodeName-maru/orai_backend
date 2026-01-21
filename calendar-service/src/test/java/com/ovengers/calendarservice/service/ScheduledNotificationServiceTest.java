package com.ovengers.calendarservice.service;

import com.ovengers.common.auth.TokenUserInfo;
import com.ovengers.calendarservice.dto.request.ScheduleRequestDto;
import com.ovengers.calendarservice.dto.response.ScheduleResponseDto;
import com.ovengers.calendarservice.entity.Department;
import com.ovengers.calendarservice.entity.Schedule;
import com.ovengers.calendarservice.repository.CalendarRepository;
import com.ovengers.calendarservice.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.*;


@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(CalendarServiceTest.class);

    @InjectMocks
    private CalendarService calendarService;

    @Mock
    private CalendarRepository calendarRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    private ScheduleRequestDto scheduleRequestDto;
    private Schedule schedule;
    private Department department;
    private TokenUserInfo tokenUserInfo;

    @BeforeEach
    void setUp() {
        tokenUserInfo = new TokenUserInfo("user123", "team1"); // Given: Mock User 정보 생성

        department = new Department();
        department.setDepartmentId("team1");

        scheduleRequestDto = ScheduleRequestDto.builder()
                .title("Meeting")
                .description("Team meeting")
                .start(LocalDate.now())
                .end(LocalDate.now().plusDays(1))
                .scheduleStatus(Schedule.ScheduleStatus.UPCOMING)
                .build();

        schedule = Schedule.builder()
                .scheduleId("sched001")
                .title(scheduleRequestDto.getTitle())
                .description(scheduleRequestDto.getDescription())
                .userId(tokenUserInfo.getId())
                .type(Schedule.Type.GROUP)
                .scheduleStatus(scheduleRequestDto.getScheduleStatus())
                .startTime(scheduleRequestDto.getStart())
                .endTime(scheduleRequestDto.getEnd())
                .department(department)
                .build();
    }

    @Test
    void 일정_생성_성공() {
        // Given
        given(departmentRepository.findById(tokenUserInfo.getDepartmentId())).willReturn(Optional.of(department));
        given(calendarRepository.save(any(Schedule.class))).willReturn(schedule);

        // When
        ScheduleResponseDto responseDto = calendarService.createSchedule(tokenUserInfo, scheduleRequestDto);
        logger.info("일정 생성 성공: {}", responseDto);

        // Then
        assertThat(responseDto).isNotNull();
        assertThat(responseDto.getTitle()).isEqualTo(scheduleRequestDto.getTitle());
        assertThat(responseDto.getDescription()).isEqualTo(scheduleRequestDto.getDescription());

        then(calendarRepository).should(times(1)).save(any(Schedule.class));
    }

    @Test
    void 일정_생성_실패_부서없음() {
        // Given
        given(departmentRepository.findById(tokenUserInfo.getDepartmentId())).willReturn(Optional.empty());

        // When & Then
        Exception exception = assertThrows(RuntimeException.class, () ->
                calendarService.createSchedule(tokenUserInfo, scheduleRequestDto));
        logger.error("일정 생성 실패: {}", exception.getMessage());

        assertThat(exception.getMessage()).isEqualTo("Department not found: team1");
    }

    @Test
    void 전체_일정_조회() {
        // Given
        given(calendarRepository.findAll()).willReturn(List.of(schedule));

        // When
        List<ScheduleResponseDto> responseDtos = calendarService.getAllSchedules();
        logger.info("조회된 일정 목록: {}", responseDtos);

        // Then
        assertThat(responseDtos).isNotNull();
        assertThat(responseDtos).hasSize(1);
        assertThat(responseDtos.get(0).getTitle()).isEqualTo(schedule.getTitle());

        then(calendarRepository).should(times(1)).findAll();
    }

    @Test
    void 일정_수정_성공() {
        // Given
        given(calendarRepository.findById(schedule.getScheduleId())).willReturn(Optional.of(schedule));
        given(calendarRepository.save(any(Schedule.class))).willReturn(schedule);

        ScheduleRequestDto updateRequestDto = ScheduleRequestDto.builder()
                .title("Updated Meeting")
                .description("Updated Description")
                .start(LocalDate.now().plusDays(2))
                .end(LocalDate.now().plusDays(3))
                .scheduleStatus(Schedule.ScheduleStatus.IN_PROGRESS)
                .build();

        // When
        ScheduleResponseDto updatedSchedule = calendarService.updateSchedule(schedule.getScheduleId(), updateRequestDto);
        logger.info("일정 수정 성공: {}", updatedSchedule);

        // Then
        assertThat(updatedSchedule).isNotNull();
        assertThat(updatedSchedule.getTitle()).isEqualTo(updateRequestDto.getTitle());
        assertThat(updatedSchedule.getDescription()).isEqualTo(updateRequestDto.getDescription());

        then(calendarRepository).should(times(1)).findById(schedule.getScheduleId());
        then(calendarRepository).should(times(1)).save(any(Schedule.class));
    }

    @Test
    void 일정_삭제_성공() {
        // Given
        given(calendarRepository.existsById(schedule.getScheduleId())).willReturn(true);
        willDoNothing().given(calendarRepository).deleteById(schedule.getScheduleId());

        // When
        calendarService.deleteSchedule(schedule.getScheduleId());
        logger.info("일정 삭제 성공: {}", schedule.getScheduleId());

        // Then
        then(calendarRepository).should(times(1)).existsById(schedule.getScheduleId());
        then(calendarRepository).should(times(1)).deleteById(schedule.getScheduleId());
    }

    @Test
    void 일정_삭제_실패_존재하지않음() {
        // Given
        given(calendarRepository.existsById(schedule.getScheduleId())).willReturn(false);

        // When & Then
        Exception exception = assertThrows(RuntimeException.class, () ->
                calendarService.deleteSchedule(schedule.getScheduleId()));
        logger.error("일정 삭제 실패: {}", exception.getMessage());

        assertThat(exception.getMessage()).isEqualTo("Schedule not found for ID: " + schedule.getScheduleId());
    }
}
