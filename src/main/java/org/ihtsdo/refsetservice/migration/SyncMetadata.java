package org.ihtsdo.refsetservice.migration;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class Metadata.
 */
public class SyncMetadata {

    /** The modified. */
    private Date modified;

    /** The modified by. */
    private String modifiedBy;

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(SyncMetadata.class);

    /** The sdf. */
    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * Instantiates a new metadata.
     *
     * @param modified the modified
     * @param modifiedBy the modified by
     */
    public SyncMetadata(final String modified, final String modifiedBy) {

        try {

            this.modifiedBy = modifiedBy;

            if (modified == null || modified.isEmpty() || modified.equals("NULL")) {

                this.modified = new Date();

            } else {

                this.modified = sdf.parse(modified.replaceAll("\"", ""));
            }

        } catch (Exception e) {

            logger.error("Failed with mod/modBy: " + modified + " / " + modifiedBy);
            e.printStackTrace();
        }

    }

    /**
     * Instantiates a new metadata.
     *
     * @param modified the modified
     * @param modifiedBy the modified by
     */
    public SyncMetadata(final Date modified, final String modifiedBy) {

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

    public SimpleDateFormat getSdf() {

        return sdf;
    }
}
