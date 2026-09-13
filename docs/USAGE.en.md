# Aegis — Usage Guide (English)

> Everything you need to **install, run, use, and tune** Aegis.
> Korean version: [MANUAL.md](MANUAL.md) · Deep dive: [STUDY_GUIDE.md](STUDY_GUIDE.md) · Test recipes: [TEST_GUIDE.md](TEST_GUIDE.md)

---

## What Aegis is (and is not)

**Aegis is a defensive security layer for web applications**, built with Spring Boot 3 / Java 21.
It sits in front of your application logic and **blocks, detects, and records** attacks:
brute force, request flooding, SQL injection, XSS, CSRF, and unauthorized access.

- ✅ It protects **a web service** (its APIs, its admin pages, its accounts).
- ❌ It is **not** antivirus/endpoint protection — it does not protect your laptop's files or other programs.
- ❌ It performs **no offensive actions** (no scanning, no attacking). Defense, detection, and audit only.

**Design principle — defense in depth:** if one layer is bypassed, the next still holds
(account lockout + IP blocking + rate limiting + input validation + security headers + audit trail).

---

## 1. Features at a glance

| Area | What you get |
|---|---|
| **Authentication** | Signup/login REST API, BCrypt(12) password hashing, JWT **access + refresh** tokens |
| **Token lifecycle** | Refresh **rotation** (one-time use), **reuse detection** → revoke all sessions, logout endpoint |
| **Account protection** | Lock an account for 15 min after 5 failed logins |
| **Intrusion detection** | Aggregate failures **per IP** → auto-block the IP (403) with auto-expiry |
| **Rate limiting** | Bucket4j token bucket, N requests/min per IP → 429 (abstracted for distributed backends) |
| **Whitelist** | Exempt trusted IPs from blocking and rate limiting |
| **Hardening** | CSP, HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, CSRF |
| **Input/Output safety** | Whitelist validation (`@Valid` + patterns, unknown JSON fields rejected), output escaping, JPA parameter binding only (no string-concatenated SQL) |
| **Audit trail** | `AuditLog`: timestamp / actor / IP / event type / result — **append-only (immutable)** |
| **Dashboard** | Admin-only REST API + server-rendered monitoring page |
| **Observability** | Actuator + Micrometer/Prometheus counters (`aegis_detection_*`) |
| **Alerting** | Webhook notification on IP block / token reuse (async, fail-safe, off by default) |
| **Ops readiness** | dev/prod profiles, Flyway migrations, JSON logging, OpenAPI, Docker + compose, CI |

---

## 2. Requirements

- **Java 21** (required) — verify with `java -version`. Install from [Adoptium](https://adoptium.net) or `winget install EclipseAdoptium.Temurin.21.JDK`.
- **Docker** (optional) — only for the container/PostgreSQL deployment.
- No database setup needed for development: the `dev` profile uses in-memory H2.

---

## 3. Run it

### A) Development (fastest)
```bash
./gradlew bootRun          # Windows: run.bat (double-click) also works
```
Verify: `http://localhost:8080/health` → `{"status":"UP"}`

**Dev-only seeded accounts** (created automatically in the `dev` profile, never in `prod`):

| Username | Password | Role |
|---|---|---|
| `daeyoung0` | `dae0` | `ROLE_ADMIN` |
| `admin` | `12340` | `ROLE_USER` (named "admin", but a regular user) |

### B) Docker Compose (app + PostgreSQL, prod profile)
```bash
export AEGIS_JWT_SECRET="<a strong secret, 32+ bytes>"
export DB_PASSWORD="<db password>"
docker compose up --build
```

### C) Standalone jar (external PostgreSQL)
```bash
./gradlew build
export DB_HOST=... DB_PORT=5432 DB_NAME=aegis DB_USERNAME=... DB_PASSWORD=...
export AEGIS_JWT_SECRET="<a strong secret, 32+ bytes>"
java -jar build/libs/aegis-0.1.0.jar --spring.profiles.active=prod
```

---

## 4. Use it — a 5-minute tour

Open **Swagger UI**: `http://localhost:8080/swagger-ui.html`

1. **Sign up** — `POST /api/auth/signup`
   ```json
   { "username": "alice", "password": "password123" }
   ```
   → `201 Created`. Duplicate username → `409`. Weak/invalid input → `400`.
2. **Log in** — `POST /api/auth/login` → returns `accessToken` + `refreshToken`.
3. **Authorize** — click **Authorize** (top-right), paste the `accessToken`.
4. **Call a protected resource** — `GET /api/me` → `200` with your username and role.
5. **Rotate** — `POST /api/auth/refresh` with the `refreshToken` → new token pair (old one is now dead).
6. **Log out** — `POST /api/auth/logout` with the `refreshToken` → `204`, token revoked.

### The admin dashboard
`http://localhost:8080/admin/dashboard` (log in as `daeyoung0` / `dae0`)
Shows totals, Micrometer counters, per-type event counts, currently blocked IPs, and the 50 most recent security events.

### Equivalent with curl
```bash
# sign up + log in
curl -s -X POST localhost:8080/api/auth/signup -H 'Content-Type: application/json' \
     -d '{"username":"alice","password":"password123"}'
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
     -d '{"username":"alice","password":"password123"}' | jq -r .accessToken)

# protected resource
curl -s localhost:8080/api/me -H "Authorization: Bearer $TOKEN"

# admin API (basic auth also works in dev)
curl -s -u daeyoung0:dae0 localhost:8080/api/admin/dashboard/stats
```

---

## 5. See the defenses actually working

Run these to watch Aegis react. **Do the last two at the end** — they will block your own IP;
restart the app to reset (dev uses an in-memory database).

| Try this | Expected |
|---|---|
| Log in with a wrong password 5× on the same account | 6th attempt returns **423 Locked** even with the right password |
| Inspect response headers (`curl -D - localhost:8080/health`) | `Content-Security-Policy`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy` |
| Call an admin route as a normal user | **403** + an `ACCESS_DENIED` row in the audit log |
| Reuse an already-rotated refresh token | **401** + `TOKEN_REUSE` audit event + **all sessions revoked** |
| Send 65 requests within a minute | **429 Too Many Requests** after the 60th |
| Fail logins 12× using *different* usernames | Your IP is blocked → even `/health` returns **403** |

After each step, refresh the dashboard: counters rise and events appear with actor/IP/result.

---

## 6. Tune it

All knobs live in `src/main/resources/application.yml` under `aegis.security.*`:

| Setting | Default | Meaning |
|---|---|---|
| `lockout.max-failed-attempts` | 5 | Failures before an account is locked |
| `lockout.lock-minutes` | 15 | Lock duration |
| `bruteforce.ip-fail-threshold` | 10 | Failures (per window) before the IP is blocked |
| `bruteforce.ip-fail-window-minutes` | 10 | Sliding window for counting failures |
| `bruteforce.ip-block-minutes` | 10 | Block duration (auto-expires) |
| `rate-limit.requests-per-minute` | 60 | Per-IP request budget |
| `whitelist` | `[]` | IPs exempt from blocking and rate limiting |
| `jwt.expiration-minutes` | 30 | Access token lifetime |
| `jwt.refresh-expiration-minutes` | 10080 | Refresh token lifetime (7 days) |

**Environment variables (production):**

| Variable | Purpose |
|---|---|
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | PostgreSQL connection |
| `AEGIS_JWT_SECRET` | JWT signing secret (32+ bytes, **required**, no default) |
| `AEGIS_ALERT_WEBHOOK` | Optional webhook URL for IP-block / token-reuse alerts |

---

## 7. Integrate Aegis into your own project

Aegis is structured as independent packages, so you can adopt it incrementally:

```
com.aegis
├── auth        authentication, JWT, lockout, roles
├── detection   brute-force detection, IP blocking, rate limiting, metrics
├── security    filter chain order, security headers, CSRF, access-denied handling
├── audit       append-only audit log
├── dashboard   admin monitoring API + page
└── common      global exception handling, error DTO
```

Suggested path:
1. Copy `common` (consistent error responses) and `audit` (the audit trail) first.
2. Add `detection` + the filter wiring in `security/SecurityConfig` — this alone gives you IP blocking and rate limiting.
3. Adopt `auth` if you need JWT authentication, or point the audit/detection hooks at your existing login flow
   (detection listens to Spring Security's `AbstractAuthenticationFailureEvent`, so it works with any auth mechanism).
4. Add `dashboard` last for visibility.

**Request pipeline** (every request passes through, in order):
```
IpBlockFilter (403) → RateLimitFilter (429) → JwtAuthenticationFilter → CSRF → authorization (403) → controller
```

---

## 8. Operating notes & limits

- Run Aegis **behind TLS** (reverse proxy such as nginx/Caddy). It does not terminate HTTPS itself.
- Set `server.forward-headers-strategy` (already on in `prod`) so client IPs are correct behind a proxy.
- Actuator exposes only `health`, `info`, `prometheus`, and **requires authentication**; the public health check is `/health`.
- Schema changes go through **Flyway migrations only** (`ddl-auto=validate`).
- Rate limiting is **in-memory per instance**; for multi-instance deployments implement the `RateLimiter`
  interface with a shared backend (e.g. Redis).
- Dev seed accounts and H2 exist **only** in the `dev` profile. Create production admins through your own process.
- Scope and limitations (no audit/pentest performed, responsible use): see [SECURITY.md](../SECURITY.md).

---

## 9. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Everything returns **403** | Your IP got blocked by brute-force detection → restart the app (dev) or wait for expiry |
| **429** on every request | Rate limit exceeded → wait a minute |
| Login returns **423** | Account locked after 5 failures → wait 15 minutes or restart (dev) |
| `admin` login fails | Not running the `dev` profile — check the log for `[DEV-SEED]` |
| Build fails on startup | Java 21 required; Gradle 8.7 does not run on JDK 22+ |
| Port 8080 in use | Stop the previous instance (`stop.bat` on Windows) |

---

## 10. Where to go next

| Document | Purpose |
|---|---|
| [README.md](../README.md) | Project overview, deployment, KO/EN/JA summaries |
| [STUDY_GUIDE.md](STUDY_GUIDE.md) | Every feature explained (what / why / how / where in code) — *Korean* |
| [TEST_GUIDE.md](TEST_GUIDE.md) | Step-by-step verification recipes — *Korean* |
| [OVERVIEW.md](OVERVIEW.md) | Architecture and UI walkthrough — *Korean* |
| [SECURITY.md](../SECURITY.md) | Scope, limitations, responsible use |
