/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are public by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.sync.util;

/**
 * The Class SyncRefsetMetadata.
 */
public class SyncProjectMetadata {

    /** The project's rtt id. */
    private String rttId;

    /** The crowd id within the rule associated with project. */
    private String organizationCrowdId;

    /** The project's name. */
    private String name;

    /** The project's description. */
    private String description;

    /** The project's corresponding edition. */
    private String editionShortName;

    /**
     * Instantiates a {@link SyncProjectMetadata} from the specified parameters.
     *
     * @param rttId the rtt project id
     * @param organizationCrowdId the organization crowd Id
     * @param name the name name
     * @param description the name description
     */
    public SyncProjectMetadata(final String rttId, final String organizationCrowdId, final String name, final String description, final String editionShortName) {

        this.rttId = rttId;
        this.organizationCrowdId = organizationCrowdId;
        this.name = name;
        this.description = description;
        this.editionShortName = editionShortName;
    }

    /**
     * Returns the project's rtt id.
     *
     * @return the project's rtt id.
     */
    public String getRttId() {

        return rttId;
    }

    /**
     * Sets the project's rtt id.
     *
     * @param rttId project's rtt id.
     */
    public void setRttId(final String rttId) {

        this.rttId = rttId;
    }

    /**
     * Returns the project's name.
     *
     * @return the project's name.
     */
    public String getName() {

        return name;
    }

    /**
     * Sets the project's name.
     *
     * @param name the project's name.
     */
    public void setName(final String name) {

        this.name = name;
    }

    /**
     * Returns the project's description.
     *
     * @return the project's description
     */
    public String getDescription() {

        return description;
    }

    /**
     * Sets the project's description.
     *
     * @param description the project's description
     */
    public void setDescription(final String description) {

        this.description = description;
    }

    /**
     * Returns the project's id in crowd (if exists).
     *
     * @return the crowd Id
     */
    public String getCrowdId() {

        return organizationCrowdId;
    }

    /**
     * Sets the project's id in crowd (if exists).
     *
     * @param organizationCrowdId the organization crowd Id
     */
    public void setCrowdId(final String organizationCrowdId) {

        this.organizationCrowdId = organizationCrowdId;
    }

    /**
     * Returns the edition's short name.
     *
     * @return the edition's short name
     */
    public String getEditionShortName() {

        return editionShortName;
    }

    /**
     * Sets the edition's short name.
     *
     * @param editionShortName edition's short name
     */
    public void setEditionShortName(final String editionShortName) {

        this.editionShortName = editionShortName;
    }
}
