
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

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(PropertyUtility.class);

    /** the Spring environment variable. */
    @Autowired
    private Environment env;

    /** the config properties cache. */
    private static Properties properties = new Properties();

    /** are the properties ready to be accessed. */
    private static volatile boolean ready = false;

    /**
     * initialize the properties.
     */
    @SuppressWarnings("rawtypes")
    @PostConstruct
    private void init() throws Exception{

        final MutablePropertySources sources = ((AbstractEnvironment) env).getPropertySources();
        logger.info("Property Sources: " + sources.toString());
        StreamSupport.stream(sources.spliterator(), false)
                .filter(ps -> ps instanceof EnumerablePropertySource)
                .map(ps -> ((EnumerablePropertySource) ps).getPropertyNames())
                .flatMap(Arrays::stream).distinct()
                .forEach(prop -> properties.setProperty(prop, env.getProperty(prop)));
        ready = true;
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
     * @param removePrefix Should the prefix be removed from the keys of the
     *            returned properties
     * @return the properties with the specified prefix
     * @throws Exception the exception
     */
    public static Properties getPrefixedProperties(final String prefix, final boolean removePrefix)
        throws Exception {

        assureReadiness();

        final Properties propertiesSubset = new Properties();
        final Iterator<Object> keys = properties.keySet().iterator();

        // get any properties that start with the prefix
        while (keys.hasNext()) {

            String key = keys.next().toString();
            String originalKey = key;

            if (key.startsWith(prefix)) {

                if (removePrefix) {
                    key = key.replace(prefix, "");
                }

                propertiesSubset.put(key, properties.getProperty(originalKey));
            }
        }

        // logger.debug("****** propertiesSubset: ", propertiesSubset);
        return propertiesSubset;
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
     * Ensure that the properties are ready to be accessed before allowing code
     * to continue.
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
        // logger.debug("Properties not ready. Waiting for 1s..");
        // Thread.sleep(1000);
        // } catch (InterruptedException e) {
        //
        // logger.error("Error waiting for properties to load.", e);
        // }
        // }
        // }
        // }
    }
}
