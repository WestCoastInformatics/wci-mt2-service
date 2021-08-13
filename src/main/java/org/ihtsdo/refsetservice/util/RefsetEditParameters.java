
package org.ihtsdo.refsetservice.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.ihtsdo.refsetservice.model.DefinitionClause;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Represents parameters for creating or modifying a refset.
 */
@JsonInclude(Include.NON_EMPTY)
public class RefsetEditParameters {

    /** The name. */
    private String name;

    /** The refset concept ID. */
    private String refsetConceptId;
    
    /** The parent concept ID. */
    private String parentConceptId;
    
    /** The branch. */
    private String branch;
    
    /** The project ID. */
    private String projectId;
    
    /** The organization ID. */
    private String organizationId;
    
    /** The narrative. */
    private String narrative;
    
    /** The version notes. */
    private String versionNotes;
    
    /** The definition clauses. */
    private List<DefinitionClause> definitionClauses = new ArrayList<>();
    
    /** The refset type (Extensional or Intesional). */
    private String type;
    
    /** The tags. */
    private Set<String> tags;

    /** The sort ascending. */
    private Boolean localSet;

    /**
     * Instantiates an empty {@link RefsetEditParameters}.
     */
    public RefsetEditParameters() {
        // n/a
    }

    /**
     * Instantiates a {@link RefsetEditParameters} from the specified parameters.
     *
     * @param other the other
     */
    public RefsetEditParameters(final RefsetEditParameters other) {
        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final RefsetEditParameters other) {
        name = other.getName();
        refsetConceptId = other.getRefsetConceptId();
        parentConceptId = other.getParentConceptId();
        branch = other.getBranch();
        projectId = other.getProjectId();
        organizationId = other.getOrganizationId();
        narrative = other.getNarrative();
        versionNotes = other.getVersionNotes();
        definitionClauses = other.getDefinitionClauses();
        type = other.getType();
        tags = other.getTags();
        localSet = other.getLocalSet();
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
     * Returns the refset concept ID.
     *
     * @return the refset concept ID
     */
    public String getRefsetConceptId() {
        return refsetConceptId;
    }

    /**
     * Sets the refset concept ID.
     *
     * @param refsetConceptId the refset concept ID
     */
    public void setrefsetConceptId(final String refsetConceptId) {
        this.refsetConceptId = refsetConceptId;
    }
    
    /**
     * Returns the parent concept ID.
     *
     * @return the parent concept ID
     */
    public String getParentConceptId() {
        return parentConceptId;
    }
    
    /**
     * Sets the parent concept ID.
     *
     * @param parentConceptId the parent concept ID
     */
    public void setParentConceptId(final String parentConceptId) {
        this.parentConceptId = parentConceptId;
    }
    
    /**
     * Returns the branch.
     *
     * @return the branch
     */
    public String getBranch() {
        return branch;
    }
    
    /**
     * Sets the branch.
     *
     * @param branch the branch
     */
    public void setBranch(final String branch) {
        this.branch = branch;
    }
    
    /**
     * Returns the project ID.
     *
     * @return the project ID
     */
    public String getProjectId() {
        return projectId;
    }
    
    /**
     * Sets the project ID.
     *
     * @param projectId the project ID
     */
    public void setProjectId(final String projectId) {
        this.projectId = projectId;
    }
    
    /**
     * Returns the organizationId.
     *
     * @return the organizationId
     */
    public String getOrganizationId() {
        return organizationId;
    }
    
    /**
     * Sets the organization ID.
     *
     * @param organizationId the organization ID
     */
    public void setOrganizationId(final String organizationId) {
        this.organizationId = organizationId;
    }
    
    /**
     * Returns the narrative.
     *
     * @return the narrative
     */
    public String getNarrative() {
        return narrative;
    }
    
    /**
     * Sets the narrative.
     *
     * @param narrative the narrative
     */
    public void setNarrative(final String narrative) {
        this.narrative = narrative;
    }
    
    /**
     * Returns the version notes.
     *
     * @return the versionNotes
     */
    public String getVersionNotes() {
        return versionNotes;
    }
    
    /**
     * Sets the version notes.
     *
     * @param versionNotes the version notes
     */
    public void setVersionNotes(final String versionNotes) {
        this.versionNotes = versionNotes;
    }
    
    /**
     * Gets the definition clauses.
     *
     * @return the definitionClauses
     */
    public List<DefinitionClause> getDefinitionClauses() {

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
    public void setDefinitionClauses(final List<DefinitionClause> definitionClauses) {
        this.definitionClauses = definitionClauses;
    }
    
    /**
     * Returns the type.
     *
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type.
     *
     * @param type the type
     */
    public void setType(final String type) {
        this.type = type;
    }
    
    /**
     * Returns the tags.
     *
     * @return the tags
     */
    public Set<String> getTags() {
        return tags;
    }
    
    /**
     * Sets the tags.
     *
     * @param tags the tags
     */
    public void setTags(final Set<String> tags) {
        this.tags = tags;
    }
    
    /**
     * Returns the local set flag.
     *
     * @return the local set flag
     */
    public Boolean getLocalSet() {
        return localSet;
    }

    /**
     * Sets the local set flag.
     *
     * @param localSet the local set flag
     */
    public void setLocalSet(final Boolean localSet) {
        this.localSet = localSet;
    }

    /**
     * If this equals another object.
     *
     * @param obj the obj
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

        final RefsetEditParameters other = (RefsetEditParameters) obj;

        if (refsetConceptId == null) {

            if (other.refsetConceptId != null) {
                return false;
            }

        } else if (!refsetConceptId.equals(other.refsetConceptId)) {
            return false;
        }
        
        if (parentConceptId == null) {
            
            if (other.parentConceptId != null) {
                return false;
            }
            
        } else if (!parentConceptId.equals(other.parentConceptId)) {
            return false;
        }
        
        if (branch == null) {
            
            if (other.branch != null) {
                return false;
            }
            
        } else if (!branch.equals(other.branch)) {
            return false;
        }
        
        if (projectId == null) {
            
            if (other.projectId != null) {
                return false;
            }
            
        } else if (!projectId.equals(other.projectId)) {
            return false;
        }
        
        if (organizationId == null) {
            
            if (other.organizationId != null) {
                return false;
            }
            
        } else if (!organizationId.equals(other.organizationId)) {
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
        
        if (definitionClauses == null) {
            
            if (other.definitionClauses != null) {
                return false;
            }
            
        } else if (!definitionClauses.equals(other.definitionClauses)) {
            return false;
        }
        
        if (type == null) {
            
            if (other.type != null) {
                return false;
            }
            
        } else if (!type.equals(other.type)) {
            return false;
        }
        
        if (tags == null) {
            
            if (other.tags != null) {
                return false;
            }
            
        } else if (!tags.equals(other.tags)) {
            return false;
        }

        if (name == null) {

            if (other.name != null) {
                return false;
            }

        } else if (!name.equals(other.name)) {
            return false;
        }

        if (!localSet.equals(other.localSet)) {
            return false;
        }

        return true;
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
        result = prime * result + ((parentConceptId == null) ? 0 : parentConceptId.hashCode());
        result = prime * result + ((branch == null) ? 0 : branch.hashCode());
        result = prime * result + ((projectId == null) ? 0 : projectId.hashCode());
        result = prime * result + ((organizationId == null) ? 0 : organizationId.hashCode());
        result = prime * result + ((narrative == null) ? 0 : narrative.hashCode());
        result = prime * result + ((versionNotes == null) ? 0 : versionNotes.hashCode());
        result = prime * result + ((definitionClauses == null) ? 0 : definitionClauses.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((tags == null) ? 0 : tags.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + (localSet ? 1 : 0);
        return result;
    }

    /* see superclass */
    @Override
    public String toString() {

        try {
            return ModelUtility.toJson(this);
        } catch (final Exception e) {
            return e.getMessage();
        }
    }
}
