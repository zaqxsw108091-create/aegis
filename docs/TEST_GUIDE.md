# Aegis 사용 & 기능 시험 가이드 (직접 해보기)

이 문서 하나로 **기동 → 모든 기능 시험 → 정상 동작 확인**까지 할 수 있다.
환경: Windows PowerShell + `curl.exe`(Windows 10+ 기본 포함). 브라우저로 클릭 테스트도 가능.

> 명령의 `curl.exe` 는 PowerShell의 `curl`(=Invoke-WebRequest) 별칭과 다른 **진짜 curl** 이다. 꼭 `.exe` 까지 입력.

---

## 0. 준비 & 기동
```powershell
# 프로젝트 폴더에서 (dev 프로파일 = H2 인메모리, 자동)
./gradlew bootRun
# 콘솔에 "Started AegisApplication" 이 뜨면 준비 완료. 종료는 Ctrl+C.
```
- 기동 시 콘솔에 `[DEV-SEED] 시드 계정 생성: admin / user` 로그가 보인다.

### 🔑 dev 시드 계정 (이 문서에서만 사용, 운영 금지)
| 사용자 | 비밀번호 | 권한 |
|---|---|---|
| `daeyoung0` | `dae0nooli` | ROLE_ADMIN |
| `people` | `admin123400` | ROLE_USER |

### 보호 자원 호출 2가지 방법
- **Basic 인증(가장 쉬움)**: `curl.exe -u daeyoung0:dae0nooli ...`
- **JWT**: 로그인으로 accessToken 받아 `-H "Authorization: Bearer <토큰>"`
- **브라우저**: Swagger UI(`/swagger-ui.html`)의 **Authorize** 버튼에 토큰 입력

---

## 1. 헬스체크 (가장 먼저)
```powershell
curl.exe -s http://localhost:8080/health
```
✅ 기대: `{"status":"UP"}`

---

## 2. Swagger UI 로 클릭 테스트 (CLI 없이 가장 편함)
브라우저에서 → **http://localhost:8080/swagger-ui.html**
1. `auth` → `/api/auth/signup` 또는 `/api/auth/login` 을 **Try it out** 으로 실행
2. 로그인 응답의 `accessToken` 복사
3. 우측 상단 **Authorize** 클릭 → 토큰 붙여넣기
4. 이제 `/api/me`, `/api/admin/dashboard/*` 등 보호 자원도 클릭으로 호출 가능

---

## 3. 인증 (P2) — 회원가입 / 로그인 / 토큰 / 잠금 / 권한

### 3-1. 회원가입
```powershell
curl.exe -s -X POST http://localhost:8080/api/auth/signup -H "Content-Type: application/json" -d "{`"username`":`"alice`",`"password`":`"password123`"}"
```
✅ 기대: `201`, `{"id":...,"username":"alice","role":"USER"}`

### 3-2. 중복 가입 (같은 명령 한 번 더)
✅ 기대: `409` (`USERNAME_TAKEN`)

### 3-3. 검증 실패 (짧은 비번 / 잘못된 사용자명)
```powershell
curl.exe -s -o NUL -w "%{http_code}`n" -X POST http://localhost:8080/api/auth/signup -H "Content-Type: application/json" -d "{`"username`":`"a b!`",`"password`":`"short`"}"
```
✅ 기대: `400` (화이트리스트 검증 실패)

### 3-4. 로그인 성공
```powershell
curl.exe -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{`"username`":`"alice`",`"password`":`"password123`"}"
```
✅ 기대: `200`, `accessToken` / `refreshToken` 포함. (토큰을 복사해 둔다)

### 3-5. 보호 자원 접근
```powershell
# 토큰 없이 → 401
curl.exe -s -o NUL -w "no-token=%{http_code}`n" http://localhost:8080/api/me
# Basic 인증으로 → 200
curl.exe -s -u alice:password123 http://localhost:8080/api/me
```
✅ 기대: 토큰 없으면 `401`, 인증되면 `200` + `{"username":"alice","role":"ROLE_USER"}`

### 3-6. 로그인 실패 (틀린 비번)
```powershell
curl.exe -s -o NUL -w "%{http_code}`n" -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{`"username`":`"alice`",`"password`":`"WRONG`"}"
```
✅ 기대: `401` (`INVALID_CREDENTIALS`)

### 3-7. 계정 잠금 (5회 실패 → 15분 잠금)
```powershell
# 'bob' 가입 후 5번 틀리기
curl.exe -s -o NUL -X POST http://localhost:8080/api/auth/signup -H "Content-Type: application/json" -d "{`"username`":`"bob`",`"password`":`"password123`"}"
for ($i=1; $i -le 5; $i++) {
  curl.exe -s -o NUL -w "fail$i=%{http_code}`n" -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{`"username`":`"bob`",`"password`":`"WRONG`"}"
}
# 이제 올바른 비번이어도 잠김
curl.exe -s -o NUL -w "locked=%{http_code}`n" -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{`"username`":`"bob`",`"password`":`"password123`"}"
```
✅ 기대: 5번 모두 `401`, 마지막(올바른 비번)은 `423` (`ACCOUNT_LOCKED`)

### 3-8. 토큰 갱신
```powershell
# 3-4 로그인 응답의 refreshToken 을 <REFRESH> 자리에 넣는다
curl.exe -s -X POST http://localhost:8080/api/auth/refresh -H "Content-Type: application/json" -d "{`"refreshToken`":`"<REFRESH>`"}"
```
✅ 기대: `200` + 새 토큰. 잘못된 값 넣으면 `401` (`INVALID_TOKEN`)

### 3-9. 권한 거부 (일반 유저가 관리자 자원 접근)
```powershell
curl.exe -s -o NUL -w "%{http_code}`n" -u people:admin123400 http://localhost:8080/api/admin/dashboard/stats
```
✅ 기대: `403` (ROLE_USER 는 ADMIN 자원 불가)

---

## 4. 방어 (P4) — 보안 헤더 확인
```powershell
curl.exe -s -D - -o NUL http://localhost:8080/health
```
✅ 응답 헤더에 다음이 보여야 한다:
- `Content-Security-Policy: ... frame-ancestors 'none' ...`
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: no-referrer`
> `Strict-Transport-Security`(HSTS)는 HTTPS 요청에만 붙는다(로컬 http 에선 안 보이는 게 정상).

---

## 5. 대시보드 (P5) — 관리자 모니터링
### 5-1. REST API (Basic 인증)
```powershell
curl.exe -s -u daeyoung0:dae0nooli http://localhost:8080/api/admin/dashboard/stats
curl.exe -s -u daeyoung0:dae0nooli http://localhost:8080/api/admin/dashboard/events
curl.exe -s -u daeyoung0:dae0nooli http://localhost:8080/api/admin/dashboard/blocked-ips
```
✅ 기대: `200`. `stats` 에는 전체 이벤트수/로그인 실패수/차단 IP수/유형별 집계/메트릭값.
(3장에서 실패를 많이 만들었으면 `loginFailuresTotal` 이 올라가 있다)

### 5-2. 웹 화면 (브라우저)
브라우저 → **http://localhost:8080/admin/dashboard**
- Basic 인증 창이 뜨면 `daeyoung0` / `dae0nooli` 입력 → 어두운 관제 콘솔 화면이 뜬다.
- 위협 수준 배너, 요약 카드, 메트릭, 이벤트 유형별 집계, **사용자 현황**, 차단 IP, 최근 이벤트 표가 보인다.
✅ 비관리자(`people`)로 들어가면 `403`.

### 5-3. 관리자 조치 (브라우저에서 클릭)
1. **수동 IP 차단**: 차단 IP 표 아래 입력창에 `203.0.113.5` / `10` / `테스트` 입력 → `IP 차단` 클릭
   ✅ 상단에 "IP를 차단했습니다." 메시지, 차단 IP 표에 행 추가, 최근 이벤트에 `IP_BLOCKED`(행위자=daeyoung0)
2. **차단 해제**: 그 행의 `차단 해제` 클릭 ✅ 행이 사라지고 `IP_UNBLOCKED` 기록
3. **계정 잠금 해제**: 3-7에서 잠근 `bob`이 사용자 현황 표에 `LOCKED` / 실패 5로 보인다 →
   `잠금 해제 / 초기화` 클릭 ✅ `ACTIVE` / 실패 0, 이제 `bob`으로 바로 로그인 가능
4. (보안 확인) 이 버튼들은 CSRF 토큰이 자동으로 붙는다. curl로 토큰 없이 `POST /admin/blocked-ips` 를 보내면 `403`:
```powershell
curl.exe -s -o NUL -w "%{http_code}`n" -u daeyoung0:dae0nooli -X POST http://localhost:8080/admin/blocked-ips -d "ip=1.2.3.4"
```
✅ 기대: `403` (토큰 없는 상태 변경 거부 = CSRF 방어 동작)

### 5-4. 사용자별 실패 횟수 API
```powershell
curl.exe -s -u daeyoung0:dae0nooli http://localhost:8080/api/admin/dashboard/users
```
✅ 기대: 각 사용자의 `failedLoginCount`, `locked`, `lockedUntil` 이 JSON으로 온다.

---

## 6. 메트릭 (Actuator/Prometheus)
```powershell
curl.exe -s -u daeyoung0:dae0nooli http://localhost:8080/actuator/prometheus | findstr aegis_detection
```
✅ 기대: `aegis_detection_login_failures_total`, `aegis_detection_ip_blocked_total`,
`aegis_detection_rate_limited_total` 가 보인다.

---

## 7. 탐지 (P3) — ⚠️ 이 둘은 "맨 마지막에" 하거나, 하고 나서 앱을 재시작하라
localhost(127.0.0.1)가 차단되거나 한도에 걸리면 **그 뒤 모든 요청이 막혀서** 다른 시험이 안 된다.
H2는 인메모리라 **앱을 재시작하면 차단/카운트가 초기화**된다.

### 7-1. 레이트 리미팅 (분당 60회 초과 → 429)
```powershell
for ($i=1; $i -le 65; $i++) { curl.exe -s -o NUL -w "$i=%{http_code} " http://localhost:8080/health }
```
✅ 기대: 앞쪽은 `200`, 60회를 넘기면 `429` 가 나오기 시작.

### 7-2. 무차별 대입 → IP 자동 차단 (403)
> 팁: 같은 아이디로 하면 5회에서 "계정 잠금"이 먼저 걸린다. **IP 차단(10회)** 을 보려면
> 매번 다른 아이디로 실패시켜 계정 잠금을 피하고 IP 실패만 쌓는다.
```powershell
for ($i=1; $i -le 12; $i++) {
  curl.exe -s -o NUL -w "try$i=%{http_code}`n" -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{`"username`":`"ghost$i`",`"password`":`"x`"}"
}
# 임계치(10) 초과 후에는 공개 엔드포인트조차 차단된다
curl.exe -s -o NUL -w "health-after-block=%{http_code}`n" http://localhost:8080/health
```
✅ 기대: 10회 이후 IP가 차단되어 `/health` 도 `403` (`IP_BLOCKED`).
🔄 복구: 앱 재시작(Ctrl+C 후 `./gradlew bootRun`) → 차단 해제(또는 10분 후 자동 만료).

---

## ✅ 전체 점검 체크리스트
| # | 시험 항목 | 명령/방법 | 기대 |
|---|---|---|---|
| 1 | 헬스체크 | `GET /health` | 200 `{"status":"UP"}` |
| 2 | 회원가입 | `POST /api/auth/signup` | 201 |
| 3 | 중복 가입 | 같은 요청 재전송 | 409 |
| 4 | 입력 검증 | 짧은 비번/이상한 id | 400 |
| 5 | 로그인 성공 | `POST /api/auth/login` | 200 + 토큰 |
| 6 | 로그인 실패 | 틀린 비번 | 401 |
| 7 | 보호 자원(미인증) | `GET /api/me` | 401 |
| 8 | 보호 자원(인증) | `-u alice:...` | 200 |
| 9 | 계정 잠금 | 5회 실패 후 정상 로그인 | 423 |
| 10 | 토큰 갱신 | `POST /api/auth/refresh` | 200 / 잘못되면 401 |
| 11 | 권한 거부 | user 로 admin 자원 | 403 |
| 12 | 보안 헤더 | `curl.exe -D -` | CSP/X-Frame/nosniff/Referrer |
| 13 | 대시보드 API | `-u admin:...` | 200 + 통계 |
| 14 | 대시보드 웹 | 브라우저 `/admin/dashboard` | 관제 화면 표시 |
| 14-1 | 수동 IP 차단/해제 | 화면 버튼 | 표 갱신 + 감사 기록 |
| 14-2 | 계정 잠금 해제 | 사용자 현황 버튼 | 실패 0 · 재로그인 가능 |
| 14-3 | 관리자 조치 CSRF | 토큰 없이 POST | 403 |
| 14-4 | 사용자별 실패 횟수 API | `/api/admin/dashboard/users` | JSON |
| 15 | 메트릭 | `/actuator/prometheus` | aegis_detection_* |
| 16 | 레이트리밋 | 65회 GET /health | 429 발생 |
| 17 | IP 차단 | 12회 로그인 실패(다른 id) | 이후 403 |

---

## 이 프로그램의 강점 (어필 포인트)
- **다층 방어(defense-in-depth)**: 계정 잠금(계정 단위) + IP 차단(IP 단위) + 레이트 리밋이 동시에 작동.
- **운영 친화적 기반**: 프로파일 분리(dev/prod), Flyway 스키마 관리, Actuator/Prometheus 메트릭,
  JSON 구조적 로깅, OpenAPI 문서, 일관된 에러 응답 포맷.
- **검증 가능한 보안**: 무차별 대입/레이트리밋/권한거부/보안헤더/토큰 회전/관리자 조치(CSRF)를 **자동화 테스트 51개**로 보증.
- **무결성 있는 감사**: AuditLog가 append-only(@Immutable)라 보안 이벤트 기록을 사후 변조 불가.
- **확장 고려 설계**: 레이트리미터를 인터페이스로 추상화 → 분산 환경에서 Redis 등으로 교체 가능.
- **시크릿 안전**: JWT/DB 시크릿 환경변수화, 비밀번호 BCrypt(12), 로그·에러에 민감정보 비노출.
- **배포 준비**: 멀티스테이지 Dockerfile(비루트 실행) + docker-compose(app+PostgreSQL).

---

## 마무리 / 주의
- 시드 계정(admin/user)과 H2는 **dev 전용**이다. 운영(prod)에선 시드되지 않으며 시크릿을 반드시 교체.
- 시험 중 막히면(403/429) 대개 7장 때문이다. **앱 재시작**으로 초기화하면 된다.
- 더 깊은 보안 정책/범위는 [SECURITY.md](../SECURITY.md), [docs/SECURITY.md](SECURITY.md) 참고.
