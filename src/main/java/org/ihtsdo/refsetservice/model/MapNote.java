package org.ihtsdo.refsetservice.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.xml.bind.annotation.XmlID;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The Map Note Jpa object
 */
@Entity
@Table(name = "map_notes")
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class MapNote extends AbstractHasId {

    /** The id. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user. */
    @ManyToOne(targetEntity = MapUser.class)
    @JoinColumn(nullable = false)
    private MapUser user;

    /** The note. */
    @Column(nullable = false, length = 4000)
    private String note;

    /** The timestamp. */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date timestamp = new Date();

    /** Default constructor */
    public MapNote() {

    }

    /**
     * Constructor
     * @param id the id
     * @param user the user
     * @param note the note
     * @param timestamp the timestamp
     */
    public MapNote(final Long id, final MapUser user, final String note, final Date timestamp) {

        super();
        this.user = user;
        this.note = note;
        this.timestamp = timestamp;
    }

    /**
     * Instantiates a {@link MapNote} from the specified parameters.
     *
     * @param mapNote the map note
     * @param keepIds the keep ids
     */
    public MapNote(final MapNote mapNote, final boolean keepIds) {

        // if deep copy not indicated, copy id and timestamp
        if (keepIds) {
            super.setId(mapNote.getId());
        }

        this.timestamp = mapNote.getTimestamp();

        // copy basic type fields (non-persisted objects)
        this.note = mapNote.getNote();

        // copy objects/collections excluded from deep copy (i.e. retain persistence
        // references)
        this.user = new MapUser(mapNote.getUser());
    }

    /**
     * Returns the id in string form
     * @return the id in string form
     */
    @XmlID

    public String getObjectId() {

        return (this.id == null ? null : id.toString());
    }

    public MapUser getUser() {

        return user;
    }

    public void setUser(final MapUser user) {

        this.user = user;
    }

    public String getNote() {

        return note;
    }

    public void setNote(final String note) {

        this.note = note;
    }

    public Date getTimestamp() {

        return timestamp;
    }

    public void setTimestamp(final Date timestamp) {

        this.timestamp = timestamp;
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

        final int prime = 31;
        int result = 1;
        result = prime * result + ((note == null) ? 0 : note.hashCode());
        result = prime * result + ((user == null) ? 0 : user.hashCode());
        return result;
    }

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
        final MapNote other = (MapNote) obj;
        if (note == null) {
            if (other.note != null) {
                return false;
            }
        } else if (!note.equals(other.note)) {
            return false;
        }
        if (user == null) {
            if (other.user != null) {
                return false;
            }
        } else if (!user.equals(other.user)) {
            return false;
        }
        return true;
    }

}
