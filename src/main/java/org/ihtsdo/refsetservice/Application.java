package org.ihtsdo.refsetservice;

import java.util.Arrays;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;
import javax.persistence.PersistenceException;

import org.hibernate.search.engine.reporting.spi.RootFailureCollector;
import org.hibernate.search.util.common.SearchException;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
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

        try {
            
            SpringApplication.run(Application.class, args);
            
            try (final TerminologyService service = new TerminologyService()) {
                // just kicking off the lucene reindexing
            }
        
        } catch (PersistenceException e) {
            
            logger.error("Elasticsearch error", e);
            System.exit(1);
        }
        
        logger.debug("REFSET SERVICE MAIN APPLICATION START");
        
        init();
    }
    
    /**
     * Initialize the application once it is started.
     */
    private static void init() throws Exception{

        // don't run this method during tests
        if (!PropertyUtility.getProperty("springProfiles").toLowerCase().contains("test")) {
            //RefsetMemberService.cacheAllMemberAncestors();
        } else {
            logger.debug("Not caching all members during tests.");
        }
    }
}
