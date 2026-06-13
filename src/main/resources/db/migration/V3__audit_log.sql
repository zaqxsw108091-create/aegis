-- V3__audit_log.sql
-- 방어 모듈(P4): security_event 테이블을 audit_log 로 통합 승격하고 result(결과) 컬럼 추가.
-- H2(dev)/PostgreSQL(prod) 양쪽에서 동작하는 표준 SQL 사용.

ALTER TABLE security_event RENAME TO audit_log;
ALTER TABLE audit_log ADD COLUMN result VARCHAR(20);
