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

import java.util.Set;

import org.ihtsdo.refsetservice.model.Edition;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The Class SyncRefsetMetadata.
 */
public class SyncRefsetMetadata {

    /** The refset node. */
    private JsonNode refsetNode;

    /** The edition. */
    private Edition edition;

    /** The all refset versions. */
    private Set<Long> allRefsetVersions;

    /** The version. */
    private long version;

    /** The branch path. */
    private String branchPath;

    /** The module Id . */
    private String moduleId;

    /**
     * Instantiates a {@link SyncRefsetMetadata} from the specified parameters.
     *
     * @param refsetNode the refset node
     * @param edition the edition
     * @param allRefsetVersions the all refset versions
     * @param version the version
     * @param branchPath the branch path
     */
    public SyncRefsetMetadata(final JsonNode refsetNode, final Edition edition, final Set<Long> allRefsetVersions, final long version, final String branchPath,
        final String moduleId) {

        this.refsetNode = refsetNode;
        this.edition = edition;
        this.allRefsetVersions = allRefsetVersions;
        this.version = version;
        this.branchPath = branchPath;
        this.moduleId = moduleId;
    }

    /**
     * Returns the refset node.
     *
     * @return the refset node
     */
    public JsonNode getRefsetNode() {

        return refsetNode;
    }

    /**
     * Returns the refset id.
     *
     * @return the refset id
     */
    public String getRefsetId() {

        return refsetNode.get("conceptId").asText();
    }

    /**
     * Sets the refset node.
     *
     * @param refsetNode the refset node
     */
    protected void setRefsetNode(final JsonNode refsetNode) {

        this.refsetNode = refsetNode;
    }

    /**
     * Returns the edition.
     *
     * @return the edition
     */
    public Edition getEdition() {

        return edition;
    }

    /**
     * Sets the edition.
     *
     * @param edition the edition
     */
    protected void setEdition(final Edition edition) {

        this.edition = edition;
    }

    /**
     * Returns the all refset versions.
     *
     * @return the all refset versions
     */
    protected Set<Long> getAllRefsetVersions() {

        return allRefsetVersions;
    }

    /**
     * Sets the all refset versions.
     *
     * @param allRefsetVersions the all refset versions
     */
    protected void setAllRefsetVersions(final Set<Long> allRefsetVersions) {

        this.allRefsetVersions = allRefsetVersions;
    }

    /**
     * Returns the version.
     *
     * @return the version
     */
    public long getVersion() {

        return version;
    }

    /**
     * Sets the version.
     *
     * @param version the version
     */
    protected void setVersion(final long version) {

        this.version = version;
    }

    /**
     * Returns the branch path.
     *
     * @return the branch path
     */
    public String getBranchPath() {

        return branchPath;
    }

    /**
     * Sets the branch path.
     *
     * @param branchPath the branch path
     */
    protected void setBranchPath(final String branchPath) {

        this.branchPath = branchPath;
    }

    /**
     * Returns the module Id.
     *
     * @return the module Id
     */
    public String getModuleId() {

        return moduleId;
    }

    /**
     * Sets the module Id.
     *
     * @param moduleId the module Id
     */
    protected void setModuleId(final String moduleId) {

        this.moduleId = moduleId;
    }
}
