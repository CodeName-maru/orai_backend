package com.ovengers.calendarservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class DepartmentRequestDto {

    @NotBlank(message = "부서명은 필수입니다.")
    @Size(max = 50, message = "부서명은 50자를 초과할 수 없습니다.")
    private String name;

    @Size(max = 50, message = "상위 부서 ID는 50자를 초과할 수 없습니다.")
    private String parent;

    @NotBlank(message = "부서 타입은 필수입니다.")
    @Pattern(regexp = "^(team|dept|org)$", message = "부서 타입은 team, dept, org 중 하나여야 합니다.")
    private String type;
}
