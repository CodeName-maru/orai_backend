package com.ovengers.calendarservice.dto.response;

import lombok.*;

import com.ovengers.calendarservice.entity.Schedule.ScheduleStatus;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Setter
@Getter
@Builder
@AllArgsConstructor
@ToString
@NoArgsConstructor
public class ScheduleResponseDto {

//    private String userId;

    private String scheduleId;

    private String title;

    private String description;

    private ScheduleStatus scheduleStatus;

    private String start;

    private String end;

    private String type;

    // 날짜를 ISO8601 형식의 문자열로 변환
    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
    }

}
