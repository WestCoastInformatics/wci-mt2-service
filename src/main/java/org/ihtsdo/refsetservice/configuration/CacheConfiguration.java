/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.configuration;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;

/**
 * Cache configuration.
 *
 * @author Arun
 */
@Configuration
public class CacheConfiguration {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(CacheConfiguration.class);

    /** the cache manager *. */
    @Autowired
    private CacheManager cacheManager;

    /**
     * Scheduled method to evict all cache managed by spring cache manager.
     * 
     * The schedule is defined by the cron expression.
     */
    @Scheduled(cron = "0 0 0 * * ?", zone = "America/Los_Angeles")
    public void evictAll() {

        LOG.info("evictAll()");
        final Collection<String> cacheNames = cacheManager.getCacheNames();
        if (CollectionUtils.isEmpty(cacheNames)) {
            return;
        }

        cacheNames.stream().forEach(name -> cacheManager.getCache(name).clear());
    }

}
