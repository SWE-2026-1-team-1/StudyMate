package com.studymate.auth.security;

import com.studymate.common.exception.ErrorCode;
import com.studymate.common.dto.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // logout 은 principal 이 필요하므로 authenticated() 로 보호
                .requestMatchers(
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/api/auth/email/send-code",
                        "/api/auth/email/verify",
                        "/api/auth/token/refresh",
                        // Swagger UI
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        // Actuator
                        "/actuator/health"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    // - 인증 실패 시 UNAUTHORIZED JSON 응답
                    ErrorCode code = ErrorCode.UNAUTHORIZED;
                    res.setStatus(code.httpStatus().value());
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.setCharacterEncoding("UTF-8");
                    objectMapper.writeValue(res.getWriter(),
                            new ApiErrorResponse(code.name(), code.defaultMessage()));
                })
            )
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService),
                UsernamePasswordAuthenticationFilter.class
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
