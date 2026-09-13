# Aegis 보안 정책 (방어 정책 요약)

방어형 웹 보안 시스템으로서 Aegis가 적용하는 입력 검증 / 출력 인코딩 / SQL / 헤더 / 감사 정책을 정리한다.

## 1. 입력 검증 (화이트리스트 원칙)
- 모든 요청 DTO는 Bean Validation(`@Valid` + `@NotNull/@Size/@Pattern` 등)으로 검증한다.
  허용하는 값만 통과시키는 **화이트리스트** 방식을 기본으로 한다(블랙리스트 금지).
- 예상치 못한 JSON 필드는 거부한다: `spring.jackson.deserialization.fail-on-unknown-properties=true`.
- 검증 실패는 `GlobalExceptionHandler`가 일관된 `ErrorResponse`(400, `VALIDATION_ERROR`)로 응답하며,
  어떤 필드가 왜 틀렸는지만 알려주고 내부 구현은 노출하지 않는다.

## 2. 출력 인코딩 (XSS 방지)
- **JSON API**: 응답은 Jackson이 JSON으로 직렬화하며 `Content-Type: application/json` 으로 내려간다.
  브라우저가 HTML로 해석하지 않도록 `X-Content-Type-Options: nosniff` 를 강제한다.
- **HTML(Thymeleaf)**: 출력은 기본 이스케이프(`th:text`)만 사용한다.
  신뢰할 수 없는 데이터에 `th:utext`(미이스케이프)를 **절대 사용하지 않는다**.
- **방어선(defense-in-depth)**: CSP(`default-src 'self'`, `object-src 'none'` 등)로
  인라인/외부 스크립트 실행을 제한한다.
- URL/속성/JS 컨텍스트에 데이터를 넣을 때는 각 컨텍스트에 맞는 인코딩을 사용한다.

## 3. SQL (인젝션 방지)
- 모든 DB 접근은 **Spring Data JPA 파생 쿼리 / 파라미터 바인딩**만 사용한다.
- 문자열로 SQL을 직접 조합하지 않는다. 네이티브 쿼리(`createNativeQuery`),
  `JdbcTemplate` 문자열 결합, 동적 `@Query` 문자열 결합을 금지한다.
- 현재 코드 점검 결과: 원시 SQL/문자열 결합 **사용 없음**(전부 파라미터 바인딩).

## 4. 보안 헤더 / CSRF
- `Content-Security-Policy`: `default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'`
- `X-Frame-Options: DENY` (클릭재킹 방지)
- `Strict-Transport-Security`: `max-age=31536000; includeSubDomains; preload` (HTTPS 요청)
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: no-referrer`
- **CSRF 활성화**: 쿠키 기반 토큰 저장소. 상태 변경 요청(POST/PUT/DELETE)은 유효한 CSRF 토큰을 요구한다.

## 5. 감사 로깅 (AuditLog)
- 보안 이벤트를 `audit_log` 테이블과 콘솔에 동시에 기록한다.
- 기록 항목: **시각(createdAt) / 행위자(actor) / IP / 이벤트유형(type) / 결과(result)** + 부가 detail.
- 이벤트유형: `LOGIN_SUCCESS, LOGIN_FAILURE, IP_BLOCKED, RATE_LIMITED, ACCESS_DENIED, TOKEN_REUSE, LOGOUT, IP_UNBLOCKED, ACCOUNT_UNLOCKED`
- 결과: `SUCCESS, FAILURE, BLOCKED, DENIED`
- **append-only(변경 불가) 정책**: 엔티티는 `@Immutable` + setter 없음으로 INSERT 후 수정 불가
  (Hibernate가 UPDATE SQL을 발행하지 않음). 애플리케이션 코드는 감사 로그를 수정/삭제하지 않는다.
- **비밀번호/토큰/시크릿은 감사 로그·콘솔에 절대 기록하지 않는다.** (actor 에는 사용자명만, 비밀번호 금지)

## 6. 탐지/차단 (요약, 상세는 detection 모듈)
- IP별 로그인 실패 집계 → 임계치 초과 시 자동 차단(403).
- IP별 요청 레이트 리미팅(429).

## 7. 도구 자체 보안 점검(self-review)
- **시크릿 하드코딩 없음**: JWT 시크릿(`AEGIS_JWT_SECRET`), DB 접속정보는 prod에서 환경변수(`${...}`)로만
  주입. 소스/깃에 평문 시크릿 없음(`gradle.properties`는 머신 경로용이며 gitignore).
- **Actuator 보호**: `/actuator/**` 인증 필요, `health/info/prometheus` 만 노출. 비인증 공개는 커스텀 `/health` 만.
- **에러 민감정보 비노출**: 전역/인증 예외 핸들러가 스택트레이스·내부 메시지를 응답에 담지 않고 일반 메시지로 응답.
