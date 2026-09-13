# Aegis `v0.1.0`

방어형(defensive) 웹 보안 시스템 — **Spring Boot 3.3 / Java 21 / Spring Security 6**.
무차별 대입, 요청 폭주, SQLi/XSS 등으로부터 웹 애플리케이션을 **보호·탐지·기록**한다.
(공격/침투 도구가 아니라 방어·탐지·감사 전용 — 범위와 한계는 [SECURITY.md](SECURITY.md) 참고)

---

## 개요
Aegis는 인증, 침입 탐지, 레이트 리미팅, 보안 헤더, 감사 로그, 관리자 모니터링을 한 번에 제공하는
참조용 방어 시스템이다. 운영 기반(프로파일 분리·Flyway·Actuator/Prometheus·구조적 로깅·OpenAPI)을
먼저 갖추고, 그 위에 인증/탐지/방어/대시보드 기능을 단계적으로 쌓았다.

## 방어 대상 공격 유형
| 공격 | 방어 방식 |
|---|---|
| 무차별 대입(brute-force) | 로그인 실패를 IP·계정 단위로 집계 → IP 자동 차단(403) + 계정 잠금(5회/15분) |
| 요청 폭주(DoS성) | Bucket4j IP당 분당 레이트 리미팅(429), 화이트리스트 예외 |
| SQL 인젝션 | 전 구간 JPA 파라미터 바인딩(원시 SQL/문자열 결합 금지) |
| XSS | 출력 인코딩(Thymeleaf 자동 이스케이프) + CSP, `X-Content-Type-Options: nosniff` |
| CSRF | 쿠키 기반 토큰(상태 기반 엔드포인트 보호), 무상태 JWT API는 예외 |
| 클릭재킹 | `X-Frame-Options: DENY`, CSP `frame-ancestors 'none'` |
| 자격증명 노출 | 비밀번호 BCrypt(12) 해싱, 시크릿 환경변수화, 로그·에러 민감정보 마스킹/비노출 |

## 아키텍처 (요청 흐름)
```
            ┌────────────────────────── Security Filter Chain ──────────────────────────┐
HTTP 요청 → │ IpBlockFilter → RateLimitFilter → JwtAuthenticationFilter → CSRF → 인가    │ → Controller
            │  (차단 403)      (한도 429)        (Bearer 검증)         (토큰)  (역할)     │
            └───────────────────────────────────────────────────────────────────────────┘
                        │                 │                                   │
                        ▼                 ▼                                   ▼
                   BlockedIp         RateLimiter(추상화)                 auth / dashboard
                   (DB, 자동만료)     InMemory(Bucket4j)                  Service 계층
                        │                                                     │
                        └──────────────► AuditLog (append-only) ◄─────────────┘
                                          + Micrometer 메트릭 → /actuator/prometheus

패키지: auth · detection · security · audit · dashboard · common
프로파일: dev(H2) / prod(PostgreSQL) · 스키마는 Flyway 전용(ddl-auto=validate)
```

## 기술 스택
- Java 21, Spring Boot 3.3, Spring Security 6
- Spring Data JPA, Flyway, H2(dev) / PostgreSQL(prod)
- JWT(jjwt 0.12), Bucket4j(레이트 리미팅)
- Actuator + Micrometer/Prometheus, springdoc-openapi, logstash-logback-encoder(JSON 로그)
- Gradle(wrapper), JaCoCo(커버리지), Docker(멀티스테이지) + docker-compose

## 진행 현황
| 단계 | 내용 | 상태 |
|---|---|:---:|
| P0 | 프로젝트 골격 | ✅ |
| P1 | 프로덕션 기반 인프라(프로파일·Flyway·Actuator/Prometheus·JSON 로깅·OpenAPI·전역 예외) | ✅ |
| P2 | 인증(User·BCrypt12·회원가입/로그인·JWT Access+Refresh·계정 잠금·권한) | ✅ |
| P3 | 탐지(무차별 대입 차단·IP 블록·레이트 리미팅·화이트리스트·추상화·메트릭·감사) | ✅ |
| P4 | 방어(보안 헤더·CSRF·입력검증·SQLi/XSS 점검·AuditLog 행위자/append-only) | ✅ |
| P5 | 대시보드(관리자 전용 모니터링 API·관제 콘솔 화면·메트릭 연동·**수동 IP 차단/해제·계정 잠금 해제·사용자별 실패 횟수**) | ✅ |
| P6 | 릴리스 0.1.0(테스트/커버리지·Docker·배포 가이드·문서 정비) | ✅ |
| P7 | CI/품질(GitHub Actions 빌드+테스트, OWASP dependency-check 취약점 스캔) | ✅ |
| P8 | 토큰 수명주기(Refresh 회전·재사용 감지 시 전체 폐기·로그아웃) | ✅ |
| P9 | 보안 알림(IP 차단/토큰 재사용 → 웹훅 통지, 기본 비활성) | ✅ |

> 후속 과제(선택): Spotless/Checkstyle, 분산 레이트리밋(Redis), 2FA(TOTP), 대시보드 그래프/수동 차단.

## 빌드 · 실행 (개발)
> **빌드에는 JDK 21 권장.** 시스템 JDK가 22+ 뿐이면 Gradle 8.7이 구동되지 않으니 JDK 21을 설치해 사용한다.
```bash
./gradlew bootRun                 # 개발 실행 (H2, dev 프로파일)
# 확인: GET http://localhost:8080/health  ->  {"status":"UP"}
./gradlew build                   # 빌드 + 테스트 (+ JaCoCo 리포트: build/reports/jacoco)
java -jar build/libs/aegis-0.1.0.jar   # fat jar 단독 실행
```

## 배포
### A) Docker Compose (app + PostgreSQL)
```bash
# 운영 시크릿을 환경변수로 주입(필수)
export AEGIS_JWT_SECRET="<32바이트 이상의 강한 시크릿>"
export DB_PASSWORD="<강한 DB 비밀번호>"
docker compose up --build
# app 은 prod 프로파일로 기동되어 postgres 에 연결된다. http://localhost:8080/health
```
### B) 단독 jar (외부 PostgreSQL)
```bash
java -jar build/libs/aegis-0.1.0.jar \
  --spring.profiles.active=prod
# 아래 환경변수를 미리 주입해야 한다.
```

## 환경 변수 (prod)
| 변수 | 설명 |
|---|---|
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | PostgreSQL 접속 정보 |
| `AEGIS_JWT_SECRET` | JWT 서명 시크릿(32바이트 이상, 기본값 없음 — 필수) |
| `AEGIS_ALERT_WEBHOOK` | (선택) IP 차단/토큰 재사용 알림 웹훅 URL. 비우면 알림 비활성 |

## 운영 시 주의
- **dev 전용 기능은 prod에서 반드시 비활성**: H2 콘솔(dev만 enabled), dev 기본 JWT 시크릿은
  prod에서 `AEGIS_JWT_SECRET`로 **반드시 교체**(기본값으로 기동 금지).
- Actuator는 `health/info/prometheus`만 노출하고 인증을 요구한다. 비인증 공개는 커스텀 `/health`만.
- `ddl-auto=validate` 고정 — 스키마 변경은 `src/main/resources/db/migration/V{n}__*.sql` 추가로만.
- **관리자 IP는 `aegis.security.whitelist`에 넣어라.** 관리자 본인 IP가 무차별 대입 탐지에 걸려
  차단되면 대시보드도 403이 되어 스스로 해제할 수 없다(화이트리스트 IP는 차단/레이트리밋 면제).
- 관리자(ROLE_ADMIN) 계정은 시크릿 하드코딩을 피하기 위해 시드하지 않는다. 운영에서 DB로 특정
  사용자 role을 ADMIN으로 승격해 사용한다.
- 프록시 뒤 배포 시 `server.forward-headers-strategy`(prod 기본 적용)로 실제 클라이언트 IP를 인식.

## 주요 엔드포인트
| 메서드 | 경로 | 접근 |
|---|---|---|
| GET | `/health` | 공개 |
| POST | `/api/auth/signup` `/api/auth/login` `/api/auth/refresh` `/api/auth/logout` | 공개 |
| GET | `/api/me` | 인증(Bearer) |
| GET | `/api/admin/dashboard/{events,blocked-ips,stats,users}` | ROLE_ADMIN |
| GET | `/admin/dashboard` | ROLE_ADMIN (웹 화면) |
| POST | `/admin/blocked-ips` · `/admin/blocked-ips/unblock` · `/admin/users/unlock` | ROLE_ADMIN + CSRF (수동 차단/해제/잠금 해제) |
| GET | `/api/admin/ping` | ROLE_ADMIN (권한 확인용 샘플) |
| GET | `/actuator/health` `/info` `/prometheus` | 인증 |
| GET | `/swagger-ui.html` | API 문서 |

## 패키지 구조 (`com.aegis`)
`auth` 인증/JWT/계정잠금 · `detection` 탐지/차단/레이트리밋 · `security` 보안설정/필터/헤더 ·
`audit` 감사 로그(AuditLog) · `dashboard` 관리자 모니터링 · `common` 공통(전역 예외/에러 DTO)

## 문서
**사용법 (한국어)**
- 📘 **설치 → 실행 → 사용 설명서**: [docs/MANUAL.md](docs/MANUAL.md) ← 처음이라면 여기부터
- 🧭 전체 구조·UI 한눈에 보기: [docs/OVERVIEW.md](docs/OVERVIEW.md)
- 📚 기능 완전 정복(설명·발표용): [docs/STUDY_GUIDE.md](docs/STUDY_GUIDE.md)
- 🧪 기능 시험 레시피: [docs/TEST_GUIDE.md](docs/TEST_GUIDE.md)

**Usage (English)**
- 📗 **Install → run → use → tune**: [docs/USAGE.en.md](docs/USAGE.en.md)

**정책·개발**
- 보안 범위·한계·책임 있는 사용: [SECURITY.md](SECURITY.md)
- 보안 통제 정책(입력검증/출력인코딩/SQL/헤더/감사): [docs/SECURITY.md](docs/SECURITY.md)
- 개발 컨텍스트·단계 로드맵: [CLAUDE.md](CLAUDE.md)

---

## English (Summary)
**Aegis** is a defensive web-security reference system built with Spring Boot 3.3 / Java 21 /
Spring Security 6. It protects an application against brute-force, request flooding, SQLi and XSS by
combining: JWT authentication (access + refresh) with account lockout (5 fails / 15 min), per-IP
brute-force blocking and Bucket4j rate limiting (with an IP whitelist and a pluggable limiter
abstraction), security headers (CSP/HSTS/X-Frame-Options/...), CSRF protection, strict input
validation, an append-only **AuditLog** (timestamp / actor / IP / event type / result), Micrometer
metrics on `/actuator/prometheus`, and an admin-only monitoring dashboard.
It is **defense/detection/audit only — not an attack or penetration-testing tool**; scope and limits
are documented in [SECURITY.md](SECURITY.md).
Run locally with `./gradlew bootRun`; deploy with `docker compose up --build` (app + PostgreSQL,
secrets via env vars `AEGIS_JWT_SECRET`, `DB_PASSWORD`).

👉 **Full usage guide in English: [docs/USAGE.en.md](docs/USAGE.en.md)** — install, run, a 5-minute
API tour, how to watch each defense actually trigger, tuning reference, and integration notes.

## 日本語（要約）
**Aegis** は Spring Boot 3.3 / Java 21 / Spring Security 6 で構築した防御型 Web セキュリティの
リファレンス実装である。総当たり攻撃・リクエスト過多・SQLi・XSS から Web アプリを守るため、
JWT 認証（アクセス＋リフレッシュ）とアカウントロック（5回/15分）、IP 単位の総当たり遮断と
Bucket4j レート制限（ホワイトリスト・抽象化対応）、セキュリティヘッダ（CSP/HSTS/X-Frame-Options 等）、
CSRF 対策、入力検証、追記専用（append-only）の **監査ログ**（時刻・実行者・IP・種別・結果）、
`/actuator/prometheus` のメトリクス、管理者専用ダッシュボードを備える。
本ツールは**防御・検知・監査専用であり、攻撃や侵入テストは行わない**。範囲と限界は
[SECURITY.md](SECURITY.md) を参照。ローカルは `./gradlew bootRun`、配備は
`docker compose up --build`（app + PostgreSQL、シークレットは環境変数で注入）。
