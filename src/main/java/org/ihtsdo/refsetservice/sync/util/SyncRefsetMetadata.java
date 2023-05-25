package org.ihtsdo.refsetservice.sync.util;

import java.util.Set;

import org.ihtsdo.refsetservice.model.Edition;

import com.fasterxml.jackson.databind.JsonNode;

public class SyncRefsetMetadata {

    private JsonNode refsetNode;

    private Edition edition;

    private Set<Long> allRefsetVersions;

    private long version;

    private String branchPath;

    public SyncRefsetMetadata(final JsonNode refsetNode, final Edition edition, final Set<Long> allRefsetVersions, final long version, final String branchPath) {

        this.refsetNode = refsetNode;
        this.edition = edition;
        this.allRefsetVersions = allRefsetVersions;
        this.version = version;
        this.branchPath = branchPath;
    }

    public JsonNode getRefsetNode() {

        return refsetNode;
    }

    public String getRefsetId() {

        return refsetNode.get("conceptId").asText();
    }

    protected void setRefsetNode(final JsonNode refsetNode) {

        this.refsetNode = refsetNode;
    }

    public Edition getEdition() {

        return edition;
    }

    protected void setEdition(final Edition edition) {

        this.edition = edition;
    }

    protected Set<Long> getAllRefsetVersions() {

        return allRefsetVersions;
    }

    protected void setAllRefsetVersions(final Set<Long> allRefsetVersions) {

        this.allRefsetVersions = allRefsetVersions;
    }

    public long getVersion() {

        return version;
    }

    protected void setVersion(final long version) {

        this.version = version;
    }

    public String getBranchPath() {

        return branchPath;
    }

    protected void setBranchPath(final String branchPath) {

        this.branchPath = branchPath;
    }
}
