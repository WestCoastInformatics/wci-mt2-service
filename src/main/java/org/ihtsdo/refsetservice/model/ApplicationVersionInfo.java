/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

/**
 * The Class ApplicationVersionInfo.
 */
public class ApplicationVersionInfo {

    /** The version. */
    private String version;

    /** The time. */
    private String time;

    /**
     * Instantiates a new application version info.
     *
     * @param version the version
     * @param time the time
     */
    public ApplicationVersionInfo(final String version, final String time) {

        this.version = version;
        this.time = time;
    }

    /**
     * Gets the version.
     *
     * @return the version
     */
    public String getVersion() {

        return version;
    }

    /**
     * Gets the time.
     *
     * @return the time
     */
    public String getTime() {

        return time;
    }
}
