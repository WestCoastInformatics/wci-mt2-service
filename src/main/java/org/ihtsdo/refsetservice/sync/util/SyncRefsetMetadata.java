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

    public SyncRefsetMetadata(JsonNode refsetNode, Edition edition, Set<Long> allRefsetVersions, long version, String branchPath) {
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

    protected void setRefsetNode(JsonNode refsetNode) {

        this.refsetNode = refsetNode;
    }

    public Edition getEdition() {

        return edition;
    }

    protected void setEdition(Edition edition) {

        this.edition = edition;
    }

    protected Set<Long> getAllRefsetVersions() {

        return allRefsetVersions;
    }

    protected void setAllRefsetVersions(Set<Long> allRefsetVersions) {

        this.allRefsetVersions = allRefsetVersions;
    }

    public long getVersion() {

        return version;
    }

    protected void setVersion(long version) {

        this.version = version;
    }

    public String getBranchPath() {

        return branchPath;
    }

    protected void setBranchPath(String branchPath) {

        this.branchPath = branchPath;
    }
}
