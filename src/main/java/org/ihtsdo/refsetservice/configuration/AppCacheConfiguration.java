/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache configuration.
 *
 * @author Arun
 */
@Configuration
@EnableCaching
public class AppCacheConfiguration {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(AppCacheConfiguration.class);

    /**
     * Eh cache manager.
     *
     * @return the net.sf.ehcache. cache manager
     */
    @Bean
    public net.sf.ehcache.CacheManager ehCacheManager() {

        return net.sf.ehcache.CacheManager.create();
    }

    /**
     * Cache manager.
     *
     * @return the cache manager
     */
    @Bean
    public CacheManager cacheManager() {

        return new EhCacheCacheManager(ehCacheManager());
    }

}
