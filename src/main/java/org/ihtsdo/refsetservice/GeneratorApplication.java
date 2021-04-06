
package org.ihtsdo.refsetservice;

import org.ihtsdo.refsetservice.service.TerminologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Entry point for webapp.
 */
@Service
class GeneratorApplication {

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

        ApplicationContext app = SpringApplication.run(Application.class, new String[0]);

        logger.debug("TEMPLATE SERVICE GENERATOR APPLICATION START");

        try {

            // Check args/usage
            if (args.length == 0) {
                System.out.println("usage: ... <command> ...args...");
            }

            final String command = args[0];

            // reindex the database
            if (command.equals("reindex")) {

                logger.info("Reindexing Started");

                try (TerminologyService service = new TerminologyService()) {
                    service.computeLuceneIndexes(null);
                }

                logger.info("Reindexing Finished");

            }

            // Run the RTT refset migration importer
            else if (command.equals("migrateRttRefset")) {

                if (args.length != 2) {
                    throw new Exception("Usage: ... migrateRttRefset filePath");
                }
            }

        } catch (

        final Throwable t) {
            logger.error("Unexpected error", t);
            System.exit(1);
        }

        System.exit(0);
    }
}
