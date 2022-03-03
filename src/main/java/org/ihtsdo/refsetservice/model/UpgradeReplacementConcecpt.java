
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
 * Represents an inactive concept during the refset upgrade process.
 * 
 */
@Entity
@Table(name = "upgrade_replacement_concecpts")
public class UpgradeReplacementConcecpt extends AbstractHasModified {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(UpgradeReplacementConcecpt.class);
    
    /** The code. */
    @Column(nullable = false, length = 256)
    private String code;

    /** The descriptions. */
    @Column(nullable = false, length = 10000)
    @Type(type = "text")
    private String descriptions;
    
    /** The reason for replacement. */
    @Column(nullable = false, length = 256)
    private String reason;

    /** The flag showing if this concept is already an existing member of the refset. */
    @Column(nullable = false)
    private boolean existingMember;

    /** The flag showing if this concept has been added as a replacement member. */
    @Column(nullable = false)
    private boolean added;

    /**
     * Instantiates an empty {@link UpgradeReplacementConcecpt}.
     */
    public UpgradeReplacementConcecpt() {
        // n/a
    }

    /**
     * Instantiates a {@link UpgradeReplacementConcecpt} from the specified parameters.
     *
     * @param other the other
     */
    public UpgradeReplacementConcecpt(final UpgradeReplacementConcecpt other) {
        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final UpgradeReplacementConcecpt other) {
        
        super.populateFrom(other);
        code = other.getCode();
        descriptions = other.getDescriptions();
        reason = other.getReason();
        existingMember = other.isExistingMember();
        added = other.isAdded();
    }
    
    /**
     * Returns the code.
     *
     * @return the code
     */
    public String getCode() {
        return code;
    }
    
    /**
     * Sets the code.
     *
     * @param code the code
     */
    public void setCode(final String code) {
        this.code = code;
    }

    /**
     * Returns the descriptions.
     *
     * @return the descriptions
     */
    public String getDescriptions() {
        return descriptions;
    }

    /**
     * Sets the descriptions.
     *
     * @param descriptions the descriptions
     */
    public void setDescriptions(final String descriptions) {
        this.descriptions = descriptions;
    }
    
    /**
     * Returns the reason.
     *
     * @return the reason
     */
    public String getReason() {
        return reason;
    }
    
    /**
     * Sets the reason.
     *
     * @param reason the reason
     */
    public void setReason(final String reason) {
        this.reason = reason;
    }

    /**
     * Checks if this concept is already an existing member of the refset.
     *
     * @return the existingMember flag
     */
    public boolean isExistingMember() {
        return existingMember;
    }

    /**
     * Sets the flag showing if this concept is already an existing member of the refset.
     *
     * @param existingMember the flag value to set
     */
    public void setExistingMember(final boolean existingMember) {
        this.existingMember = existingMember;
    }

    /**
     * Checks if this concept has been added as a replacement member.
     *
     * @return the added flag
     */
    public boolean isAdded() {
        return added;
    }

    /**
     * Sets the flag showing if this concept has been added as a replacement member.
     *
     * @param added the flag to set
     */
    public void setAdded(final boolean added) {
        this.added = added;
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
        result = prime * result + ((code == null) ? 0 : code.hashCode());
        result = prime * result + ((descriptions == null) ? 0 : descriptions.hashCode());
        result = prime * result + (existingMember ? 1 : 0);
        result = prime * result + (added ? 1 : 0);
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

        final UpgradeReplacementConcecpt other = (UpgradeReplacementConcecpt) obj;
        
        if (code == null) {
            if (other.code != null) {
                return false;
            }
        } else if (!code.equals(other.code)) {
            return false;
        }

        if (descriptions == null) {
            if (other.descriptions != null) {
                return false;
            }
        } else if (!descriptions.equals(other.descriptions)) {
            return false;
        }
        
        if (reason == null) {
            if (other.reason != null) {
                return false;
            }
        } else if (!reason.equals(other.reason)) {
            return false;
        }

        if (existingMember != other.existingMember) {
            return false;
        }

        if (added != other.added) {
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
