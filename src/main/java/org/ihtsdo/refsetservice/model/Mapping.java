package org.ihtsdo.refsetservice.model;

import java.util.List;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Class Mapping.
 */
@Entity
@Schema(description = "Represents a mapping")
@Table(name = "mappings")
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

        return mapEntries;
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

        return "Mapping [code=" + code + ", name=" + name + ", mapSetId=" + mapSetId + ", mapEntries=" + mapEntries + "]";
    }

    @Override
    public void lazyInit() {

        // n/a
    }

}
