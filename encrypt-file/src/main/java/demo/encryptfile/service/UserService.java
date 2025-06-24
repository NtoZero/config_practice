package demo.encryptfile.service;

import demo.encryptfile.config.JasyptConfig;
import demo.encryptfile.entity.User;
import demo.encryptfile.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 서비스
 * 암호화 기능이 포함된 사용자 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final JasyptConfig.EncryptionService encryptionService;

    /**
     * 모든 사용자 조회
     */
    public List<User> findAll() {
        log.debug("모든 사용자 조회");
        return userRepository.findAll();
    }

    /**
     * ID로 사용자 조회
     */
    public Optional<User> findById(Long id) {
        log.debug("사용자 조회 - ID: {}", id);
        return userRepository.findById(id);
    }

    /**
     * 사용자명으로 사용자 조회
     */
    public Optional<User> findByUsername(String username) {
        log.debug("사용자 조회 - 사용자명: {}", username);
        return userRepository.findByUsername(username);
    }

    /**
     * 이메일로 사용자 조회
     */
    public Optional<User> findByEmail(String email) {
        log.debug("사용자 조회 - 이메일: {}", email);
        return userRepository.findByEmail(email);
    }

    /**
     * 활성 사용자 조회
     */
    public List<User> findActiveUsers() {
        log.debug("활성 사용자 조회");
        return userRepository.findActiveUsers();
    }

    /**
     * 키워드로 사용자 검색
     */
    public List<User> searchUsers(String keyword) {
        log.debug("사용자 검색 - 키워드: {}", keyword);
        return userRepository.searchByKeyword(keyword);
    }

    /**
     * 사용자 생성
     */
    @Transactional
    public User createUser(User user) {
        log.info("사용자 생성 - 사용자명: {}", user.getUsername());
        
        // 중복 검사
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("이미 존재하는 사용자명입니다: " + user.getUsername());
        }
        
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다: " + user.getEmail());
        }

        // 민감한 데이터 암호화 (예: 전화번호)
        if (user.getPhoneNumber() != null && !user.getPhoneNumber().trim().isEmpty()) {
            String encryptedPhone = encryptionService.encrypt(user.getPhoneNumber());
            user.setEncryptedData(encryptedPhone);
            log.debug("전화번호가 암호화되어 저장됩니다");
        }

        User savedUser = userRepository.save(user);
        log.info("사용자 생성 완료 - ID: {}, 사용자명: {}", savedUser.getId(), savedUser.getUsername());
        
        return savedUser;
    }

    /**
     * 사용자 정보 수정
     */
    @Transactional
    public User updateUser(Long id, User userDetails) {
        log.info("사용자 수정 - ID: {}", id);
        
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + id));

        // 수정 가능한 필드 업데이트
        existingUser.setFullName(userDetails.getFullName());
        existingUser.setEmail(userDetails.getEmail());
        
        // 전화번호 암호화 처리
        if (userDetails.getPhoneNumber() != null && !userDetails.getPhoneNumber().trim().isEmpty()) {
            existingUser.setPhoneNumber(userDetails.getPhoneNumber());
            String encryptedPhone = encryptionService.encrypt(userDetails.getPhoneNumber());
            existingUser.setEncryptedData(encryptedPhone);
        }

        User updatedUser = userRepository.save(existingUser);
        log.info("사용자 수정 완료 - ID: {}", updatedUser.getId());
        
        return updatedUser;
    }

    /**
     * 사용자 상태 변경
     */
    @Transactional
    public User updateUserStatus(Long id, User.UserStatus status) {
        log.info("사용자 상태 변경 - ID: {}, 상태: {}", id, status);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + id));

        user.setStatus(status);
        User updatedUser = userRepository.save(user);
        
        log.info("사용자 상태 변경 완료 - ID: {}, 상태: {}", updatedUser.getId(), updatedUser.getStatus());
        return updatedUser;
    }

    /**
     * 사용자 삭제 (소프트 삭제)
     */
    @Transactional
    public void deleteUser(Long id) {
        log.info("사용자 삭제 - ID: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + id));

        user.markAsDeleted();
        userRepository.save(user);
        
        log.info("사용자 삭제 완료 - ID: {}", id);
    }

    /**
     * 사용자 완전 삭제 (하드 삭제)
     */
    @Transactional
    public void hardDeleteUser(Long id) {
        log.warn("사용자 완전 삭제 - ID: {}", id);
        
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + id);
        }

        userRepository.deleteById(id);
        log.warn("사용자 완전 삭제 완료 - ID: {}", id);
    }

    /**
     * 암호화된 데이터 복호화
     */
    public String decryptUserData(User user) {
        if (user.getEncryptedData() == null || user.getEncryptedData().trim().isEmpty()) {
            return null;
        }
        
        try {
            return encryptionService.decrypt(user.getEncryptedData());
        } catch (Exception e) {
            log.error("데이터 복호화 실패 - 사용자 ID: {}", user.getId(), e);
            return "[복호화 실패]";
        }
    }

    /**
     * 사용자 통계 조회
     */
    public UserStats getUserStats() {
        log.debug("사용자 통계 조회");
        
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(User.UserStatus.ACTIVE);
        long inactiveUsers = userRepository.countByStatus(User.UserStatus.INACTIVE);
        long suspendedUsers = userRepository.countByStatus(User.UserStatus.SUSPENDED);
        long deletedUsers = userRepository.countByStatus(User.UserStatus.DELETED);

        return UserStats.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .inactiveUsers(inactiveUsers)
                .suspendedUsers(suspendedUsers)
                .deletedUsers(deletedUsers)
                .build();
    }

    /**
     * 사용자 통계 DTO
     */
    public static class UserStats {
        public final long totalUsers;
        public final long activeUsers;
        public final long inactiveUsers;
        public final long suspendedUsers;
        public final long deletedUsers;

        @lombok.Builder
        public UserStats(long totalUsers, long activeUsers, long inactiveUsers, 
                        long suspendedUsers, long deletedUsers) {
            this.totalUsers = totalUsers;
            this.activeUsers = activeUsers;
            this.inactiveUsers = inactiveUsers;
            this.suspendedUsers = suspendedUsers;
            this.deletedUsers = deletedUsers;
        }
    }
}
