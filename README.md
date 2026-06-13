# Aegis

방어형(defensive) 웹 보안 시스템 — **Spring Boot 3.3 / Java 21 / Spring Security 6**.
무차별 대입(brute-force), 요청 폭주, SQLi/XSS 등으로부터 웹 애플리케이션을 **보호·탐지·기록**한다.
(공격/침투 도구가 아니라 방어·탐지·감사 전용)

## 기술 스택
- Java 21, Spring Boot 3.3, Spring Security 6
- Spring Data JPA, Flyway(스키마 관리), H2(dev) / PostgreSQL(prod)
- Bucket4j(레이트 리미팅), Actuator + Micrometer/Prometheus, springdoc-openapi
- Gradle (wrapper 포함)

## 진행 현황
> 실제 구현 순서는 **P0 → P1 → P3 → P4**이고, **P2(인증)는 의도적으로 뒤로 미뤘다.**
> 단계 번호는 커밋 메시지(git log)와 일치시키기 위해 그대로 둔다.

| 단계 | 내용 | 상태 |
|---|---|:---:|
| P0 | 프로젝트 골격 | ✅ |
| P1 | 프로덕션 기반 인프라(프로파일·Flyway·Actuator/Prometheus·JSON 로깅·OpenAPI·전역 예외) | ✅ |
| P2 | 인증(User·BCrypt(12)·회원가입/로그인·JWT Access+Refresh·계정 잠금·권한) | ✅ |
| P3 | 탐지(무차별 대입 차단·IP 블록·레이트 리미팅·감사) | ✅ |
| P4 | 방어(보안 헤더·CSRF·입력검증·SQLi/XSS 점검·AuditLog) | ✅ |
| P5 | 대시보드(통계 API·관리자 화면) | ⬜ 예정 |
| P6 | 마무리(테스트 보강·README 한/영/일) | ⬜ 예정 |

## 구현된 기능 (현재)
- **인증/인가**: 회원가입·로그인 REST API, 비밀번호 BCrypt(strength 12) 해싱, JWT Access+Refresh 토큰(만료/갱신), 로그인 5회 실패 시 계정 15분 잠금, 역할 기반 접근(ROLE_USER/ROLE_ADMIN).
- **프로파일 분리**: dev(H2) / prod(PostgreSQL, 접속정보·시크릿은 환경변수). 스키마는 Flyway 전용, JPA는 `ddl-auto=validate`.
- **모니터링**: Actuator는 `health/info/prometheus`만 노출 + 인증 필요. 비인증 공개 헬스체크는 `/health`.
- **API 문서**: springdoc-openapi (`/swagger-ui.html`).
- **일관 에러 응답**: `@RestControllerAdvice`(`ErrorResponse`: timestamp/status/code/message), 스택트레이스·내부 메시지 비노출.
- **탐지**: 로그인 실패를 IP 단위로 집계 → 임계치(기본 10회/10분) 초과 시 자동 차단(403)·자동 만료, Bucket4j로 IP당 분당 60회 레이트 리밋(429). 예외 IP 화이트리스트, 레이트리미터 추상화(분산 환경 대비), 탐지/차단 Micrometer 메트릭(`/actuator/prometheus`).
- **방어**: 보안 헤더(CSP·X-Frame-Options·HSTS·X-Content-Type-Options·Referrer-Policy), CSRF 활성화, 입력 화이트리스트(미정의 JSON 필드 거부).
- **감사(AuditLog)**: 보안 이벤트(로그인 성공/실패·IP 차단·레이트리밋·권한거부)를 **시각/IP/이벤트유형/결과**로 콘솔과 DB에 기록.
- **SQL**: 전부 JPA 파라미터 바인딩(원시 SQL/문자열 결합 없음). 정책: [docs/SECURITY.md](docs/SECURITY.md).

## 빌드 · 실행
> **빌드에는 JDK 21 권장.** 시스템 JDK가 22+ 뿐이면 Gradle 8.7이 구동되지 않으니 JDK 21을 설치해 사용한다.

```bash
# 개발 실행 (H2)
./gradlew bootRun
# 확인: GET http://localhost:8080/health  ->  {"status":"UP"}

# 테스트
./gradlew test

# 운영 실행 (PostgreSQL) — 환경변수 주입 필요
java -jar build/libs/*.jar --spring.profiles.active=prod
```

운영 환경변수: `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` `AEGIS_JWT_SECRET`

## 주요 엔드포인트
| 메서드 | 경로 | 접근 |
|---|---|---|
| GET | `/health` | 공개 |
| POST | `/api/auth/signup` `/api/auth/login` `/api/auth/refresh` | 공개 |
| GET | `/api/me` | 인증 필요(Bearer) |
| GET | `/api/admin/ping` | ROLE_ADMIN |
| GET | `/actuator/health` `/actuator/info` `/actuator/prometheus` | 인증 필요 |
| GET | `/swagger-ui.html` | API 문서 |

## 패키지 구조 (`com.aegis`)
- `auth` 인증/JWT/계정 잠금/권한
- `detection` 무차별 대입 탐지 · IP 차단 · 레이트 리미팅
- `security` 보안 설정 · 필터 · 헤더 · CSRF
- `audit` 보안 이벤트 감사 로그(AuditLog)
- `common` 공통(전역 예외처리, 에러 응답 DTO)
- `dashboard` *(예정)* 모니터링 API · 관리자 화면

## 문서
- 보안 정책(입력검증/출력인코딩/SQL/헤더/감사): [docs/SECURITY.md](docs/SECURITY.md)
- 개발 컨텍스트·단계 로드맵: [CLAUDE.md](CLAUDE.md)
