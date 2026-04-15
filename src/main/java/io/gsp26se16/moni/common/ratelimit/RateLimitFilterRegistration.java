package io.gsp26se16.moni.common.ratelimit;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers RateLimitFilter with highest precedence so it runs
 * BEFORE Spring Security filter chain — ensuring even unauthenticated
 * requests (e.g. brute-force login attempts) are rate-limited first.
 */
@Configuration
public class RateLimitFilterRegistration {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE); // runs before Spring Security
        registration.addUrlPatterns("/*");
        return registration;
    }
}
