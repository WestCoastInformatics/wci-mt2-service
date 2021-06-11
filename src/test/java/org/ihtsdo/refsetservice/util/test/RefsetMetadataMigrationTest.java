package org.ihtsdo.refsetservice.util.test;

import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.HistoricDataMigrator;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates the code-system-* files in the "src/resources" folder.
 */
public class RefsetMetadataMigrationTest extends BaseTest {

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(RefsetMetadataMigrationTest.class);


    /**
     * Import all refsets.
     *
     * @throws Exception the exception
     */
//    @Test
    public void testAllRefsets() throws Exception {
        HistoricDataMigrator migrator = new HistoricDataMigrator();
        migrator.migrate();
        logger.info("Historic Data Migration Finished");
    }
}
