package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(description = "Represents a map record")
// @UniqueConstraint here is being used to create an index, not to enforce
// uniqueness
@Table(name = "map_records", uniqueConstraints = {
    @UniqueConstraint(columnNames = {
        "mapProjectId", "id"
    }), @UniqueConstraint(columnNames = {
        "conceptId", "id"
    }), @UniqueConstraint(columnNames = {
        "owner_id", "id"
    })
})
// TODO: determine if this is needed. If so, needs to be updated to current version of ElasticSearch
//
// @AnalyzerDef(name = "noStopWord", tokenizer = @TokenizerDef(factory = StandardTokenizerFactory.class), filters = {
// @TokenFilterDef(factory = StandardFilterFactory.class), @TokenFilterDef(factory = LowerCaseFilterFactory.class)
// })
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class MapRecord extends AbstractHasModified {

    /** The owner. */
    @ManyToOne(targetEntity = MapUser.class, fetch = FetchType.EAGER)
    @JoinColumn(nullable = false)
    @IndexedEmbedded(targetType = MapUser.class)
    private MapUser owner;

    /** The timestamp. */
    @Column(nullable = false)
    private Long timestamp = (new Date()).getTime();

    /** The user last modifying this record. */
    @ManyToOne(targetEntity = MapUser.class, fetch = FetchType.EAGER)
    @JoinColumn(nullable = false)
    private MapUser lastModifiedBy;

    /** The time at which the last user modified this record. */
    @Column(nullable = false)
    private Long lastModified = (new Date()).getTime();

    /** The map project id. */
    @Column(nullable = true)
    private String mapProjectId;

    /** The concept id. */
    @Column(nullable = false)
    private String conceptId;

    /** The concept name. */
    @Column(nullable = false)
    private String conceptName;

    /** The map entries. */
    @OneToMany(mappedBy = "map_records", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true, targetEntity = MapEntry.class)
    @IndexedEmbedded(targetType = MapEntry.class)
    private List<MapEntry> mapEntries = new ArrayList<>();

    /** The map notes. */
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true, targetEntity = MapNote.class)
    @CollectionTable(name = "map_records_map_notes", joinColumns = @JoinColumn(name = "map_records_id"))
    private Set<MapNote> mapNotes = new HashSet<>();

    /** The map principles. */
    @ManyToMany(targetEntity = MapPrinciple.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "map_records_map_principles", joinColumns = @JoinColumn(name = "map_records_id"))
    @IndexedEmbedded(targetType = MapPrinciple.class)
    private Set<MapPrinciple> mapPrinciples = new HashSet<>();

    /** The originIds. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "map_records_origin_ids", joinColumns = @JoinColumn(name = "id"))
    @Column(nullable = true)
    private Set<Long> originIds = new HashSet<>();

    /** Indicates whether the record is flagged for map lead review. */
    @Column(unique = false, nullable = false)
    private boolean flagForMapLeadReview = false;

    /** Indicates whether the record is flagged for editorial review. */
    @Column(unique = false, nullable = false)
    private boolean flagForEditorialReview = false;

    /** Indicates if the record is flagged for consensus review. */
    @Column(unique = false, nullable = false)
    private boolean flagForConsensusReview = false;

    /** The workflow status. */
    @Enumerated(EnumType.STRING)
    private MapWorkflowStatus mapWorkflowStatus;

    /** Whether this record has discrepancy review. */
    @Transient
    private boolean isDiscrepancyReview = false;

    /** The labels for this map record. */
    @ElementCollection
    @CollectionTable(name = "map_records_labels", joinColumns = @JoinColumn(name = "id"))
    @Column(nullable = true)
    // treat labels as a single field called labels
    private Set<String> labels = new HashSet<>();

    /** The reasons for conflict for this map record. */
    @ElementCollection
    @CollectionTable(name = "map_records_reasons", joinColumns = @JoinColumn(name = "id"))
    @Column(nullable = true)
    // treat reasons as a single field called reasonsForConflict
    private Set<String> reasonsForConflict = new HashSet<>();

    /**
     * Default constructor.
     */
    public MapRecord() {

    }

    /**
     * Deep copy constructor for Map Record Instantiates a {@link MapRecord} from the specified parameters.
     *
     * @param mapRecord the map record to be copied
     * @param keepIds true: copy persisted objects into new JPA objects
     */
    public MapRecord(final MapRecord mapRecord, final boolean keepIds) {

        // if deep copy not indicated, copy id and timestamp
        if (keepIds) {
            super.setId(mapRecord.getId());
            this.timestamp = mapRecord.getTimestamp();
        }

        // copy basic type fields (non-persisted objects)
        this.mapProjectId = mapRecord.getMapProjectId();
        this.conceptId = mapRecord.getConceptId();
        this.conceptName = mapRecord.getConceptName();
        this.originIds = new HashSet<>(mapRecord.getOriginIds());
        this.flagForMapLeadReview = mapRecord.isFlagForMapLeadReview();
        this.flagForEditorialReview = mapRecord.isFlagForEditorialReview();
        this.flagForConsensusReview = mapRecord.isFlagForConsensusReview();
        this.mapWorkflowStatus = mapRecord.getMapWorkflowStatus();
        this.lastModified = (new Date()).getTime(); // overwrite last modified by

        // copy objects/collections excluded from deep copy (i.e. retain persistence
        // references)
        for (final MapPrinciple mapPrinciple : mapRecord.getMapPrinciples()) {
            if (mapPrinciple == null) {
                continue;
            }
            addMapPrinciple(new MapPrinciple(mapPrinciple));
        }

        this.owner = new MapUser(mapRecord.getOwner());
        this.lastModifiedBy = new MapUser(mapRecord.getLastModifiedBy());

        // copy objects/collections with deep copy potential
        for (final MapEntry mapEntry : mapRecord.getMapEntries()) {
            addMapEntry(new MapEntry(mapEntry, keepIds));
        }
        for (final MapNote mapNote : mapRecord.getMapNotes()) {
            addMapNote(new MapNote(mapNote, keepIds));
        }
        labels = new HashSet<>(mapRecord.getLabels());
        reasonsForConflict = new HashSet<>(mapRecord.getReasonsForConflict());

    }

    /**
     * Returns the owner.
     *
     * @return the owner
     */
    public MapUser getOwner() {

        return owner;
    }

    /**
     * Sets the owner.
     *
     * @param owner the owner
     */
    public void setOwner(final MapUser owner) {

        this.owner = owner;
    }

    /**
     * Returns the timestamp.
     *
     * @return the timestamp
     */
    public Long getTimestamp() {

        return timestamp;
    }

    /**
     * Sets the timestamp.
     *
     * @param timestamp the timestamp
     */
    public void setTimestamp(final Long timestamp) {

        this.timestamp = timestamp;
    }

    /**
     * Returns the last modified by.
     *
     * @return the last modified by
     */
    public MapUser getLastModifiedBy() {

        return lastModifiedBy;
    }

    /**
     * Sets the last modified by.
     *
     * @param mapUser the last modified by
     */
    public void setLastModifiedBy(final MapUser mapUser) {

        this.lastModifiedBy = mapUser;
    }

    /**
     * Returns the last modified.
     *
     * @return the last modified
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public Long getLastModified() {

        return this.lastModified;
    }

    /**
     * Sets the last modified.
     *
     * @param lastModified the last modified
     */
    public void setLastModified(final Long lastModified) {

        this.lastModified = lastModified;
    }

    /**
     * Returns the map project id.
     *
     * @return the map project id
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getMapProjectId() {

        return mapProjectId;
    }

    /**
     * Sets the map project id.
     *
     * @param mapProjectId the map project id
     */
    public void setMapProjectId(final String mapProjectId) {

        this.mapProjectId = mapProjectId;
    }

    /**
     * Returns the concept id.
     *
     * @return the concept id
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getConceptId() {

        return conceptId;
    }

    /**
     * Sets the concept id.
     *
     * @param conceptId the concept id
     */
    public void setConceptId(final String conceptId) {

        this.conceptId = conceptId;
    }

    /**
     * Returns the concept name.
     *
     * @return the concept name
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    @FullTextField(analyzer = "noStopWord")
    public String getConceptName() {

        return this.conceptName;
    }

    /**
     * Sets the concept name.
     *
     * @param conceptName the concept name
     */
    public void setConceptName(final String conceptName) {

        this.conceptName = conceptName;
    }

    /**
     * Returns the map notes.
     *
     * @return the map notes
     */
    public Set<MapNote> getMapNotes() {

        if (mapNotes == null)
            mapNotes = new HashSet<>(); // ensures proper deserialization
        return mapNotes;
    }

    /**
     * Sets the map notes.
     *
     * @param mapNotes the map notes
     */
    public void setMapNotes(final Set<MapNote> mapNotes) {

        this.mapNotes = mapNotes;
    }

    /**
     * Adds the map note.
     *
     * @param mapNote the map note
     */
    public void addMapNote(final MapNote mapNote) {

        mapNotes.add(mapNote);
    }

    /**
     * Removes the map note.
     *
     * @param mapNote the map note
     */
    public void removeMapNote(final MapNote mapNote) {

        mapNotes.remove(mapNote);
    }

    /**
     * Returns the map entries.
     *
     * @return the map entries
     */
    public List<MapEntry> getMapEntries() {

        if (mapEntries == null)
            mapEntries = new ArrayList<>(); // ensures proper deserialization
        return mapEntries;
    }

    /**
     * Sets the map entries.
     *
     * @param mapEntries the map entries
     */
    public void setMapEntries(final List<MapEntry> mapEntries) {

        if (mapEntries == null)
            this.mapEntries = new ArrayList<>();
        else
            this.mapEntries = mapEntries;
    }

    /**
     * Adds the map entry.
     *
     * @param mapEntry the map entry
     */
    public void addMapEntry(final MapEntry mapEntry) {

        mapEntries.add(mapEntry);
    }

    /**
     * Function to correctly set the record object for map entries Must be called after deserialization in RESTful services after receiving Json/XML object
     * Rationale: deserialization provides only the record id, not the object.
     *
     */
    public void assignToChildren() {

        // assign to entries
        for (final MapEntry entry : mapEntries) {
            entry.setMapRecord(this);
        }

    }

    /**
     * Removes the map entry.
     *
     * @param mapEntry the map entry
     */
    public void removeMapEntry(final MapEntry mapEntry) {

        mapEntries.remove(mapEntry);
    }

    /**
     * Returns the map principles.
     *
     * @return the map principles
     */
    public Set<MapPrinciple> getMapPrinciples() {

        return mapPrinciples;
    }

    /**
     * Sets the map principles.
     *
     * @param mapPrinciples the map principles
     */
    public void setMapPrinciples(final Set<MapPrinciple> mapPrinciples) {

        this.mapPrinciples = mapPrinciples;
    }

    /**
     * Adds the map principle.
     *
     * @param mapPrinciple the map principle
     */
    public void addMapPrinciple(final MapPrinciple mapPrinciple) {

        mapPrinciples.add(mapPrinciple);
    }

    /**
     * Removes the map principle.
     *
     * @param mapPrinciple the map principle
     */
    public void removeMapPrinciple(final MapPrinciple mapPrinciple) {

        mapPrinciples.remove(mapPrinciple);
    }

    /**
     * Returns the origin ids.
     *
     * @return the origin ids
     */
    public Set<Long> getOriginIds() {

        return originIds;
    }

    /**
     * Sets the origin ids.
     *
     * @param originIds the origin ids
     */
    public void setOriginIds(final Set<Long> originIds) {

        this.originIds = originIds;
    }

    /**
     * Adds the origin.
     *
     * @param origin the origin
     */
    public void addOrigin(final Long origin) {

        this.originIds.add(origin);
    }

    /**
     * Removes the origin.
     *
     * @param origin the origin
     */
    public void removeOrigin(final Long origin) {

        originIds.remove(origin);
    }

    /**
     * Indicates whether or not flag for map lead review is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public boolean isFlagForMapLeadReview() {

        return flagForMapLeadReview;
    }

    /**
     * Sets the flag for map lead review.
     *
     * @param flag the flag for map lead review
     */
    public void setFlagForMapLeadReview(final boolean flag) {

        flagForMapLeadReview = flag;
    }

    /**
     * Indicates whether or not flag for editorial review is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public boolean isFlagForEditorialReview() {

        return flagForEditorialReview;
    }

    /**
     * Sets the flag for editorial review.
     *
     * @param flag the flag for editorial review
     */
    public void setFlagForEditorialReview(final boolean flag) {

        flagForEditorialReview = flag;
    }

    /**
     * Indicates whether or not flag for consensus review is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public boolean isFlagForConsensusReview() {

        return flagForConsensusReview;
    }

    /**
     * Sets the flag for consensus review.
     *
     * @param flag the flag for consensus review
     */
    public void setFlagForConsensusReview(final boolean flag) {

        flagForConsensusReview = flag;
    }

    /**
     * Indicates whether or not equivalent is the case.
     *
     * @param mapRecord the map record
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public boolean isEquivalent(final MapRecord mapRecord) {

        // first check all the non-collection fields
        if (conceptId == null) {
            if (mapRecord.getConceptId() != null)
                return false;
        } else if (!conceptId.equals(mapRecord.getConceptId()))
            return false;
        if (conceptName == null) {
            if (mapRecord.getConceptName() != null)
                return false;
        } else if (!conceptName.equals(mapRecord.getConceptName()))
            return false;
        if (flagForConsensusReview != mapRecord.isFlagForConsensusReview())
            return false;
        if (flagForEditorialReview != mapRecord.isFlagForEditorialReview())
            return false;
        if (flagForMapLeadReview != mapRecord.isFlagForMapLeadReview())
            return false;
        if (lastModified == null) {
            if (mapRecord.getLastModified() != null)
                return false;
        } else if (!lastModified.equals(mapRecord.getLastModified()))
            return false;
        if (lastModifiedBy == null) {
            if (mapRecord.getLastModifiedBy() != null)
                return false;
        } else if (!lastModifiedBy.equals(mapRecord.getLastModifiedBy()))
            return false;

        if (mapProjectId == null) {
            if (mapRecord.getMapProjectId() != null)
                return false;
        } else if (!mapProjectId.equals(mapRecord.getMapProjectId()))
            return false;

        if (owner == null) {
            if (mapRecord.getOwner() != null)
                return false;
        } else if (!owner.equals(mapRecord.getOwner()))
            return false;
        if (timestamp == null) {
            if (mapRecord.getTimestamp() != null)
                return false;
        } else if (!timestamp.equals(mapRecord.getTimestamp()))
            return false;
        if (mapWorkflowStatus != mapRecord.getMapWorkflowStatus())
            return false;

        // check the collection fields
        // * if not same length, return false
        // * if each collection element not contained in the mapRecord, return false
        // * don't care about order of collections
        // * not currently checking advices (not used presently)

        // check entries
        if (mapRecord.getMapEntries().size() != mapEntries.size())
            return false;
        for (final MapEntry entry : mapEntries) {
            if (!mapRecord.getMapEntries().contains(entry))
                return false;
        }

        // check notes
        if (mapRecord.getMapNotes().size() != mapNotes.size())
            return false;
        for (final MapNote note : mapNotes) {
            if (!mapRecord.getMapNotes().contains(note))
                return false;
        }

        // check principles
        if (mapRecord.getMapPrinciples().size() != mapPrinciples.size())
            return false;
        for (final MapPrinciple principle : mapPrinciples) {
            if (!mapRecord.getMapPrinciples().contains(principle))
                return false;
        }

        // if passed all checks, return true
        return true;

    }

    /**
     * Sets the mapping workflow status.
     *
     * @param mapWorkflowStatus the mapping workflow status
     */
    public void setMapWorkflowStatus(final MapWorkflowStatus mapWorkflowStatus) {

        this.mapWorkflowStatus = mapWorkflowStatus;
    }

    /**
     * Returns the workflow status.
     *
     * @return the workflow status
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public MapWorkflowStatus getMapWorkflowStatus() {

        return mapWorkflowStatus;
    }

    /**
     * Adds the origins.
     *
     * @param origins the origins
     */
    public void addOrigins(final Set<Long> origins) {

        originIds.addAll(origins);
    }

    /**
     * Indicates whether or not discrepancy review is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public boolean isDiscrepancyReview() {

        return isDiscrepancyReview;
    }

    /**
     * Sets the discrepancy review.
     *
     * @param isDiscrepancyReview the discrepancy review
     */
    public void setDiscrepancyReview(final boolean isDiscrepancyReview) {

        this.isDiscrepancyReview = isDiscrepancyReview;
    }

    /**
     * Gets the labels.
     *
     * @return the labels
     */
    public Set<String> getLabels() {

        return labels;
    }

    /**
     * Sets the labels.
     *
     * @param labels the new labels
     */
    public void setLabels(final Set<String> labels) {

        this.labels = labels;
    }

    /**
     * Adds the label.
     *
     * @param label the label
     */
    public void addLabel(final String label) {

        labels.add(label);
    }

    /**
     * Removes the label.
     *
     * @param label the label
     */
    public void removeLabel(final String label) {

        labels.remove(label);
    }

    /**
     * Returns the reasons for conflict.
     *
     * @return the reasons for conflict
     */
    public Set<String> getReasonsForConflict() {

        return reasonsForConflict;
    }

    /**
     * Sets the reasons for conflict.
     *
     * @param reasons the reasons for conflict
     */
    public void setReasonsForConflict(final Set<String> reasons) {

        this.reasonsForConflict = reasons;
    }

    /**
     * Adds the reason for conflict.
     *
     * @param reason the reason
     */
    public void addReasonForConflict(final String reason) {

        reasonsForConflict.add(reason);
    }

    /**
     * Removes the reason for conflict.
     *
     * @param reason the reason
     */
    public void removeReasonForConflict(final String reason) {

        reasonsForConflict.remove(reason);
    }

    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((conceptId == null) ? 0 : conceptId.hashCode());
        result = prime * result + ((conceptName == null) ? 0 : conceptName.hashCode());
        result = prime * result + (flagForConsensusReview ? 1231 : 1237);
        result = prime * result + (flagForEditorialReview ? 1231 : 1237);
        result = prime * result + (flagForMapLeadReview ? 1231 : 1237);
        result = prime * result + ((lastModified == null) ? 0 : lastModified.hashCode());
        result = prime * result + ((lastModifiedBy == null) ? 0 : lastModifiedBy.hashCode());
        result = prime * result + ((mapEntries == null) ? 0 : mapEntries.hashCode());
        result = prime * result + ((mapPrinciples == null) ? 0 : mapPrinciples.hashCode());
        result = prime * result + ((mapProjectId == null) ? 0 : mapProjectId.hashCode());
        result = prime * result + ((originIds == null) ? 0 : originIds.hashCode());
        result = prime * result + ((owner == null) ? 0 : owner.hashCode());
        result = prime * result + ((timestamp == null) ? 0 : timestamp.hashCode());
        result = prime * result + ((mapWorkflowStatus == null) ? 0 : mapWorkflowStatus.hashCode());
        result = prime * result + ((labels == null) ? 0 : labels.hashCode());
        result = prime * result + ((reasonsForConflict == null) ? 0 : reasonsForConflict.hashCode());
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
        final MapRecord other = (MapRecord) obj;
        if (conceptId == null) {
            if (other.conceptId != null)
                return false;
        } else if (!conceptId.equals(other.conceptId))
            return false;
        if (conceptName == null) {
            if (other.conceptName != null)
                return false;
        } else if (!conceptName.equals(other.conceptName))
            return false;
        if (flagForConsensusReview != other.flagForConsensusReview)
            return false;
        if (flagForEditorialReview != other.flagForEditorialReview)
            return false;
        if (flagForMapLeadReview != other.flagForMapLeadReview)
            return false;
        if (lastModified == null) {
            if (other.lastModified != null)
                return false;
        } else if (!lastModified.equals(other.lastModified))
            return false;
        if (lastModifiedBy == null) {
            if (other.lastModifiedBy != null)
                return false;
        } else if (!lastModifiedBy.equals(other.lastModifiedBy))
            return false;
        if (mapEntries == null) {
            if (other.mapEntries != null)
                return false;
        } else if (!mapEntries.equals(other.mapEntries))
            return false;
        if (mapPrinciples == null) {
            if (other.mapPrinciples != null)
                return false;
        } else if (!mapPrinciples.equals(other.mapPrinciples))
            return false;
        if (mapProjectId == null) {
            if (other.mapProjectId != null)
                return false;
        } else if (!mapProjectId.equals(other.mapProjectId))
            return false;
        if (originIds == null) {
            if (other.originIds != null)
                return false;
        } else if (!originIds.equals(other.originIds))
            return false;
        if (owner == null) {
            if (other.owner != null)
                return false;
        } else if (!owner.equals(other.owner))
            return false;
        if (timestamp == null) {
            if (other.timestamp != null)
                return false;
        } else if (!timestamp.equals(other.timestamp))
            return false;
        if (labels == null) {
            if (other.labels != null)
                return false;
        } else if (!labels.equals(other.labels))
            return false;
        if (reasonsForConflict == null) {
            if (other.reasonsForConflict != null)
                return false;
        } else if (!reasonsForConflict.equals(other.reasonsForConflict))
            return false;
        if (mapWorkflowStatus != other.mapWorkflowStatus)
            return false;
        return true;
    }

    @Override
    public String toString() {

        return "MapRecord [id=" + super.getId() + ", owner=" + owner + ", timestamp=" + timestamp + ", lastModifiedBy=" + lastModifiedBy + ", lastModified="
            + lastModified + ", mapProjectId=" + mapProjectId + ", conceptId=" + conceptId + ", conceptName=" + conceptName + ", mapEntries="
            + mapEntries.size() + ", mapNotes=" + mapNotes + ", mapPrinciples=" + mapPrinciples + ", originIds=" + originIds + ", flagForMapLeadReview="
            + flagForMapLeadReview + ", flagForEditorialReview=" + flagForEditorialReview + ", flagForConsensusReview=" + flagForConsensusReview
            + ", mapWorkflowStatus=" + mapWorkflowStatus + ", labels=" + labels + ", reasonsForConflict=" + reasonsForConflict + "]";
    }

    @Override
    public void lazyInit() {

        // TODO Auto-generated method stub

    }

}
