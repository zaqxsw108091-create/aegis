-- V5__audit_log_actor.sql
-- 감사 로그 강화: 행위자(actor) 컬럼 추가.
-- audit_log 는 append-only(불변) 정책 — INSERT/SELECT 만 사용한다.
-- H2(dev)/PostgreSQL(prod) 양쪽에서 동작하는 표준 SQL.

ALTER TABLE audit_log ADD COLUMN actor VARCHAR(100);
