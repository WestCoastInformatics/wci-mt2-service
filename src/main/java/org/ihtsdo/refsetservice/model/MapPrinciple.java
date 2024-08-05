package org.ihtsdo.refsetservice.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A JPA enabled implementation of {@link MapPrinciple}.
 */
@Entity
@Table(name = "map_principles", uniqueConstraints = {
    @UniqueConstraint(columnNames = {
        "name", "principleId"
    })
})
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class MapPrinciple extends AbstractHasId {

    /** The principle id. */
    @Column(nullable = true, length = 255)
    private String principleId;

    /** The name. */
    @Column(nullable = false, length = 255)
    private String name;

    /** The detail. */
    @Column(nullable = true, length = 4000)
    private String detail;

    /** The section ref. */
    @Column(nullable = true, length = 4000)
    private String sectionRef;

    /**
     * Default constructor.
     */
    public MapPrinciple() {

        // left empty
    }

    /**
     * Instantiates a new map principle jpa.
     *
     * @param id the id
     * @param principleId the principle id
     * @param name the name
     * @param detail the detail
     * @param sectionRef the section ref
     */
    public MapPrinciple(final Long id, final String principleId, final String name, final String detail, final String sectionRef) {

        super();
        this.principleId = principleId;
        this.name = name;
        this.detail = detail;
        this.sectionRef = sectionRef;
    }

    /**
     * Instantiates a {@link MapPrinciple} from the specified parameters.
     *
     * @param mapPrinciple the map principle
     */
    public MapPrinciple(final MapPrinciple mapPrinciple) {

        detail = mapPrinciple.getDetail();
        name = mapPrinciple.getName();
        principleId = mapPrinciple.getPrincipleId();
        sectionRef = mapPrinciple.getSectionRef();

    }

    /**
     * Gets the principle id.
     *
     * @return the principle id
     */
    public String getPrincipleId() {

        return this.principleId;
    }

    /**
     * Sets the principle id.
     *
     * @param principleId the new principle id
     */
    public void setPrincipleId(final String principleId) {

        this.principleId = principleId;
    }

    /**
     * Get the detail.
     *
     * @return the detail
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getDetail() {

        return this.detail;
    }

    /**
     * Set the detail.
     *
     * @param detail the detail
     */
    public void setDetail(final String detail) {

        this.detail = detail;

    }

    /**
     * Get the name.
     *
     * @return the name
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getName() {

        return this.name;
    }

    /**
     * Set the name.
     *
     * @param name the name
     */
    public void setName(final String name) {

        this.name = name;

    }

    /**
     * Get the section reference.
     *
     * @return the section reference
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getSectionRef() {

        return this.sectionRef;
    }

    /**
     * Set the section reference.
     *
     * @param sectionRef the section reference
     */
    public void setSectionRef(final String sectionRef) {

        this.sectionRef = sectionRef;

    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((detail == null) ? 0 : detail.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((principleId == null) ? 0 : principleId.hashCode());
        result = prime * result + ((sectionRef == null) ? 0 : sectionRef.hashCode());
        return result;
    }

    /* see superclass */
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
        final MapPrinciple other = (MapPrinciple) obj;
        if (detail == null) {
            if (other.detail != null) {
                return false;
            }
        } else if (!detail.equals(other.detail)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (principleId == null) {
            if (other.principleId != null) {
                return false;
            }
        } else if (!principleId.equals(other.principleId)) {
            return false;
        }
        if (sectionRef == null) {
            if (other.sectionRef != null) {
                return false;
            }
        } else if (!sectionRef.equals(other.sectionRef)) {
            return false;
        }
        return true;
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
