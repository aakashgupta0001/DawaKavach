package com.dawacheck.config;

import com.dawacheck.repo.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Passwords are stored as BCrypt hashes and never logged (section 14).
 * Authentication is session based because the frontend calls fetch() with
 * {@code credentials: 'include'} and asks GET /api/auth/me on page load.
 */
@Configuration
public class SecurityConfig {

  private final AppProperties props;

  public SecurityConfig(AppProperties props) {
    this.props = props;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService(UserRepository users) {
    return email -> users.findByEmailIgnoreCase(email)
        .map(u -> User.withUsername(u.getEmail())
            .password(u.getPasswordHash())
            .roles(u.getRole())
            .build())
        .orElseThrow(() -> new UsernameNotFoundException("No account for that email."));
  }

  @Bean
  AuthenticationManager authenticationManager(UserDetailsService uds, PasswordEncoder encoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(uds);
    provider.setPasswordEncoder(encoder);
    // Same message whether the email is unknown or the password is wrong.
    provider.setHideUserNotFoundExceptions(true);
    return provider::authenticate;
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // The API is called by fetch() from the same origin and the session
        // cookie is SameSite=lax. A production deployment should turn CSRF back
        // on and hand the frontend a token from /api/auth/csrf.
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsSource()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/", "/index.html", "/dashboard.html", "/member.html", "/scan.html",
                "/admin.html", "/css/**", "/js/**", "/assets/**", "/favicon.ico",
                "/README.md", "/error")
            .permitAll()
            .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/me",
                "/api/auth/logout")
            .permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/**").authenticated()
            .anyRequest().permitAll())
        // 401 as JSON instead of a redirect to a login page.
        .exceptionHandling(e -> e.authenticationEntryPoint(
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .logout(l -> l.disable())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable);
    return http.build();
  }

  private CorsConfigurationSource corsSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(props.getCorsOrigins() == null ? List.of() : props.getCorsOrigins());
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cfg);
    return source;
  }
}
