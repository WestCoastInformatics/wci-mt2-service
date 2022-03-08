
package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Type;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

// TODO: Auto-generated Javadoc
/**
 * Represents a refset.
 * 
 */
@Entity
@Table(name = "refset_history")
@Indexed
public class RefsetEditHistory extends AbstractHasModified implements Comparable<RefsetEditHistory> {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetEditHistory.class);
    
    /** The refset ID. */
    @Column(nullable = false, length = 256)
    private String refsetId;

    /** The name. */
    @Column(nullable = false, length = 4000)
    private String name;

    /** The refset type. */
    @Column(nullable = false, length = 256)
    private String type;
    
    /** The version status. */
    @Column(nullable = false, length = 256)
    private String versionStatus;
    
    /** The workflow status. */
    @Column(nullable = true, length = 256)
    private String workflowStatus;

    /** The version date. */
    @Column(nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    private Date versionDate;

    /** The version narrative. */
    @Column(nullable = true, length = 10000)
    @Type(type = "text")
    private String narrative;

    /** The version status. */
    @Column(nullable = true, length = 10000)
    @Type(type = "text")
    private String versionNotes;

    /** The private flag. */
    @Column(nullable = false)
    private boolean privateRefset;

    /** The local set flag. */
    @Column(nullable = false)
    private boolean localSet;

    /** The module ID. */
    @Column(nullable = false, length = 256)
    private String moduleId;
    
    /** The edit branch ID. */
    @Column(nullable = true, length = 256)
    private String editBranchId;

    /** The external URL. */
    @Column(nullable = true, length = 4000)
    private String externalUrl;
    
    /** The refset member count. */
    @Column(nullable = false)
    private int memberCount = -1;

    /** The tags. */
    @ElementCollection
    private Set<String> tags = new HashSet<String>();

    /** The definition clauses. */
    // @Fetch(FetchMode.JOIN)
    @OneToMany(cascade = CascadeType.ALL, targetEntity = DefinitionClauseEditHistory.class,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DefinitionClauseEditHistory> definitionClauses = new ArrayList<>();
    
    /**
     * Instantiates an empty {@link RefsetEditHistory}.
     */
    public RefsetEditHistory() {
        // n/a
    }

    /**
     * Instantiates a {@link RefsetEditHistory} from the specified parameters.
     *
     * @param code the code
     */
    public RefsetEditHistory(final String code) {
        this.refsetId = code;
    }

    /**
     * Instantiates a {@link RefsetEditHistory} from the specified parameters.
     *
     * @param terminology the terminology
     * @param code the code
     * @param name the name
     */
    public RefsetEditHistory(final String terminology, final String code, final String name) {
        
        this.type = terminology;
        this.refsetId = code;
        this.name = name;
    }

    /**
     * Instantiates a {@link RefsetEditHistory} from the specified parameters.
     *
     * @param other the other
     */
    public RefsetEditHistory(final RefsetEditHistory other) {
        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final RefsetEditHistory other) {
        
        super.populateFrom(other);
        refsetId = other.getRefsetId();
        name = other.getName();
        type = other.getType();
        narrative = other.getNarrative();
        versionDate = other.getVersionDate();
        versionNotes = other.getVersionNotes();
        versionStatus = other.getVersionStatus();
        workflowStatus = other.getWorkflowStatus();
        externalUrl = other.getExternalUrl();
        moduleId = other.getModuleId();
        editBranchId = other.getEditBranchId();
        privateRefset = other.isPrivateRefset();
        definitionClauses = new ArrayList<DefinitionClauseEditHistory>(other.getDefinitionClauses());
        tags = new HashSet<String>(other.getTags());
        memberCount = other.getMemberCount();
    }
    
    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final Refset other) {
        
        super.populateFrom(other);
        refsetId = other.getRefsetId();
        name = other.getName();
        type = other.getType();
        narrative = other.getNarrative();
        versionDate = other.getVersionDate();
        versionNotes = other.getVersionNotes();
        versionStatus = other.getVersionStatus();
        workflowStatus = other.getWorkflowStatus();
        externalUrl = other.getExternalUrl();
        moduleId = other.getModuleId();
        editBranchId = other.getEditBranchId();
        privateRefset = other.isPrivateRefset();
        tags = new HashSet<String>(other.getTags());
        memberCount = other.getMemberCount();
    }

    /**
     * Returns the refset ID.
     *
     * @return the refset ID
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getRefsetId() {
        return refsetId;
    }

    /**
     * Sets the refset ID.
     *
     * @param refsetId the refset ID
     */
    public void setRefsetId(final String refsetId) {
        this.refsetId = refsetId;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the name
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public String getType() {
        return type.toUpperCase();
    }

    /**
     * Sets the type.
     *
     * @param type the type
     */
    public void setType(final String type) {
        this.type = type.toUpperCase();
    }

    /**
     * Returns the version status.
     *
     * @return the version status
     */
    public String getVersionStatus() {
        return versionStatus;
    }

    /**
     * Sets the version.
     *
     * @param versionStatus the version status
     */
    public void setVersionStatus(final String versionStatus) {
        this.versionStatus = versionStatus;
    }
    
    /**
     * Returns the workflow status.
     *
     * @return the workflow status
     */
    public String getWorkflowStatus() {
        return workflowStatus;
    }
    
    /**
     * Sets the workflow status.
     *
     * @param workflowStatus the workflow status
     */
    public void setWorkflowStatus(final String workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    /**
     * Gets the version date.
     *
     * @return the versionDate
     */
    public Date getVersionDate() {
        return versionDate;
    }

    /**
     * Sets the version date.
     *
     * @param versionDate the versionDate to set
     */
    public void setVersionDate(final Date versionDate) {
        this.versionDate = versionDate;
    }

    /**
     * Gets the narrative.
     *
     * @return the narrative
     */
    public String getNarrative() {
        return narrative;
    }

    /**
     * Sets the narrative.
     *
     * @param narrative the narrative to set
     */
    public void setNarrative(final String narrative) {
        this.narrative = narrative;
    }

    /**
     * Gets the version notes.
     *
     * @return the versionNotes
     */
    public String getVersionNotes() {
        return versionNotes;
    }

    /**
     * Sets the version notes.
     *
     * @param versionNotes the versionNotes to set
     */
    public void setVersionNotes(final String versionNotes) {
        this.versionNotes = versionNotes;
    }

    /**
     * Checks if is private refset.
     *
     * @return the isPrivateRefset
     */
    public boolean isPrivateRefset() {
        return privateRefset;
    }

    /**
     * Sets the private refset.
     *
     * @param privateRefset the isPrivateRefset to set
     */
    public void setPrivateRefset(final boolean privateRefset) {
        this.privateRefset = privateRefset;
    }
   
    
    /**
     * Gets the tags.
     *
     * @return the tags
     */
    public Set<String> getTags() {

        if (tags == null) {
            tags = new HashSet<>();
        }

        return tags;
    }

    /**
     * Sets the tags.
     *
     * @param tags the tags to set
     */
    public void setTags(final Set<String> tags) {
        this.tags = tags;
    }
    
    /**
     * Returns the member count.
     *
     * @return the member count
     */
    public int getMemberCount() {
        return memberCount;
    }
    
    /**
     * Sets the member count.
     *
     * @param memberCount the member count to set
     */
    public void setMemberCount(final int memberCount) {
        this.memberCount = memberCount;
    }

    /**
     * Checks if is local set.
     *
     * @return the localSet
     */
    public boolean isLocalSet() {
        return localSet;
    }

    /**
     * Sets the local set.
     *
     * @param localSet the localSet to set
     */
    public void setLocalSet(final boolean localSet) {
        this.localSet = localSet;
    }

    /**
     * Gets the module id.
     *
     * @return the moduleId
     */
    public String getModuleId() {
        return moduleId;
    }

    /**
     * Sets the module id.
     *
     * @param moduleId the moduleId to set
     */
    public void setModuleId(final String moduleId) {
        this.moduleId = moduleId;
    }

    /**
     * Gets the edit branch ID.
     *
     * @return the edit branch ID
     */
    public String getEditBranchId() {
        return editBranchId;
    }

    /**
     * Sets the edit branch ID.
     *
     * @param editBranchId the edit branch ID to set
     */
    public void setEditBranchId(final String editBranchId) {
        this.editBranchId = editBranchId;
    }
    
    /**
     * Gets the external url.
     *
     * @return the externalUrl
     */
    public String getExternalUrl() {
        return externalUrl;
    }
    
    /**
     * Sets the external url.
     *
     * @param externalUrl the externalUrl to set
     */
    public void setExternalUrl(final String externalUrl) {
        this.externalUrl = externalUrl;
    }

    /**
     * Gets the definition clauses.
     *
     * @return the definitionClauses
     */
    public List<DefinitionClauseEditHistory> getDefinitionClauses() {

        if (definitionClauses == null) {
            definitionClauses = new ArrayList<>();
        }

        return definitionClauses;
    }

    /**
     * Sets the definition clauses.
     *
     * @param definitionClauses the definitionClauses to set
     */
    public void setDefinitionClauses(final List<DefinitionClauseEditHistory> definitionClauses) {
        
        Collections.sort(definitionClauses, (o1, o2) -> (o1.getCreated().compareTo(o2.getCreated())));
        this.definitionClauses = definitionClauses;
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((refsetId == null) ? 0 : refsetId.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((versionDate == null) ? 0 : versionDate.hashCode());
        result = prime * result + ((versionStatus == null) ? 0 : versionStatus.hashCode());
        result = prime * result + ((workflowStatus == null) ? 0 : workflowStatus.hashCode());
        result = prime * result + ((narrative == null) ? 0 : narrative.hashCode());
        result = prime * result + ((versionNotes == null) ? 0 : versionNotes.hashCode());
        result = prime * result + ((moduleId == null) ? 0 : moduleId.hashCode());
        result = prime * result + ((editBranchId == null) ? 0 : editBranchId.hashCode());
        result = prime * result + ((externalUrl == null) ? 0 : externalUrl.hashCode());
        result = prime * result + memberCount;
        result = prime * result + (privateRefset ? 1 : 0);
        result = prime * result + (localSet ? 1 : 0);
        return result;
    }
    
    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    @Override
    public boolean equals(final Object obj) {

        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        final RefsetEditHistory other = (RefsetEditHistory) obj;

        if (refsetId == null) {
            if (other.refsetId != null) {
                return false;
            }
        } else if (!refsetId.equals(other.refsetId)) {
            return false;
        }

        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }

        if (type == null) {
            if (other.type != null) {
                return false;
            }
        } else if (!type.equals(other.type)) {
            return false;
        }

        if (versionStatus == null) {
            if (other.versionStatus != null) {
                return false;
            }
        } else if (!versionStatus.equals(other.versionStatus)) {
            return false;
        }
        
        if (workflowStatus == null) {
            if (other.workflowStatus != null) {
                return false;
            }
        } else if (!workflowStatus.equals(other.workflowStatus)) {
            return false;
        }

        if (versionDate == null) {
            if (other.versionDate != null) {
                return false;
            }
        } else if (!versionDate.equals(other.versionDate)) {
            return false;
        }

        if (narrative == null) {
            if (other.narrative != null) {
                return false;
            }
        } else if (!narrative.equals(other.narrative)) {
            return false;
        }

        if (versionNotes == null) {
            if (other.versionNotes != null) {
                return false;
            }
        } else if (!versionNotes.equals(other.versionNotes)) {
            return false;
        }

        if (moduleId == null) {
            if (other.moduleId != null) {
                return false;
            }
        } else if (!moduleId.equals(other.moduleId)) {
            return false;
        }
        
        if (editBranchId == null) {
            if (other.editBranchId != null) {
                return false;
            }
        } else if (!editBranchId.equals(other.editBranchId)) {
            return false;
        }

        if (externalUrl == null) {
            if (other.externalUrl != null) {
                return false;
            }
        } else if (!externalUrl.equals(other.externalUrl)) {
            return false;
        }
        
        if (memberCount != other.memberCount) {
            return false;
        }

        if (privateRefset != other.privateRefset) {
            return false;
        }

        if (localSet != other.localSet) {
            return false;
        }

        return true;
    }

    /**
     * Compare to.
     *
     * @param o the o
     * @return the int
     */
    @Override
    public int compareTo(final RefsetEditHistory o) {
        // Handle null
        return (name + refsetId).compareToIgnoreCase(o.getName() + o.getRefsetId());
    }

    /**
     * Lazy init.
     */
    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub

    }
}
