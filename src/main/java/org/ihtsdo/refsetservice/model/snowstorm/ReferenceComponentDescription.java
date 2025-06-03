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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The Class ReferenceComponentTerm.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceComponentDescription {

    /** The term. */
    private String term;

    /** The lang. */
    private String lang;

    /**
     * Instantiates an empty {@link ReferenceComponentDescription}.
     */
    public ReferenceComponentDescription() {

        // n/a
    }

    /**
     * Instantiates a {@link ReferenceComponentDescription} from the specified parameters.
     *
     * @param term the term
     * @param lang the lang
     */
    public ReferenceComponentDescription(final String term, final String lang) {

        this.term = term;
        this.lang = lang;
    }

    /**
     * Returns the term.
     *
     * @return the term
     */
    public String getTerm() {

        return term;
    }

    /**
     * Sets the term.
     *
     * @param term the term to set
     */
    public void setTerm(String term) {

        this.term = term;
    }

    /**
     * Returns the lang.
     *
     * @return the lang
     */
    public String getLang() {

        return lang;
    }

    /**
     * Sets the lang.
     *
     * @param lang the lang to set
     */
    public void setLang(String lang) {

        this.lang = lang;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((lang == null) ? 0 : lang.hashCode());
        result = prime * result + ((term == null) ? 0 : term.hashCode());
        return result;
    }

    /* see superclass */
    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ReferenceComponentDescription)) {
            return false;
        }
        ReferenceComponentDescription other = (ReferenceComponentDescription) obj;
        if (lang == null) {
            if (other.lang != null) {
                return false;
            }
        } else if (!lang.equals(other.lang)) {
            return false;
        }
        if (term == null) {
            if (other.term != null) {
                return false;
            }
        } else if (!term.equals(other.term)) {
            return false;
        }
        return true;
    }

    /* see superclass */
    @Override
    public String toString() {

        return "ReferenceComponentTerm [term=" + term + ", lang=" + lang + "]";
    }

}
