package com.example.cineone_admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .authorizeHttpRequests(auth -> auth
                // Endpoint công khai
                .requestMatchers(HttpMethod.GET, "/api/v1/seats/held").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/movies/**", "/api/v1/cinemas/**", "/api/v1/showtimes/**").permitAll()
                
                // Endpoint giữ / nhả ghế (tạm thời để authenticated hoặc permitAll tùy bạn test)
                .requestMatchers(HttpMethod.POST, "/api/v1/seats/hold", "/api/v1/seats/release").permitAll()

                // Web Admin & tài nguyên tĩnh
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/webjars/**").permitAll()
                .anyRequest().hasRole("ADMIN")
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
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