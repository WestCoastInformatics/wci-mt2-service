
package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

import org.hibernate.annotations.Type;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Auto-generated Javadoc
/**
 * Represents an inactive concept during the refset upgrade process.
 * 
 */
@Entity
@Table(name = "upgrade_inactive_concepts")
@Indexed
public class UpgradeInactiveConcept extends AbstractHasModified implements Comparable<UpgradeInactiveConcept> {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(UpgradeInactiveConcept.class);
    
    /** The refset ID. */
    @Column(nullable = false, length = 256)
    private String refsetId;

    /** The code. */
    @Column(nullable = false, length = 256)
    private String code;

    /** The descriptions. */
    @Column(nullable = false, length = 10000)
    @Type(type = "text")
    private String descriptions;
    
    /** The descriptions. */
    @Column(nullable = false, length = 256)
    @Type(type = "text")
    private String inactivationReason;

    /** The flag showing if this concept is still a member of the refset. */
    @Column(nullable = false)
    private boolean stillMember;

    /** The flag showing if this concept has had a replacement chosen. */
    @Column(nullable = false)
    private boolean replaced;
    
    /** The replacement concepts. */
    @OneToMany(cascade = CascadeType.ALL, targetEntity = UpgradeReplacementConcept.class, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("created ASC")
    private List<UpgradeReplacementConcept> replacementConcepts = new ArrayList<>();

    /**
     * Instantiates an empty {@link UpgradeInactiveConcept}.
     */
    public UpgradeInactiveConcept() {
        setActive(false);
    }

    /**
     * Instantiates a {@link UpgradeInactiveConcept} from the specified parameters.
     *
     * @param other the other
     */
    public UpgradeInactiveConcept(final UpgradeInactiveConcept other) {
        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final UpgradeInactiveConcept other) {
        
        super.populateFrom(other);
        refsetId = other.getRefsetId();
        code = other.getCode();
        descriptions = other.getDescriptions();
        inactivationReason = other.getInactivationReason();
        stillMember = other.isStillMember();
        replaced = other.isReplaced();
        replacementConcepts = new ArrayList<UpgradeReplacementConcept>(other.getReplacementConcepts());
    }

    /**
     * Returns the refset ID.
     *
     * @return the refset ID
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
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
     * Returns the code.
     *
     * @return the code
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
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
     * Returns the inactivation reason.
     *
     * @return the inactivation reason
     */
    public String getInactivationReason() {
        return inactivationReason;
    }
    
    /**
     * Sets the inactivation reason.
     *
     * @param inactivationReason the inactivation reason
     */
    public void setInactivationReason(final String inactivationReason) {
        this.inactivationReason = inactivationReason;
    }

    /**
     * Checks if this concept is still a member of the refset.
     *
     * @return the stillMember flag
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public boolean isStillMember() {
        return stillMember;
    }

    /**
     * Sets the flag showing if this concept is still a member of the refset.
     *
     * @param stillMember the flag value to set
     */
    public void setStillMember(final boolean stillMember) {
        this.stillMember = stillMember;
    }

    /**
     * Checks if this concept has had a replacement chosen.
     *
     * @return the replaced flag
     */
    public boolean isReplaced() {
        return replaced;
    }

    /**
     * Sets the flag showing if this concept has had a replacement chosen.
     *
     * @param replaced the flag to set
     */
    public void setReplaced(final boolean replaced) {
        this.replaced = replaced;
    }
    
    /**
     * Gets the replacement concepts.
     *
     * @return the replacementConcepts
     */
    @IndexedEmbedded(targetType = UpgradeReplacementConcept.class)
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    public List<UpgradeReplacementConcept> getReplacementConcepts() {

        if (replacementConcepts == null) {
            replacementConcepts = new ArrayList<>();
        }

        return replacementConcepts;
    }

    /**
     * Sets the replacement concepts.
     *
     * @param replacementConcepts the replacementConcepts to set
     */
    public void setReplacementConcepts(final List<UpgradeReplacementConcept> replacementConcepts) {
        
        Collections.sort(replacementConcepts, (o1, o2) -> (o1.getCreated().compareTo(o2.getCreated())));
        this.replacementConcepts = replacementConcepts;
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
        result = prime * result + ((code == null) ? 0 : code.hashCode());
        result = prime * result + ((descriptions == null) ? 0 : descriptions.hashCode());
        result = prime * result + ((inactivationReason == null) ? 0 : inactivationReason.hashCode());
        result = prime * result + ((replacementConcepts == null) ? 0 : replacementConcepts.hashCode());
        result = prime * result + (stillMember ? 1 : 0);
        result = prime * result + (replaced ? 1 : 0);
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

        final UpgradeInactiveConcept other = (UpgradeInactiveConcept) obj;

        if (refsetId == null) {
            if (other.refsetId != null) {
                return false;
            }
        } else if (!refsetId.equals(other.refsetId)) {
            return false;
        }

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
        
        if (inactivationReason == null) {
            if (other.inactivationReason != null) {
                return false;
            }
        } else if (!inactivationReason.equals(other.inactivationReason)) {
            return false;
        }
        
        if (replacementConcepts == null) {
            if (other.replacementConcepts != null) {
                return false;
            }
        } else if (!replacementConcepts.equals(other.replacementConcepts)) {
            return false;
        }

        if (stillMember != other.stillMember) {
            return false;
        }

        if (replaced != other.replaced) {
            return false;
        }

        return true;
    }

    /**
     * Compare to.
     *
     * @param other the other
     * @return the int
     */
    @Override
    public int compareTo(final UpgradeInactiveConcept other) {
        // Handle null
        return (code + refsetId).compareToIgnoreCase(other.getCode() + other.getRefsetId());
    }

    /**
     * Lazy init.
     */
    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub

    }
}
