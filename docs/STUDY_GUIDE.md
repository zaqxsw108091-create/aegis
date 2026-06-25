# Aegis 완전 학습서 (기능 정복 & 설명용)

> **목적**: 이 파일 하나로 Aegis의 모든 기능을 **이해하고, 직접 확인하고, 남에게 설명**할 수 있게 한다.
> 각 기능은 같은 틀로 정리한다 → **무엇 / 왜 / 어떻게 동작 / 직접 확인 / 코드 위치 / 한마디 설명**.
> 실습 명령은 [TEST_GUIDE.md](TEST_GUIDE.md), 한눈 요약은 [OVERVIEW.md](OVERVIEW.md) 참고.

---

## 0. 30초 설명 (엘리베이터 피치)
> "Aegis는 웹 서비스 앞에 두는 **소프트웨어 방패**입니다. 로그인과 요청을 감시하면서
> **무차별 대입·요청 폭주·SQL 인젝션·XSS·CSRF** 같은 공격을 막고(방어), 수상한 IP를 자동 차단하고(탐지),
> 모든 보안 사건을 **위조 불가능한 일지(감사 로그)** 에 남깁니다. 관리자는 대시보드로 현황을 봅니다.
> Spring Boot 3 / Java 21 로 만들었고, 인증은 JWT, 배포는 Docker로 합니다."

**핵심 철학 = 다층 방어(Defense in Depth)**: 한 겹이 뚫려도 다음 겹이 막는다.
(계정 잠금 + IP 차단 + 레이트리밋 + 입력검증 + 보안헤더 + 감사 …)

---

## 1. 먼저 알아둘 핵심 용어 (쉽게)
| 용어 | 쉬운 뜻 |
|---|---|
| **인증(Authentication)** | "너 누구야?" — 로그인으로 신원 확인 |
| **인가(Authorization)** | "넌 여기 들어와도 돼?" — 권한 확인 (USER/ADMIN) |
| **JWT** | 로그인 후 받는 **디지털 출입증**(서명된 문자열). 위조하면 서명이 깨져 들통남 |
| **해시(BCrypt)** | 비밀번호를 **되돌릴 수 없는 형태**로 변환해 저장(원문 보관 X) |
| **필터(Filter)** | 요청이 본 기능에 닿기 전에 거치는 **검문소** |
| **레이트 리미팅** | "분당 N번까지만" 요청 허용(과속 방지) |
| **Flyway** | DB 표 구조를 **버전 관리**하는 도구(V1, V2 … 순서대로 적용) |
| **Actuator / Prometheus** | 앱 상태·수치를 **모니터링**용으로 노출 |
| **감사 로그(Audit Log)** | 보안 사건 기록(CCTV 녹화본 같은 것) |

---

## 2. 전체 구조 (코드 지도)
```
com.aegis
├── auth        🔑 회원가입/로그인/JWT/계정잠금/권한
├── detection   🚨 무차별대입 탐지·IP차단·레이트리밋·화이트리스트·메트릭
├── security    🛡️ 보안설정(필터 순서·헤더·CSRF·접근규칙·권한거부 처리)
├── audit       📒 감사 로그(AuditLog) 기록
├── dashboard   📊 관리자 모니터링 API + 웹 화면
├── common      🧩 공통(전역 예외처리, 에러 응답 형식)
└── config      ⚙️ 설정값 바인딩, dev 시드 계정, OpenAPI 설정
```
- **DB 테이블**: `users`(계정), `blocked_ip`(차단 IP), `audit_log`(감사 로그), `flyway_schema_history`.
- **프로파일**: `dev`(H2 메모리 DB, 기본) / `prod`(PostgreSQL).

---

## 3. 기능 완전 정복

### 3-1. 회원가입
- **무엇**: 새 계정을 만든다. (`POST /api/auth/signup`)
- **왜**: 서비스를 쓰려면 신원이 필요하니까.
- **어떻게 동작**: 입력값 검증(아이디 영문/숫자/밑줄 3~50자, 비번 8~72자) → 중복 확인 →
  비밀번호를 **BCrypt(strength 12)로 해시** → `users` 테이블에 저장(권한은 항상 `USER`).
- **직접 확인**: Swagger에서 signup 실행 → `201`. 같은 아이디 또 하면 `409`. 비번 짧으면 `400`.
- **코드**: `auth/AuthController.signup`, `auth/AuthService.signup`, `auth/dto/SignupRequest`(검증 규칙).
- **한마디**: "가입 시 비번은 절대 원문 저장 안 하고, 되돌릴 수 없는 해시로만 보관합니다."

### 3-2. 비밀번호 해싱 (BCrypt 12)
- **무엇**: 비밀번호를 안전하게 저장하는 방식.
- **왜**: DB가 유출돼도 원래 비번을 알 수 없게 하려고.
- **어떻게 동작**: BCrypt는 **단방향 해시 + 솔트(salt)** 를 자동 적용. strength 12 = 2^12번 반복 →
  **일부러 느리게** 만들어 무차별 대입을 어렵게 함. 로그인 시엔 입력 비번을 같은 방식으로 해시해 비교.
- **코드**: `security/SecurityConfig.passwordEncoder()` → `new BCryptPasswordEncoder(12)`.
- **한마디**: "BCrypt는 일부러 느린 해시라, 비번을 훔쳐도 대입 공격이 비싸집니다."

### 3-3. 로그인 + JWT(Access·Refresh)
- **무엇**: 로그인 성공 시 **출입증 2개** 발급. (`POST /api/auth/login`)
- **왜**: 매 요청마다 비번을 보내지 않고, 토큰으로 신원을 증명하려고(무상태/stateless).
- **어떻게 동작**:
  1. 아이디/비번 확인 → 성공 시 **Access 토큰(30분)** + **Refresh 토큰(7일)** 발급.
  2. 토큰은 **HS256으로 서명**(서버의 비밀키). 안에 사용자·권한·만료시각·종류(type)가 들어 있음.
  3. 이후 요청엔 `Authorization: Bearer <Access토큰>` 을 붙이면 `JwtAuthenticationFilter`가 검증 후 통과.
- **직접 확인**: login → `accessToken` 복사 → `GET /api/me` 에 Bearer로 넣으면 `200`, 없으면 `401`.
- **코드**: `auth/JwtService`(발급/검증), `auth/JwtAuthenticationFilter`(요청에서 토큰 읽기).
- **한마디**: "로그인하면 서명된 출입증을 주고, 그 출입증만으로 다음 요청을 통과시킵니다."

### 3-4. 토큰 갱신 (Refresh)
- **무엇**: Access 토큰이 만료되면 **다시 로그인 없이** 새 토큰 받기. (`POST /api/auth/refresh`)
- **왜**: Access는 짧게(보안), 사용자 편의는 Refresh로 유지.
- **어떻게 동작**: Refresh 토큰의 서명·만료·**종류(type=refresh)** 를 확인하고, 사용자가 여전히 유효하면
  새 Access+Refresh를 발급(회전). Access 토큰을 refresh 자리에 넣으면 종류 불일치로 `401`.
- **코드**: `auth/AuthService.refresh`, `auth/JwtService.isRefreshToken`.
- **한마디**: "짧은 출입증이 만료되면, 긴 재발급권으로 조용히 새 출입증을 받습니다."

### 3-5. 권한 (ROLE_USER / ROLE_ADMIN)
- **무엇**: 사용자 등급. 관리자 자원은 ADMIN만.
- **왜**: 일반 사용자가 관리자 기능(대시보드 등)에 접근하면 안 되니까.
- **어떻게 동작**: 토큰/계정의 역할이 `ROLE_ADMIN`이어야 `/api/admin/**`, `/admin/**` 접근 가능.
  아니면 **403(권한 거부)** + 감사 로그(ACCESS_DENIED) 기록.
- **직접 확인**: `user` 계정으로 `/api/admin/dashboard/stats` → `403`. `admin` 계정이면 `200`.
- **코드**: `security/SecurityConfig`(`hasRole("ADMIN")`), `security/AuditingAccessDeniedHandler`.
- **한마디**: "출입증에 등급이 적혀 있어, 관리자 구역은 ADMIN만 들어갑니다."

### 3-6. 계정 잠금 (무차별 대입 방어 ①, 계정 단위)
- **무엇**: 한 계정에서 **비번 5회 실패 → 15분 잠금**.
- **왜**: 한 계정의 비번을 계속 찍는 공격을 막으려고.
- **어떻게 동작**: 로그인 실패마다 `users.failedLoginCount`를 +1. 5에 도달하면 `lockedUntil`을 15분 뒤로 설정.
  잠긴 동안은 **올바른 비번이어도 `423`(Locked)**. 15분 지나면 자동 해제(다음 시도 때 카운트 초기화).
  > 실패 카운트는 **별도 트랜잭션**으로 저장 → 실패 예외로 롤백돼도 카운트는 확실히 남음(중요 설계).
- **직접 확인**: 같은 계정 5번 틀린 뒤 올바른 비번 → `423`.
- **코드**: `auth/LoginAttemptService`(카운트/잠금), `auth/AuthService.login`.
- **한마디**: "한 계정 비번 5번 틀리면 15분간 금고처럼 잠급니다."

### 3-7. IP 자동 차단 (무차별 대입 방어 ②, IP 단위)
- **무엇**: 한 **IP**에서 로그인 실패가 **10분 내 10회** 넘으면 그 IP를 차단(10분).
- **왜**: 여러 계정을 번갈아 공격하는 IP는 계정 잠금만으론 못 막으니, IP 자체를 막는다.
- **어떻게 동작**: 실패 이벤트를 IP별로 집계 → 임계치 초과 시 `blocked_ip` 테이블에 등록(해제 예정 시각 포함).
  이후 그 IP의 **모든 요청**은 `IpBlockFilter`가 가장 앞단에서 **403**으로 막음. 시간이 지나면 **자동 만료**.
- **직접 확인**: 서로 다른 아이디로 12번 로그인 실패 → 이후 `/health`도 `403`. (앱 재시작하면 초기화)
- **코드**: `detection/BruteForceProtectionService`, `detection/BlockedIp`, `detection/IpBlockFilter`.
- **한마디**: "한 IP가 계속 실패하면, 그 IP는 명단에 올려 모든 요청을 막습니다."

### 3-8. 레이트 리미팅 (요청 폭주 방어)
- **무엇**: IP당 **분당 60회**까지만 허용, 넘으면 **429**.
- **왜**: 짧은 시간 대량 요청(DoS성)으로 서버를 못 쓰게 하는 걸 막으려고.
- **어떻게 동작**: **Bucket4j 토큰 버킷** — IP마다 버킷에 분당 60개 토큰이 차고, 요청 1건이 1개 소비.
  토큰이 없으면 `RateLimitFilter`가 429로 거절. 거부 로그는 폭주 시 DB가 넘치지 않게 **IP당 1분 1회만** 기록.
- **확장 포인트**: `RateLimiter` **인터페이스**로 추상화 → 서버 여러 대인 분산 환경에선 Redis 구현으로 교체 가능.
- **직접 확인**: `/health`를 65번 연속 호출 → 60 넘어가면 `429`.
- **코드**: `detection/RateLimiter`(인터페이스), `detection/Bucket4jRateLimiter`(구현), `detection/RateLimitFilter`.
- **한마디**: "입장 인원을 분당 60명으로 제한하고, 초과하면 잠시 돌려보냅니다."

### 3-9. 화이트리스트 (예외 IP)
- **무엇**: 신뢰하는 IP는 차단·레이트리밋에서 **면제**.
- **왜**: 내부 모니터링 서버, 사내 IP 등은 막히면 안 되니까.
- **어떻게 동작**: `aegis.security.whitelist` 설정의 IP는 IpBlockFilter/RateLimitFilter/집계에서 그냥 통과.
- **직접 확인**: 화이트리스트에 넣은 IP로 15번 실패해도 차단 안 됨(테스트: `WhitelistTest`).
- **코드**: `detection/IpWhitelist`, 설정 `application.yml`의 `aegis.security.whitelist`.
- **한마디**: "VIP(신뢰 IP)는 검문 없이 프리패스입니다."

### 3-10. 보안 헤더
- **무엇**: 응답에 브라우저 보호용 헤더를 붙임.
- **왜**: 브라우저 단에서 클릭재킹·스니핑·스크립트 주입 등을 막으려고.
- **어떻게 동작**(붙는 헤더):
  - `Content-Security-Policy` — 외부/인라인 스크립트 제한(XSS 완화)
  - `X-Frame-Options: DENY` — 다른 사이트가 iframe에 못 넣음(**클릭재킹** 방지)
  - `X-Content-Type-Options: nosniff` — MIME 스니핑 방지
  - `Referrer-Policy: no-referrer` — 참조 주소 유출 방지
  - `Strict-Transport-Security`(HSTS) — HTTPS 강제(HTTPS 요청에만 부착)
- **직접 확인**: `curl.exe -D - -o NUL http://localhost:8080/health` → 헤더 확인.
- **코드**: `security/SecurityConfig`의 `.headers(...)`.
- **한마디**: "브라우저에게 '이 사이트는 이렇게만 다뤄' 라고 안전 규칙을 알려줍니다."

### 3-11. CSRF 방어
- **무엇**: 사용자가 모르는 사이 요청이 위조되는 공격(CSRF) 방어.
- **왜**: 로그인된 사용자의 권한으로 악성 사이트가 몰래 요청을 보낼 수 있어서.
- **어떻게 동작**: 상태 변경 요청에 **CSRF 토큰**을 요구(쿠키 기반). 단, **JWT API(/api/**)는 예외** —
  JWT는 헤더로 직접 보내는 방식이라 브라우저가 자동 전송하지 않아 CSRF에 안전하기 때문.
- **코드**: `security/SecurityConfig`의 `.csrf(...).ignoringRequestMatchers("/api/**")`.
- **한마디**: "쿠키로 자동 전송되는 요청만 토큰으로 한 번 더 확인합니다. 토큰 헤더 API는 원래 안전해서 예외."

### 3-12. 입력 검증 (화이트리스트 방식)
- **무엇**: 들어오는 값이 **허용된 형식인지** 검사.
- **왜**: 이상한/악성 입력을 애초에 거르려고("막을 것"이 아니라 "허용할 것"만 통과 = 화이트리스트).
- **어떻게 동작**: DTO에 `@NotBlank/@Size/@Pattern` 등 규칙 + `@Valid`. 예상치 못한 JSON 필드는 거부
  (`spring.jackson.deserialization.fail-on-unknown-properties=true`). 위반 시 `400` + 어떤 필드가 틀렸는지 안내.
- **코드**: `auth/dto/*Request`(규칙), `common/error/GlobalExceptionHandler`(검증 실패 응답).
- **한마디**: "허용된 모양의 입력만 받아들이고, 나머지는 입구에서 돌려보냅니다."

### 3-13. XSS 방지 (출력 인코딩)
- **무엇**: 악성 스크립트가 화면에서 실행되는 걸 막음.
- **왜**: 공격자가 심은 `<script>`가 다른 사용자 브라우저에서 돌면 큰 사고.
- **어떻게 동작**: 화면(Thymeleaf)은 `th:text`로 **자동 이스케이프** → 스크립트가 "글자"로만 표시됨.
  추가로 CSP 헤더가 2차 방어. JSON 응답은 `Content-Type: application/json` + nosniff로 HTML 해석 차단.
- **코드**: `templates/dashboard.html`(전부 `th:text`), 보안 헤더(3-10).
- **한마디**: "화면에 출력할 땐 스크립트를 실행이 아니라 글자로 보여줍니다."

### 3-14. SQL 인젝션 방지
- **무엇**: 입력으로 DB 명령을 조작하는 공격 차단.
- **왜**: `' OR 1=1 --` 같은 입력으로 DB가 털릴 수 있어서.
- **어떻게 동작**: 모든 DB 접근을 **JPA(파라미터 바인딩)** 로만. 문자열로 SQL을 이어붙이지 않음 →
  입력은 항상 "값"으로만 취급돼 명령이 될 수 없음. (코드 점검 결과 원시 SQL/문자열결합 **0건**.)
- **코드**: `*Repository`(Spring Data JPA 파생 쿼리), 정책 문서 `docs/SECURITY.md`.
- **한마디**: "입력을 SQL 문장에 끼워넣지 않고, 항상 분리된 '값'으로만 전달합니다."

### 3-15. 감사 로그 (AuditLog) — 가장 중요한 "기록"
- **무엇**: 보안 사건을 **시각 / 행위자 / IP / 유형 / 결과** 로 기록.
- **왜**: 무슨 일이 언제 누구에 의해 일어났는지 추적·증빙하려고(사고 분석/감사).
- **어떻게 동작**:
  - 기록되는 사건(유형): `LOGIN_SUCCESS, LOGIN_FAILURE, IP_BLOCKED, RATE_LIMITED, ACCESS_DENIED`
  - 결과: `SUCCESS, FAILURE, BLOCKED, DENIED`
  - **append-only(변경 불가)**: 엔티티가 `@Immutable` + setter 없음 → 한 번 쓰면 **수정 불가**(위조 방지).
  - 콘솔과 DB(`audit_log`)에 동시 기록. **비밀번호/토큰은 절대 기록하지 않음**.
- **직접 확인**: 로그인 몇 번 한 뒤 대시보드 "최근 보안 이벤트" 표 확인.
- **코드**: `audit/AuditLog`(엔티티), `audit/AuditService`(기록), `audit/AuditEventType`/`AuditResult`.
- **한마디**: "모든 보안 사건을 지울 수 없는 일지에 '누가·언제·무엇을·결과'로 남깁니다."

### 3-16. 관리자 대시보드
- **무엇**: 관리자가 보안 현황을 보는 화면 + API.
- **왜**: 사람이 한눈에 상태를 보고 대응하려고.
- **어떻게 동작**:
  - REST API: `/api/admin/dashboard/events`(최근 이벤트), `/blocked-ips`(차단 IP), `/stats`(요약+메트릭).
  - 웹 화면: `/admin/dashboard`(Thymeleaf) — 요약 카드/메트릭/유형별 집계/차단 IP/최근 이벤트 표.
  - **ADMIN 권한 필수**. 데이터는 `audit_log`/`blocked_ip`/메트릭에서 모음.
- **직접 확인**: 브라우저 `/admin/dashboard` → `admin/admin1234` 로그인.
- **코드**: `dashboard/DashboardController`(API), `dashboard/DashboardViewController`+`templates/dashboard.html`(화면), `dashboard/DashboardService`.
- **한마디**: "관리자가 보안 상황을 한 화면에서 보는 관제실입니다."

### 3-17. 메트릭 (Micrometer → Prometheus)
- **무엇**: 탐지/차단 횟수 등을 **수치(카운터)** 로 노출.
- **왜**: Prometheus/Grafana 같은 모니터링 도구가 읽어가 그래프·알림으로 쓰려고.
- **어떻게 동작**: `aegis_detection_login_failures_total`, `ip_blocked_total`, `rate_limited_total` 카운터를
  `/actuator/prometheus`에 노출(인증 필요). 사건이 생길 때마다 +1.
- **직접 확인**: `curl.exe -u admin:admin1234 http://localhost:8080/actuator/prometheus | findstr aegis_detection`
- **코드**: `detection/DetectionMetrics`.
- **한마디**: "탐지·차단 횟수를 모니터링 도구가 읽을 수 있는 숫자로 내보냅니다."

### 3-18. 전역 예외 처리 (일관된 에러)
- **무엇**: 모든 오류를 **같은 형식**으로 응답: `timestamp / status / code / message`.
- **왜**: 클라이언트가 다루기 쉽고, **내부 정보(스택트레이스) 노출을 막으려고**(보안).
- **어떻게 동작**: `@RestControllerAdvice`가 검증 실패(400)·인증 실패(401)·잠금(423)·중복(409) 등을
  안전한 메시지로 변환. 내부 상세는 서버 로그에만.
- **코드**: `common/error/GlobalExceptionHandler`, `auth/AuthExceptionHandler`, `common/error/ErrorResponse`.
- **한마디**: "오류가 나도 내부를 까보이지 않고, 정해진 안전한 형식으로만 답합니다."

### 3-19. 운영 기반 (프로파일·Flyway·로깅·문서)
- **프로파일**: `dev`(H2 메모리, 기본) / `prod`(PostgreSQL). 운영 접속정보·시크릿은 **환경변수**로만 주입.
- **Flyway**: DB 표 구조를 V1~V5 마이그레이션으로 관리. JPA는 `ddl-auto=validate`(자동 변경 금지, 검증만).
- **구조적 로깅**: prod에선 **JSON 로그**(로그 수집·검색 용이), 비밀번호/토큰은 마스킹.
- **API 문서**: springdoc-openapi → **Swagger UI**(`/swagger-ui.html`)에서 API를 보고 직접 호출.
- **한마디**: "개발/운영 설정을 분리하고, DB 변경은 버전으로 관리하며, 로그·문서까지 운영 수준으로 갖췄습니다."

### 3-20. 배포 (Docker / fat jar)
- **무엇**: 어디서나 같게 실행되도록 패키징.
- **어떻게 동작**:
  - **fat jar**: `aegis-0.1.0.jar` 하나로 `java -jar`로 실행.
  - **멀티스테이지 Dockerfile**: 빌드 단계(JDK)에서 jar 생성 → 실행 단계(경량 JRE, **비루트 유저**)로 작은 이미지.
  - **docker-compose**: app + PostgreSQL을 함께 띄움(운영 시크릿은 환경변수).
- **코드**: `Dockerfile`, `docker-compose.yml`.
- **한마디**: "한 번 빌드하면 내 PC든 서버든 똑같이 도는 컨테이너로 배포합니다."

---

## 4. 요청 하나의 여정 (발표용 시나리오)
누군가 `POST /api/auth/login` 을 보냈다고 하자. 이 요청은 순서대로 관문을 통과한다:
```
요청
 → ① IpBlockFilter   : 이 IP 차단 명단에 있나? (있으면 403, 끝)
 → ② RateLimitFilter : 분당 한도 넘었나? (넘으면 429, 끝)
 → ③ JwtAuthFilter   : Bearer 토큰 있으면 신원 세팅 (로그인 요청엔 보통 없음)
 → ④ CSRF 검사       : /api/** 는 예외라 통과
 → ⑤ 인가 검사       : /api/auth/** 는 공개라 통과
 → AuthController.login → AuthService.login
      - 사용자 조회 → 잠김? (423) / 비번 확인
      - 실패: 계정 카운트+1(필요시 잠금) + IP 실패 집계(필요시 차단) + 감사 LOGIN_FAILURE → 401
      - 성공: 카운트 초기화 + 감사 LOGIN_SUCCESS + JWT 2개 발급 → 200
```
> 이 한 흐름만 설명해도 "다층 방어 + 감사"가 어떻게 맞물리는지 전부 보여줄 수 있다.

---

## 5. 발표/면접 예상 질문 (이렇게 답하세요)
- **Q. 계정 잠금이랑 IP 차단, 왜 둘 다?**
  A. 계정 잠금은 "한 계정 집중 공격", IP 차단은 "여러 계정 갈아타는 공격"을 막습니다. 노리는 게 달라요.
- **Q. JWT는 어디에 저장해요? 탈취되면?**
  A. 클라이언트가 보관하고 헤더로 보냅니다. Access는 30분으로 짧게 해 피해를 줄이고, 만료 시 Refresh로 갱신합니다.
  (개선 여지: Refresh 토큰 폐기 목록(블랙리스트)·회전 강화.)
- **Q. 비밀번호 안전한가요?**
  A. BCrypt(strength 12) 단방향 해시 + 솔트로 저장합니다. 원문은 어디에도 없습니다.
- **Q. 감사 로그를 공격자가 지우면?**
  A. append-only(@Immutable)라 앱에서 수정/삭제 경로가 없습니다. (운영에선 DB 권한·백업으로 더 강화 가능.)
- **Q. 한계는?**
  A. HTTPS·실제 시크릿관리(Vault)·분산 레이트리밋(Redis)·침투테스트는 범위 밖입니다(자세히는 SECURITY.md).
- **Q. 왜 Flyway에 `ddl-auto=validate`?**
  A. 스키마를 코드가 멋대로 바꾸지 못하게 하고, 변경 이력을 마이그레이션으로 남기기 위해서입니다.

---

## 6. 설정값 한눈에 (기본값)
| 설정 | 기본값 | 의미 |
|---|---|---|
| 계정 잠금 임계 | 5회 | 이만큼 실패하면 잠금 |
| 잠금 시간 | 15분 | 잠금 유지 |
| IP 실패 임계 | 10회 / 10분 | 이 시간창 내 초과하면 IP 차단 |
| IP 차단 시간 | 10분 | 차단 유지(자동 만료) |
| 레이트리밋 | 분당 60회 | IP당 허용량 |
| Access 토큰 | 30분 | 짧게 |
| Refresh 토큰 | 7일(10080분) | 길게 |
> 모두 `application.yml`의 `aegis.security.*` 에서 바꿀 수 있다.

---

## 7. 직접 해보며 익히는 순서 (추천)
1. `run.bat` 더블클릭 → 대시보드 로그인(`admin`/`admin1234`)으로 "관제실" 확인.
2. Swagger(`/swagger-ui.html`)에서 회원가입→로그인→`/api/me` 흐름 체험.
3. 일부러 로그인 실패 몇 번 → 대시보드 새로고침 → 숫자/이벤트가 쌓이는 것 관찰.
4. (마지막에) 레이트리밋 429, IP 차단 403 체험 → 막히면 앱 재시작.
   - 명령 모음: [TEST_GUIDE.md](TEST_GUIDE.md)

---

## 8. 더 보기
- 한눈 요약: [OVERVIEW.md](OVERVIEW.md)
- 실습 명령: [TEST_GUIDE.md](TEST_GUIDE.md)
- 보안 정책/범위·한계: [docs/SECURITY.md](SECURITY.md) · [SECURITY.md](../SECURITY.md)
- 프로젝트 소개/배포: [README.md](../README.md)
