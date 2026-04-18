package com.example.sydneyinfo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
            new CaffeineCache("parking",
                Caffeine.newBuilder().expireAfterWrite(2, TimeUnit.MINUTES).build()),
            new CaffeineCache("weather",
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build()),
            new CaffeineCache("metro",
                Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build()),
            new CaffeineCache("fuel",
                Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build()),
            new CaffeineCache("events",
                Caffeine.newBuilder().expireAfterWrite(60, TimeUnit.MINUTES).build()),
            new CaffeineCache("fuelToken",
                Caffeine.newBuilder().expireAfterWrite(50, TimeUnit.MINUTES).build())
        ));
        return manager;
    }
}
