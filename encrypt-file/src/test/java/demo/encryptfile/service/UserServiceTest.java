package demo.encryptfile.service;

import demo.encryptfile.entity.User;
import demo.encryptfile.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 사용자 서비스 테스트
 */
//@SpringBootTest
@ActiveProfiles("local")
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("사용자 생성 테스트")
    void 사용자_생성_테스트() {
        // Given
        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .fullName("테스트 사용자")
                .phoneNumber("010-1234-5678")
                .build();

        // When
        User savedUser = userService.createUser(user);

        // Then
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getUsername()).isEqualTo("testuser");
        assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        assertThat(savedUser.getEncryptedData()).isNotNull(); // 전화번호가 암호화되어 저장됨
    }

    @Test
    @DisplayName("중복 사용자명으로 생성 시 예외 발생")
    void 중복_사용자명_예외_테스트() {
        // Given
        User user1 = User.builder()
                .username("duplicate")
                .email("user1@example.com")
                .build();
        userService.createUser(user1);

        User user2 = User.builder()
                .username("duplicate")
                .email("user2@example.com")
                .build();

        // When & Then
        assertThatThrownBy(() -> userService.createUser(user2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 사용자명입니다");
    }

    @Test
    @DisplayName("중복 이메일로 생성 시 예외 발생")
    void 중복_이메일_예외_테스트() {
        // Given
        User user1 = User.builder()
                .username("user1")
                .email("duplicate@example.com")
                .build();
        userService.createUser(user1);

        User user2 = User.builder()
                .username("user2")
                .email("duplicate@example.com")
                .build();

        // When & Then
        assertThatThrownBy(() -> userService.createUser(user2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 이메일입니다");
    }

    @Test
    @DisplayName("사용자 조회 테스트")
    void 사용자_조회_테스트() {
        // Given
        User user = User.builder()
                .username("searchuser")
                .email("search@example.com")
                .fullName("검색 사용자")
                .build();
        User savedUser = userService.createUser(user);

        // When
        Optional<User> foundById = userService.findById(savedUser.getId());
        Optional<User> foundByUsername = userService.findByUsername("searchuser");
        Optional<User> foundByEmail = userService.findByEmail("search@example.com");

        // Then
        assertThat(foundById).isPresent();
        assertThat(foundByUsername).isPresent();
        assertThat(foundByEmail).isPresent();
        
        assertThat(foundById.get().getId()).isEqualTo(savedUser.getId());
        assertThat(foundByUsername.get().getUsername()).isEqualTo("searchuser");
        assertThat(foundByEmail.get().getEmail()).isEqualTo("search@example.com");
    }

    @Test
    @DisplayName("사용자 정보 수정 테스트")
    void 사용자_수정_테스트() {
        // Given
        User user = User.builder()
                .username("updateuser")
                .email("update@example.com")
                .fullName("수정 전 이름")
                .phoneNumber("010-1111-1111")
                .build();
        User savedUser = userService.createUser(user);

        User updateInfo = User.builder()
                .fullName("수정 후 이름")
                .email("updated@example.com")
                .phoneNumber("010-2222-2222")
                .build();

        // When
        User updatedUser = userService.updateUser(savedUser.getId(), updateInfo);

        // Then
        assertThat(updatedUser.getFullName()).isEqualTo("수정 후 이름");
        assertThat(updatedUser.getEmail()).isEqualTo("updated@example.com");
        assertThat(updatedUser.getPhoneNumber()).isEqualTo("010-2222-2222");
        assertThat(updatedUser.getUsername()).isEqualTo("updateuser"); // 사용자명은 변경되지 않음
    }

    @Test
    @DisplayName("사용자 상태 변경 테스트")
    void 사용자_상태_변경_테스트() {
        // Given
        User user = User.builder()
                .username("statususer")
                .email("status@example.com")
                .build();
        User savedUser = userService.createUser(user);

        // When
        User suspendedUser = userService.updateUserStatus(savedUser.getId(), User.UserStatus.SUSPENDED);

        // Then
        assertThat(suspendedUser.getStatus()).isEqualTo(User.UserStatus.SUSPENDED);
        assertThat(suspendedUser.isActive()).isFalse();
    }

    @Test
    @DisplayName("사용자 소프트 삭제 테스트")
    void 사용자_소프트_삭제_테스트() {
        // Given
        User user = User.builder()
                .username("deleteuser")
                .email("delete@example.com")
                .build();
        User savedUser = userService.createUser(user);

        // When
        userService.deleteUser(savedUser.getId());

        // Then
        Optional<User> deletedUser = userService.findById(savedUser.getId());
        assertThat(deletedUser).isPresent();
        assertThat(deletedUser.get().getStatus()).isEqualTo(User.UserStatus.DELETED);
    }

    @Test
    @DisplayName("활성 사용자 조회 테스트")
    void 활성_사용자_조회_테스트() {
        // Given
        User activeUser = User.builder()
                .username("activeuser")
                .email("active@example.com")
                .build();
        userService.createUser(activeUser);

        User inactiveUser = User.builder()
                .username("inactiveuser")
                .email("inactive@example.com")
                .build();
        User savedInactive = userService.createUser(inactiveUser);
        userService.updateUserStatus(savedInactive.getId(), User.UserStatus.INACTIVE);

        // When
        List<User> activeUsers = userService.findActiveUsers();

        // Then
        assertThat(activeUsers).isNotEmpty();
        assertThat(activeUsers.stream()
                .allMatch(User::isActive)).isTrue();
    }

    @Test
    @DisplayName("사용자 검색 테스트")
    void 사용자_검색_테스트() {
        // Given
        User user = User.builder()
                .username("searchtest")
                .email("searchtest@example.com")
                .fullName("검색 테스트 사용자")
                .build();
        userService.createUser(user);

        // When
        List<User> searchResult1 = userService.searchUsers("searchtest");
        List<User> searchResult2 = userService.searchUsers("검색");

        // Then
        assertThat(searchResult1).isNotEmpty();
        assertThat(searchResult2).isNotEmpty();
        assertThat(searchResult1.get(0).getUsername()).contains("searchtest");
    }

    @Test
    @DisplayName("사용자 통계 조회 테스트")
    void 사용자_통계_테스트() {
        // Given
        User activeUser = User.builder()
                .username("statsactive")
                .email("statsactive@example.com")
                .build();
        userService.createUser(activeUser);

        User inactiveUser = User.builder()
                .username("statsinactive")
                .email("statsinactive@example.com")
                .build();
        User savedInactive = userService.createUser(inactiveUser);
        userService.updateUserStatus(savedInactive.getId(), User.UserStatus.INACTIVE);

        // When
        UserService.UserStats stats = userService.getUserStats();

        // Then
        assertThat(stats.totalUsers).isGreaterThan(0);
        assertThat(stats.activeUsers).isGreaterThan(0);
        assertThat(stats.inactiveUsers).isGreaterThan(0);
    }

    @Test
    @DisplayName("암호화된 데이터 복호화 테스트")
    void 암호화_데이터_복호화_테스트() {
        // Given
        User user = User.builder()
                .username("encryptuser")
                .email("encrypt@example.com")
                .phoneNumber("010-9999-9999")
                .build();
        User savedUser = userService.createUser(user);

        // When
        String decryptedData = userService.decryptUserData(savedUser);

        // Then
        assertThat(decryptedData).isEqualTo("010-9999-9999");
    }
}
