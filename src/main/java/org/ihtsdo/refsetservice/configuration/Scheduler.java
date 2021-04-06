
package org.ihtsdo.refsetservice.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduler.
 */
@Configuration
@EnableScheduling
public class Scheduler {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(Scheduler.class);

    /**
     * Instantiates an empty {@link Scheduler}.
     */
    public Scheduler() {
        logger.debug("Creating instance of class Scheduler");
    }

}
