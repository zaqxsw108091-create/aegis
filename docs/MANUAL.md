# Aegis 사용 설명서 (설치 → 실행 → 사용)

> **이 문서 하나로**: 아무것도 모르는 상태에서 → 설치하고 → 켜고 → 실제로 써보고 → 끄는 것까지.
> 개념 공부는 [STUDY_GUIDE.md](STUDY_GUIDE.md), 상세 시험은 [TEST_GUIDE.md](TEST_GUIDE.md) 참고.

---

## 1장. 준비물 (설치해야 하는 것)

### 필수: Java 21
Aegis는 Java 21로 만들어졌다. 딱 하나만 설치하면 된다.

**확인 방법** — PowerShell(또는 cmd)을 열고:
```powershell
java -version
```
- `21.x.x` 가 나오면 → 준비 끝, 2장으로.
- 없다고 나오거나 다른 버전이면 → 아래 중 하나로 설치:

| 방법 | 하는 법 |
|---|---|
| A. 공식 설치 (권장) | https://adoptium.net 접속 → **Temurin 21 (LTS)** 다운로드 → 설치 |
| B. winget (명령 한 줄) | `winget install EclipseAdoptium.Temurin.21.JDK` |

> 💡 **이 PC(개발했던 컴퓨터)는 이미 준비돼 있다** — `C:\Users\USER\.jdks\jdk-21.0.11+10` 에 Java 21이 있고, `run.bat`이 자동으로 찾아 쓴다. 아무것도 설치할 필요 없음.

### 프로젝트 파일 받기
- 이 PC: 이미 `C:\Users\USER\projects\aegis` 에 있음.
- 다른 PC로 옮길 때: `aegis` 폴더를 통째로 복사(USB/압축)하면 된다. 별도 설치 과정 없음.

### 선택: Docker (운영 배포용, 없어도 됨)
PostgreSQL과 함께 컨테이너로 띄우고 싶을 때만. https://www.docker.com/products/docker-desktop

---

## 2장. 켜기 (실행) — 3가지 방법

### 방법 A. 더블클릭 (가장 쉬움) ⭐
1. `aegis` 폴더에서 **`run.bat` 더블클릭**
2. 검은 창이 열리고 서버가 켜짐 (처음엔 20~40초)
3. 잠시 후 **브라우저가 자동으로 관리자 대시보드를 연다**
4. 로그인 창: **아이디 `daeyoung0` / 비밀번호 `dae0nooli`**

끄기: 검은 창 닫기 또는 **`stop.bat` 더블클릭**

> 브라우저가 먼저 열려 "연결할 수 없음"이 보이면 → 검은 창에 `Started AegisApplication`이 뜬 뒤 **F5(새로고침)**.

### 방법 B. 명령어로
```powershell
cd C:\Users\USER\projects\aegis
./gradlew bootRun
# 끝났으면 Ctrl+C 로 종료
```

### 방법 C. Docker로 (운영 방식, PostgreSQL 포함)
```powershell
cd C:\Users\USER\projects\aegis
$env:AEGIS_JWT_SECRET = "아무32바이트이상긴문자열로바꾸세요........"
docker compose up --build
```

### 켜졌는지 확인 (공통)
브라우저에서 http://localhost:8080/health → `{"status":"UP"}` 이 보이면 성공.

---

## 3장. 쓰기 (실제 사용법)

Aegis를 쓰는 입구는 **3개**다. 순서대로 해보자.

### 3-1. 관리자 대시보드 — "보안 관제실" 👮
- 주소: **http://localhost:8080/admin/dashboard**
- 로그인: `daeyoung0` / `dae0nooli`
- 보이는 것: 맨 위 **위협 수준 배너**(초록 정상 / 노랑 로그인 실패 감지 / 빨강 차단 진행 중) / 전체 이벤트 수 / 로그인 실패(누적·24시간) / 차단된 IP 목록 / 최근 보안 사건 50개(누가·언제·어디서·무슨 일·결과, 결과별 색 배지)
- 30초마다 자동으로 새로고침된다(관제 화면처럼 켜두고 보면 됨).
- **관리자가 직접 할 수 있는 조치**(버튼/입력창):
  - **사용자 현황** 표: 사용자별 **로그인 실패 횟수**와 **잠금 상태**가 보이고, `잠금 해제 / 초기화` 버튼으로 풀 수 있다.
  - **차단 IP** 표: 각 IP 옆 `차단 해제` 버튼. 아래 입력창에 IP·시간(분)·사유를 넣고 `IP 차단`을 누르면 **수동 차단**.
  - 모든 조치는 감사 로그에 "누가·언제·무엇을" 기록된다.
- ⚠️ 내 IP가 차단되면 대시보드도 403이 되어 스스로 해제 못 한다 → 운영에선 관리자 IP를 화이트리스트에 넣는다.
  (테스트 중이면 앱 재시작으로 초기화)
- 쓰는 법: 위쪽 **"새로고침"** 을 누르면 최신 상태로 갱신된다. 처음엔 전부 0 — 정상(아직 사건이 없어서).

### 3-2. Swagger — "기능을 클릭으로 써보는 조종석" 🕹️
- 주소: **http://localhost:8080/swagger-ui.html**
- 여기서 모든 기능(API)을 클릭만으로 실행할 수 있다.

**따라하기 (5분 코스):**
1. `auth` 섹션 → **POST /api/auth/signup** → `Try it out` → 입력:
   ```json
   { "username": "myname", "password": "mypassword1" }
   ```
   → `Execute` → 응답 `201` = 회원가입 성공
2. **POST /api/auth/login** → 같은 아이디/비번 → `Execute` → 응답에서 **`accessToken` 값 복사**
3. 화면 오른쪽 위 **`Authorize`** 버튼 → 복사한 토큰 붙여넣기 → `Authorize`
   (= "출입증을 지갑에 넣었다")
4. `account` 섹션 → **GET /api/me** → `Execute` → 내 정보가 나오면 **JWT 인증 성공!**
5. **POST /api/auth/refresh** 에 로그인 때 받은 `refreshToken`을 넣으면 → 새 토큰 재발급(회전)
6. **POST /api/auth/logout** 에 `refreshToken`을 넣으면 → 그 토큰 폐기(진짜 로그아웃)

**보안이 동작하는 걸 직접 보기:**
- 로그인을 **일부러 5번 틀려보기** → 6번째는 맞는 비번도 `423`(계정 잠금 15분)
- 그 후 대시보드 새로고침 → `LOGIN_FAILURE` 사건들이 기록돼 있다

### 3-3. 메트릭 — "숫자로 보는 상태" 📈 (선택)
- 주소: http://localhost:8080/actuator/prometheus (admin 로그인 필요)
- Prometheus/Grafana 같은 모니터링 도구가 읽어가는 원시 수치. `aegis_detection_` 으로 시작하는 줄이 우리 카운터.

---

## 4장. 설정 바꾸기 (입맛대로 조정)

설정 파일: `src/main/resources/application.yml` 의 `aegis.security` 부분. 바꾼 뒤 재시작하면 적용.

| 바꾸고 싶은 것 | 설정 키 | 기본값 |
|---|---|---|
| 계정 잠금까지 실패 횟수 | `lockout.max-failed-attempts` | 5회 |
| 계정 잠금 시간 | `lockout.lock-minutes` | 15분 |
| IP 차단까지 실패 횟수 | `bruteforce.ip-fail-threshold` | 10회 |
| IP 차단 시간 | `bruteforce.ip-block-minutes` | 10분 |
| 분당 허용 요청 수 | `rate-limit.requests-per-minute` | 60회 |
| 검사 면제 IP(화이트리스트) | `whitelist` | 없음 |
| Access 토큰 수명 | `jwt.expiration-minutes` | 30분 |

**알림(웹훅) 켜기** — IP 차단/토큰 탈취 의심 시 Slack 등으로 통지받기:
```powershell
# 서버 켜기 전에 환경변수로 웹훅 주소 지정 (비우면 알림 꺼짐 = 기본)
$env:AEGIS_ALERT_WEBHOOK = "https://hooks.slack.com/services/여러분의훅주소"
./gradlew bootRun
```

---

## 5장. 문제가 생겼을 때 (FAQ)

| 증상 | 원인 & 해결 |
|---|---|
| run.bat 켰는데 창이 바로 꺼짐 | Java 21이 없을 가능성 → 1장대로 설치 |
| 브라우저 "연결할 수 없음" | 서버가 아직 켜지는 중 → 검은 창에 `Started AegisApplication` 뜬 후 F5 |
| 갑자기 모든 요청이 **403** | 내 IP가 차단됨(로그인 실패 10회) → **앱 재시작**하면 초기화 (dev는 메모리 DB) |
| 갑자기 **429** | 분당 60회 초과 → 1분 기다리거나 재시작 |
| 로그인이 **423** | 계정 잠금(5회 실패) → 15분 대기 or 재시작 |
| admin 로그인 안 됨 | dev 모드인지 확인 — 검은 창에 `[DEV-SEED] 시드 계정 생성` 로그가 있어야 함 |
| 포트 8080 이미 사용 중 | `stop.bat` 실행 후 다시 `run.bat` |

---

## 6장. 공부 순서 (이 프로그램으로 배우기)

1. **써본다** — 이 문서 2~3장. 눈으로 동작을 먼저 본다.
2. **이해한다** — [OVERVIEW.md](OVERVIEW.md): 전체 그림(구조·방어 원리)을 쉬운 말로.
3. **깊이 판다** — [STUDY_GUIDE.md](STUDY_GUIDE.md): 기능 20개를 "무엇/왜/어떻게/코드 위치"로. 남에게 설명 가능한 수준까지.
4. **검증한다** — [TEST_GUIDE.md](TEST_GUIDE.md): 17+ 시험 항목을 직접 돌려본다.
5. **코드를 읽는다** — 추천 순서: `HealthController`(제일 쉬움) → `AuthController/AuthService`(인증 흐름) → `SecurityConfig`(검문소 순서) → `BruteForceProtectionService`(탐지) → `AuditService`(기록).

> 💡 요령: 기능 하나를 골라 → Swagger로 실행해보고 → 대시보드에서 기록을 확인하고 → 그 기능의 코드를 열어본다. "동작 → 기록 → 코드" 순서가 가장 빨리 는다.

---

## 부록. 계정/주소 한 장 요약

| 항목 | 값 |
|---|---|
| 관리자 계정 (dev 전용) | `daeyoung0` / `dae0nooli` |
| 일반 계정 (dev 전용) | `people` / `admin123400` |
| 서버 확인 | http://localhost:8080/health |
| 대시보드 | http://localhost:8080/admin/dashboard |
| API 조종석(Swagger) | http://localhost:8080/swagger-ui.html |
| 메트릭 | http://localhost:8080/actuator/prometheus |
| 켜기 / 끄기 | `run.bat` / `stop.bat` |

> ⚠️ dev 계정과 메모리 DB(H2)는 **연습용**이다. 실제 운영(prod)에서는 자동 생성되지 않으며,
> `AEGIS_JWT_SECRET` 등 시크릿을 반드시 환경변수로 넣어야 켜진다. (자세히: [README.md](../README.md))
