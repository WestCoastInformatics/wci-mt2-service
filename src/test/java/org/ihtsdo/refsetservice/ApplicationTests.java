
package org.ihtsdo.refsetservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/**
 * The Class ApplicationTests.
 */
@SpringBootTest
public class ApplicationTests extends BaseTest {

    /** The logger. */
    private static final Logger logger = LoggerFactory.getLogger(ApplicationTests.class);

    /** The context. */
    @Autowired
    ApplicationContext context;

    /** The env. */
    @Autowired
    Environment env;

    /** The config properties. */
    Properties properties = PropertyUtility.getProperties();

    /**
     * Context loads.
     */
    @Test
    public void contextLoads() {
        assertThat(this.context).isNotNull();
        logger.info("context loaded successfully");
    }

    /**
     * Properties loads.
     */
    @Test
    public void propertiesLoads() {
        assertThat(properties.getProperty("spring.application.name")).isEqualTo("ihtsdo-refset-service");
        logger.info("properties loaded successfully");
    }

}
