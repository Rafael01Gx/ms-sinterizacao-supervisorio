package br.com.sinterpiloto.supervisorio.infra.config.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

@Slf4j
public class ResilientCacheErrorHandler implements CacheErrorHandler {
    @Override
    public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
        log.warn("Cache GET falhou — cache='{}', key='{}'. Buscando na fonte de dados. Causa: {}",
                cache.getName(), key, e.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
        log.warn("Cache PUT falhou — cache='{}', key='{}'. Dado não foi cacheado. Causa: {}",
                cache.getName(), key, e.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
        log.warn("Cache EVICT falhou — cache='{}', key='{}'. Causa: {}",
                cache.getName(), key, e.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException e, Cache cache) {
        log.warn("Cache CLEAR falhou — cache='{}'. Causa: {}",
                cache.getName(), e.getMessage());
    }
}
