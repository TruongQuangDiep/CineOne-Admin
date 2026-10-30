package com.example.cineone_admin.config;

import com.example.cineone_admin.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Tắt CSRF cho toàn bộ REST API
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            
            // 2. Phân quyền endpoint
            .authorizeHttpRequests(auth -> auth
                // Endpoint REST API công khai
                .requestMatchers("/api/v1/auth/**", "/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/seats/held").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/movies/**", "/api/v1/cinemas/**", "/api/v1/showtimes/**").permitAll()

                // Endpoint REST API bắt buộc xác thực qua Token
                .requestMatchers(HttpMethod.POST, "/api/v1/seats/hold", "/api/v1/seats/release").authenticated()
                .requestMatchers("/api/v1/bookings/**", "/api/v1/vouchers/**", "/api/v1/tickets/**", "/api/v1/users/**").authenticated()

                // Giao diện Web Admin và tài nguyên tĩnh
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/webjars/**").permitAll()
                .anyRequest().hasRole("ADMIN")
            )
            
            // 3. Xử lý lỗi xác thực: Trả về HTTP 401 JSON cho API thay vì chuyển hướng 302 sang trang HTML
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    (request, response, authException) -> {
                        response.setContentType("application/json;charset=UTF-8");
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("{\"status\":401,\"message\":\"Yêu cầu xác thực tài khoản hoặc token không hợp lệ.\",\"data\":null}");
                    },
                    request -> request.getRequestURI().startsWith("/api/") // Dùng Lambda trực tiếp, không cần AntPathRequestMatcher
                )
            )
            
            // 4. Gắn Filter xử lý JWT trước bộ lọc mặc định của Spring
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            
            // 5. Luồng đăng nhập Form Login cho trang Web Admin
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            
            // 6. Luồng đăng xuất Web Admin
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }
}