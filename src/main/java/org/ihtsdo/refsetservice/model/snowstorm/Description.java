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
 * The Class Description.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Description {

    /**
     * The Enum Field.
     */
    public enum Field {

        /** The active. */
        ACTIVE("active"),
        /** The module id. */
        MODULE_ID("moduleId"),
        /** The released. */
        RELEASED("released"),
        /** The released effective time. */
        RELEASED_EFFECTIVE_TIME("releasedEffectiveTime"),
        /** The description id. */
        DESCRIPTION_ID("descriptionId"),
        /** The term. */
        TERM("term"),
        /** The concept id. */
        CONCEPT_ID("conceptId"),
        /** The type id. */
        TYPE_ID("typeId"),
        /** The acceptability map. */
        ACCEPTABILITY_MAP("acceptabilityMap"),
        /** The type. */
        TYPE("type"),
        /** The lang. */
        LANG("lang"),
        /** The case significance. */
        CASE_SIGNIFICANCE("caseSignificance"),
        /** The effective time. */
        EFFECTIVE_TIME("effectiveTime");

        /** The value. */
        private final String value;

        /**
         * Instantiates a {@link Field} from the specified parameters.
         *
         * @param value the value
         */
        Field(String value) {

            this.value = value;
        }

        /**
         * Returns the value.
         *
         * @return the value
         */
        public String getValue() {

            return value;
        }
    }

    // Example JSON:
    // {
    // "active": true,
    // "moduleId": "900000000000207008",
    // "released": true,
    // "releasedEffectiveTime": 20170731,
    // "descriptionId": "2901894013",
    // "term": "Eccentric opening of tricuspid aortic valve",
    // "conceptId": "449123008",
    // "typeId": "900000000000013009",
    // "acceptabilityMap": {
    // "900000000000509007": "PREFERRED",
    // "900000000000508004": "PREFERRED"
    // },
    // "type": "SYNONYM",
    // "lang": "en",
    // "caseSignificance": "CASE_INSENSITIVE",
    // "effectiveTime": "20170731"
    // }

    /** The active. */
    private Boolean active;

    /** The module id. */
    private String moduleId;

    /** The released. */
    private Boolean released;

    /** The released effective time. */
    private Long releasedEffectiveTime;

    /** The description id. */
    private String descriptionId;

    /** The term. */
    private String term;

    /** The concept id. */
    private String conceptId;

    /** The type. */
    private String type;

    /** The language. */
    private String lang;

    /** The case significance. */
    private String caseSignificance;

    /** The effective time. */
    private String effectiveTime;

    /** The acceptability map. */
    private Map<String, String> acceptabilityMap;

    /**
     * Instantiates a new description.
     */
    public Description() {

        // Do nothing
    }

    /**
     * Gets the active.
     *
     * @return the active
     */
    public Boolean getActive() {

        return active;
    }

    /**
     * Sets the active.
     *
     * @param active the new active
     */
    public void setActive(final Boolean active) {

        this.active = active;
    }

    /**
     * Gets the module id.
     *
     * @return the module id
     */
    public String getModuleId() {

        return moduleId;
    }

    /**
     * Sets the module id.
     *
     * @param moduleId the new module id
     */
    public void setModuleId(final String moduleId) {

        this.moduleId = moduleId;
    }

    /**
     * Gets the released.
     *
     * @return the released
     */
    public Boolean getReleased() {

        return released;
    }

    /**
     * Sets the released.
     *
     * @param released the new released
     */
    public void setReleased(final Boolean released) {

        this.released = released;
    }

    /**
     * Gets the released effective time.
     *
     * @return the released effective time
     */
    public Long getReleasedEffectiveTime() {

        return releasedEffectiveTime;
    }

    /**
     * Sets the released effective time.
     *
     * @param releasedEffectiveTime the new released effective time
     */
    public void setReleasedEffectiveTime(final Long releasedEffectiveTime) {

        this.releasedEffectiveTime = releasedEffectiveTime;
    }

    /**
     * Gets the description id.
     *
     * @return the description id
     */
    public String getDescriptionId() {

        return descriptionId;
    }

    /**
     * Sets the description id.
     *
     * @param descriptionId the new description id
     */
    public void setDescriptionId(final String descriptionId) {

        this.descriptionId = descriptionId;
    }

    /**
     * Gets the term.
     *
     * @return the term
     */
    public String getTerm() {

        return term;
    }

    /**
     * Sets the term.
     *
     * @param term the new term
     */
    public void setTerm(final String term) {

        this.term = term;
    }

    /**
     * Gets the concept id.
     *
     * @return the concept id
     */
    public String getConceptId() {

        return conceptId;
    }

    /**
     * Sets the concept id.
     *
     * @param conceptId the new concept id
     */
    public void setConceptId(final String conceptId) {

        this.conceptId = conceptId;
    }

    /**
     * Gets the type.
     *
     * @return the type
     */
    public String getType() {

        return type;
    }

    /**
     * Sets the type.
     *
     * @param type the new type
     */
    public void setType(final String type) {

        this.type = type;
    }

    /**
     * Gets the language.
     *
     * @return the language
     */
    public String getLang() {

        return lang;
    }

    /**
     * Sets the language.
     *
     * @param lang the new language
     */
    public void setLang(final String lang) {

        this.lang = lang;
    }

    /**
     * Gets the case significance.
     *
     * @return the case significance
     */
    public String getCaseSignificance() {

        return caseSignificance;
    }

    /**
     * Sets the case significance.
     *
     * @param caseSignificance the new case significance
     */
    public void setCaseSignificance(final String caseSignificance) {

        this.caseSignificance = caseSignificance;
    }

    /**
     * Gets the effective time.
     *
     * @return the effective time
     */
    public String getEffectiveTime() {

        return effectiveTime;
    }

    /**
     * Sets the effective time.
     *
     * @param effectiveTime the new effective time
     */
    public void setEffectiveTime(final String effectiveTime) {

        this.effectiveTime = effectiveTime;
    }

    /**
     * Returns the acceptability map.
     *
     * @return the acceptability map
     */
    public Map<String, String> getAcceptabilityMap() {

        return acceptabilityMap;
    }

    /**
     * Sets the acceptability map.
     *
     * @param acceptabilityMap the acceptability map
     */
    public void setAcceptabilityMap(Map<String, String> acceptabilityMap) {

        this.acceptabilityMap = acceptabilityMap;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((acceptabilityMap == null) ? 0 : acceptabilityMap.hashCode());
        result = prime * result + ((active == null) ? 0 : active.hashCode());
        result = prime * result + ((caseSignificance == null) ? 0 : caseSignificance.hashCode());
        result = prime * result + ((conceptId == null) ? 0 : conceptId.hashCode());
        result = prime * result + ((descriptionId == null) ? 0 : descriptionId.hashCode());
        result = prime * result + ((effectiveTime == null) ? 0 : effectiveTime.hashCode());
        result = prime * result + ((lang == null) ? 0 : lang.hashCode());
        result = prime * result + ((moduleId == null) ? 0 : moduleId.hashCode());
        result = prime * result + ((released == null) ? 0 : released.hashCode());
        result = prime * result + ((releasedEffectiveTime == null) ? 0 : releasedEffectiveTime.hashCode());
        result = prime * result + ((term == null) ? 0 : term.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    /* see superclass */
    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Description)) {
            return false;
        }
        Description other = (Description) obj;
        if (acceptabilityMap == null) {
            if (other.acceptabilityMap != null) {
                return false;
            }
        } else if (!acceptabilityMap.equals(other.acceptabilityMap)) {
            return false;
        }
        if (active == null) {
            if (other.active != null) {
                return false;
            }
        } else if (!active.equals(other.active)) {
            return false;
        }
        if (caseSignificance == null) {
            if (other.caseSignificance != null) {
                return false;
            }
        } else if (!caseSignificance.equals(other.caseSignificance)) {
            return false;
        }
        if (conceptId == null) {
            if (other.conceptId != null) {
                return false;
            }
        } else if (!conceptId.equals(other.conceptId)) {
            return false;
        }
        if (descriptionId == null) {
            if (other.descriptionId != null) {
                return false;
            }
        } else if (!descriptionId.equals(other.descriptionId)) {
            return false;
        }
        if (effectiveTime == null) {
            if (other.effectiveTime != null) {
                return false;
            }
        } else if (!effectiveTime.equals(other.effectiveTime)) {
            return false;
        }
        if (lang == null) {
            if (other.lang != null) {
                return false;
            }
        } else if (!lang.equals(other.lang)) {
            return false;
        }
        if (moduleId == null) {
            if (other.moduleId != null) {
                return false;
            }
        } else if (!moduleId.equals(other.moduleId)) {
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
        if (term == null) {
            if (other.term != null) {
                return false;
            }
        } else if (!term.equals(other.term)) {
            return false;
        }
        if (type == null) {
            if (other.type != null) {
                return false;
            }
        } else if (!type.equals(other.type)) {
            return false;
        }
        return true;
    }

    /* see superclass */
    @Override
    public String toString() {

        return "Description [active=" + active + ", moduleId=" + moduleId + ", released=" + released + ", releasedEffectiveTime=" + releasedEffectiveTime
            + ", descriptionId=" + descriptionId + ", term=" + term + ", conceptId=" + conceptId + ", type=" + type + ", lang=" + lang + ", caseSignificance="
            + caseSignificance + ", effectiveTime=" + effectiveTime + ", acceptabilityMap=" + acceptabilityMap + "]";
    }

}
