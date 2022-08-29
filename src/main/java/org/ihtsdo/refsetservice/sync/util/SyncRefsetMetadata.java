package org.ihtsdo.refsetservice.sync.util;

import java.util.Date;
import java.util.Set;

import org.ihtsdo.refsetservice.model.Edition;

import com.fasterxml.jackson.databind.JsonNode;

public class SyncRefsetMetadata {

    private JsonNode refsetNode;

    private Edition edition;

    private Set<Date> allRefsetVersions;

    private Date version;

    private String branchPath;

    public SyncRefsetMetadata(JsonNode refsetNode, Edition edition, Set<Date> allRefsetVersions, Date version, String branchPath) {

        this.refsetNode = refsetNode;
        this.edition = edition;
        this.allRefsetVersions = allRefsetVersions;
        this.version = version;
        this.branchPath = branchPath;
    }

    public JsonNode getRefsetNode() {

        return refsetNode;
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

    protected Set<Date> getAllRefsetVersions() {

        return allRefsetVersions;
    }

    protected void setAllRefsetVersions(Set<Date> allRefsetVersions) {

        this.allRefsetVersions = allRefsetVersions;
    }

    public Date getVersion() {

        return version;
    }

    protected void setVersion(Date version) {

        this.version = version;
    }

    public String getBranchPath() {

        return branchPath;
    }

    protected void setBranchPath(String branchPath) {

        this.branchPath = branchPath;
    }
}
