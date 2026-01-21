package com.ovengers.userservice.service;

import com.ovengers.userservice.client.CalendarServiceClient;
import com.ovengers.userservice.common.configs.AwsS3Config;
import com.ovengers.userservice.common.util.MfaSecretGenerator;
import com.ovengers.userservice.common.util.SmsUtil;
import com.ovengers.userservice.dto.AttitudeResponseDto;
import com.ovengers.userservice.dto.SignUpRequestDto;
import com.ovengers.userservice.dto.UserResponseDto;
import com.ovengers.userservice.entity.Position;
import com.ovengers.userservice.entity.User;
import com.ovengers.userservice.entity.UserState;
import com.ovengers.userservice.repository.AttitudeRepository;
import com.ovengers.userservice.repository.UserRepository;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ovengers.userservice.entity.QUser.user;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService{
    private final UserRepository userRepository;
    private final AttitudeRepository attitudeRepository;
    private final PasswordEncoder encoder;
    private final JPAQueryFactory queryFactory;
    private final SmsUtil smsUtil;
    private final AwsS3Config s3Config;
    private final CalendarServiceClient calendarServiceClient;

    // 유저 생성 쿼리
    public User createUser(@Valid SignUpRequestDto dto, String uniqueFileName) {
        return userRepository.save(dto.toEntity(encoder,uniqueFileName));
    }

    // 검색 Query
    public List<UserResponseDto> search(final Map<String, String> searchCondition, Map<String, String> map) {
        List<User> users = queryFactory
                .selectFrom(user)
                .where(allCond(searchCondition))
                .fetch();
        return users.stream().map(user -> new UserResponseDto(user,map)).collect(Collectors.toList());

    }
    //유저 정보 업데이트
    @Transactional
    public long patchUsers(final String userId, final Map<String, Object> updateFields) throws Exception {
        // 조건 생성
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(user.userId.eq(userId)); // 유저 ID로 조건 설정

        // 업데이트 빌더 생성
        JPAUpdateClause updateQuery = queryFactory
                .update(user)
                .where(builder);

        //department맵찾기
        Map<String,String> map = calendarServiceClient.getDepartmentMap();
        // 업데이트 필드 적용
        if (updateFields.containsKey("email")) {
            updateQuery.set(user.email, (String) updateFields.get("email"));
        }
        if (updateFields.containsKey("name")) {
            updateQuery.set(user.name, (String) updateFields.get("name"));
        }
        if (updateFields.containsKey("position")) {
            updateQuery.set(user.position, Enum.valueOf(Position.class, (String) updateFields.get("position")));
        }
        if (updateFields.containsKey("phoneNum")) {
            updateQuery.set(user.phoneNum, (String) updateFields.get("phoneNum"));
        }
        if (updateFields.containsKey("state")) {
            updateQuery.set(user.state, Enum.valueOf(UserState.class, (String) updateFields.get("state")));
        }
        if (updateFields.containsKey("accountActive")) {
            updateQuery.set(user.accountActive, (Boolean) updateFields.get("accountActive"));
        }
        if (updateFields.containsKey("departmentId")) {
            if(!map.containsKey(updateFields.get("departmentId"))){
                throw new Exception("그런 부서는 없습니다.");
            }
            updateQuery.set(user.departmentId, (String) updateFields.get("departmentId"));
        }
        if (updateFields.containsKey("profileImage")) {
            log.info("profile exists");
            MultipartFile profileImage = (MultipartFile) updateFields.get("profileImage");
            if (profileImage != null && !profileImage.isEmpty()) {
                String uniqueFileName = UUID.randomUUID() + "_" + profileImage.getOriginalFilename();
                String imageUrl
                        = s3Config.uploadToS3Bucket(profileImage.getBytes(), uniqueFileName);
                updateQuery.set(user.profileImage, imageUrl);

            }
        }

        // 실행 및 결과 반환
        return updateQuery.execute();
    }



    //검색 관련 BooleanBuilder 생성
    private BooleanBuilder allCond(final Map<String, String> searchCondition) {
        BooleanBuilder builder = new BooleanBuilder();

        return builder
                .and(emailEq(searchCondition.getOrDefault("email", null)))
                .and(nameLike(searchCondition.getOrDefault("name", null)))
                .and(positionEq(searchCondition.getOrDefault("position", null)))
                .and(phoneNumEq(searchCondition.getOrDefault("phoneNum", null)))
                .and(stateEq(searchCondition.getOrDefault("state", null)))
                .and(accountActiveEq(searchCondition.getOrDefault("accountActive", null)))
                .and(departmentIdEq(searchCondition.getOrDefault("departmentId", null)))
                .and(createdAtAfter(searchCondition.getOrDefault("createdAtAfter", null)))
                .and(createdAtBefore(searchCondition.getOrDefault("createdAtBefore", null)));
    }

    // 조건1: 이메일 조건
    private BooleanExpression emailEq(final String email) {
        return StringUtils.hasText(email) ? user.email.eq(email) : null;
    }

    // 조건2: 이름 검색 조건
    private BooleanExpression nameLike(final String name) {
        return StringUtils.hasText(name) ? user.name.contains(name) : null;
    }

    // 조건3: 직책 검색 조건
    private BooleanExpression positionEq(final String position) {
        if (!StringUtils.hasText(position)) return null;
        try {
            return user.position.eq(Enum.valueOf(Position.class, position));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // 조건4: 전화번호 검색 조건
    private BooleanExpression phoneNumEq(final String phoneNum) {
        return StringUtils.hasText(phoneNum) ? user.phoneNum.eq(phoneNum) : null;
    }

    // 조건5: 사용자 상태 검색 조건
    private BooleanExpression stateEq(final String state) {
        if (!StringUtils.hasText(state)) return null;
        try {
            return user.state.eq(Enum.valueOf(UserState.class, state));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // 조건6: 계정 활성 상태 검색 조건
    private BooleanExpression accountActiveEq(final String accountActive) {
        return StringUtils.hasText(accountActive) ? user.accountActive.eq(Boolean.parseBoolean(accountActive)) : null;
    }

    // 조건7: 소속 아이디 검색 조건
    private BooleanExpression departmentIdEq(final String departmentId) {
        return StringUtils.hasText(departmentId) ? user.departmentId.eq(departmentId) : null;
    }

    // 조건8: createdAt 이후 검색 조건
    private BooleanExpression createdAtAfter(final String createdAtAfter) {
        if (!StringUtils.hasText(createdAtAfter)) return null;
        try {
            LocalDateTime afterDate = LocalDateTime.parse(createdAtAfter, DateTimeFormatter.ISO_DATE_TIME);
            return user.createdAt.goe(afterDate);
        } catch (Exception e) {
            return null; // 유효하지 않은 날짜 형식 처리
        }
    }

    // 조건9: createdAt 이전 검색 조건
    private BooleanExpression createdAtBefore(final String createdAtBefore) {
        if (!StringUtils.hasText(createdAtBefore)) return null;
        try {
            LocalDateTime beforeDate = LocalDateTime.parse(createdAtBefore, DateTimeFormatter.ISO_DATE_TIME);
            return user.createdAt.loe(beforeDate);
        } catch (Exception e) {
            return null; // 유효하지 않은 날짜 형식 처리
        }
    }

    /**
     * UserResponseDTO 객체 리스트를 Page 객체로 변환합니다.
     *
     * @param users   UserResponseDTO 객체 리스트
     * @param pageable 페이지네이션 정보를 담고 있는 Pageable 객체
     * @return UserResponseDTO 객체의 Page
     */
    public Page<UserResponseDto> listToPage(List<UserResponseDto> users, Pageable pageable) {
        if (users == null || pageable == null) {
            throw new IllegalArgumentException("Users 리스트와 pageable은 null이 될 수 없습니다.");
        }

        // 정렬 처리: pageable.getSort()에 설정된 필드를 기준으로 정렬
        List<UserResponseDto> sortedUsers = users.stream()
                .sorted((user1, user2) -> {
                    for (Sort.Order order : pageable.getSort()) {
                        int comparisonResult = 0;
                        switch (order.getProperty()) {
                            case "name":
                                comparisonResult = user1.getName().compareTo(user2.getName());
                                break;
                            case "email":
                                comparisonResult = user1.getEmail().compareTo(user2.getEmail());
                                break;
                            case "position":
                                comparisonResult = user1.getPosition().compareTo(user2.getPosition());
                                break;
                            case "accountActive":
                                comparisonResult = Boolean.compare(user1.isAccountActive(), user2.isAccountActive());
                                break;
                            case "departmentId":
                                comparisonResult = user1.getDepartmentId().compareTo(user2.getDepartmentId());
                                break;
                        }

                        if (comparisonResult != 0) {
                            return order.getDirection() == Sort.Direction.ASC ? comparisonResult : -comparisonResult;
                        }
                    }
                    return 0; // 모든 기준이 같으면 동일함
                })
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        if (start >= sortedUsers.size()) {
            return new PageImpl<>(List.of(), pageable, sortedUsers.size());
        }
        int end = Math.min(start + pageable.getPageSize(), sortedUsers.size());

        return new PageImpl<>(sortedUsers.subList(start, end), pageable, sortedUsers.size());
    }

    public void smsService(String phoneNum) {
        SingleMessageSentResponse res = smsUtil.sendOne(phoneNum);
        log.info("smsservice : {} ",res);
    }

    //근태 조회
    public List<AttitudeResponseDto> selectAttitude(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() ->
            new EntityNotFoundException("User with id " + userId + " not found.")
        );
        return attitudeRepository.findByUser(user).stream().map(AttitudeResponseDto::new).collect(Collectors.toList());
    }

    public boolean deleteUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() ->
            new EntityNotFoundException("User not found.")
        );
        userRepository.delete(user);
        return true;
    }
}
