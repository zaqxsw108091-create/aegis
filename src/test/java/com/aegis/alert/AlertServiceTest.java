package com.aegis.alert;

import com.aegis.config.AegisSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 웹훅 알림: 비활성 시 no-op, 활성 시 JSON POST 수신, 실패해도 예외 전파 없음.
 */
class AlertServiceTest {

    private AlertService service(String url) {
        var props = new AegisSecurityProperties(
                new AegisSecurityProperties.Jwt("0123456789012345678901234567890123456789", 30, 10080),
                new AegisSecurityProperties.Lockout(5, 15),
                new AegisSecurityProperties.Bruteforce(10, 10, 10),
                new AegisSecurityProperties.RateLimit(60),
                List.of(),
                new AegisSecurityProperties.Alert(url));
        return new AlertService(props, new ObjectMapper());
    }

    @Test
    void URL이_비어있으면_비활성이고_아무_일도_없다() {
        AlertService alert = service("");
        assertThat(alert.isEnabled()).isFalse();
        assertThatCode(() -> alert.notify("IP_BLOCKED", "x").join()).doesNotThrowAnyException();
    }

    @Test
    void 활성화되면_웹훅으로_JSON이_전송된다() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hook", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                received.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            AlertService alert = service("http://localhost:" + port + "/hook");
            assertThat(alert.isEnabled()).isTrue();

            alert.notify("IP_BLOCKED", "ip=203.0.113.9").get(10, TimeUnit.SECONDS);

            assertThat(received.get()).contains("IP_BLOCKED").contains("203.0.113.9").contains("aegis");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void 웹훅_서버가_죽어있어도_예외가_전파되지_않는다() {
        // 아무도 듣지 않는 포트로 전송 → 내부에서 삼키고 정상 종료해야 한다
        AlertService alert = service("http://localhost:1/hook");
        assertThatCode(() -> alert.notify("TOKEN_REUSE", "x").join()).doesNotThrowAnyException();
    }
}
