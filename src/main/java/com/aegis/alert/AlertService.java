package com.aegis.alert;

import com.aegis.config.AegisSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 중대 보안 이벤트를 웹훅으로 알린다 (예: Slack/Discord/자체 수집 서버).
 *
 * <p>원칙:
 * <ul>
 *   <li>URL 미설정(기본)이면 완전 비활성 — 아무 것도 하지 않는다.</li>
 *   <li>비동기 전송 — 요청 처리 경로를 절대 지연시키지 않는다.</li>
 *   <li>실패 안전 — 알림 서버 장애/타임아웃은 경고 로그만 남기고 삼킨다(보안 기능에 무영향).</li>
 *   <li>페이로드에 비밀번호/토큰 등 민감정보는 담지 않는다.</li>
 * </ul>
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final String webhookUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public AlertService(AegisSecurityProperties props, ObjectMapper objectMapper) {
        this.webhookUrl = props.alert().webhookUrl();
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public boolean isEnabled() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    /**
     * 보안 알림 전송(비동기). 비활성이거나 실패해도 호출자에게 영향 없음.
     *
     * @return 전송 완료 future (테스트/관찰용)
     */
    public CompletableFuture<Void> notify(String event, String detail) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                String body = objectMapper.writeValueAsString(Map.of(
                        "source", "aegis",
                        "event", event,
                        "detail", detail,
                        "timestamp", OffsetDateTime.now().toString()));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                client.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                // 알림 실패는 보안 기능에 영향을 주지 않는다(로그만).
                log.warn("[ALERT] 웹훅 전송 실패 event={} cause={}", event, e.toString());
            }
        });
    }
}
