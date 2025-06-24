package demo.encryptfile.controller;

import demo.encryptfile.entity.User;
import demo.encryptfile.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 사용자 관리 REST API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 개발 편의를 위해 CORS 허용
public class UserController {

    private final UserService userService;

    /**
     * 모든 사용자 조회
     */
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        log.info("모든 사용자 조회 요청");
        List<User> users = userService.findAll();
        return ResponseEntity.ok(users);
    }

    /**
     * 활성 사용자만 조회
     */
    @GetMapping("/active")
    public ResponseEntity<List<User>> getActiveUsers() {
        log.info("활성 사용자 조회 요청");
        List<User> users = userService.findActiveUsers();
        return ResponseEntity.ok(users);
    }

    /**
     * ID로 사용자 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        log.info("사용자 조회 요청 - ID: {}", id);
        
        return userService.findById(id)
                .map(user -> ResponseEntity.ok(user))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 사용자명으로 사용자 조회
     */
    @GetMapping("/username/{username}")
    public ResponseEntity<User> getUserByUsername(@PathVariable String username) {
        log.info("사용자 조회 요청 - 사용자명: {}", username);
        
        return userService.findByUsername(username)
                .map(user -> ResponseEntity.ok(user))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 사용자 검색
     */
    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(@RequestParam String keyword) {
        log.info("사용자 검색 요청 - 키워드: {}", keyword);
        List<User> users = userService.searchUsers(keyword);
        return ResponseEntity.ok(users);
    }

    /**
     * 새 사용자 생성
     */
    @PostMapping
    public ResponseEntity<User> createUser(@Valid @RequestBody User user) {
        log.info("사용자 생성 요청 - 사용자명: {}", user.getUsername());
        
        try {
            User createdUser = userService.createUser(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
        } catch (IllegalArgumentException e) {
            log.warn("사용자 생성 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 사용자 정보 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @Valid @RequestBody User user) {
        log.info("사용자 수정 요청 - ID: {}", id);
        
        try {
            User updatedUser = userService.updateUser(id, user);
            return ResponseEntity.ok(updatedUser);
        } catch (IllegalArgumentException e) {
            log.warn("사용자 수정 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 사용자 상태 변경
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<User> updateUserStatus(
            @PathVariable Long id, 
            @RequestBody Map<String, String> statusRequest) {
        
        log.info("사용자 상태 변경 요청 - ID: {}", id);
        
        try {
            String statusStr = statusRequest.get("status");
            User.UserStatus status = User.UserStatus.valueOf(statusStr.toUpperCase());
            
            User updatedUser = userService.updateUserStatus(id, status);
            return ResponseEntity.ok(updatedUser);
        } catch (IllegalArgumentException e) {
            log.warn("사용자 상태 변경 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 사용자 삭제 (소프트 삭제)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.info("사용자 삭제 요청 - ID: {}", id);
        
        try {
            userService.deleteUser(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("사용자 삭제 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 사용자 완전 삭제 (하드 삭제)
     */
    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDeleteUser(@PathVariable Long id) {
        log.warn("사용자 완전 삭제 요청 - ID: {}", id);
        
        try {
            userService.hardDeleteUser(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("사용자 완전 삭제 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 사용자 통계 조회
     */
    @GetMapping("/stats")
    public ResponseEntity<UserService.UserStats> getUserStats() {
        log.info("사용자 통계 조회 요청");
        UserService.UserStats stats = userService.getUserStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 사용자의 암호화된 데이터 복호화
     */
    @GetMapping("/{id}/decrypt")
    public ResponseEntity<Map<String, String>> decryptUserData(@PathVariable Long id) {
        log.info("사용자 데이터 복호화 요청 - ID: {}", id);
        
        return userService.findById(id)
                .map(user -> {
                    String decryptedData = userService.decryptUserData(user);
                    Map<String, String> response = Map.of(
                        "userId", String.valueOf(user.getId()),
                        "decryptedData", decryptedData != null ? decryptedData : "N/A"
                    );
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
