package com.toursim.management.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring Cache abstraction. Uses an in-memory ConcurrentMapCache for simplicity.
 * In production, swap ConcurrentMapCacheManager for a Redis/Caffeine CacheManager.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("tours");
    }
}
