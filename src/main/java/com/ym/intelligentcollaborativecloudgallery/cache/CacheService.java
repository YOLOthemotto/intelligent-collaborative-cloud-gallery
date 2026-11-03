package com.ym.intelligentcollaborativecloudgallery.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Cache-Aside 缓存门面，支持本地、Redis 和本地 + Redis 多级缓存。
 */
@Service
@Slf4j
public class CacheService {

    private final Cache<String, LocalCacheEntry> localCache = Caffeine.newBuilder()
            .initialCapacity(1024)
            .maximumSize(10_000)
            .expireAfter(new Expiry<String, LocalCacheEntry>() {
                @Override
                public long expireAfterCreate(String key, LocalCacheEntry value, long currentTime) {
                    return value.getTtlNanos();
                }

                @Override
                public long expireAfterUpdate(String key, LocalCacheEntry value, long currentTime,
                                              long currentDuration) {
                    return value.getTtlNanos();
                }

                @Override
                public long expireAfterRead(String key, LocalCacheEntry value, long currentTime,
                                            long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public CacheService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 先按所选缓存层读取；未命中时执行 loader，并将非空结果写回对应缓存层。
     */
    public <T> T getOrLoad(String key, CacheType cacheType, Duration localTtl, Duration redisTtl,
                           TypeReference<T> typeReference, Supplier<T> loader) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(cacheType, "cacheType");
        Objects.requireNonNull(typeReference, "typeReference");
        Objects.requireNonNull(loader, "loader");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("缓存 key 不能为空");
        }
        if (cacheType != CacheType.REDIS) {
            validateTtl(localTtl, "localTtl");
        }
        if (cacheType != CacheType.LOCAL) {
            validateTtl(redisTtl, "redisTtl");
        }

        if (cacheType != CacheType.REDIS) {
            T localValue = readLocal(key, typeReference);
            if (localValue != null) {
                return localValue;
            }
        }

        if (cacheType != CacheType.LOCAL) {
            String redisValue = readRedis(key);
            if (redisValue != null) {
                T value = deserialize(redisValue, typeReference);
                if (value != null) {
                    if (cacheType == CacheType.MULTI_LEVEL) {
                        localCache.put(key, new LocalCacheEntry(redisValue, localTtl));
                    }
                    return value;
                }
            }
        }

        T value = loader.get();
        if (value == null) {
            return null;
        }

        String serializedValue = serialize(value);
        if (cacheType != CacheType.REDIS) {
            localCache.put(key, new LocalCacheEntry(serializedValue, localTtl));
        }
        if (cacheType != CacheType.LOCAL) {
            writeRedis(key, serializedValue, redisTtl);
        }
        return value;
    }

    private <T> T readLocal(String key, TypeReference<T> typeReference) {
        LocalCacheEntry entry = localCache.getIfPresent(key);
        if (entry == null) {
            return null;
        }
        T value = deserialize(entry.getValue(), typeReference);
        if (value == null) {
            localCache.invalidate(key);
        }
        return value;
    }

    private String readRedis(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (DataAccessException e) {
            log.warn("Redis cache read failed for key {}", key, e);
            return null;
        }
    }

    private <T> T deserialize(String value, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(value, typeReference);
        } catch (IOException e) {
            log.warn("Invalid cached JSON; ignoring the cache entry", e);
            return null;
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化缓存值", e);
        }
    }

    private void writeRedis(String key, String value, Duration ttl) {
        try {
            ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
            operations.set(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (DataAccessException e) {
            log.warn("Redis cache write failed for key {}", key, e);
        }
    }

    private void validateTtl(Duration ttl, String name) {
        Objects.requireNonNull(ttl, name);
        if (ttl.isZero() || ttl.isNegative() || ttl.toMillis() == 0) {
            throw new IllegalArgumentException(name + " 必须大于 1 毫秒");
        }
    }

    private static class LocalCacheEntry {
        private final String value;
        private final long ttlNanos;

        private LocalCacheEntry(String value, Duration ttl) {
            this.value = value;
            this.ttlNanos = ttl.toNanos();
        }

        private String getValue() {
            return value;
        }

        private long getTtlNanos() {
            return ttlNanos;
        }
    }
}
