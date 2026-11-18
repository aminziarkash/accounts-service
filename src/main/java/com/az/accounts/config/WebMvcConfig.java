package com.az.accounts.config;

import com.az.accounts.api.web.RateLimitInterceptor;
import com.az.accounts.api.web.RequestTimingInterceptor;
import com.az.accounts.service.RateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({CorsProperties.class, RateLimitProperties.class})
public class WebMvcConfig implements WebMvcConfigurer {

    private final CorsProperties cors;
    private final RateLimiter rateLimiter;

    public WebMvcConfig(CorsProperties cors, RateLimiter rateLimiter) {
        this.cors = cors;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestTimingInterceptor()).addPathPatterns("/api/**");
        registry.addInterceptor(new RateLimitInterceptor(rateLimiter)).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (cors.allowedOrigins().isEmpty()) {
            return; // default: no cross-origin access at all
        }
        registry.addMapping("/api/**")
                .allowedOrigins(cors.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders(
                        "Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id")
                .exposedHeaders("Location", "Retry-After", "X-Correlation-Id")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
