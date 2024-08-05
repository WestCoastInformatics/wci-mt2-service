/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Properties;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

/**
 * Set up config properties cache.
 */
@Component
public class PropertyUtility {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(PropertyUtility.class);

    /** the Spring environment variable. */
    @Autowired
    private Environment env;

    /** the config properties cache. */
    private static Properties properties = new Properties();

    /** are the properties ready to be accessed. */
    private static volatile boolean ready = false;

    /**
     * initialize the properties.
     *
     * @throws Exception the exception
     */
    @SuppressWarnings("rawtypes")
    @PostConstruct
    private void init() throws Exception {

        final MutablePropertySources sources = ((AbstractEnvironment) env).getPropertySources();

        LOG.info("Property Sources: " + sources.toString());

        StreamSupport.stream(sources.spliterator(), false).filter(ps -> ps instanceof EnumerablePropertySource)
            .map(ps -> ((EnumerablePropertySource) ps).getPropertyNames()).flatMap(Arrays::stream).distinct()
            .forEach(prop -> properties.setProperty(prop, env.getProperty(prop)));
        ready = true;

        properties.setProperty("springProfiles", Arrays.toString(env.getActiveProfiles()));

        // only uncomment for testing - do not print out properties in Production environments
        // TreeSet<Object> sortedPropertyNames = new TreeSet<>(properties.keySet());
        // for (Object propertyName : sortedPropertyNames) {
        // LOG.info("Property: " + propertyName + " = " + properties.get(propertyName));
        // }

    }

    /**
     * get all properties.
     *
     * @return the properties
     */
    public static Properties getProperties() {

        assureReadiness();
        return properties;
    }

    /**
     * update active status.
     *
     * @param key the property key
     * @param value The property value
     */
    public static void setProperty(final String key, final String value) {

        assureReadiness();
        properties.put(key, value);
    }

    /**
     * update active status.
     *
     * @param key The key of the property to return
     * @return the value of the requested property or null
     */
    public static String getProperty(final String key) {

        assureReadiness();

        if (properties.containsKey(key)) {
            return properties.getProperty(key);
        }

        return null;
    }

    /**
     * Return properties with the specified prefix.
     *
     * @param prefix the prefix of the properties to return
     * @param removePrefix Should the prefix be removed from the keys of the returned properties
     * @return the properties with the specified prefix
     * @throws Exception the exception
     */
    public static Properties getPrefixedProperties(final String prefix, final boolean removePrefix) throws Exception {

        assureReadiness();

        final Properties propertiesSubset = new Properties();
        final Iterator<Object> keys = properties.keySet().iterator();

        // get any properties that start with the prefix
        while (keys.hasNext()) {

            String key = keys.next().toString();
            final String originalKey = key;

            if (key.startsWith(prefix)) {

                if (removePrefix) {
                    key = key.replace(prefix, "");
                }

                propertiesSubset.put(key, properties.getProperty(originalKey));
            }
        }

        // LOG.debug("****** propertiesSubset: ", propertiesSubset);
        return propertiesSubset;
    }

    /**
     * Return JPA specific properties, including any additional info.
     *
     * @return the JPA properties
     * @throws Exception the exception
     */
    public static Properties getJpaProperties() throws Exception {

        assureReadiness();

        final Properties jpaProperties = getPrefixedProperties("spring.jpa.properties.", true);

        // additional JPA properties that are not included in the properties file
        if (!"[test]".equalsIgnoreCase(properties.getProperty("springProfiles"))) {
            jpaProperties.put("hibernate.search.backend.analysis.configurer",
                "class:org.ihtsdo.refsetservice.configuration.ElasticsearchCustomAnalysisConfigurer");
        } else {
            jpaProperties.put("hibernate.search.backend.analysis.configurer", "class:org.ihtsdo.refsetservice.configuration.LuceneCustomAnalysisConfigurer");
        }

        return jpaProperties;
    }

    /**
     * Are the properties ready to be retrieved.
     * 
     * @return the properties ready status
     */
    @SuppressWarnings("unused")
    private static boolean isReady() {

        return ready;
    }

    /**
     * Ensure that the properties are ready to be accessed before allowing code to continue.
     */
    public static void assureReadiness() {

        if (true) {
            return;
        }

        // if (!isReady()) {
        //
        // synchronized (properties) {
        //
        // while (!isReady()) {
        //
        // try {
        //
        // LOG.debug("Properties not ready. Waiting for 1s..");
        // Thread.sleep(1000);
        // } catch (InterruptedException e) {
        //
        // LOG.error("Error waiting for properties to load.", e);
        // }
        // }
        // }
        // }
    }
}
