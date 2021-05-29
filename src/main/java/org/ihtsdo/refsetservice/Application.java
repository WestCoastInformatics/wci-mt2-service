package org.ihtsdo.refsetservice;

import org.ihtsdo.refsetservice.service.TerminologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for webapp.
 */
@SpringBootApplication(exclude = {
        FlywayAutoConfiguration.class
})
@EnableCaching
@EnableScheduling
public class Application extends SpringBootServletInitializer {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(Application.class);

    /**
     * Configure.
     *
     * @param application the application
     * @return the spring application builder
     */
    @Override
    protected SpringApplicationBuilder configure(final SpringApplicationBuilder application) {
        // TODO: I don't think this ever gets called..
        logger.debug("************ Configure method called");
        return application.sources(Application.class);
    }

    /**
     * Application entry point.
     *
     * @param args the command line arguments
     * @throws Exception the exception
     */
    @SuppressWarnings("resource")
    public static void main(final String[] args) throws Exception {

        SpringApplication.run(Application.class, args);
        
        try (final TerminologyService service = new TerminologyService()) {
            // just kicking off the lucene reindexing
        }
        
        logger.debug("TEMPLATE SERVICE MAIN APPLICATION START");
    }

}
