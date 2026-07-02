package at.jku.dke.bigkgolap.surface.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(
                      "/api/health",
                      "/actuator/health",
                      "/actuator/health/**",
                      "/actuator/info",
                      "/actuator/prometheus",
                      "/actuator/metrics/**")
                  .permitAll();
              auth.anyRequest().authenticated();
            })
        .httpBasic(httpBasic -> {});
    return http.build();
  }

  @Bean
  public UserDetailsService userDetailsService(LakehouseProperties props) {
    var user =
        User.withUsername(props.auth().username())
            .password(props.auth().password())
            .roles("ADMIN")
            .build();
    return new InMemoryUserDetailsManager(user);
  }

  @SuppressWarnings("deprecation")
  @Bean
  public PasswordEncoder passwordEncoder() {
    return NoOpPasswordEncoder.getInstance();
  }
}
