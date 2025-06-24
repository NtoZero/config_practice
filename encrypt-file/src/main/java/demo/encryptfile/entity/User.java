package demo.encryptfile.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자 엔티티
 * 암호화 기능 테스트를 위한 샘플 엔티티
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    @NotBlank(message = "사용자명은 필수입니다")
    @Size(min = 3, max = 50, message = "사용자명은 3~50자 사이여야 합니다")
    private String username;

    @Column(nullable = false, length = 100)
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이어야 합니다")
    private String email;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "encrypted_data", columnDefinition = "TEXT")
    private String encryptedData; // 암호화된 데이터 저장용

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version; // 낙관적 락을 위한 버전 필드

    /**
     * 사용자 상태 열거형
     */
    public enum UserStatus {
        ACTIVE("활성"),
        INACTIVE("비활성"),
        SUSPENDED("정지"),
        DELETED("삭제");

        private final String description;

        UserStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // 편의 메서드
    public boolean isActive() {
        return UserStatus.ACTIVE.equals(this.status);
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void markAsDeleted() {
        this.status = UserStatus.DELETED;
    }
}
