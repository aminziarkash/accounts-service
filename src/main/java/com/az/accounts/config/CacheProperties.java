package com.az.accounts.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param profileMaxSize the most customer profiles kept in the cache at once (default 1000)
 * @param profileTtl how long a profile stays cached after it was written (default 5 minutes)
 */
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(Integer profileMaxSize, Duration profileTtl) {

    public CacheProperties {
        profileMaxSize = profileMaxSize == null ? 1000 : profileMaxSize;
        profileTtl = profileTtl == null ? Duration.ofMinutes(5) : profileTtl;
        if (profileMaxSize < 1) {
            throw new IllegalArgumentException("app.cache.profile-max-size must be at least 1");
        }
        if (profileTtl.isZero() || profileTtl.isNegative()) {
            throw new IllegalArgumentException("app.cache.profile-ttl must be above 0");
        }
    }
}
