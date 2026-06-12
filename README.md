# Aegis

방어형 웹 보안 시스템 (Spring Boot 3 / Java 21).
무차별 대입, SQL 인젝션, XSS, 요청 폭주로부터 웹 애플리케이션을 보호한다.

## 실행
```bash
./gradlew bootRun
# 확인: http://localhost:8080/health
```

## 구조
- `auth` 인증/JWT/계정 잠금
- `detection` 무차별 대입 탐지 · IP 차단 · 레이트 리미팅
- `security` 보안 설정 · 필터 · 헤더
- `audit` 보안 이벤트 감사 로그
- `dashboard` 모니터링 API · 관리자 화면

> 개발 단계별 진행 상황은 `CLAUDE.md` 참고.
