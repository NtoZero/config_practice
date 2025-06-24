package demo.encryptfile.repository;

import demo.encryptfile.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 리포지토리
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 사용자명으로 사용자 조회
     */
    Optional<User> findByUsername(String username);

    /**
     * 이메일로 사용자 조회
     */
    Optional<User> findByEmail(String email);

    /**
     * 사용자명 존재 여부 확인
     */
    boolean existsByUsername(String username);

    /**
     * 이메일 존재 여부 확인
     */
    boolean existsByEmail(String email);

    /**
     * 상태별 사용자 조회
     */
    List<User> findByStatus(User.UserStatus status);

    /**
     * 활성 사용자 조회
     */
    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE'")
    List<User> findActiveUsers();

    /**
     * 사용자명 또는 이메일로 검색
     */
    @Query("SELECT u FROM User u WHERE u.username LIKE %:keyword% OR u.email LIKE %:keyword% OR u.fullName LIKE %:keyword%")
    List<User> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 상태별 사용자 수 조회
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status")
    long countByStatus(@Param("status") User.UserStatus status);
}
