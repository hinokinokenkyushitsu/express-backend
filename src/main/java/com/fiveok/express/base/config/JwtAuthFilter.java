package com.fiveok.express.base.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiveok.express.base.utils.JwtUtil;
import com.fiveok.express.base.utils.Result;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * JWT 认证过滤器（共享组件）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    private static final Set<String> WHITE_LIST = Set.of(
            "/api/auth/login",
            "/api/auth/register"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (WHITE_LIST.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, "请先登录");
            return;
        }

        try {
            var claims = jwtUtil.parseToken(token);
            request.setAttribute("userId", Long.parseLong(claims.getSubject()));
            request.setAttribute("role", claims.get("role", String.class));
            chain.doFilter(request, response);
        } catch (JwtException e) {
            log.warn("无效 Token: {}", e.getMessage());
            writeUnauthorized(response, "Token 无效或已过期，请重新登录");
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.unauthorized(msg)));
    }
}
