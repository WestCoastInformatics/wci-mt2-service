package org.ihtsdo.refsetservice.migration;

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

    protected SyncRefsetMetadata(JsonNode refsetNode, Edition edition, Set<Date> allRefsetVersions, Date version, String branchPath) {

        this.refsetNode = refsetNode;
        this.edition = edition;
        this.allRefsetVersions = allRefsetVersions;
        this.version = version;
        this.branchPath = branchPath;
    }

    protected JsonNode getRefsetNode() {

        return refsetNode;
    }

    protected void setRefsetNode(JsonNode refsetNode) {

        this.refsetNode = refsetNode;
    }

    protected Edition getEdition() {

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

    protected Date getVersion() {

        return version;
    }

    protected void setVersion(Date version) {

        this.version = version;
    }

    protected String getBranchPath() {

        return branchPath;
    }

    protected void setBranchPath(String branchPath) {

        this.branchPath = branchPath;
    }
}
