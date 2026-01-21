package com.ovengers.calendarservice.client;

import com.ovengers.common.dto.CommonResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public CommonResDto<UserResponseDto> getUser(String userId) {
        log.warn("Fallback: user-service 호출 실패. userId={}", userId);
        UserResponseDto fallbackUser = new UserResponseDto();
        fallbackUser.setUserId(userId);
        fallbackUser.setName("Unknown User");
        fallbackUser.setEmail("unknown@unknown.com");
        return new CommonResDto<>(HttpStatus.OK, "fallback response", fallbackUser);
    }

    @Override
    public CommonResDto<List<UserResponseDto>> getUsersToList(Map<String, String> params) {
        log.warn("Fallback: user-service 목록 조회 실패. params={}", params);
        return new CommonResDto<>(HttpStatus.OK, "fallback response", Collections.emptyList());
    }

    @Override
    public CommonResDto<Page<UserResponseDto>> getUsersToPage(Map<String, String> params, int page, int size) {
        log.warn("Fallback: user-service 페이지 조회 실패. params={}, page={}, size={}", params, page, size);
        Page<UserResponseDto> emptyPage = new PageImpl<>(Collections.emptyList());
        return new CommonResDto<>(HttpStatus.OK, "fallback response", emptyPage);
    }

    @Override
    public List<UserResponseDto> findUserIdsByDepartmentId(String departmentId) {
        log.warn("Fallback: user-service 부서별 사용자 조회 실패. departmentId={}", departmentId);
        return Collections.emptyList();
    }
}
