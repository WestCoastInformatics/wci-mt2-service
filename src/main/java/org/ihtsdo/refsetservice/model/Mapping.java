package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Class Mapping.
 */
@Entity
@Schema(description = "Represents a mapping")
@Table(name = "mappings")
@JsonInclude(Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class Mapping extends AbstractHasModified {

    /** The code. */
    @Column(nullable = true, length = 4000)
    private String code;

    /** The name. */
    @Column(nullable = true, length = 4000)
    private String name;

    /** The map set id. */
    @Column(nullable = true, length = 4000)
    private String mapSetId;

    /** The map entries. */
    @ManyToOne(targetEntity = MapEntry.class, optional = false)
    @Fetch(FetchMode.JOIN)
    private List<MapEntry> mapEntries;

    /** The descriptions. */
    @Transient
    private List<Description> descriptions = new ArrayList<>();

    /**
     * Gets the code.
     *
     * @return the code
     */
    public String getCode() {

        return code;
    }

    /**
     * Sets the code.
     *
     * @param code the new code
     */
    public void setCode(final String code) {

        this.code = code;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {

        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the new name
     */
    public void setName(final String name) {

        this.name = name;
    }

    /**
     * Gets the map set id.
     *
     * @return the map set id
     */
    public String getMapSetId() {

        return mapSetId;
    }

    /**
     * Sets the map set id.
     *
     * @param mapSetId the new map set id
     */
    public void setMapSetId(final String mapSetId) {

        this.mapSetId = mapSetId;
    }

    /**
     * Gets the map entries.
     *
     * @return the map entries
     */
    public List<MapEntry> getMapEntries() {
        
        return mapEntries == null ? (mapEntries = new ArrayList<>()) : mapEntries;
    }

    /**
     * Sets the map entries.
     *
     * @param mapEntries the new map entries
     */
    public void setMapEntries(final List<MapEntry> mapEntries) {

        this.mapEntries = mapEntries;
    }

    /**
     * Gets the descriptions.
     *
     * @return the descriptions
     */
    @JsonGetter()
    public List<Description> getDescriptions() {

        return (descriptions == null) ? new ArrayList<>() : descriptions;
        
    }

    /**
     * Sets the descriptions.
     *
     * @param descriptions the descriptions
     */
    public void setDescriptions(final List<Description> descriptions) {

        this.descriptions = descriptions;
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hash(code, mapEntries, mapSetId, name);
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
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Mapping other = (Mapping) obj;
        return Objects.equals(code, other.code) && Objects.equals(mapEntries, other.mapEntries) && Objects.equals(mapSetId, other.mapSetId)
            && Objects.equals(name, other.name);
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {

        try {
            return ModelUtility.toJson(this);
        } catch (final Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public void lazyInit() {

        // n/a
    }

}
