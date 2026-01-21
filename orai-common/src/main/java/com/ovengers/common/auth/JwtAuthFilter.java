package com.ovengers.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${app.admin.department-id:team9}")
    private String adminDepartmentId;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String departmentId = request.getHeader("X-User-DepartmentId");
        if (departmentId == null) {
            departmentId = "";
        }

        log.debug("X-User-Id: {}", userId);
        log.debug("X-User-DepartmentId: {}", departmentId);
        log.debug("Request URL: {}", request.getRequestURI());

        if (userId != null) {
            String userRole = adminDepartmentId.equals(departmentId) ? "ADMIN" : "USER";
            log.debug("userRole: {}", userRole);

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + userRole));

            Authentication auth = new UsernamePasswordAuthenticationToken(
                    new TokenUserInfo(userId, departmentId, userRole),
                    "",
                    authorities
            );

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
