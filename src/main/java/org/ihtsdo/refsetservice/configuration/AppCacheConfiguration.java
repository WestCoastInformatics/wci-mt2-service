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

import java.net.URI;
import java.net.URL;

import javax.cache.Caching;

import org.ehcache.config.Configuration;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.ehcache.xml.XmlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

/**
 * Cache configuration (EhCache 3 via JCache).
 *
 * @author Arun
 */
@org.springframework.context.annotation.Configuration
@EnableCaching
public class AppCacheConfiguration {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(AppCacheConfiguration.class);

    /**
     * JCache manager. Unique URI per cache manager name so parallel tests do not share one JCache manager.
     *
     * @param cacheManagerName the cache manager name
     * @return the JCache manager
     * @throws Exception the exception
     */
    @Bean(destroyMethod = "close")
    public javax.cache.CacheManager jCacheManager(@Value("${ehcache.cache.manager.name:refsetServiceCacheManager}") final String cacheManagerName)
        throws Exception {

        final URL xmlUrl = new ClassPathResource("ehcache.xml").getURL();
        final Configuration xmlConfig = new XmlConfiguration(xmlUrl);
        final EhcacheCachingProvider provider = (EhcacheCachingProvider) Caching.getCachingProvider(EhcacheCachingProvider.class.getName());
        return provider.getCacheManager(URI.create("ehcache://" + cacheManagerName), xmlConfig);
    }

    /**
     * Spring cache manager wrapping JCache.
     *
     * @param jCacheManager the JCache manager
     * @return the spring cache manager
     */
    @Bean
    public CacheManager cacheManager(final javax.cache.CacheManager jCacheManager) {

        return new JCacheCacheManager(jCacheManager);
    }

}
