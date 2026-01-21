package com.ovengers.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 날짜/시간 포맷팅을 위한 유틸리티 클래스입니다.
 * 모든 포맷터는 스레드 안전하며 재사용됩니다.
 */
public final class DateTimeUtils {

    private DateTimeUtils() {
        // 유틸리티 클래스 인스턴스화 방지
    }

    // ISO 8601 표준 포맷
    public static final DateTimeFormatter ISO_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 표시용 포맷
    public static final DateTimeFormatter DISPLAY_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static final DateTimeFormatter DISPLAY_DATE_TIME_WITH_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 한국어 표시용 포맷
    public static final DateTimeFormatter KOREAN_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시 mm분");

    public static final DateTimeFormatter KOREAN_DATE =
            DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");

    /**
     * LocalDateTime을 ISO 8601 형식 문자열로 변환합니다.
     */
    public static String toIsoString(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(ISO_DATE_TIME) : null;
    }

    /**
     * LocalDate를 ISO 8601 형식 문자열로 변환합니다.
     */
    public static String toIsoString(LocalDate date) {
        return date != null ? date.format(ISO_DATE) : null;
    }

    /**
     * LocalDateTime을 표시용 문자열로 변환합니다.
     */
    public static String toDisplayString(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DISPLAY_DATE_TIME) : null;
    }

    /**
     * LocalDate를 표시용 문자열로 변환합니다.
     */
    public static String toDisplayString(LocalDate date) {
        return date != null ? date.format(DISPLAY_DATE) : null;
    }

    /**
     * ISO 8601 형식 문자열을 LocalDateTime으로 파싱합니다.
     */
    public static LocalDateTime parseDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(dateTimeString, ISO_DATE_TIME);
    }

    /**
     * ISO 8601 형식 문자열을 LocalDate로 파싱합니다.
     */
    public static LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        return LocalDate.parse(dateString, ISO_DATE);
    }
}
