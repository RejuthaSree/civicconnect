package com.civic_connect.backend.auth.config;

import com.civic_connect.backend.auth.security.JwtAuthenticationFilter;
import com.civic_connect.backend.auth.oauth.OAuth2LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final OAuth2LoginSuccessHandler SuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    public SecurityConfig(OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.SuccessHandler = oAuth2LoginSuccessHandler;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }
    @Bean //Global CORS across all controllers
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("http://localhost:5500"));//frontend
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                    .csrf(csrf -> csrf.disable())

                    .sessionManagement(session ->
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    )

                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/", "/login", "/oauth2/**","/api/test", "/css/**", "/js/**", "/images/**")
                            .permitAll()
                            .anyRequest()
                            .authenticated()
                    )

                    .oauth2Login(oauth -> oauth
                            .loginPage("/login")
                            .successHandler(SuccessHandler)
                    )

                    .addFilterBefore(jwtAuthenticationFilter,
                            UsernamePasswordAuthenticationFilter.class);
            return http.build();

        }
    }

