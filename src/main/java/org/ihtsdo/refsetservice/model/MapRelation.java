package org.ihtsdo.refsetservice.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlAttribute;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The Class MapRelation.
 */
@Entity
@Table(name = "map_relations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {
        "name"
    })
})
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class MapRelation extends AbstractHasId {

    /** The terminology id. */
    @Column(nullable = false)
    private String terminologyId;

    /** The name. */
    @Column(nullable = false)
    private String name;

    /** The abbreviation for display. */
    @Column(nullable = true)
    private String abbreviation;

    /** Whether this relation can be used for null targets. */
    @Column(nullable = false)
    private boolean isAllowableForNullTarget;

    /** Whether this relation is computed. */
    @Column(nullable = false)
    private boolean isComputed;

    /**
     * Instantiates a new map relation jpa.
     */
    public MapRelation() {

        // do nothing
    }

    /**
     * Instantiates a new map relation jpa.
     *
     * @param id the id
     * @param terminologyId the terminology id
     * @param name the name
     * @param abbreviation the abbreviation
     * @param isAllowableForNullTarget the is allowable for null target
     * @param isComputed the is computed
     */
    public MapRelation(final Long id, final String terminologyId, final String name, final String abbreviation, final boolean isAllowableForNullTarget,
        final boolean isComputed) {

        super();
        this.terminologyId = terminologyId;
        this.name = name;
        this.abbreviation = abbreviation;
        this.isAllowableForNullTarget = isAllowableForNullTarget;
        this.isComputed = isComputed;
    }

    /**
     * Instantiates a new map relation jpa.
     *
     * @param mapRelation the map relation
     */
    public MapRelation(final MapRelation mapRelation) {

        super();
        this.terminologyId = mapRelation.getTerminologyId();
        this.name = mapRelation.getName();
        this.abbreviation = mapRelation.getAbbreviation();
        this.isAllowableForNullTarget = mapRelation.isAllowableForNullTarget();
        this.isComputed = mapRelation.isComputed();
    }

    public String getTerminologyId() {

        return terminologyId;
    }

    public void setTerminologyId(final String terminologyId) {

        this.terminologyId = terminologyId;
    }

    public String getName() {

        return name;
    }

    public void setName(final String name) {

        this.name = name;
    }

    public String getAbbreviation() {

        return abbreviation;
    }

    public void setAbbreviation(final String abbreviation) {

        this.abbreviation = abbreviation;
    }

    @XmlAttribute(name = "isAllowableForNullTarget")
    public boolean isAllowableForNullTarget() {

        return isAllowableForNullTarget;
    }

    public void setAllowableForNullTarget(final boolean isAllowableForNullTarget) {

        this.isAllowableForNullTarget = isAllowableForNullTarget;
    }

    @XmlAttribute(name = "isComputed")
    public boolean isComputed() {

        return isComputed;
    }

    public void setComputed(final boolean isComputed) {

        this.isComputed = isComputed;
    }

    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((terminologyId == null) ? 0 : terminologyId.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {

        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final MapRelation other = (MapRelation) obj;
        if (terminologyId == null) {
            if (other.terminologyId != null)
                return false;
        } else if (!terminologyId.equals(other.terminologyId))
            return false;
        return true;
    }

    @Override
    public String toString() {

        return "MapRelation [id=" + super.getId() + ", terminologyId=" + terminologyId + ", name=" + name + ", abbreviation=" + abbreviation
            + ", isAllowableForNullTarget=" + isAllowableForNullTarget + ", isComputed=" + isComputed + "]";
    }

}
