package com.recruitment.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/api/auth/register/**", "/api/auth/login", "/api/auth/refresh")
                                        .permitAll()
                                        .requestMatchers("/api/public/**")
                                        .permitAll()
                                        // Logo cong ty la thong tin cong khai (FR-C02 dung de hien thi card
                                        // viec lam / trang chi tiet cong ty), khong yeu cau dang nhap. File
                                        // rieng tu (vd resume) KHONG nam trong /uploads/logos/** nen roi vao
                                        // anyRequest().authenticated() ben duoi - chi tai duoc qua endpoint
                                        // co kiem quyen so huu (xem ResumeCandidateController).
                                        .requestMatchers("/uploads/logos/**")
                                        .permitAll()
                                        // Quy uoc path-prefix cho RBAC: chan o tang authorization truoc khi
                                        // DispatcherServlet tim handler, nen candidate goi /api/hr/** van nhan
                                        // dung 403 du co hay khong co controller thuc map path do.
                                        .requestMatchers("/api/hr/**")
                                        .hasRole("HR")
                                        .requestMatchers("/api/candidates/**")
                                        .hasRole("CANDIDATE")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(authenticationEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
