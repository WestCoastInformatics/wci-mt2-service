package org.ihtsdo.refsetservice.util;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class Metadata.
 */
public class Metadata {

    /** The modified. */
    private Date modified;

    /** The modified by. */
    private String modifiedBy;

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(Metadata.class);

    /** The sdf. */
    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * Instantiates a new metadata.
     *
     * @param modified the modified
     * @param modifiedBy the modified by
     */
    public Metadata(final String modified, final String modifiedBy) {

        String updatedModified = modified;

        try {

            if (modified == null || modified.isEmpty() || modified.equals("NULL")) {

                updatedModified = new Date().toString();
            }

            this.modified = sdf.parse(updatedModified.replaceAll("\"", ""));
            this.modifiedBy = modifiedBy;
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
    public Metadata(final Date modified, final String modifiedBy) {

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

    public static SimpleDateFormat getSdf() {

        return sdf;
    }
}
