/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.util;

import java.util.Optional;

import org.ihtsdo.refsetservice.configuration.AppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * The Class CachingUtility.
 */
public class CachingUtility {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(CachingUtility.class);
    
    /** The Constant cacheManager. */
    final static CacheManager cacheManager = AppContext.getBean(CacheManager.class);

    /**
     * Cache object.
     *
     * @param <T> the generic type
     * @param cacheName the cache name
     * @param cacheKey the cache key
     * @param clazz the clazz
     * @param value the value
     * @throws Exception the exception
     */
    public static <T> void cacheObject(final String cacheName, final String cacheKey, final Class<T> clazz, final Object value) throws Exception {

        if (!clazz.isInstance(value)) {
            throw new Exception("Object is not an instance of class. Invalid class type for caching");
        }

        final Cache cache = cacheManager.getCache(cacheName);
        cache.put(cacheKey, value);

    }

    /**
     * Gets the cached data.
     *
     * @param <T> the generic type
     * @param cacheName the cache name
     * @param cacheKey the cache key
     * @param clazz the clazz
     * @return the cached data
     * @throws ClassCastException the class cast exception
     */
    public static <T> Optional<T> getObject(final String cacheName, final String cacheKey, final Class<T> clazz) throws ClassCastException {

        final Cache conceptsCache = cacheManager.getCache(cacheName);
        final Cache.ValueWrapper cachedValue = conceptsCache.get(cacheKey);

        if (cachedValue != null) {
            if (clazz.isInstance(cachedValue.get())) {
                return Optional.of(clazz.cast(cachedValue.get()));
            } else {
                throw new ClassCastException("The cached object is not an instance of the expected class");
            }
        }

        return Optional.empty();

    }

    /**
     * Contains object.
     *
     * @param cacheName the cache name
     * @param cacheKey the cache key
     * @return true, if successful
     */
    public static boolean containsObjects(final String cacheName, final String cacheKey) {
        
        try {
            final Cache conceptsCache = cacheManager.getCache(cacheName);
            return conceptsCache.get(cacheKey) != null;
        } catch (Exception e) {
            LOG.debug("Failed to find for key: " + cacheKey + " in cache: " + cacheName);
            return false;
        }
    }

    /**
     * Load cache.
     */
    public static void loadCache() {

        for (final String cacheName : cacheManager.getCacheNames()) {
            cacheManager.getCache(cacheName);
        }
    }

    /**
     * Shutdown.
     */
    public static void shutdown() {

        if (cacheManager == null) {
            return;
        }
        cacheManager.getCacheNames().forEach(cacheName -> cacheManager.getCache(cacheName).clear());
    }

}
