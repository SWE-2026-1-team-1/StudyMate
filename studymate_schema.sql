-- ================================================================
-- StudyMate MySQL 스키마
-- 관리자: 채범수 (marcus)
-- 변경 이력 / 결정 근거: docs/decisions-log.md (D-NNN, P-NNN 식별자)
--
-- 주요 결정 반영:
--   D-001 / D-002 : refresh_token 테이블 신설 (MySQL 단일 저장)
--   D-003         : DB 트리거 및 가드 테이블 제거 → 비즈니스 규칙은 서비스 레이어 트랜잭션 + 락 담당
--   D-005         : email_verification 테이블 신설 (send-code / verify / signup 3단계 분리)
--   P-001         : email_verification, refresh_token 신규 (본 파일에서 처리)
--
-- 트리거가 강제하던 규칙은 모두 서비스 레이어로 이관됨. 아래 주석에 "(서비스 레이어 담당)"
-- 으로 표시된 항목은 DB 제약으로 강제하지 않으며, 서비스가 트랜잭션 + 락으로 보장한다.
-- ================================================================

DROP DATABASE IF EXISTS studymate;
CREATE DATABASE studymate
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE studymate;

-- ================================================================
-- 1. 사용자 테이블
-- ================================================================
CREATE TABLE app_user (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '사용자 번호',
    email               VARCHAR(255)    NOT NULL COMMENT '로그인 이메일',
    password_hash       VARCHAR(255)    NOT NULL COMMENT '해시 비밀번호 (BCrypt)',
    name                VARCHAR(100)    NOT NULL COMMENT '이름',
    is_email_verified   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '이메일 인증 여부 (signup 시 서비스가 1로 설정)',
    is_deleted          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (id),
    UNIQUE KEY uq_app_user_email (email),
    KEY idx_app_user_deleted_created (is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 테이블';

-- ================================================================
-- 1-A. 이메일 인증 (D-005, P-001 신규)
--   - send-code 호출 시 row 신규 생성 (code_hash 저장)
--   - verify 호출 시 코드 매칭 + 만료/시도횟수 검증 → verified_at 갱신
--   - signup 호출 시 verified 미사용 row 확인 → 사용 후 consumed_at 갱신
--   - email 은 app_user FK 가 아님 (미가입 상태에서도 사용)
--   - 동일 email 에 여러 row 존재 가능 → 서비스가 최신/유효 row 선택
--   - 코드 길이/TTL/시도횟수/쿨다운 상수는 P-008 미확정 (현재 본 파일 주석은 가정값)
-- ================================================================
CREATE TABLE email_verification (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '이메일 인증 레코드 ID',
    email           VARCHAR(255)    NOT NULL COMMENT '인증 대상 이메일',
    code_hash       VARCHAR(255)    NOT NULL COMMENT '인증 코드 해시 (평문 저장 금지)',
    expires_at      DATETIME        NOT NULL COMMENT '코드 만료 시각 (P-008 가정: TTL 10분)',
    verified_at     DATETIME        DEFAULT NULL COMMENT 'verify 성공 시각 (NULL = 미인증)',
    consumed_at     DATETIME        DEFAULT NULL COMMENT 'signup 에서 사용된 시각 (재사용 방지)',
    attempt_count   INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'verify 시도 횟수 (P-008 가정: 상한 5회)',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',

    PRIMARY KEY (id),
    KEY idx_email_verification_email_created (email, created_at),
    KEY idx_email_verification_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='이메일 인증 코드 관리 (D-005, P-001)';

-- ================================================================
-- 1-B. Refresh Token 저장소 (D-001 / D-002, P-001 신규)
--   - 평문 저장 금지: token_hash 로만 저장
--   - Rotation: 사용 시 신규 token 발급 + 기존 row revoked_at 갱신
--   - 만료된 row 는 배치/cron 으로 정리 (서비스 레이어 담당)
--   - 로그아웃 정책(전 디바이스 vs 단일 세션)은 P-007 미확정
-- ================================================================
CREATE TABLE refresh_token (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Refresh Token 레코드 ID',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '소유 사용자 ID',
    token_hash  VARCHAR(255)    NOT NULL COMMENT '토큰 해시 (평문 저장 금지)',
    expires_at  DATETIME        NOT NULL COMMENT '만료 시각 (D-001 가정: Refresh 14일)',
    revoked_at  DATETIME        DEFAULT NULL COMMENT '폐기 시각 (NULL = 활성)',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급 시각',

    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_token_hash (token_hash),
    KEY idx_refresh_token_user_revoked (user_id, revoked_at),
    KEY idx_refresh_token_expires (expires_at),
    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Refresh Token 저장소 (D-001/D-002, P-001)';

-- ================================================================
-- 2. 태그 테이블
-- ================================================================
CREATE TABLE tag (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '태그 번호',
    name        VARCHAR(100)    NOT NULL COMMENT '태그명',
    category    VARCHAR(50)     NOT NULL COMMENT '태그 분류',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (id),
    UNIQUE KEY uq_tag_name (name),
    KEY idx_tag_category_name (category, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='태그 테이블';

-- ================================================================
-- 3. 스터디 역할 테이블: ENUM 보다 권한 확장이 용이
-- ================================================================
CREATE TABLE study_role (
    code                     VARCHAR(30) NOT NULL COMMENT '역할 코드',
    name                     VARCHAR(50) NOT NULL COMMENT '역할명',
    sort_order               INT UNSIGNED NOT NULL DEFAULT 99 COMMENT '정렬값',
    can_approve_application  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '신청 승인 가능 여부',
    can_manage_member        TINYINT(1) NOT NULL DEFAULT 0 COMMENT '멤버 관리 가능 여부',
    can_create_attendance    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '출석 세션 생성 가능 여부',
    can_post_notice          TINYINT(1) NOT NULL DEFAULT 0 COMMENT '공지 게시 가능 여부',
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='스터디 역할 및 권한 테이블';

-- ================================================================
-- 4. 사용자 언어 테이블
-- ================================================================
CREATE TABLE user_language (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '사용자 언어 번호',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '사용자 번호',
    language    VARCHAR(50)     NOT NULL COMMENT '사용 가능 언어',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (id),
    UNIQUE KEY uq_user_language (user_id, language),
    CONSTRAINT fk_user_language_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 언어 테이블';

-- ================================================================
-- 5. 사용자 태그 테이블
-- ================================================================
CREATE TABLE user_tag (
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '사용자 번호',
    tag_id      BIGINT UNSIGNED NOT NULL COMMENT '태그 번호',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',

    PRIMARY KEY (user_id, tag_id),
    KEY idx_user_tag_tag (tag_id),
    CONSTRAINT fk_user_tag_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_user_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tag (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 태그 테이블';

-- ================================================================
-- 6. 스터디 테이블
--   - leader_id 미저장: 현재 조장은 study_member 의 활성 LEADER 기록으로 표현
--   - current_member_count: 서비스 레이어가 가입/탈퇴 트랜잭션 안에서 유지
--     (D-003: 트리거 제거. 정원 초과 방지는 SELECT ... FOR UPDATE 또는 낙관적 락으로)
-- ================================================================
CREATE TABLE study (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '스터디 번호',
    title                 VARCHAR(200)    NOT NULL COMMENT '제목',
    purpose               TEXT            NOT NULL COMMENT '목적 설명',
    max_members           INT UNSIGNED NOT NULL DEFAULT 10 COMMENT '최대 멤버 수',
    current_member_count  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '현재 활성 멤버 수 (서비스 레이어 유지, D-003)',
    activity_cycle        VARCHAR(100)    NOT NULL COMMENT '활동 빈도',
    duration_weeks        INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '활동 기간(주) — D-013',
    status                ENUM('OPEN','CLOSED','CANCELLED') NOT NULL DEFAULT 'OPEN' COMMENT '모집 상태',
    is_deleted            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부',
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (id),
    KEY idx_study_status_deleted_created (status, is_deleted, created_at),
    KEY idx_study_created (created_at),
    FULLTEXT KEY ft_study_title_purpose (title, purpose)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='스터디 테이블';

-- ================================================================
-- 7. 스터디 태그 테이블
-- ================================================================
CREATE TABLE study_tag (
    study_id    BIGINT UNSIGNED NOT NULL COMMENT '스터디 번호',
    tag_id      BIGINT UNSIGNED NOT NULL COMMENT '태그 번호',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',

    PRIMARY KEY (study_id, tag_id),
    KEY idx_study_tag_tag (tag_id),
    CONSTRAINT fk_study_tag_study
        FOREIGN KEY (study_id) REFERENCES study (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_study_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tag (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='스터디 태그 테이블';

-- ================================================================
-- 8-1. 스터디 언어 테이블 (D-013 신규)
-- ================================================================
CREATE TABLE study_language (
    study_id      BIGINT UNSIGNED NOT NULL COMMENT '스터디 번호',
    language_code VARCHAR(30)     NOT NULL COMMENT '언어 코드 (예: ko/en/zh)',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (study_id, language_code),
    CONSTRAINT fk_study_language_study
        FOREIGN KEY (study_id) REFERENCES study (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='스터디 사용 언어 (D-013)';

-- ================================================================
-- 8. 스터디 멤버 테이블: 보존형 히스토리 (탈퇴 시 row 삭제 대신 is_active=0)
--   (서비스 레이어 담당)
--     - 활성 멤버 유일성: 같은 (study_id, user_id) 에 is_active=1 row 1건만 허용
--     - 활성 리더 유일성: 같은 study_id 에 role_code='LEADER' AND is_active=1 row 1건만 허용
--     - 정원 초과 방지: study.current_member_count < study.max_members 검사
--     - 비활성 row 의 is_active=0 → 1 부활 금지 (새 row 발급)
--     - history 물리 삭제 금지 (탈퇴는 is_active=0 처리)
--   idx_member_id_study (uq_member_id_study) 는 attendance / application / post / comment 의
--   합성 외래키 참조용으로 유지.
-- ================================================================
CREATE TABLE study_member (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '멤버 기록 번호',
    study_id                BIGINT UNSIGNED NOT NULL COMMENT '스터디 번호',
    user_id                 BIGINT UNSIGNED NOT NULL COMMENT '사용자 번호',
    role_code               VARCHAR(30)     NOT NULL DEFAULT 'MEMBER' COMMENT '역할 코드',
    is_active               TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '활성 멤버 여부',
    joined_at               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입 시각',
    left_at                 DATETIME        DEFAULT NULL COMMENT '탈퇴 시각',
    left_reason             ENUM('VOLUNTARY','KICKED','STUDY_CLOSED') DEFAULT NULL COMMENT '탈퇴 사유',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (id),
    UNIQUE KEY uq_member_id_study (id, study_id),
    INDEX idx_member_study_user_active (study_id, user_id, is_active),
    INDEX idx_member_study_active (study_id, is_active),
    INDEX idx_member_user_active (user_id, is_active),
    INDEX idx_member_study_role_active (study_id, role_code, is_active),
    CONSTRAINT fk_member_study
        FOREIGN KEY (study_id) REFERENCES study (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_member_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_member_role
        FOREIGN KEY (role_code) REFERENCES study_role (code)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='스터디 멤버 테이블';

-- ================================================================
-- 9. 신청 테이블: 히스토리 보존
--   (서비스 레이어 담당)
--     - 같은 (study_id, applicant_id) 에 status='PENDING' row 1건만 허용
--     - PENDING 가 아닌 row 의 status 변경 금지 (한 번 처리되면 종결)
--     - ACCEPTED 처리 시: 해당 user 의 활성 멤버 존재 여부 / 스터디 OPEN 여부 검사
--                          + study_member row 생성 + current_member_count 갱신
--                          (이전엔 트리거가 자동 INSERT, D-003 이후 서비스가 직접 INSERT)
--     - 처리자(processed_by_member_id)는 해당 스터디 활성 멤버 + can_approve_application 권한 보유 필수
--   idx_application_id_study (uq_application_id_study) 는 notification 의 합성 외래키 참조용으로 유지.
-- ================================================================
CREATE TABLE application (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '신청 번호',
    study_id                BIGINT UNSIGNED NOT NULL COMMENT '스터디 번호',
    applicant_id            BIGINT UNSIGNED NOT NULL COMMENT '신청인 번호',
    status                  ENUM('PENDING','ACCEPTED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '신청 상태',
    message                 VARCHAR(500) DEFAULT NULL COMMENT '신청 메시지',
    applied_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청 시각',
    processed_at            DATETIME DEFAULT NULL COMMENT '처리 시각',
    processed_by_member_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '승인 멤버 번호',
    reject_reason           VARCHAR(500) DEFAULT NULL COMMENT '거절 사유',
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (id),
    UNIQUE KEY uq_application_id_study (id, study_id),
    INDEX idx_application_study_status (study_id, status, applied_at),
    INDEX idx_application_applicant_status (applicant_id, status, applied_at),
    INDEX idx_application_pending_lookup (study_id, applicant_id, status),
    INDEX idx_application_processor (processed_by_member_id, study_id),
    CONSTRAINT fk_application_study
        FOREIGN KEY (study_id) REFERENCES study (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_application_applicant
        FOREIGN KEY (applicant_id) REFERENCES app_user (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_application_processor
        FOREIGN KEY (processed_by_member_id, study_id) REFERENCES study_member (id, study_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='스터디 신청 테이블';

-- ================================================================
-- 10. 알림 테이블
-- ================================================================
CREATE TABLE notification (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '알림 번호',
    receiver_id          BIGINT UNSIGNED NOT NULL COMMENT '수신인 번호',
    type                ENUM('APPLICATION_SUBMITTED','APPLICATION_ACCEPTED','APPLICATION_REJECTED','STUDY_NOTICE','COMMENT_REPLY','SYSTEM')
                        NOT NULL DEFAULT 'SYSTEM' COMMENT '알림 유형',
    message             VARCHAR(500) NOT NULL COMMENT '알림 내용',
    is_read             TINYINT(1) NOT NULL DEFAULT 0 COMMENT '읽음 여부',
    read_at             DATETIME DEFAULT NULL COMMENT '읽은 시각',
    ref_study_id        BIGINT UNSIGNED DEFAULT NULL COMMENT '연관 스터디 번호',
    ref_application_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '연관 신청 번호',
    ref_post_id         BIGINT UNSIGNED DEFAULT NULL COMMENT '연관 게시글 번호',
    ref_comment_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '연관 댓글 번호',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (id),
    KEY idx_notification_receiver_read_created (receiver_id, is_read, created_at),
    KEY idx_notification_study (ref_study_id),
    KEY idx_notification_application (ref_application_id),
    KEY idx_notification_post (ref_post_id),
    KEY idx_notification_comment (ref_comment_id),
    CONSTRAINT fk_notification_receiver
        FOREIGN KEY (receiver_id) REFERENCES app_user (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_notification_study
        FOREIGN KEY (ref_study_id) REFERENCES study (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_notification_application
        FOREIGN KEY (ref_application_id) REFERENCES application (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='알림 테이블';

-- ================================================================
-- 11. 출석 세션 테이블
--   (서비스 레이어 담당) created_by_member_id 가 해당 study 의 활성 멤버이고
--                          can_create_attendance 권한 보유한지 확인.
-- ================================================================
CREATE TABLE attendance_session (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '출석 세션 번호',
    study_id              BIGINT UNSIGNED NOT NULL COMMENT '스터디 번호',
    session_num           INT UNSIGNED NOT NULL COMMENT '세션 번호',
    session_date          DATE NOT NULL COMMENT '활동 일자',
    title                 VARCHAR(200) DEFAULT NULL COMMENT '세션 제목',
    created_by_member_id  BIGINT UNSIGNED NOT NULL COMMENT '생성 멤버 번호',
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (id),
    UNIQUE KEY uq_attendance_session_num (study_id, session_num),
    UNIQUE KEY uq_attendance_session_id_study (id, study_id),
    KEY idx_attendance_session_date (study_id, session_date),
    CONSTRAINT fk_attendance_session_study
        FOREIGN KEY (study_id) REFERENCES study (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_attendance_session_creator
        FOREIGN KEY (created_by_member_id, study_id) REFERENCES study_member (id, study_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='출석 세션 테이블';

-- ================================================================
-- 12. 출석 기록 테이블
--   합성 외래키 (session_id, study_id) / (member_id, study_id) 로
--   A 스터디 세션에 B 스터디 멤버가 기록되는 것을 DB 레벨에서 차단.
--   (서비스 레이어 담당) member 가 여전히 활성 멤버인지 확인.
-- ================================================================
CREATE TABLE attendance_record (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '출석 기록 번호',
    study_id    BIGINT UNSIGNED NOT NULL COMMENT '스터디 번호',
    session_id  BIGINT UNSIGNED NOT NULL COMMENT '출석 세션 번호',
    member_id   BIGINT UNSIGNED NOT NULL COMMENT '멤버 기록 번호',
    status      ENUM('PRESENT','ABSENT','LATE') NOT NULL DEFAULT 'ABSENT' COMMENT '출석 상태',
    note        VARCHAR(300) DEFAULT NULL COMMENT '비고',
    checked_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 시각',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (id),
    UNIQUE KEY uq_attendance_record_session_member (session_id, member_id),
    KEY idx_attendance_record_member (member_id, checked_at),
    KEY idx_attendance_record_study_status (study_id, status),
    CONSTRAINT fk_attendance_record_session
        FOREIGN KEY (session_id, study_id) REFERENCES attendance_session (id, study_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_attendance_record_member
        FOREIGN KEY (member_id, study_id) REFERENCES study_member (id, study_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='출석 기록 테이블';

-- ================================================================
-- 13. 게시글 테이블
--   (서비스 레이어 담당) author 가 활성 멤버인지 / NOTICE 작성은 can_post_notice 권한 보유 여부 확인.
-- ================================================================
CREATE TABLE post (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '게시글 번호',
    study_id          BIGINT UNSIGNED NOT NULL COMMENT '스터디 번호',
    author_member_id  BIGINT UNSIGNED NOT NULL COMMENT '작성자 멤버 번호',
    type              ENUM('NOTICE','FREE') NOT NULL DEFAULT 'FREE' COMMENT '게시글 유형',
    title             VARCHAR(300) NOT NULL COMMENT '제목',
    content           TEXT NOT NULL COMMENT '본문',
    is_deleted        TINYINT(1) NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부',
    deleted_at        DATETIME DEFAULT NULL COMMENT '삭제 시각',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '게시 시각',
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (id),
    UNIQUE KEY uq_post_id_study (id, study_id),
    KEY idx_post_study_deleted_created (study_id, is_deleted, created_at),
    KEY idx_post_author_created (author_member_id, created_at),
    FULLTEXT KEY ft_post_title_content (title, content),
    CONSTRAINT fk_post_study
        FOREIGN KEY (study_id) REFERENCES study (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_post_author_member
        FOREIGN KEY (author_member_id, study_id) REFERENCES study_member (id, study_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게시글 테이블';

-- ================================================================
-- 14. 댓글 테이블
--   (서비스 레이어 담당) author 가 활성 멤버인지 / 대상 post 가 미삭제 상태인지 확인.
-- ================================================================
CREATE TABLE post_comment (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '댓글 번호',
    study_id          BIGINT UNSIGNED NOT NULL COMMENT '스터디 번호',
    post_id           BIGINT UNSIGNED NOT NULL COMMENT '게시글 번호',
    author_member_id  BIGINT UNSIGNED NOT NULL COMMENT '작성자 멤버 번호',
    content           TEXT NOT NULL COMMENT '댓글 내용',
    is_deleted        TINYINT(1) NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부',
    deleted_at        DATETIME DEFAULT NULL COMMENT '삭제 시각',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '게시 시각',
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    PRIMARY KEY (id),
    KEY idx_comment_post_deleted_created (post_id, is_deleted, created_at),
    KEY idx_comment_author_created (author_member_id, created_at),
    CONSTRAINT fk_comment_post
        FOREIGN KEY (post_id, study_id) REFERENCES post (id, study_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_comment_author_member
        FOREIGN KEY (author_member_id, study_id) REFERENCES study_member (id, study_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='댓글 테이블';

-- notification 의 post / comment 외래키는 두 테이블 생성 이후 추가
ALTER TABLE notification
    ADD CONSTRAINT fk_notification_post
        FOREIGN KEY (ref_post_id) REFERENCES post (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    ADD CONSTRAINT fk_notification_comment
        FOREIGN KEY (ref_comment_id) REFERENCES post_comment (id)
        ON DELETE SET NULL ON UPDATE CASCADE;

-- ================================================================
-- (D-003) 트리거 및 가드 테이블 (study_active_member_guard, study_active_leader_guard,
-- application_pending_guard) 은 본 스키마에서 모두 제거됨. 동일 규칙은 서비스 레이어가
-- 트랜잭션 + 락으로 보장한다. 자세한 내용은 docs/decisions-log.md D-003 영향 항목 참조.
-- ================================================================


-- ================================================================
-- 샘플 데이터 (트리거 제거에 따라 명시 INSERT 로 일관성 유지)
--   - 이전엔 application ACCEPTED 트리거가 study_member 를 자동 생성
--     → 이제는 (8) 에 명시 INSERT
--   - 이전엔 study_member INSERT 트리거가 study.current_member_count 를 증분
--     → 이제는 (6) study INSERT 시 정확한 값으로 박음
-- ================================================================

-- 1. 역할
INSERT INTO study_role
(code, name, sort_order, can_approve_application, can_manage_member, can_create_attendance, can_post_notice) VALUES
('LEADER',    '조장',      1, 1, 1, 1, 1),
('CO_LEADER', '부조장',    2, 1, 1, 1, 1),
('OPERATOR',  '운영자',    3, 0, 1, 1, 1),
('MEMBER',    '일반 멤버', 9, 0, 0, 0, 0);

-- 2. 사용자
INSERT INTO app_user (id, email, password_hash, name, is_email_verified) VALUES
(1,  'alice@university.ac.kr',   '$2b$12$exampleHashAlice',   'Alice Kim',     1),
(2,  'bob@university.ac.kr',     '$2b$12$exampleHashBob',     'Bob Lee',        1),
(3,  'charlie@university.ac.kr', '$2b$12$exampleHashCharlie', 'Charlie Park',   1),
(4,  'diana@university.ac.kr',   '$2b$12$exampleHashDiana',   'Diana Choi',     1),
(5,  'evan@university.ac.kr',    '$2b$12$exampleHashEvan',    'Evan Jang',      1),
(6,  'fiona@university.ac.kr',   '$2b$12$exampleHashFiona',   'Fiona Wu',       1),
(7,  'george@university.ac.kr',  '$2b$12$exampleHashGeorge',  'George Chen',    1),
(8,  'hana@university.ac.kr',    '$2b$12$exampleHashHana',    'Hana Moon',      1),
(9,  'ivan@university.ac.kr',    '$2b$12$exampleHashIvan',    'Ivan Santos',    1),
(10, 'julia@university.ac.kr',   '$2b$12$exampleHashJulia',   'Julia Han',      0);

-- 3. 태그
INSERT INTO tag (id, name, category) VALUES
(1,  'Major Study',        'MAJOR'),
(2,  'Language',           'LANGUAGE'),
(3,  'Career',             'CAREER'),
(4,  'Certification',      'CERTIFICATION'),
(5,  'Algorithms',         'MAJOR'),
(6,  'Data Analysis',      'MAJOR'),
(7,  'English Speaking',   'LANGUAGE'),
(8,  'TOEIC',              'CERTIFICATION'),
(9,  'Coding Test',        'CAREER'),
(10, 'Team Project',       'MAJOR');

-- 4. 사용자 언어
INSERT INTO user_language (user_id, language) VALUES
(1,  'Korean'),
(2,  'Korean'),
(3,  'Korean'),
(4,  'Korean'),
(5,  'Korean'),
(6,  'English'),
(6,  'Chinese'),
(7,  'English'),
(9,  'English'),
(10, 'Korean');

-- 5. 사용자 태그
INSERT INTO user_tag (user_id, tag_id) VALUES
(1,  1),
(1,  5),
(2,  3),
(2,  9),
(3,  2),
(3,  7),
(4,  4),
(4,  8),
(5,  6),
(6,  2);

-- 6. 스터디 (current_member_count 를 정확한 값으로 명시 — D-003)
INSERT INTO study (id, title, purpose, max_members, current_member_count, activity_cycle, status) VALUES
(1,  'Algorithms Study Group',    'Solve and review algorithm problems for coding tests',            6, 3, 'Once a week (Monday)',    'OPEN'),
(2,  'TOEIC Study Group',         'Target TOEIC 850+, intensive study by section',                  5, 2, 'Twice a week (Tue/Thu)',  'OPEN'),
(3,  'English Speaking Group',    'Improve conversational English; international students welcome',  5, 3, 'Once a week (Wednesday)', 'OPEN'),
(4,  'Certification Study Group', 'Pass the Engineer Information Processing written and practical',   6, 2, 'Twice a week (Mon/Fri)',  'OPEN'),
(5,  'Data Analysis Group',       'Data analysis and visualization practice with Python/R',          5, 1, 'Once a week (Saturday)',  'OPEN'),
(6,  'Coding Test Group',         'Solve and discuss coding test problems for major companies',      8, 1, 'Once a week (Sunday)',    'OPEN'),
(7,  'Team Project Group',        'Build portfolio projects and prepare presentations',              4, 0, 'Twice a week (Tue/Thu)',  'CANCELLED'),
(8,  'Job Preparation Group',     'Resume review, mock interviews, and guest lecture sessions',      6, 1, 'Once a week (Tuesday)',   'OPEN'),
(9,  'CS Deep Dive Group',        'In-depth study of OS and networking fundamentals',                5, 1, 'Once a week (Thursday)',  'OPEN'),
(10, 'Language Exchange Group',   'Korean-English language exchange; international students welcome', 6, 1, 'Once a week (Wednesday)', 'CLOSED');

-- 7. 스터디 태그
INSERT INTO study_tag (study_id, tag_id) VALUES
(1,  5),
(1,  9),
(2,  8),
(2,  2),
(3,  7),
(3,  2),
(4,  4),
(5,  6),
(6,  9),
(8,  3);

-- 8. 멤버 기록
--   (a) 조장: 기존 LEADER 9건
--   (b) ACCEPTED 신청에 의해 발생한 일반 멤버: id 10~15 (이전 트리거가 자동 생성하던 부분)
INSERT INTO study_member (id, study_id, user_id, role_code, joined_at, is_active) VALUES
-- 조장
(1,  1,  1, 'LEADER', '2026-03-01 09:00:00', 1),
(2,  2,  2, 'LEADER', '2026-03-01 10:00:00', 1),
(3,  3,  3, 'LEADER', '2026-03-01 11:00:00', 1),
(4,  4,  4, 'LEADER', '2026-03-01 12:00:00', 1),
(5,  5,  5, 'LEADER', '2026-03-01 13:00:00', 1),
(6,  6,  1, 'LEADER', '2026-03-02 09:00:00', 1),
(7,  8,  3, 'LEADER', '2026-03-02 10:00:00', 1),
(8,  9,  4, 'LEADER', '2026-03-02 11:00:00', 1),
(9,  10, 5, 'LEADER', '2026-03-02 12:00:00', 1),
-- 일반 멤버 (신청 ACCEPTED 처리 결과)
(10, 1,  3, 'MEMBER', '2026-03-06 09:00:00', 1),  -- application id 2
(11, 1,  9, 'MEMBER', '2026-03-06 09:30:00', 1),  -- application id 3
(12, 2,  4, 'MEMBER', '2026-03-04 08:30:00', 1),  -- application id 6
(13, 3,  5, 'MEMBER', '2026-03-04 08:00:00', 1),  -- application id 7
(14, 3,  6, 'MEMBER', '2026-03-04 08:10:00', 1),  -- application id 8
(15, 4,  7, 'MEMBER', '2026-03-05 11:00:00', 1);  -- application id 9

-- 9. 신청
INSERT INTO application
(id, study_id, applicant_id, status, message, applied_at, processed_at, processed_by_member_id, reject_reason) VALUES
(1,  1,  8,  'PENDING',  'I want to practice algorithms every week.', '2026-03-07 14:00:00', NULL, NULL, NULL),
(2,  1,  3,  'ACCEPTED', 'I can solve BFS and DFS problems.',        '2026-03-05 15:00:00', '2026-03-06 09:00:00', 1, NULL),
(3,  1,  9,  'ACCEPTED', 'I am preparing for coding tests.',          '2026-03-05 16:00:00', '2026-03-06 09:30:00', 1, NULL),
(4,  2,  7,  'REJECTED', 'I want to join TOEIC study.',               '2026-03-04 13:00:00', '2026-03-05 10:00:00', 2, 'The study is focused on current textbook users this month.'),
(5,  2,  10, 'PENDING',  'I want to prepare TOEIC.',                  '2026-03-08 16:00:00', NULL, NULL, NULL),
(6,  2,  4,  'ACCEPTED', 'I can attend Tuesday and Thursday.',         '2026-03-03 11:00:00', '2026-03-04 08:30:00', 2, NULL),
(7,  3,  5,  'ACCEPTED', 'I want to practice English speaking.',       '2026-03-03 10:00:00', '2026-03-04 08:00:00', 3, NULL),
(8,  3,  6,  'ACCEPTED', 'I can help with English conversation.',      '2026-03-03 10:30:00', '2026-03-04 08:10:00', 3, NULL),
(9,  4,  7,  'ACCEPTED', 'I am preparing for certification exam.',     '2026-03-04 09:00:00', '2026-03-05 11:00:00', 4, NULL),
(10, 5,  10, 'PENDING',  'I want to learn pandas and visualization.',  '2026-03-10 17:00:00', NULL, NULL, NULL);

-- 10. 알림
INSERT INTO notification
(receiver_id, type, message, is_read, ref_study_id, ref_application_id) VALUES
(1,  'APPLICATION_SUBMITTED', 'New applicant Hana Moon has applied to your Algorithms Study Group.',     0, 1, 1),
(3,  'APPLICATION_ACCEPTED',  'Your application to Algorithms Study Group has been accepted.',           1, 1, 2),
(9,  'APPLICATION_ACCEPTED',  'Your application to Algorithms Study Group has been accepted.',           1, 1, 3),
(2,  'APPLICATION_SUBMITTED', 'New applicant George Chen has applied to your TOEIC Study Group.',         1, 2, 4),
(7,  'APPLICATION_REJECTED',  'Your application to TOEIC Study Group has been rejected.',                 0, 2, 4),
(2,  'APPLICATION_SUBMITTED', 'New applicant Julia Han has applied to your TOEIC Study Group.',           0, 2, 5),
(4,  'APPLICATION_ACCEPTED',  'Your application to TOEIC Study Group has been accepted.',                 1, 2, 6),
(5,  'APPLICATION_ACCEPTED',  'Your application to English Speaking Group has been accepted.',            1, 3, 7),
(6,  'APPLICATION_ACCEPTED',  'Your application to English Speaking Group has been accepted.',            1, 3, 8),
(7,  'APPLICATION_ACCEPTED',  'Your application to Certification Study Group has been accepted.',         1, 4, 9);

-- 11. 출석 세션
INSERT INTO attendance_session
(id, study_id, session_num, session_date, title, created_by_member_id) VALUES
(1,  1, 1, '2026-03-10', 'Algorithms Session 1',       1),
(2,  1, 2, '2026-03-17', 'Algorithms Session 2',       1),
(3,  2, 1, '2026-03-11', 'TOEIC Session 1',            2),
(4,  2, 2, '2026-03-18', 'TOEIC Session 2',            2),
(5,  3, 1, '2026-03-12', 'English Speaking Session 1', 3),
(6,  3, 2, '2026-03-19', 'English Speaking Session 2', 3),
(7,  4, 1, '2026-03-13', 'Certification Session 1',    4),
(8,  4, 2, '2026-03-20', 'Certification Session 2',    4),
(9,  5, 1, '2026-03-14', 'Data Analysis Session 1',    5),
(10, 5, 2, '2026-03-21', 'Data Analysis Session 2',    5);

-- 12. 출석 기록
INSERT INTO attendance_record (study_id, session_id, member_id, status, note) VALUES
(1, 1, 1,  'PRESENT', 'Leader attended'),
(1, 1, 10, 'PRESENT', 'Member attended'),
(1, 1, 11, 'LATE',    'Arrived 10 minutes late'),
(1, 2, 1,  'ABSENT',  'Personal reason'),
(2, 3, 2,  'PRESENT', 'Leader attended'),
(2, 3, 12, 'LATE',    'Traffic delay'),
(3, 5, 3,  'PRESENT', 'Leader attended'),
(3, 5, 13, 'PRESENT', 'Member attended'),
(4, 7, 4,  'PRESENT', 'Leader attended'),
(4, 7, 15, 'ABSENT',  'No notice');

-- 13. 게시글
INSERT INTO post (id, study_id, author_member_id, type, title, content) VALUES
(1,  1, 1,  'NOTICE', '[Week 1] Algorithms Study Notice', 'This week: 3 BFS/DFS basic problems. Attach your solution file before attending.'),
(2,  1, 10, 'FREE',   'Question about recursive approach', 'Why does solving BFS with recursion cause a stack overflow? Any tips?'),
(3,  2, 2,  'NOTICE', '[Session 1] TOEIC Part 7 Focus Notice', 'Mandatory pre-study: Part 7 long passages.'),
(4,  2, 12, 'FREE',   'High-score vocabulary list', 'I compiled 50 frequently tested business words.'),
(5,  3, 3,  'NOTICE', '[Session 1] English Speaking Topic', 'Topic: Introducing yourself. Prepare a 3-minute script.'),
(6,  3, 13, 'FREE',   'Looking for a speaking practice partner', 'Anyone want to practice before the session?'),
(7,  4, 4,  'NOTICE', '[Session 1] Certification Study Schedule', 'Pre-study required: Module 1 exam scope.'),
(8,  4, 15, 'FREE',   'Recommended textbook for the exam', 'The OOO publisher textbook has many past exam questions.'),
(9,  5, 5,  'NOTICE', '[Session 1] Data Analysis: pandas basics', 'Install Jupyter Notebook beforehand.'),
(10, 5, 5,  'FREE',   'Question about groupby', 'How do I apply multiple aggregation functions after groupby?');

-- 14. 댓글
INSERT INTO post_comment (study_id, post_id, author_member_id, content) VALUES
(1, 1, 10, 'Got it! I will solve the problems before coming.'),
(1, 1, 1,  'If everyone prepares in advance, we can have a deeper discussion.'),
(1, 2, 1,  'It is due to recursion depth. I recommend iterative BFS.'),
(2, 3, 12, 'Will bring my textbook. Thanks for the heads-up!'),
(2, 4, 2,  'Downloaded the vocabulary file. Really useful.'),
(3, 5, 13, 'Yes, I will prepare my script and come ready.'),
(3, 6, 3,  'I want to practice too. Shall we meet 30 minutes earlier?'),
(4, 7, 15, 'Noted the schedule. I will pre-study the material.'),
(5, 9, 5,  'Jupyter Notebook is installed and ready.'),
(5, 10, 5, 'You can use agg() to apply multiple aggregations.');

-- 15. 게시글/댓글 관련 알림
INSERT INTO notification
(receiver_id, type, message, is_read, ref_study_id, ref_post_id, ref_comment_id) VALUES
(1,  'COMMENT_REPLY', 'Charlie commented on your notice.', 0, 1, 1, 1),
(10, 'STUDY_NOTICE',  'New data analysis notice was posted.', 0, 5, 9, NULL);
