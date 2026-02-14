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

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

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
     * EhCache manager factory bean. Uses EhCacheManagerFactoryBean to ensure
     * proper shutdown (dispose) so disk-persistent caches are flushed on exit.
     *
     * @return the eh cache manager factory bean
     */
    @Bean
    public EhCacheManagerFactoryBean ehCacheManagerFactoryBean(
            @Value("${ehcache.cache.manager.name:refsetServiceCacheManager}") final String cacheManagerName) {

        final EhCacheManagerFactoryBean bean = new EhCacheManagerFactoryBean();
        bean.setConfigLocation(new ClassPathResource("ehcache.xml"));
        bean.setShared(false);
        bean.setCacheManagerName(cacheManagerName);
        return bean;
    }

    /**
     * Cache manager.
     *
     * @param ehCacheManager the underlying eh cache manager from factory
     * @return the spring cache manager
     */
    @Bean
    public CacheManager cacheManager(final net.sf.ehcache.CacheManager ehCacheManager) {

        return new EhCacheCacheManager(ehCacheManager);
    }

}
