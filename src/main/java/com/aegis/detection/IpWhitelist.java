package com.aegis.detection;

import com.aegis.config.AegisSecurityProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 차단/레이트리밋 면제 IP(화이트리스트). 정확히 일치하는 IP만 면제한다.
 * (CIDR 대역 지원은 추후 확장 여지)
 */
@Component
public class IpWhitelist {

    private final Set<String> exempt;

    public IpWhitelist(AegisSecurityProperties props) {
        this.exempt = Set.copyOf(props.whitelist());
    }

    public boolean isWhitelisted(String ip) {
        return ip != null && exempt.contains(ip);
    }
}
