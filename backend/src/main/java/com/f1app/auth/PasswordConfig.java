package com.f1app.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    /**
     * BCrypt at strength 10 (the default): roughly 100ms per hash on modern
     * hardware. That slowness is the point - it is what makes offline brute
     * forcing of a leaked table expensive. Raising the cost factor is the knob
     * to turn as hardware improves.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
