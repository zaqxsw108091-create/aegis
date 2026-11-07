package com.aegis.dashboard;

import java.util.Map;

/**
 * 이벤트 유형 → 처음 보는 사람도 이해할 수 있는 한 줄 설명.
 * 대시보드 메인 화면과 "행위자/IP 상세" 드릴다운 화면이 공유한다.
 */
public final class EventExplanations {

    public static final Map<String, String> MAP = Map.ofEntries(
            Map.entry("LOGIN_SUCCESS", "아이디/비밀번호가 맞아 로그인에 성공했습니다. 정상 동작입니다."),
            Map.entry("LOGIN_FAILURE", "아이디 또는 비밀번호가 틀렸습니다. 한 계정에서 일정 횟수 이상 쌓이면 그 계정이 자동으로 잠깁니다."),
            Map.entry("IP_BLOCKED", "같은 IP에서 실패가 임계치를 넘어 자동으로 차단됐거나, 관리자가 수동으로 차단했습니다. 그 IP의 모든 요청이 일정 시간 거부됩니다."),
            Map.entry("RATE_LIMITED", "같은 IP가 1분 안에 너무 많은 요청을 보내 일시적으로 거부됐습니다. 과도한 요청(DoS성 트래픽)을 막는 장치입니다."),
            Map.entry("ACCESS_DENIED", "권한이 없는 자원에 접근하려다 거부됐습니다(예: 일반 사용자가 관리자 화면 접근 시도)."),
            Map.entry("TOKEN_REUSE", "이미 한 번 사용된 refresh 토큰이 다시 들어왔습니다. 토큰이 탈취된 것으로 보고 해당 사용자의 모든 로그인 세션을 즉시 폐기했습니다."),
            Map.entry("LOGOUT", "사용자가 로그아웃하며 토큰을 스스로 폐기했습니다."),
            Map.entry("IP_UNBLOCKED", "관리자가 차단돼 있던 IP를 수동으로 풀어줬습니다."),
            Map.entry("ACCOUNT_UNLOCKED", "관리자가 잠겨 있던 계정을 수동으로 풀고 실패 횟수를 0으로 되돌렸습니다."));

    private EventExplanations() {
    }
}
