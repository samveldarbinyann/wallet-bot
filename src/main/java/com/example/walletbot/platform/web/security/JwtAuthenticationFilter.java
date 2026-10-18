package com.example.walletbot.platform.web.security;

import com.example.walletbot.shared.constant.UserStatus;
import com.example.walletbot.shared.security.JwtService;
import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.infrastructure.UserCacheService;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserCacheService userCacheService;

    @Override
    protected boolean shouldNotFilter(@Nonnull HttpServletRequest request) {
        String path = request.getServletPath();
        boolean isProtected = path.startsWith("/api/miniapp/") || path.startsWith("/api/admin/");
        boolean isPublicAuth = path.equals("/api/miniapp/auth");
        return !isProtected || isPublicAuth;
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Unauthorized\",\"code\":\"UNAUTHORIZED\",\"status\":401}");
    }

    private static final int MAX_TOKEN_LENGTH = 4096;

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
                filterChain.doFilter(request, response);
                return;
            }
            var accessToken = jwtService.parseAccess(token);
            if (accessToken.isPresent()) {
                JwtService.AccessToken jwt = accessToken.get();
                UserEntity user = userCacheService.findById(jwt.userId()).orElse(null);
                if (user == null) {
                    writeUnauthorized(response);
                    return;
                }
                if (jwt.tokenVersion() == null || jwt.tokenVersion() != user.getTokenVersion()) {
                    writeUnauthorized(response);
                    return;
                }
                if (user.getStatus() == UserStatus.BLOCKED) {
                    writeForbidden(response, "ACCOUNT_BLOCKED", user.getStatusReason());
                    return;
                }
                var authority = new SimpleGrantedAuthority("ROLE_" + jwt.role());
                var principal = new AuthenticatedUser(user.getId(), user.getTelegramId(), jwt.role());
                var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(auth);
                request.setAttribute("userLang", user.getLang());
                request.setAttribute(CurrentUserResolver.CURRENT_USER_ATTR, user);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletResponse response, String code, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        StringBuilder json = new StringBuilder();
        json.append("{\"message\":\"Forbidden\",\"code\":\"").append(escapeJson(code)).append("\",\"status\":403");
        if (reason != null) {
            json.append(",\"reason\":\"").append(escapeJson(reason)).append('"');
        }
        json.append('}');
        response.getWriter().write(json.toString());
    }
}
