package com.az.accounts.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    /** Name of the customer profile cache, shared by the annotations and the tests. */
    public static final String PROFILES = "profiles";

    @Bean
    CacheManager cacheManager(CacheProperties props) {
        CaffeineCacheManager manager = new CaffeineCacheManager(PROFILES);
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(props.profileMaxSize())
                        .expireAfterWrite(props.profileTtl())
                        .recordStats());
        return manager;
    }
}
