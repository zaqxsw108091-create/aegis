# Aegis - 프로젝트 컨텍스트 (Claude Code용)

## 목적
방어형(defensive) 웹 보안 시스템. 내 애플리케이션을 다음 공격으로부터 보호한다:
무차별 대입(brute-force), SQL 인젝션, XSS, CSRF, 요청 폭주(DoS성 트래픽).
※ 공격/침투 도구는 만들지 않는다. 방어·탐지·기록만 구현한다.

## 기술 스택
- Java 21, Spring Boot 3.3, Spring Security 6
- Spring Data JPA + H2(개발), JWT(jjwt 0.12), Bucket4j(레이트 리미팅)
- 빌드: Gradle (wrapper 포함)

## 패키지 구조 (com.aegis)
- auth       : 회원/로그인/JWT/계정 잠금
- detection  : 무차별 대입 탐지, IP 차단, 레이트 리미팅
- security   : Spring Security 설정, 보안 필터/헤더, CSRF
- audit      : 보안 이벤트 감사 로그
- dashboard  : 모니터링 API + 관리자 화면

## 절대 규칙 (보안)
1. 비밀번호는 항상 BCrypt/Argon2 해싱. 평문 저장·평문 로그 금지.
2. 모든 DB 접근은 JPA/파라미터 바인딩만. 문자열로 SQL을 직접 조합하지 않는다.
3. 시크릿(JWT secret, 비밀번호 등)은 환경변수로. 소스/깃에 평문 커밋 금지.
4. 사용자 입력은 @Valid + 화이트리스트 검증. 출력은 인코딩(XSS 방지).
5. 새 기능마다 단위 테스트를 작성하고 통과를 확인한 뒤 커밋한다.

## 빌드/실행 명령
- 실행:   ./gradlew bootRun
- 테스트: ./gradlew test
- 빌드:   ./gradlew build
- 헬스체크: GET http://localhost:8080/health  -> {"status":"UP"}

## 설정 키 (application.yml 의 aegis.security.*)
- jwt.secret / jwt.expiration-minutes
- lockout.max-failed-attempts(5) / lock-minutes(15)
- rate-limit.requests-per-minute(60)
- bruteforce.ip-fail-threshold(10) / ip-block-minutes(10)

## 작업 방식
- 한 번에 한 단계(phase)만 구현한다.
- 단계 완료 = 코드 + 테스트 통과 + 커밋.
- 커밋 메시지는 한국어로 간결하게 (예: "P1: 인증/JWT 구현").

## 빌드 단계 로드맵
- [x] P0 골격 (현재 상태)
- [ ] P1 인증: User 엔티티, 해싱, 회원가입/로그인, JWT, 계정 잠금
- [ ] P2 탐지: 무차별 대입 탐지, IP 자동 차단, Bucket4j 레이트 리미팅
- [ ] P3 방어: 보안 헤더, CSRF, 입력검증 강화, SQLi/XSS 점검, 감사 로그
- [ ] P4 대시보드: 보안 이벤트/차단 IP/실패 통계 API + 관리자 화면
- [ ] P5 마무리: 테스트 보강, README(한/영/일 요약)
