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
- common     : 공통 인프라(전역 예외처리, 에러 응답 DTO 등 횡단 관심사)

## 절대 규칙 (보안)
1. 비밀번호는 항상 BCrypt/Argon2 해싱. 평문 저장·평문 로그 금지.
2. 모든 DB 접근은 JPA/파라미터 바인딩만. 문자열로 SQL을 직접 조합하지 않는다.
3. 시크릿(JWT secret, 비밀번호 등)은 환경변수로. 소스/깃에 평문 커밋 금지.
4. 사용자 입력은 @Valid + 화이트리스트 검증. 출력은 인코딩(XSS 방지).
5. 새 기능마다 단위 테스트를 작성하고 통과를 확인한 뒤 커밋한다.

## 프로덕션 규칙 (운영 기반)
1. 프로파일: dev(H2, 기본 활성) / prod(PostgreSQL). prod의 접속정보·시크릿은
   환경변수(${...})로만 주입한다. 평문 커밋 금지.
2. 스키마는 Flyway로만 관리한다. JPA는 ddl-auto=validate 고정(자동 생성/변경 금지).
   스키마 변경은 src/main/resources/db/migration/V{n}__설명.sql 추가로만. H2/PostgreSQL 양립 SQL 사용.
3. 모니터링: Actuator는 health/info/prometheus 만 노출하고 인증을 요구한다(/actuator/**).
   비인증 공개 헬스체크는 커스텀 /health 만.
4. 로깅: prod는 JSON 구조적 로그(logstash-logback-encoder). 비밀번호·토큰·시크릿은
   로그와 에러 응답에 절대 남기지 않는다(로그는 마스킹으로 1차 방어).
5. 에러 응답: @RestControllerAdvice 단일 포맷(ErrorResponse: timestamp/status/code/message).
   스택트레이스·내부 예외 메시지 노출 금지.
6. API 문서: springdoc-openapi. /swagger-ui.html, /v3/api-docs 공개(운영 노출 범위는 추후 제한 가능).

## 환경변수 (prod)
- DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD
- AEGIS_JWT_SECRET (필수, 기본값 없음)

## 빌드/실행 명령
- 실행(dev):  ./gradlew bootRun
- 실행(prod): java -jar build/libs/*.jar --spring.profiles.active=prod   (환경변수 주입 필요)
- 테스트: ./gradlew test
- 빌드:   ./gradlew build
- 헬스체크: GET http://localhost:8080/health  -> {"status":"UP"}
- 메트릭:  GET http://localhost:8080/actuator/prometheus  (인증 필요)
- API 문서: http://localhost:8080/swagger-ui.html

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
> 진행 순서 참고: 실제로는 P0→P1→P3(탐지)→P4(방어) 순으로 먼저 구현했고,
> P2(인증)는 의도적으로 뒤로 미뤘다. 번호는 커밋 메시지와 맞추기 위해 그대로 둔다.
- [x] P0 골격
- [x] P1 프로덕션 기반 인프라: 프로파일 분리, Flyway, Actuator/Prometheus, JSON 로깅, OpenAPI, 전역 예외처리
- [x] P2 인증: User 엔티티, BCrypt(12) 해싱, 회원가입/로그인 REST API, JWT(Access+Refresh), 계정 잠금(5회/15분), 권한(ROLE_USER/ROLE_ADMIN)
- [x] P3 탐지: 무차별 대입 탐지(인증 실패 이벤트 집계), IP 자동 차단(403)·자동 만료, Bucket4j 레이트 리미팅(429), IP 화이트리스트(면제), 레이트리미터 추상화(분산 대비), Micrometer 메트릭(/actuator/prometheus), 보안 이벤트 감사 로그(콘솔+DB)
- [x] P4 방어: 보안 헤더(CSP/X-Frame-Options/HSTS 등), CSRF 활성화, 입력검증 강화(화이트리스트), SQLi/XSS 점검, 감사 로그(AuditLog). 정책 문서: docs/SECURITY.md
- [x] P5 대시보드: 관리자(ROLE_ADMIN) 전용 모니터링 REST API(이벤트/차단IP/통계/메트릭) + Thymeleaf 관리자 화면
- [ ] P6 마무리: 테스트 보강, README(한/영/일 요약)
