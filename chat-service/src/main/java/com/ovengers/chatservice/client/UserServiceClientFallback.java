package com.ovengers.chatservice.client;

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
    public UserResponseDto getUserById(String userId) {
        log.warn("Fallback: user-service 호출 실패. userId={}", userId);
        UserResponseDto fallbackUser = new UserResponseDto();
        fallbackUser.setUserId(userId);
        fallbackUser.setName("Unknown User");
        fallbackUser.setEmail("unknown@unknown.com");
        return fallbackUser;
    }

    @Override
    public List<UserResponseDto> getUsersByIds(List<String> userIds) {
        log.warn("Fallback: user-service 배치 조회 실패. userIds={}", userIds);
        return userIds.stream()
                .map(userId -> {
                    UserResponseDto dto = new UserResponseDto();
                    dto.setUserId(userId);
                    dto.setName("Unknown User");
                    dto.setEmail("unknown@unknown.com");
                    return dto;
                })
                .toList();
    }

    @Override
    public CommonResDto<List<UserResponseDto>> getUsersToList(Map<String, String> params) {
        log.warn("Fallback: user-service 목록 조회 실패. params={}", params);
        return new CommonResDto<>(HttpStatus.SERVICE_UNAVAILABLE, "service unavailable", Collections.emptyList());
    }

    @Override
    public CommonResDto<Page<UserResponseDto>> getUsersToPage(Map<String, String> params, int page, int size) {
        log.warn("Fallback: user-service 페이지 조회 실패. params={}, page={}, size={}", params, page, size);
        Page<UserResponseDto> emptyPage = new PageImpl<>(Collections.emptyList());
        return new CommonResDto<>(HttpStatus.SERVICE_UNAVAILABLE, "service unavailable", emptyPage);
    }
}
