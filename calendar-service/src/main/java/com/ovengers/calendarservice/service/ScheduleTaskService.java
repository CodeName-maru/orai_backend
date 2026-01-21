package com.ovengers.calendarservice.service;

import com.ovengers.calendarservice.entity.Schedule;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ovengers.calendarservice.repository.CalendarRepository;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleTaskService {

    private final CalendarRepository calendarRepository;
    private final CalendarService calendarService;

    @Scheduled(cron = "${app.schedule.notification.cron}")
    public void checkDailySchedules() {
        LocalDate today = LocalDate.now();
        log.info("스케줄 조회 작업 실행: {}", today);

        // 기존 CalendarRepository를 사용하여 해당 날짜의 일정 조회
        List<Schedule> schedules = calendarRepository.findByStartTime(today);
        schedules.forEach(calendarService::createNotification);

        if (schedules.isEmpty()) {
            log.info("오늘 일정이 없습니다.");
        } else {
            schedules.forEach(schedule ->
                    log.info("일정: {} - {}", schedule.getTitle(), schedule.getStartTime())
            );
        }

        log.info("스케줄 조회 완료. 처리된 일정 수: {}", schedules.size());
    }
}
