package com.ovengers.calendarservice.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ovengers.calendarservice.entity.Schedule.ScheduleStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class ScheduleRequestDto {

    @NotBlank(message = "일정 제목은 필수입니다.")
    @Size(max = 100, message = "일정 제목은 100자를 초과할 수 없습니다.")
    private String title;

    @Size(max = 500, message = "일정 설명은 500자를 초과할 수 없습니다.")
    private String description;

    private String departmentId;

    @NotNull(message = "시작일은 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate start;

    @NotNull(message = "종료일은 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate end;

    @NotNull(message = "일정 상태는 필수입니다.")
    private ScheduleStatus scheduleStatus;
}
