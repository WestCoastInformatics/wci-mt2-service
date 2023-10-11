package org.ihtsdo.refsetservice.model;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Map Entry object.
 *
 */
@Entity
@Schema(description = "Represents a map entry")
@Table(name = "map_entries")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapEntry extends AbstractHasId {

    /** The map record. */
    @ManyToOne(targetEntity = MapRecord.class, optional = false)
    // @ContainedIn
    @Fetch(FetchMode.JOIN)
    private MapRecord mapRecord;

    /** The map advices. */
    @ManyToMany(targetEntity = MapAdvice.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "map_entries_map_advices", joinColumns = @JoinColumn(name = "map_entries_id"))
    @IndexedEmbedded(targetType = MapAdvice.class)
    private Set<MapAdvice> mapAdvices = new HashSet<>();

    /** The additional map entry info. */
    @ManyToMany(targetEntity = AdditionalMapEntryInfo.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "map_entries_additional_map_entry_info", joinColumns = @JoinColumn(name = "map_entries_id"))
    @IndexedEmbedded(targetType = AdditionalMapEntryInfo.class)
    private Set<AdditionalMapEntryInfo> additionalMapEntryInfos = new HashSet<>();

    /** The target. */
    @Column(nullable = true, length = 4000)
    private String targetId;

    /** The target name. */
    @Column(nullable = true, length = 4000)
    @FullTextField(analyzer = "noStopWord")
    private String targetName;

    /** The rule. */
    @Column(nullable = true, length = 4000)
    private String rule;

    /** The map priority. */
    @Column(nullable = false)
    private int priority;

    /** The map relation. */
    @OneToOne(targetEntity = MapRelation.class, fetch = FetchType.EAGER)
    @IndexedEmbedded(targetType = MapRelation.class)
    @JsonIgnoreProperties({
        "hibernateLazyInitializer", "handler"
    })
    private MapRelation mapRelation;

    /** The map block. */
    @Column(nullable = false)
    private int block;

    /** The index (map group). */
    @Column(nullable = false)
    private int group;

    /**
     * default constructor.
     */
    public MapEntry() {

        // empty
    }

    /**
     * Constructor using fields.
     *
     * @param id the id
     * @param mapRecord the map record
     * @param mapAdvices the map advices
     * @param targetId the target id
     * @param targetName the target name
     * @param rule the rule
     * @param priority the map priority
     * @param mapRelation the map relation
     * @param block the map block
     * @param group the map group
     */
    public MapEntry(final Long id, final MapRecord mapRecord, final Set<MapAdvice> mapAdvices, final String targetId, final String targetName,
        final String rule, final int priority, final MapRelation mapRelation, final int block, final int group) {

        super();
        this.mapRecord = mapRecord;
        this.mapAdvices = mapAdvices;
        this.targetId = targetId;
        this.targetName = targetName;
        this.rule = rule;
        this.priority = priority;
        this.mapRelation = mapRelation;
        this.block = block;
        this.group = group;
    }

    /**
     * Deep copy constructor.
     *
     * @param mapEntry the map entry
     * @param keepIds the keep ids
     */
    public MapEntry(final MapEntry mapEntry, final boolean keepIds) {

        super();

        // copy id, otherwise leave null
        if (keepIds) {
            super.setId(mapEntry.getId());
        }
        this.mapRecord = mapEntry.getMapRecord();

        // copy basic type fields (non-persisted objects)
        this.targetId = mapEntry.getTargetId();
        this.targetName = mapEntry.getTargetName();
        this.rule = mapEntry.getRule();
        this.priority = mapEntry.getMapPriority();
        this.block = mapEntry.getMapBlock();
        this.group = mapEntry.getMapGroup();

        // copy advices
        for (final MapAdvice mapAdvice : mapEntry.getMapAdvices()) {
            addMapAdvice(new MapAdvice(mapAdvice));
        }

        // copy entries
        if (mapEntry.getMapRelation() != null) {
            this.mapRelation = new MapRelation(mapEntry.getMapRelation());
        }

    }

    /**
     * Gets the target id.
     *
     * @return the target id
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getTargetId() {

        return targetId;
    }

    /**
     * Sets the target id.
     *
     * @param targetId the new target id
     */
    public void setTargetId(final String targetId) {

        this.targetId = targetId;
    }

    /**
     * Gets the target name.
     *
     * @return the target name
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    @FullTextField(analyzer = "noStopWord")
    public String getTargetName() {

        return this.targetName;
    }

    /**
     * Sets the target name.
     *
     * @param targetName the new target name
     */
    public void setTargetName(final String targetName) {

        this.targetName = targetName;

    }

    /**
     * Gets the map relation.
     *
     * @return the map relation
     */
    public MapRelation getMapRelation() {

        return mapRelation;
    }

    /**
     * Sets the map relation.
     *
     * @param mapRelation the new map relation
     */
    public void setMapRelation(final MapRelation mapRelation) {

        this.mapRelation = mapRelation;
    }

    /**
     * Gets the map advices.
     *
     * @return the map advices
     */
    public Set<MapAdvice> getMapAdvices() {

        if (mapAdvices == null)
            mapAdvices = new HashSet<>();// ensures proper serialization
        return mapAdvices;
    }

    /**
     * Sets the map advices.
     *
     * @param mapAdvices the new map advices
     */
    public void setMapAdvices(final Set<MapAdvice> mapAdvices) {

        this.mapAdvices = mapAdvices;
    }

    /**
     * Gets the additional map entry infos.
     *
     * @return the additional map entry infos
     */
    public Set<AdditionalMapEntryInfo> getAdditionalMapEntryInfos() {

        if (additionalMapEntryInfos == null)
            additionalMapEntryInfos = new HashSet<>();// ensures proper serialization
        return additionalMapEntryInfos;
    }

    /**
     * Sets the additional map entry infos.
     *
     * @param additionalMapEntryInfos the new additional map entry infos
     */
    public void setAdditionalMapEntryInfos(final Set<AdditionalMapEntryInfo> additionalMapEntryInfos) {

        this.additionalMapEntryInfos = additionalMapEntryInfos;
    }

    /**
     * Adds the map advice.
     *
     * @param mapAdvice the map advice
     */
    public void addMapAdvice(final MapAdvice mapAdvice) {

        mapAdvices.add(mapAdvice);
    }

    /**
     * Removes the map advice.
     *
     * @param mapAdvice the map advice
     */
    public void removeMapAdvice(final MapAdvice mapAdvice) {

        mapAdvices.remove(mapAdvice);
    }

    /**
     * Gets the rule.
     *
     * @return the rule
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getRule() {

        return rule;
    }

    /**
     * Sets the rule.
     *
     * @param rule the new rule
     */
    public void setRule(final String rule) {

        this.rule = rule;
    }

    /**
     * Gets the map priority.
     *
     * @return the map priority
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public int getMapPriority() {

        return priority;
    }

    /**
     * Sets the map priority.
     *
     * @param priority the new map priority
     */
    public void setMapPriority(final int priority) {

        this.priority = priority;
    }

    /**
     * Gets the map record.
     *
     * @return the map record
     */
    @XmlTransient
    public MapRecord getMapRecord() {

        return this.mapRecord;
    }

    /**
     * Sets the map record.
     *
     * @param mapRecord the new map record
     */
    public void setMapRecord(final MapRecord mapRecord) {

        this.mapRecord = mapRecord;
    }

    /**
     * Returns the map record id.
     *
     * @return the map record id
     */
    public String getMapRecordId() {

        return mapRecord != null ? mapRecord.getId() : null;
    }

    /**
     * Sets the map record based on serialized id Necessary when receiving a serialized entry with only mapRecordId.
     *
     * @param mapRecordId the map record id
     */
    public void setMapRecordId(final String mapRecordId) {

        if (this.mapRecord == null) {
            this.mapRecord = new MapRecord();
            this.mapRecord.setId(mapRecordId);
        }
    }

    /**
     * Gets the map group.
     *
     * @return the map group
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public int getMapGroup() {

        return this.group;
    }

    /**
     * Sets the map group.
     *
     * @param group the new map group
     */
    public void setMapGroup(final int group) {

        this.group = group;

    }

    /**
     * Gets the map block.
     *
     * @return the map block
     */
    public int getMapBlock() {

        return this.block;
    }

    /**
     * Sets the map block.
     *
     * @param block the new map block
     */
    public void setMapBlock(final int block) {

        this.block = block;

    }

    /**
     * Hash code.
     *
     * @return the int
     */
    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((mapAdvices == null) ? 0 : mapAdvices.hashCode());
        result = prime * result + block;
        result = prime * result + group;
        result = prime * result + priority;

        // note: use map record id instead of map record to prevent hashCode()
        // circular reference chain
        result = prime * result + ((mapRecord.getId() == null) ? 0 : mapRecord.getId().hashCode());
        result = prime * result + ((mapRelation == null) ? 0 : mapRelation.hashCode());
        result = prime * result + ((rule == null) ? 0 : rule.hashCode());
        result = prime * result + ((targetId == null) ? 0 : targetId.hashCode());
        result = prime * result + ((targetName == null) ? 0 : targetName.hashCode());

        return result;
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    /* see superclass */
    @Override
    public boolean equals(final Object obj) {

        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final MapEntry other = (MapEntry) obj;
        if (mapAdvices == null) {
            if (other.mapAdvices != null)
                return false;
        } else if (!mapAdvices.equals(other.mapAdvices))
            return false;
        if (block != other.block)
            return false;
        if (group != other.group)
            return false;
        if (priority != other.priority)
            return false;

        // note: only compare map record id, otherwise equals() circular
        // reference chain
        if (mapRecord.getId() == null) {
            if (other.mapRecord.getId() != null)
                return false;
        } else if (!mapRecord.getId().equals(other.mapRecord.getId()))
            return false;
        if (mapRelation == null) {
            if (other.mapRelation != null)
                return false;
        } else if (!mapRelation.equals(other.mapRelation))
            return false;
        if (rule == null) {
            if (other.rule != null)
                return false;
        } else if (!rule.equals(other.rule))
            return false;
        if (targetId == null) {
            if (other.targetId != null)
                return false;
        } else if (!targetId.equals(other.targetId))
            return false;
        if (targetName == null) {
            if (other.targetName != null)
                return false;
        } else if (!targetName.equals(other.targetName))
            return false;
        return true;
    }

    /**
     * To string.
     *
     * @return the string
     */
    /* see superclass */
    @Override
    public String toString() {

        return "MapEntry [id=" + super.getId() + ", mapRecord=" + (mapRecord == null ? "" : mapRecord.getId()) + ", mapAdvices="
            + (mapAdvices == null ? "null" : mapAdvices) + ", targetId=" + targetId + ", targetName=" + targetName + ", rule=" + rule + ", priority=" + priority
            + ", mapRelation=" + (mapRelation == null ? "null" : mapRelation) + ", block=" + block + ", group=" + group + "]";
    }

    /**
     * Checks if is equivalent.
     *
     * @param me the me
     * @return true, if is equivalent
     */
    public boolean isEquivalent(final MapEntry me) {

        // if comparison entry is null, return false
        if (me == null) {
            return false;
        }

        // targets must be equal
        final String id1 = this.targetId == null ? "" : this.targetId;
        final String id2 = me.getTargetId() == null ? "" : me.getTargetId();
        if (!id1.equals(id2)) {
            return false;
        }

        // rules must be identical
        if (this.rule == null && me.getRule() != null) {
            return false;
        }
        if (this.rule != null && !this.rule.equals(me.getRule())) {
            return false;
        }

        // relation must be identical
        if (this.mapRelation != null) {

            // if both non-null, return false if non equal
            if (me.getMapRelation() != null) {
                if (!this.mapRelation.equals(me.getMapRelation())) {
                    return false;
                }

                // return false if this relation is non-null and me's relation
                // is null
            } else {
                return false;
            }

            // if this relation is null and me's relation is non-null, return
            // false
        } else if (me.getMapRelation() != null) {
            return false;
        }
        // advices must be identical
        if (this.mapAdvices == null && me.getMapAdvices() != null) {
            return false;
        } else if (this.mapAdvices != null && me.getMapAdvices() == null) {
            return false;
        } else if (mapAdvices != null && mapAdvices.size() != me.getMapAdvices().size()) {
            return false;
        } else if (mapAdvices != null) {
            for (final MapAdvice ma : this.mapAdvices) {
                if (!me.getMapAdvices().contains(ma)) {
                    return false;
                }
            }
        }

        // additional map entry info must be identical
        if (this.additionalMapEntryInfos == null && me.getAdditionalMapEntryInfos() != null) {
            return false;
        } else if (this.additionalMapEntryInfos != null && me.getAdditionalMapEntryInfos() == null) {
            return false;
        } else if (additionalMapEntryInfos != null && additionalMapEntryInfos.size() != me.getAdditionalMapEntryInfos().size()) {
            return false;
        } else if (additionalMapEntryInfos != null) {
            for (final AdditionalMapEntryInfo mi : this.additionalMapEntryInfos) {
                if (!me.getAdditionalMapEntryInfos().contains(mi)) {
                    return false;
                }
            }
        }

        return true;
    }

}
