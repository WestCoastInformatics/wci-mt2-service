
package org.ihtsdo.refsetservice;

import java.util.Arrays;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.HistoricDataMigrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

/**
 * Entry point for webapp.
 */
@Service
public class GeneratorApplication extends SpringBootServletInitializer {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(GeneratorApplication.class);

    /**
     * Application entry point.
     *
     * @param args the command line arguments
     */
    @SuppressWarnings({
            "resource", "unused"
    })
    public static void main(final String[] args) {

        ApplicationContext app = SpringApplication.run(GeneratorApplication.class, args);
        logger.debug("TEMPLATE SERVICE GENERATOR APPLICATION START");

        try {
            
            logger.debug("********** Args: " + Arrays.toString(args));
            // Check args/usage
            
            if (args.length == 0) {
                System.out.println("usage: ... <command> ...args...");
            }
            
            final String command = args[0];
        
            //reindex the database 
            if (command.equals("reindex")) {
                logger.info("Reindexing Started");
                
                try (TerminologyService service = new TerminologyService()) {
                    service.computeLuceneIndexes(null); 
                }
                
                logger.info("Reindexing Finished");
            }
            
            // Run the RTT refset migration importer 
            else if (command.equals("migrateRttRefset")) {

                try (TerminologyService service = new TerminologyService()) {
                    
                    service.setModifiedBy("Migration");
                    service.setModifiedFlag(true);
                    Edition e = new Edition();
                    e.setName("Test");
                    service.add(e);
                    logger.debug("************ Added Test Edition");
                    service.remove(e);
                }
                
                HistoricDataMigrator migrator = new HistoricDataMigrator();
                migrator.migrate(false);
                logger.info("Historic Data Migration Finished");
            }

        } catch (

        final Throwable t) {
            logger.error("Unexpected error", t);
            System.exit(1);
        }

        System.exit(0);
    }
}
