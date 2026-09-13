package com.aegis.dashboard;

import com.aegis.dashboard.dto.DashboardStats;
import com.aegis.dashboard.dto.MetricsSummary;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대시보드 상단 위협 수준 판정 로직 단위 테스트.
 */
class DashboardViewControllerTest {

    private static DashboardStats stats(long failures24h, long activeBlocked) {
        return new DashboardStats(0, 0, failures24h, activeBlocked, Map.of(),
                new MetricsSummary(0, 0, 0));
    }

    @Test
    void 차단중_IP가_있으면_danger() {
        assertThat(DashboardViewController.threatLevel(stats(0, 1))).isEqualTo("danger");
        // 실패도 있고 차단도 있으면 차단이 우선
        assertThat(DashboardViewController.threatLevel(stats(5, 2))).isEqualTo("danger");
    }

    @Test
    void 차단은_없고_24h_실패만_있으면_warn() {
        assertThat(DashboardViewController.threatLevel(stats(3, 0))).isEqualTo("warn");
    }

    @Test
    void 아무_일도_없으면_ok() {
        assertThat(DashboardViewController.threatLevel(stats(0, 0))).isEqualTo("ok");
    }
}
