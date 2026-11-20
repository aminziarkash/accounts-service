package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Shows the proxy trap: @Cacheable works because Spring wraps the bean in a proxy. A call from
 * inside the same class never passes through the proxy, so the cache is silently skipped.
 */
@SpringJUnitConfig(CacheSelfInvocationTest.Config.class)
class CacheSelfInvocationTest {

    @Configuration
    @EnableCaching
    static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("demo");
        }

        @Bean
        Lookup lookup() {
            return new Lookup();
        }
    }

    static class Lookup {
        private int calls;

        @Cacheable("demo")
        public String find(String key) {
            calls++;
            return "value-" + key;
        }

        public String findThroughThis(String key) {
            return find(key); // same object, so the proxy is bypassed
        }

        public int calls() {
            return calls;
        }
    }

    @Autowired private Lookup lookup;

    @Test
    void callFromAnotherBeanIsCached() {
        lookup.find("a");
        lookup.find("a");

        assertThat(lookup.calls()).isEqualTo(1);
    }

    @Test
    void callFromInsideTheSameClassSkipsTheCache() {
        int before = lookup.calls();

        lookup.findThroughThis("b");
        lookup.findThroughThis("b");

        assertThat(lookup.calls() - before).isEqualTo(2); // the method ran both times
    }
}
