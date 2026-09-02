package org.ihtsdo.refsetservice.model;

import java.util.Date;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A note attached to a mapping, keyed by map set {@code refSetCode} and source concept code
 * (Snowstorm mappings are not DB entities). Notes are shared across all versions of the same map
 * product.
 */
@Entity
@Table(name = "map_notes")
@JsonIgnoreProperties(ignoreUnknown = true, value = {
    "hibernateLazyInitializer", "handler"
})
@Indexed
public class MapNote extends AbstractHasModified {

    /** The stable map product code ({@link MapSet#getRefSetCode()}). */
    @Column(nullable = false, length = 255)
    private String refSetCode;

    /** The source concept code (referencedComponentId). */
    @Column(nullable = false, length = 255)
    private String sourceConceptCode;

    /** The user who authored the note. */
    @ManyToOne(targetEntity = MapUser.class)
    @JoinColumn(nullable = false)
    private MapUser user;

    /** The note text. */
    @Column(nullable = false, length = 4000)
    private String note;

    /** When the note was authored. */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date timestamp = new Date();

    /**
     * Instantiates an empty {@link MapNote}.
     */
    public MapNote() {

        // n/a
    }

    /**
     * Instantiates a {@link MapNote} from the specified parameters.
     *
     * @param mapNote the map note
     * @param keepIds whether to copy ids
     */
    public MapNote(final MapNote mapNote, final boolean keepIds) {

        if (keepIds && mapNote.getId() != null) {
            setId(mapNote.getId());
        }
        this.refSetCode = mapNote.getRefSetCode();
        this.sourceConceptCode = mapNote.getSourceConceptCode();
        this.timestamp = mapNote.getTimestamp();
        this.note = mapNote.getNote();
        if (mapNote.getUser() != null) {
            this.user = new MapUser(mapNote.getUser());
        }
    }

    /**
     * Returns the ref set code.
     *
     * @return the ref set code
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getRefSetCode() {

        return refSetCode;
    }

    /**
     * Sets the ref set code.
     *
     * @param refSetCode the ref set code
     */
    public void setRefSetCode(final String refSetCode) {

        this.refSetCode = refSetCode;
    }

    /**
     * Returns the source concept code.
     *
     * @return the source concept code
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getSourceConceptCode() {

        return sourceConceptCode;
    }

    /**
     * Sets the source concept code.
     *
     * @param sourceConceptCode the source concept code
     */
    public void setSourceConceptCode(final String sourceConceptCode) {

        this.sourceConceptCode = sourceConceptCode;
    }

    /**
     * Returns the user.
     *
     * @return the user
     */
    public MapUser getUser() {

        return user;
    }

    /**
     * Sets the user.
     *
     * @param user the user
     */
    public void setUser(final MapUser user) {

        this.user = user;
    }

    /**
     * Returns the note.
     *
     * @return the note
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getNote() {

        return note;
    }

    /**
     * Sets the note.
     *
     * @param note the note
     */
    public void setNote(final String note) {

        this.note = note;
    }

    /**
     * Returns the timestamp.
     *
     * @return the timestamp
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public Date getTimestamp() {

        return timestamp;
    }

    /**
     * Sets the timestamp.
     *
     * @param timestamp the timestamp
     */
    public void setTimestamp(final Date timestamp) {

        this.timestamp = timestamp;
    }

    @Override
    public void lazyInit() {

        // n/a
    }

    @Override
    public String toString() {

        try {
            return ModelUtility.toJson(this);
        } catch (final Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public int hashCode() {

        return Objects.hash(note, user);
    }

    @Override
    public boolean equals(final Object obj) {

        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MapNote other = (MapNote) obj;
        return Objects.equals(note, other.note) && Objects.equals(user, other.user);
    }

}
