/*
 * Copyright 2024 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model.snowstorm;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The Class RefsetMember.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefsetMember {

    /** The active. */
    private Boolean active;

    /** The module id. */
    private String moduleId;

    /** The released. */
    private Boolean released;

    /** The released effective time. */
    private String releasedEffectiveTime; // Assuming the date part is in YYYYMM format

    /** The member id. */
    private String memberId;

    /** The refset id. */
    private String refsetId;

    /** The referenced component id. */
    private String referencedComponentId;

    /** The additional fields. */
    private Map<String, Object> additionalFields;

    /** The referenced component. */
    private ReferencedComponent referencedComponent;

    /** The effective time. */
    private String effectiveTime; // Assuming the date part is in YYYY-MM-dd format

    /**
     * Returns the active.
     *
     * @return the active
     */
    public Boolean getActive() {

        return this.active;
    }

    /**
     * Sets the active.
     *
     * @param active the active
     */
    public void setActive(final Boolean active) {

        this.active = active;
    }

    /**
     * Returns the module id.
     *
     * @return the module id
     */
    public String getModuleId() {

        return this.moduleId;
    }

    /**
     * Sets the module id.
     *
     * @param moduleId the module id
     */
    public void setModuleId(final String moduleId) {

        this.moduleId = moduleId;
    }

    /**
     * Returns the released.
     *
     * @return the released
     */
    public Boolean getReleased() {

        return this.released;
    }

    /**
     * Sets the released.
     *
     * @param released the released
     */
    public void setReleased(final Boolean released) {

        this.released = released;
    }

    /**
     * Returns the released effective time.
     *
     * @return the released effective time
     */
    public String getReleasedEffectiveTime() {

        return this.releasedEffectiveTime;
    }

    /**
     * Sets the released effective time.
     *
     * @param releasedEffectiveTime the released effective time
     */
    public void setReleasedEffectiveTime(final String releasedEffectiveTime) {

        this.releasedEffectiveTime = releasedEffectiveTime;
    }

    /**
     * Returns the member id.
     *
     * @return the member id
     */
    public String getMemberId() {

        return this.memberId;
    }

    /**
     * Sets the member id.
     *
     * @param memberId the member id
     */
    public void setMemberId(final String memberId) {

        this.memberId = memberId;
    }

    /**
     * Returns the refset id.
     *
     * @return the refset id
     */
    public String getRefsetId() {

        return this.refsetId;
    }

    /**
     * Sets the refset id.
     *
     * @param refsetId the refset id
     */
    public void setRefsetId(final String refsetId) {

        this.refsetId = refsetId;
    }

    /**
     * Returns the referenced component id.
     *
     * @return the referenced component id
     */
    public String getReferencedComponentId() {

        return this.referencedComponentId;
    }

    /**
     * Sets the referenced component id.
     *
     * @param referencedComponentId the referenced component id
     */
    public void setReferencedComponentId(final String referencedComponentId) {

        this.referencedComponentId = referencedComponentId;
    }

    /**
     * Returns the additional fields.
     *
     * @return the additional fields
     */
    public Map<String, Object> getAdditionalFields() {

        return this.additionalFields;
    }

    /**
     * Sets the additional fields.
     *
     * @param additionalFields the additional fields
     */
    public void setAdditionalFields(final Map<String, Object> additionalFields) {

        this.additionalFields = additionalFields;
    }

    /**
     * Returns the referenced component.
     *
     * @return the referenced component
     */
    public ReferencedComponent getReferencedComponent() {

        return this.referencedComponent;
    }

    /**
     * Sets the referenced component.
     *
     * @param referencedComponent the referenced component
     */
    public void setReferencedComponent(final ReferencedComponent referencedComponent) {

        this.referencedComponent = referencedComponent;
    }

    /**
     * Returns the effective time.
     *
     * @return the effective time
     */
    public String getEffectiveTime() {

        return this.effectiveTime;
    }

    /**
     * Sets the effective time.
     *
     * @param effectiveTime the effective time
     */
    public void setEffectiveTime(final String effectiveTime) {

        this.effectiveTime = effectiveTime;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((active == null) ? 0 : active.hashCode());
        result = prime * result + ((additionalFields == null) ? 0 : additionalFields.hashCode());
        result = prime * result + ((effectiveTime == null) ? 0 : effectiveTime.hashCode());
        result = prime * result + ((memberId == null) ? 0 : memberId.hashCode());
        result = prime * result + ((moduleId == null) ? 0 : moduleId.hashCode());
        result = prime * result + ((referencedComponent == null) ? 0 : referencedComponent.hashCode());
        result = prime * result + ((referencedComponentId == null) ? 0 : referencedComponentId.hashCode());
        result = prime * result + ((refsetId == null) ? 0 : refsetId.hashCode());
        result = prime * result + ((released == null) ? 0 : released.hashCode());
        result = prime * result + ((releasedEffectiveTime == null) ? 0 : releasedEffectiveTime.hashCode());
        return result;
    }

    /* see superclass */
    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RefsetMember)) {
            return false;
        }
        RefsetMember other = (RefsetMember) obj;
        if (active == null) {
            if (other.active != null) {
                return false;
            }
        } else if (!active.equals(other.active)) {
            return false;
        }
        if (additionalFields == null) {
            if (other.additionalFields != null) {
                return false;
            }
        } else if (!additionalFields.equals(other.additionalFields)) {
            return false;
        }
        if (effectiveTime == null) {
            if (other.effectiveTime != null) {
                return false;
            }
        } else if (!effectiveTime.equals(other.effectiveTime)) {
            return false;
        }
        if (memberId == null) {
            if (other.memberId != null) {
                return false;
            }
        } else if (!memberId.equals(other.memberId)) {
            return false;
        }
        if (moduleId == null) {
            if (other.moduleId != null) {
                return false;
            }
        } else if (!moduleId.equals(other.moduleId)) {
            return false;
        }
        if (referencedComponent == null) {
            if (other.referencedComponent != null) {
                return false;
            }
        } else if (!referencedComponent.equals(other.referencedComponent)) {
            return false;
        }
        if (referencedComponentId == null) {
            if (other.referencedComponentId != null) {
                return false;
            }
        } else if (!referencedComponentId.equals(other.referencedComponentId)) {
            return false;
        }
        if (refsetId == null) {
            if (other.refsetId != null) {
                return false;
            }
        } else if (!refsetId.equals(other.refsetId)) {
            return false;
        }
        if (released == null) {
            if (other.released != null) {
                return false;
            }
        } else if (!released.equals(other.released)) {
            return false;
        }
        if (releasedEffectiveTime == null) {
            if (other.releasedEffectiveTime != null) {
                return false;
            }
        } else if (!releasedEffectiveTime.equals(other.releasedEffectiveTime)) {
            return false;
        }
        return true;
    }

    /* see superclass */
    @Override
    public String toString() {

        return "RefsetMember [active=" + active + ", moduleId=" + moduleId + ", released=" + released + ", releasedEffectiveTime=" + releasedEffectiveTime
            + ", memberId=" + memberId + ", refsetId=" + refsetId + ", referencedComponentId=" + referencedComponentId + ", additionalFields="
            + additionalFields + ", referencedComponent=" + referencedComponent + ", effectiveTime=" + effectiveTime + "]";
    }

}
