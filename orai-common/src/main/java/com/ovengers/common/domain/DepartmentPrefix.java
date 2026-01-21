package com.ovengers.common.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 부서 ID 접두사에 따른 부서 계층 유형을 정의합니다.
 */
@Getter
@RequiredArgsConstructor
public enum DepartmentPrefix {
    TEAM("team", "팀"),
    DEPARTMENT("dept", "부서"),
    ORGANIZATION("org", "조직");

    private final String prefix;
    private final String displayName;

    /**
     * 부서 ID에서 접두사를 기반으로 DepartmentPrefix를 찾습니다.
     *
     * @param departmentId 부서 ID
     * @return 매칭되는 DepartmentPrefix
     * @throws IllegalArgumentException 유효하지 않은 부서 ID인 경우
     */
    public static DepartmentPrefix fromDepartmentId(String departmentId) {
        if (departmentId == null || departmentId.isBlank()) {
            throw new IllegalArgumentException("부서 ID는 null이거나 비어있을 수 없습니다.");
        }

        return Arrays.stream(values())
                .filter(prefix -> departmentId.startsWith(prefix.getPrefix()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "잘못된 부서 ID입니다. 유효한 접두사: team, dept, org. 입력값: " + departmentId));
    }

    /**
     * 주어진 부서 ID가 유효한 접두사로 시작하는지 확인합니다.
     *
     * @param departmentId 부서 ID
     * @return 유효한 접두사로 시작하면 true
     */
    public static boolean isValidDepartmentId(String departmentId) {
        if (departmentId == null || departmentId.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(prefix -> departmentId.startsWith(prefix.getPrefix()));
    }
}
