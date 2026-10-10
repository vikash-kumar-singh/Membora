package com.membora.platform.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantAuthorizationFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        return path.startsWith("/actuator/health") || path.startsWith("/api/v1/auth/")
            || path.startsWith("/api/v1/media/")
            || (path.equals("/api/v1/organizations") && request.getMethod().equals("POST"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            var claimedOrganization = jwt.getToken().getClaimAsString("org_id");
            var requestedOrganization = request.getHeader("X-Organization-Id");
            if (requestedOrganization == null || !requestedOrganization.equals(claimedOrganization)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant context does not match authenticated organization"); return;
            }
        }
        chain.doFilter(request, response);
    }
}
