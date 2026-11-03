package com.ym.intelligentcollaborativecloudgallery.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CacheServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new CacheService(redisTemplate, new ObjectMapper());
    }

    @Test
    void localModeUsesOnlyLocalCache() {
        AtomicInteger loadCount = new AtomicInteger();

        String first = cacheService.getOrLoad("local-key", CacheType.LOCAL, Duration.ofMinutes(1),
                Duration.ofMinutes(5), new TypeReference<String>() { },
                () -> "loaded-" + loadCount.incrementAndGet());
        String second = cacheService.getOrLoad("local-key", CacheType.LOCAL, Duration.ofMinutes(1),
                Duration.ofMinutes(5), new TypeReference<String>() { },
                () -> "loaded-" + loadCount.incrementAndGet());

        assertEquals("loaded-1", first);
        assertEquals(first, second);
        assertEquals(1, loadCount.get());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void redisModeReadsAndWritesRedisWithoutUsingLocalCache() {
        AtomicInteger loadCount = new AtomicInteger();
        when(valueOperations.get("redis-key")).thenReturn(null);

        String first = cacheService.getOrLoad("redis-key", CacheType.REDIS, Duration.ofMinutes(1),
                Duration.ofMinutes(5), new TypeReference<String>() { },
                () -> "loaded-" + loadCount.incrementAndGet());
        String second = cacheService.getOrLoad("redis-key", CacheType.REDIS, Duration.ofMinutes(1),
                Duration.ofMinutes(5), new TypeReference<String>() { },
                () -> "loaded-" + loadCount.incrementAndGet());

        assertEquals("loaded-1", first);
        assertEquals("loaded-2", second);
        assertEquals(2, loadCount.get());
        verify(valueOperations, times(2)).get("redis-key");
        verify(valueOperations, times(2)).set(eq("redis-key"), anyString(), eq(300_000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void multiLevelModeBackfillsLocalCacheAfterRedisHit() {
        when(valueOperations.get("multi-key")).thenReturn("\"from-redis\"");

        String redisResult = cacheService.getOrLoad("multi-key", CacheType.MULTI_LEVEL, Duration.ofMinutes(1),
                Duration.ofMinutes(5), new TypeReference<String>() { }, () -> "from-loader");
        String localResult = cacheService.getOrLoad("multi-key", CacheType.MULTI_LEVEL, Duration.ofMinutes(1),
                Duration.ofMinutes(5), new TypeReference<String>() { }, () -> "from-loader");

        assertEquals("from-redis", redisResult);
        assertEquals(redisResult, localResult);
        verify(valueOperations, times(1)).get("multi-key");
    }
}
