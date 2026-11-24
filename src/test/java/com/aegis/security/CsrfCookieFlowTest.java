package com.aegis.security;

import com.aegis.auth.Role;
import com.aegis.auth.User;
import com.aegis.auth.UserRepository;
import com.aegis.detection.BlockedIpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 브라우저와 동일한 CSRF 흐름(쿠키 + 폼 토큰)을 <b>실제 서블릿 컨테이너</b>에서 검증한다.
 *
 * <p><b>왜 MockMvc 가 아니라 RANDOM_PORT 인가</b> — 이 테스트가 막으려는 버그는
 * "응답 전송이 시작된 뒤에야 CSRF 토큰이 만들어져 {@code Set-Cookie} 가 실리지 못하는" 문제다.
 * MockMvc 는 응답을 실제로 전송하지 않아 이 상황이 재현되지 않고, {@code .with(csrf())} 는
 * 쿠키 경로 자체를 건너뛴다. 그래서 실제 톰캣으로 요청해야만 회귀를 잡을 수 있다.
 *
 * <p>검증 내용: 관리자 화면 응답에 XSRF-TOKEN 쿠키가 실리고, 그 쿠키와 폼의 숨은 토큰을
 * 그대로 되돌려 보내면 상태 변경(수동 IP 차단)이 성공한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "AEGIS_ADMIN_USERNAME=csrfadmin",
                "AEGIS_ADMIN_PASSWORD=csrfadmin1234"
        })
class CsrfCookieFlowTest {

    private static final String ADMIN_ID = "csrfadmin";
    private static final String ADMIN_PW = "csrfadmin1234";
    private static final Pattern CSRF_INPUT = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
    private static final Pattern XSRF_COOKIE = Pattern.compile("XSRF-TOKEN=([^;]+)");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private BlockedIpRepository blockedIpRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void seedBigPage() {
        blockedIpRepository.deleteAll();
        // 주의: userRepository.deleteAll() 을 하면 부트스트랩된 관리자까지 지워져 401 이 된다.
        // 버그 재현 조건: 관리자 화면이 응답 버퍼(기본 8KB)를 넘길 만큼 커야 한다.
        // 페이지가 작으면 폼이 버퍼 안에서 렌더링돼 Set-Cookie 가 우연히 실려 버그가 숨는다.
        // 실제 운영에서는 사용자·이벤트가 쌓이면 자연히 이 크기를 넘는다.
        for (int i = 0; i < 150; i++) {
            String name = "bulkuser%03d".formatted(i);
            if (!userRepository.existsByUsername(name)) {
                userRepository.save(new User(name, "hash", Role.USER));
            }
        }
    }

    private TestRestTemplate admin() {
        return rest.withBasicAuth(ADMIN_ID, ADMIN_PW);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void 관리자_화면_응답에_XSRF_TOKEN_쿠키가_실린다() {
        ResponseEntity<String> page = admin().getForEntity(url("/admin/dashboard"), String.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> cookies = page.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies)
                .as("쿠키가 없으면 브라우저가 토큰을 되돌려 보낼 수 없어 관리자 버튼이 항상 403 이 된다")
                .isNotNull();
        assertThat(String.join(";", cookies)).contains("XSRF-TOKEN=");
    }

    @Test
    void 브라우저처럼_쿠키와_폼토큰으로_제출하면_수동차단이_성공한다() {
        // 1) 대시보드를 연다 → 쿠키와 폼의 숨은 토큰을 받는다 (브라우저가 하는 일)
        ResponseEntity<String> page = admin().getForEntity(url("/admin/dashboard"), String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);

        String setCookie = String.join(";", page.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()));
        Matcher cm = XSRF_COOKIE.matcher(setCookie);
        assertThat(cm.find()).as("XSRF-TOKEN 쿠키가 내려와야 한다").isTrue();
        String cookieValue = cm.group(1);

        Matcher fm = CSRF_INPUT.matcher(page.getBody() == null ? "" : page.getBody());
        assertThat(fm.find()).as("폼에 _csrf 숨은 필드가 있어야 한다").isTrue();
        String formToken = fm.group(1);

        // 2) 받은 쿠키와 토큰을 그대로 실어 폼 제출 (버튼 클릭과 동일)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + cookieValue);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("_csrf", formToken);
        form.add("ip", "203.0.113.200");
        form.add("minutes", "10");
        form.add("reason", "csrf-flow-test");

        ResponseEntity<String> res = admin().exchange(url("/admin/blocked-ips"), HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);

        assertThat(res.getStatusCode())
                .as("CSRF 검증을 통과해 리다이렉트되어야 한다(403 이면 쿠키/토큰 불일치)")
                .isEqualTo(HttpStatus.FOUND);
        assertThat(blockedIpRepository.count()).isEqualTo(1);
    }

    @Test
    void 폼토큰_없이_제출하면_403_이고_차단되지_않는다() {
        ResponseEntity<String> page = admin().getForEntity(url("/admin/dashboard"), String.class);
        String setCookie = String.join(";", page.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()));
        Matcher cm = XSRF_COOKIE.matcher(setCookie);
        assertThat(cm.find()).isTrue();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + cm.group(1));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("ip", "203.0.113.201");

        ResponseEntity<String> res = admin().exchange(url("/admin/blocked-ips"), HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blockedIpRepository.count()).isZero();
    }
}
