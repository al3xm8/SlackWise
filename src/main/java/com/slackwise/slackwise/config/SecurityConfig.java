package com.slackwise.slackwise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.slackwise.slackwise.security.ApiRateLimitFilter;
import com.slackwise.slackwise.security.AuditLoggingFilter;

@Configuration
public class SecurityConfig {

    @Value("${app.auth.enabled:false}")
    private boolean authEnabled;

    private final ApiRateLimitFilter apiRateLimitFilter;
    private final AuditLoggingFilter auditLoggingFilter;

    public SecurityConfig(ApiRateLimitFilter apiRateLimitFilter, AuditLoggingFilter auditLoggingFilter) {
        this.apiRateLimitFilter = apiRateLimitFilter;
        this.auditLoggingFilter = auditLoggingFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.authorizeHttpRequests(authorize -> {
            if (authEnabled) {
                authorize
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/api/tenants/**", "/api/tickets/**").authenticated()
                    .anyRequest().permitAll();
            } else {
                authorize.anyRequest().permitAll();
            }
        });

        http.addFilterBefore(apiRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(auditLoggingFilter, ApiRateLimitFilter.class);

        if (authEnabled) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        }

        return http.build();
    }
}
