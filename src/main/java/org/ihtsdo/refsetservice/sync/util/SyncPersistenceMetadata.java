/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.sync.util;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Metadata used to persist during sync.
 */
public class SyncPersistenceMetadata {

    /** The modified. */
    private Date modified;

    /** The modified by. */
    private String modifiedBy;

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncPersistenceMetadata.class);

    // TODO: Replace all formatters with DateFormat Class-based instantiations
    /** The Constant ISO_DATE_TIME_FORMAT. */
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * Instantiates a new metadata.
     *
     * @param modified the modified
     * @param modifiedBy the modified by
     */
    public SyncPersistenceMetadata(final String modified, final String modifiedBy) {

        try {

            final SimpleDateFormat sdf = new SimpleDateFormat(ISO_DATE_TIME_FORMAT);

            this.modifiedBy = modifiedBy;

            if (modified == null || modified.isEmpty() || modified.equals("NULL")) {

                this.modified = new Date();

            } else {

                this.modified = sdf.parse(modified.replaceAll("\"", ""));
            }

        } catch (final Exception e) {

            LOG.error("Failed with mod/modBy: " + modified + " / " + modifiedBy);
            e.printStackTrace();
        }

    }

    /**
     * Instantiates a new metadata.
     *
     * @param modified the modified
     * @param modifiedBy the modified by
     */
    public SyncPersistenceMetadata(final Date modified, final String modifiedBy) {

        try {

            this.modified = modified;
            this.modifiedBy = modifiedBy;
        } catch (final Exception e) {

            LOG.error("Failed with mod/modBy: " + modified + " / " + modifiedBy);
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
