package com.ovengers.calendarservice.repository;

import com.ovengers.calendarservice.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface CalendarRepository extends JpaRepository<Schedule, String> {

    // startTime 필드와 endTime 필드를 기준으로 조회 (LocalDate 타입과 일치)
    List<Schedule> findByStartTimeBetween(LocalDate start, LocalDate end);

    // 부서별 일정 조회
    @Query("SELECT s FROM Schedule s WHERE s.department.departmentId = :departmentId")
    List<Schedule> findByDepartmentId(String departmentId);

    // 오늘 일정과 관련된 userId로 조회
    @Query("SELECT DISTINCT s.userId FROM Schedule s WHERE s.startTime = :today")
    List<String> findDistinctUserIdForToday(LocalDate today);

    List<Schedule> findByUserId(String userId);

    // startTime이 LocalDate 타입이므로 DATE() 함수 불필요
    @Query("SELECT s FROM Schedule s WHERE s.startTime = :date")
    List<Schedule> findByDate(LocalDate date);

    List<Schedule> findByDepartment_DepartmentIdIn(List<String> departmentIds);

    List<Schedule> findByStartTime(LocalDate startTime);

}
