package org.ihtsdo.refsetservice.migration;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class Metadata.
 */
public class MigrationMetadata {

    /** The modified. */
    private Date modified;

    /** The modified by. */
    private String modifiedBy;

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(MigrationMetadata.class);

    MigrationUtilities utilities = new MigrationUtilities();

    /**
     * Instantiates a new metadata.
     *
     * @param modified the modified
     * @param modifiedBy the modified by
     */
    public MigrationMetadata(final String modified, final String modifiedBy) {

        String updatedModified = utilities.getSdf().format(modified);

        try {

            this.modifiedBy = modifiedBy;

            if (modified == null || modified.isEmpty() || modified.equals("NULL")) {

                this.modified = new Date();

            } else {

                this.modified = utilities.getSdf().parse(modified.replaceAll("\"", ""));
            }

        } catch (Exception e) {

            logger.error("Failed with mod/modBy: " + updatedModified.replaceAll("\"", "") + " / " + modifiedBy);
            e.printStackTrace();
        }

    }

    /**
     * Instantiates a new metadata.
     *
     * @param modified the modified
     * @param modifiedBy the modified by
     */
    public MigrationMetadata(final Date modified, final String modifiedBy) {

        try {

            this.modified = modified;
            this.modifiedBy = modifiedBy;
        } catch (Exception e) {

            logger.error("Failed with mod/modBy: " + modified + " / " + modifiedBy);
            e.printStackTrace();
        }

    }

    /**
     * Gets the modified.
     *
     * @return the modified
     */
    public Date getModified() {

        return modified;
    }

    /**
     * Gets the modified by.
     *
     * @return the modified by
     */
    public String getModifiedBy() {

        return modifiedBy;
    }
}
