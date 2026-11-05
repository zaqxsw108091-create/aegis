#!/usr/bin/env bash
# Aegis 자동 점검 스크립트 — 켜져 있는 서버(기본 http://localhost:8080)에 방어 기능을 실제로 시험한다.
# 사용:  bash scripts/verify.sh [서버주소]
# 주의: 점검용 임시 계정을 만들고, 해당 계정만 잠근다. 서버의 IP 자동 차단 임계치(10회)는 넘기지 않는다.
set -u
BASE="${1:-http://localhost:8080}"
JSON='Content-Type: application/json'
PASS=0; FAIL=0
U="verify_$RANDOM$RANDOM"; PW='Str0ng!Passw0rd'

code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
body() { curl -s "$@"; }
check() { # 설명 기대코드 실제코드
  if [ "$2" = "$3" ]; then echo "  ✅ $1 (HTTP $3)"; PASS=$((PASS+1)); else echo "  ❌ $1 (기대 $2, 실제 $3)"; FAIL=$((FAIL+1)); fi
}
json() { python3 -c "import sys,json;print(json.load(sys.stdin).get('$1',''))" 2>/dev/null; }

echo "== Aegis 점검: $BASE =="
check "서버 상태 /health" 200 "$(code "$BASE/health")"
check "회원가입" 201 "$(code -H "$JSON" -X POST "$BASE/api/auth/signup" -d "{\"username\":\"$U\",\"password\":\"$PW\"}")"
check "중복 가입 거부" 409 "$(code -H "$JSON" -X POST "$BASE/api/auth/signup" -d "{\"username\":\"$U\",\"password\":\"$PW\"}")"
check "약한 비밀번호/위험한 아이디 거부" 400 "$(code -H "$JSON" -X POST "$BASE/api/auth/signup" -d '{"username":"a<script>","password":"1234"}')"
check "role=ADMIN 몰래 넣기 거부" 400 "$(code -H "$JSON" -X POST "$BASE/api/auth/signup" -d "{\"username\":\"${U}x\",\"password\":\"$PW\",\"role\":\"ADMIN\"}")"

R=$(body -H "$JSON" -X POST "$BASE/api/auth/login" -d "{\"username\":\"$U\",\"password\":\"$PW\"}")
AT=$(echo "$R" | json accessToken); RT=$(echo "$R" | json refreshToken)
[ -n "$AT" ] && { echo "  ✅ 로그인 → 토큰 발급"; PASS=$((PASS+1)); } || { echo "  ❌ 로그인 실패(토큰 없음)"; FAIL=$((FAIL+1)); }

check "토큰 없이 보호 API 접근 거부" 401 "$(code "$BASE/api/me")"
check "토큰으로 내 정보 조회" 200 "$(code -H "Authorization: Bearer $AT" "$BASE/api/me")"
check "위조 토큰 거부" 401 "$(code -H "Authorization: Bearer ${AT}x" "$BASE/api/me")"
check "일반 사용자의 관리자 API 접근 거부" 403 "$(code -H "Authorization: Bearer $AT" "$BASE/api/admin/dashboard/stats")"
check "비로그인 대시보드 접근 거부" 401 "$(code "$BASE/admin/dashboard")"
check "비로그인 메트릭 접근 거부" 401 "$(code "$BASE/actuator/prometheus")"

R2=$(body -H "$JSON" -X POST "$BASE/api/auth/refresh" -d "{\"refreshToken\":\"$RT\"}")
RT2=$(echo "$R2" | json refreshToken)
[ -n "$RT2" ] && { echo "  ✅ 토큰 재발급(회전)"; PASS=$((PASS+1)); } || { echo "  ❌ 토큰 재발급 실패"; FAIL=$((FAIL+1)); }
check "옛 refresh 토큰 재사용 거부" 401 "$(code -H "$JSON" -X POST "$BASE/api/auth/refresh" -d "{\"refreshToken\":\"$RT\"}")"
check "재사용 감지 → 새 토큰도 폐기" 401 "$(code -H "$JSON" -X POST "$BASE/api/auth/refresh" -d "{\"refreshToken\":\"$RT2\"}")"

echo "-- 계정 잠금 (이 임시 계정에 틀린 비밀번호 5번)"
for i in 1 2 3 4 5; do code -H "$JSON" -X POST "$BASE/api/auth/login" -d "{\"username\":\"$U\",\"password\":\"wrong$i\"}" >/dev/null; done
check "잠금 후엔 올바른 비밀번호도 거부" 423 "$(code -H "$JSON" -X POST "$BASE/api/auth/login" -d "{\"username\":\"$U\",\"password\":\"$PW\"}")"

echo
echo "결과: 통과 $PASS / 실패 $FAIL"
echo "(레이트리밋 429, IP 자동 차단 403은 서버를 막아버리므로 이 스크립트에서 제외 — 자동 테스트 'bash gradlew test' 가 검증합니다)"
[ "$FAIL" -eq 0 ]
