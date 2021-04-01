/*
 * Copyright 2021 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.model;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Fields;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.SortableField;
import org.hibernate.search.annotations.Store;

/**
 * Represents the edition information for a refset.
 */
@Entity
@Table(name = "editions")
@Indexed
public class Edition extends AbstractHasModified {

    /** The name. */
    @Column(nullable = false, length = 4000)
    @Fields({
        @Field(index = Index.YES, analyze = Analyze.YES, store = Store.NO),
        @Field(name = "nameSort", index = Index.YES, analyze = Analyze.NO, store = Store.NO)
})
    @SortableField(forField = "nameSort")
    private String name;

    /** The namespace. */
    @Column(nullable = false)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
    private String namespace;

    /** The short name. */
    @Column(nullable = true)
    @Fields({
        @Field(index = Index.YES, analyze = Analyze.YES, store = Store.NO),
        @Field(name = "shortNameSort", index = Index.YES, analyze = Analyze.NO, store = Store.NO)
})
    @SortableField(forField = "shortNameSort")
    private String shortName;

    /** The flag icon URI. */
    @Column(nullable = true)
    private String iconUri;

    /** The branch to use when retrieving from a terminology server. */
    @Column(nullable = true)
    private String branch;

    /** The default language refsets. */
    @Column(nullable = true)
    @ElementCollection
    @Field(analyze = Analyze.NO, store = Store.YES)
    @IndexedEmbedded
    private Set<String> defaultLanguageRefsets = new HashSet<String>();

    /**
     * Instantiates an empty {@link Edition}.
     */
    public Edition() {
        // n/a
    }

    /**
     * Instantiates a {@link Edition} from the specified parameters.
     *
     * @param other the other
     */
    public Edition(final Edition other) {
        populateFrom(other);
    }

    /**
     * Instantiates a {@link Edition} from the specified parameters.
     *
     * @param code the key
     * @param name the value
     */
    public Edition(final String name) {
        this.name = name;
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final Edition other) {
        super.populateFrom(other);
        name = other.getName();
        namespace = other.getNamespace();
        defaultLanguageRefsets = other.getDefaultLanguageRefsets();
        branch = other.getBranch();
        iconUri = other.getIconUri();
        shortName = other.getShortName();
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
     * Gets the namespace.
     *
     * @return the namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace.
     *
     * @param namespace the namespace
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Gets the icon uri.
     *
     * @return the iconUri
     */
    public String getIconUri() {
        return iconUri;
    }

    /**
     * Sets the icon uri.
     *
     * @param iconUri the iconUri to set
     */
    public void setIconUri(String iconUri) {
        this.iconUri = iconUri;
    }

    /**
     * Gets the branch.
     *
     * @return the branch
     */
    public String getBranch() {
        return branch;
    }

    /**
     * Sets the branch.
     *
     * @param branch the branch to set
     */
    public void setBranch(String branch) {
        this.branch = branch;
    }

    /**
     * Gets the default language refsets.
     *
     * @return the default language refsets
     */
    public Set<String> getDefaultLanguageRefsets() {
        return defaultLanguageRefsets;
    }

    /**
     * Sets the default language refsets.
     *
     * @param defaultLanguageRefsets the set of default language refset Ids
     */
    public void setDescription(Set<String> defaultLanguageRefsets) {
        this.defaultLanguageRefsets = defaultLanguageRefsets;
    }

    /**
     * Gets the short name.
     *
     * @return the country
     */
    public String getShortName() {
        return shortName;
    }

    /**
     * Sets the short name.
     *
     * @param country the country to set
     */
    public void setShortName(String country) {
        this.shortName = country;
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
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result
                + ((namespace == null) ? 0 : namespace.hashCode());
        result = prime * result + ((branch == null) ? 0 : branch.hashCode());
        result = prime * result + ((iconUri == null) ? 0 : iconUri.hashCode());
        result = prime * result + ((defaultLanguageRefsets == null) ? 0
                : defaultLanguageRefsets.hashCode());
        result = prime * result
                + ((shortName == null) ? 0 : shortName.hashCode());
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

        final Edition other = (Edition) obj;

        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }

        if (namespace == null) {
            if (other.namespace != null) {
                return false;
            }
        } else if (!namespace.equals(other.namespace)) {
            return false;
        }

        if (branch == null) {
            if (other.branch != null) {
                return false;
            }
        } else if (!branch.equals(other.branch)) {
            return false;
        }

        if (iconUri == null) {
            if (other.iconUri != null) {
                return false;
            }
        } else if (!iconUri.equals(other.iconUri)) {
            return false;
        }

        if (defaultLanguageRefsets == null) {
            if (other.defaultLanguageRefsets != null) {
                return false;
            }
        } else if (!defaultLanguageRefsets
                .equals(other.defaultLanguageRefsets)) {
            return false;
        }

        if (shortName == null) {
            if (other.shortName != null) {
                return false;
            }
        } else if (!shortName.equals(other.shortName)) {
            return false;
        }

        return true;
    }

    /**
     * Lazy init.
     */
    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub

    }
}
