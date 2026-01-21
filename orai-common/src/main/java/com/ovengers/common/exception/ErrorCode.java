package com.ovengers.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 오류가 발생했습니다"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "리소스를 찾을 수 없습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "C004", "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "C005", "접근 권한이 없습니다"),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다"),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "U002", "비밀번호가 일치하지 않습니다"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U003", "이미 존재하는 이메일입니다"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "U004", "유효하지 않은 토큰입니다"),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "U005", "토큰이 만료되었습니다"),
    MFA_REQUIRED(HttpStatus.UNAUTHORIZED, "U006", "MFA 인증이 필요합니다"),
    INVALID_MFA_CODE(HttpStatus.UNAUTHORIZED, "U007", "유효하지 않은 MFA 코드입니다"),

    // Vacation
    VACATION_NOT_FOUND(HttpStatus.NOT_FOUND, "V001", "휴가 신청을 찾을 수 없습니다"),
    SUPERVISOR_NOT_FOUND(HttpStatus.NOT_FOUND, "V002", "결재자를 찾을 수 없습니다"),
    CEO_NO_SUPERVISOR(HttpStatus.BAD_REQUEST, "V003", "CEO는 휴가 결재자가 없습니다"),

    // Chat
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CH001", "채팅방을 찾을 수 없습니다"),
    CHAT_ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CH002", "채팅방 접근 권한이 없습니다"),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CH003", "메시지를 찾을 수 없습니다"),
    INVALID_CHAT_ROOM_NAME(HttpStatus.BAD_REQUEST, "CH004", "유효하지 않은 채팅방 이름입니다"),

    // Calendar
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "CA001", "일정을 찾을 수 없습니다"),
    SCHEDULE_CONFLICT(HttpStatus.CONFLICT, "CA002", "일정이 충돌합니다"),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "CA003", "잘못된 날짜 범위입니다"),
    INVALID_SCHEDULE_DATE(HttpStatus.BAD_REQUEST, "CA004", "잘못된 일정 날짜입니다"),

    // Department
    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "D001", "부서를 찾을 수 없습니다"),

    // Notification
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없습니다"),
    SSE_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "N002", "SSE 연결에 실패했습니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
