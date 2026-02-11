package org.ihtsdo.refsetservice.model.snowstorm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferencedComponent {
    
    private String conceptId;
    private Boolean active;
    private String definitionStatus;
    private String moduleId;
    private ReferenceComponentDescription fsn;
    private ReferenceComponentDescription pt;
    private String id;
    
    /**
     * @return the conceptId
     */
    public String getConceptId() {
    
        return conceptId;
    }
    
    /**
     * @param conceptId the conceptId to set
     */
    public void setConceptId(final String conceptId) {
    
        this.conceptId = conceptId;
    }
    
    /**
     * @return the active
     */
    public Boolean getActive() {
    
        return active;
    }
    
    /**
     * @param active the active to set
     */
    public void setActive(final Boolean active) {
    
        this.active = active;
    }
    
    /**
     * @return the definitionStatus
     */
    public String getDefinitionStatus() {
    
        return definitionStatus;
    }
    
    /**
     * @param definitionStatus the definitionStatus to set
     */
    public void setDefinitionStatus(final String definitionStatus) {
    
        this.definitionStatus = definitionStatus;
    }
    
    /**
     * @return the moduleId
     */
    public String getModuleId() {
    
        return moduleId;
    }
    
    /**
     * @param moduleId the moduleId to set
     */
    public void setModuleId(final String moduleId) {
    
        this.moduleId = moduleId;
    }
    
    /**
     * @return the fsn (Fully Specified Name)
     */
    public ReferenceComponentDescription getFsn() {
    
        return fsn;
    }
    
    /**
     * @param fsn the fsn to set (Fully Specified Name)
     */
    public void setFsn(final ReferenceComponentDescription fsn) {
    
        this.fsn = fsn;
    }
    
    /**
     * @return the pt (Preferred Term)
     */
    public ReferenceComponentDescription getPt() {
    
        return pt;
    }
    
    /**
     * @param pt the pt to set (Preferred Term)
     */
    public void setPt(final ReferenceComponentDescription pt) {
    
        this.pt = pt;
    }
    
    /**
     * @return the id
     */
    public String getId() {
    
        return id;
    }
    
    /**
     * @param id the id to set
     */
    public void setId(String id) {
    
        this.id = id;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((active == null) ? 0 : active.hashCode());
        result = prime * result + ((conceptId == null) ? 0 : conceptId.hashCode());
        result = prime * result + ((definitionStatus == null) ? 0 : definitionStatus.hashCode());
        result = prime * result + ((fsn == null) ? 0 : fsn.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((moduleId == null) ? 0 : moduleId.hashCode());
        result = prime * result + ((pt == null) ? 0 : pt.hashCode());
        return result;
    }

    /* see superclass */
    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ReferencedComponent)) {
            return false;
        }
        ReferencedComponent other = (ReferencedComponent) obj;
        if (active == null) {
            if (other.active != null) {
                return false;
            }
        } else if (!active.equals(other.active)) {
            return false;
        }
        if (conceptId == null) {
            if (other.conceptId != null) {
                return false;
            }
        } else if (!conceptId.equals(other.conceptId)) {
            return false;
        }
        if (definitionStatus == null) {
            if (other.definitionStatus != null) {
                return false;
            }
        } else if (!definitionStatus.equals(other.definitionStatus)) {
            return false;
        }
        if (fsn == null) {
            if (other.fsn != null) {
                return false;
            }
        } else if (!fsn.equals(other.fsn)) {
            return false;
        }
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        if (moduleId == null) {
            if (other.moduleId != null) {
                return false;
            }
        } else if (!moduleId.equals(other.moduleId)) {
            return false;
        }
        if (pt == null) {
            if (other.pt != null) {
                return false;
            }
        } else if (!pt.equals(other.pt)) {
            return false;
        }
        return true;
    }

    /* see superclass */
    @Override
    public String toString() {

        return "ReferencedComponent [conceptId=" + conceptId + ", active=" + active + ", definitionStatus=" + definitionStatus + ", moduleId=" + moduleId
            + ", fsn=" + fsn + ", pt=" + pt + ", id=" + id + "]";
    }
    
    
    
    
}
