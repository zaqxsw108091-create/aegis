package com.aegis.detection;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 탐지 메트릭(Micrometer 카운터)이 /actuator/prometheus 에 노출되는지 검증한다.
 * 카운터는 기동 시 등록되므로 값이 0이어도 스크레이프에 나타난다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DetectionMetricsTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @WithMockUser
    void prometheus에_탐지_카운터가_노출된다() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("aegis_detection_login_failures")))
                .andExpect(content().string(Matchers.containsString("aegis_detection_ip_blocked")))
                .andExpect(content().string(Matchers.containsString("aegis_detection_rate_limited")));
    }
}
