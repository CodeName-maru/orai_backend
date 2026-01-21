package com.ovengers.common.test;

import com.ovengers.common.dto.CommonResDto;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 테스트용 공통 Assertion 유틸리티 클래스
 * 응답 객체 검증 등 반복적인 검증 로직을 제공합니다.
 */
public final class TestAssertions {

    private TestAssertions() {
        // 유틸리티 클래스는 인스턴스화 방지
    }

    // ===== CommonResDto 검증 =====

    /**
     * CommonResDto가 성공 응답인지 검증
     */
    public static <T> void assertSuccessResponse(CommonResDto<T> response) {
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * CommonResDto가 생성 응답인지 검증
     */
    public static <T> void assertCreatedResponse(CommonResDto<T> response) {
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /**
     * CommonResDto가 지정된 상태 코드인지 검증
     */
    public static <T> void assertStatusCode(CommonResDto<T> response, HttpStatus expectedStatus) {
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
    }

    /**
     * CommonResDto에 결과 데이터가 포함되어 있는지 검증
     */
    public static <T> void assertHasResult(CommonResDto<T> response) {
        assertThat(response).isNotNull();
        assertThat(response.getResult()).isNotNull();
    }

    /**
     * CommonResDto에 지정된 메시지가 포함되어 있는지 검증
     */
    public static <T> void assertMessageContains(CommonResDto<T> response, String expectedMessage) {
        assertThat(response).isNotNull();
        assertThat(response.getStatusMessage()).contains(expectedMessage);
    }

    // ===== 문자열 검증 =====

    /**
     * 문자열이 비어있지 않은지 검증
     */
    public static void assertNotBlank(String value, String fieldName) {
        assertThat(value)
                .as("Field '%s' should not be blank", fieldName)
                .isNotBlank();
    }

    /**
     * 문자열이 이메일 형식인지 검증
     */
    public static void assertValidEmail(String email) {
        assertThat(email)
                .as("Email should be valid format")
                .matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    /**
     * 문자열이 전화번호 형식인지 검증
     */
    public static void assertValidPhoneNumber(String phone) {
        assertThat(phone)
                .as("Phone number should be valid format")
                .matches("^01[0-9]-\\d{3,4}-\\d{4}$");
    }

    // ===== ID 검증 =====

    /**
     * ID가 null이 아니고 비어있지 않은지 검증
     */
    public static void assertValidId(String id) {
        assertThat(id)
                .as("ID should not be null or empty")
                .isNotNull()
                .isNotEmpty();
    }

    /**
     * ID가 null이 아닌지 검증 (Long 타입)
     */
    public static void assertValidId(Long id) {
        assertThat(id)
                .as("ID should not be null and should be positive")
                .isNotNull()
                .isPositive();
    }

    // ===== 컬렉션 검증 =====

    /**
     * 컬렉션이 비어있지 않은지 검증
     */
    public static void assertNotEmpty(java.util.Collection<?> collection, String collectionName) {
        assertThat(collection)
                .as("Collection '%s' should not be empty", collectionName)
                .isNotNull()
                .isNotEmpty();
    }

    /**
     * 컬렉션의 크기가 예상과 일치하는지 검증
     */
    public static void assertSize(java.util.Collection<?> collection, int expectedSize, String collectionName) {
        assertThat(collection)
                .as("Collection '%s' should have size %d", collectionName, expectedSize)
                .hasSize(expectedSize);
    }

    // ===== 예외 검증 보조 =====

    /**
     * 예외 메시지가 예상 문자열을 포함하는지 검증
     */
    public static void assertExceptionMessage(Throwable exception, String expectedMessagePart) {
        assertThat(exception.getMessage())
                .as("Exception message should contain '%s'", expectedMessagePart)
                .contains(expectedMessagePart);
    }

    /**
     * 예외가 예상 타입인지 검증
     */
    public static <T extends Throwable> void assertExceptionType(Throwable exception, Class<T> expectedType) {
        assertThat(exception)
                .as("Exception should be of type %s", expectedType.getSimpleName())
                .isInstanceOf(expectedType);
    }
}
