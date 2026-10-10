package com.membora.platform.common;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TenantGuard {
    public UUID require(UUID organizationId) {
        if (organizationId == null) throw new IllegalArgumentException("X-Organization-Id header is required");
        return organizationId;
    }
}
