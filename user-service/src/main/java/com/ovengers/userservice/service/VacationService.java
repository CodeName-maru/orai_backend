package com.ovengers.userservice.service;

import com.ovengers.userservice.dto.VacationRequestDto;
import com.ovengers.userservice.dto.VacationResponseDto;
import com.ovengers.userservice.entity.*;
import com.ovengers.userservice.repository.ApprovalRepository;
import com.ovengers.userservice.repository.UserRepository;
import com.ovengers.userservice.repository.VacationRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Transactional
@Service
public class VacationService {

    private final UserRepository userRepository;
    private final VacationRepository vacationRepository;
    private final ApprovalRepository approvalRepository;

    public VacationResponseDto applyForVacation(VacationRequestDto requestDto) {
        // Vacation 및 Approval 생성 로직
        Vacation vacation = createVacation(requestDto);

        Approval approval = createApproval(vacation, findDirectSupervisor(requestDto.getUserId()));

        // 저장
        vacationRepository.save(vacation);
        approvalRepository.save(approval);


        // Response DTO 생성 및 반환
        return VacationResponseDto.builder()
                .vacationId(String.valueOf(vacation.getVacationId()))
                .type(vacation.getType())
                .startDate(vacation.getStartDate())
                .endDate(vacation.getEndDate())
                .vacationState(vacation.getVacationState())
                .userId(vacation.getUserId())
                .approvalId(approval.getApprovalId())
                .build();
    }

    public List<VacationResponseDto> findVacationsByUserId(String userId) {
        return vacationRepository.findByUserId(userId)
                .stream()
                .map(vacation -> VacationResponseDto.builder()
                        .vacationId(vacation.getVacationId())
                        .type(vacation.getType())
                        .startDate(vacation.getStartDate())
                        .endDate(vacation.getEndDate())
                        .vacationState(vacation.getVacationState())
                        .userId(vacation.getUserId())
                        .approvalId(null) // Approval 정보가 없으면 null
                        .build()
                )
                .collect(Collectors.toList());
    }


    private Vacation createVacation(VacationRequestDto requestDto) {
        return Vacation.builder()
                .type(requestDto.getType())
                .title(requestDto.getTitle())
                .startDate(requestDto.getStartDate())
                .endDate(requestDto.getEndDate())
                .userId(requestDto.getUserId()) // String 타입으로 처리
                .vacationState(VacationState.PENDING) // 초기 상태
                .build();
    }

    private Approval createApproval(Vacation vacation, User supervisor) {
        return Approval.builder()
                .vacation(vacation)
                .approvalUserId(supervisor.getUserId()) // String 타입으로 처리
                .vacationState(VacationState.PENDING)
                .title("Vacation Approval Request")
                .contents("Approval request for vacation from " + vacation.getStartDate() + " to " + vacation.getEndDate())
                .build();
    }

    private User findDirectSupervisor(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        return findSupervisor(user);
    }

    public User findSupervisor(User user) {
        Position position = user.getPosition();
        if (position == null) {
            throw new IllegalArgumentException("User position cannot be null");
        }

        return switch (position) {
            case EMPLOYEE -> userRepository.findByPositionAndDepartmentId(Position.TEAM_LEADER, user.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("해당 부서의 팀장을 찾을 수 없습니다"));
            case TEAM_LEADER -> userRepository.findByPositionAndDepartmentId(Position.MANAGER, user.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("해당 부서의 매니저를 찾을 수 없습니다"));
            case MANAGER -> userRepository.findByPositionAndDepartmentId(Position.CEO, user.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("CEO를 찾을 수 없습니다"));
            case CEO -> throw new IllegalArgumentException("CEO는 휴가 결재자가 없습니다");
        };
    }
}
