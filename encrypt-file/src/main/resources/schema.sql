-- ============================================================================
-- 사용자 테이블 스키마 (H2 호환)
-- ============================================================================
-- 로컬 개발환경에서는 H2 데이터베이스를 사용하므로 H2 호환 문법으로 작성

-- 사용자 테이블
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL,
    full_name VARCHAR(100),
    phone_number VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    encrypted_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);

-- 테스트 데이터 삽입 (H2용)
INSERT INTO users (username, email, full_name, phone_number, status) 
SELECT 'admin', 'admin@example.com', '관리자', '010-1234-5678', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

INSERT INTO users (username, email, full_name, phone_number, status) 
SELECT 'testuser', 'test@example.com', '테스트 사용자', '010-9876-5432', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'testuser');

INSERT INTO users (username, email, full_name, phone_number, status) 
SELECT 'demo', 'demo@example.com', '데모 사용자', '010-1111-2222', 'INACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'demo');
