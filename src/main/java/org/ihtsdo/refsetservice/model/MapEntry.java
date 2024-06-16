/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// TODO: Auto-generated Javadoc
/**
 * The Map Entry object.
 *
 */
@Entity
@Table(name = "map_entries")
@JsonIgnoreProperties(ignoreUnknown = true, value = {
    "hibernateLazyInitializer", "handler"
})
@Indexed
public class MapEntry extends AbstractHasModified {

    /** The advices. */
    @ElementCollection
    @Fetch(FetchMode.JOIN)
    private Set<String> advices = new HashSet<>();

    /** The additional map entry info. */
    @OneToMany(cascade = CascadeType.ALL, targetEntity = AdditionalMapEntryInfo.class, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("created ASC")
    private Set<AdditionalMapEntryInfo> additionalMapEntryInfos = new HashSet<>();

    /** The target. */
    @Column(nullable = false, length = 4000)
    private String toCode;

    /** The target name. */
    @Column(nullable = false, length = 4000)
    private String toName;

    /** The rule. */
    @Column(nullable = false, length = 4000)
    private String rule;

    /** The map priority. */
    @Column(nullable = false)
    private int priority;

    /** The relation. */
    @Column(nullable = false, length = 4000)
    private String relation;
    
    /** The relation. */
    private String relationCode;

    /** The map block. */
    @Column(nullable = false)
    private int block;

    /** The map group. */
    @Column(nullable = false, name = "map_group")
    private int group;
    
    /** The module id. */
    @Column(nullable = false)
    private String moduleId;


    /**
     * default constructor.
     */
    public MapEntry() {

        // empty
    }

    /**
     * Returns the to code.
     *
     * @return the to code
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getToCode() {

        return toCode;
    }

    /**
     * Sets the to code.
     *
     * @param toCode the to code
     */
    public void setToCode(final String toCode) {

        this.toCode = toCode;
    }

    /**
     * Returns the to name.
     *
     * @return the to name
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getToName() {

        return this.toName;
    }

    /**
     * Sets the to name.
     *
     * @param toName the to name
     */
    public void setToName(final String toName) {

        this.toName = toName;

    }

    /**
     * Gets the relation.
     *
     * @return the relation
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getRelation() {

        return relation;
    }

    /**
     * Sets the relation.
     *
     * @param relation the relation
     */
    public void setRelation(final String relation) {

        this.relation = relation;
    }
    

    /**
     * Gets the relation code.
     *
     * @return the relation code
     */
    @Transient
    public String getRelationCode() {

        return relationCode;
    }


    /**
     * Sets the relation.
     *
     * @param relationCode the new relation
     */
    public void setRelationCode(final String relationCode) {

        this.relationCode = relationCode;
    }

    /**
     * Gets the advices.
     *
     * @return the advices
     */
    public Set<String> getAdvices() {

        if (advices == null) {
            advices = new HashSet<>();// ensures proper serialization
        }
        return advices;
    }

    /**
     * Sets the advices.
     *
     * @param advices the advices
     */
    public void setAdvices(final Set<String> advices) {

        this.advices = advices;
    }

    /**
     * Gets the additional map entry infos.
     *
     * @return the additional map entry infos
     */
    public Set<AdditionalMapEntryInfo> getAdditionalMapEntryInfos() {

        if (additionalMapEntryInfos == null) {
            additionalMapEntryInfos = new HashSet<>();// ensures proper serialization
        }
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
     * Adds the advice.
     *
     * @param advice the advice
     */
    public void addAdvice(final String advice) {

        advices.add(advice);
    }

    /**
     * Removes the advice.
     *
     * @param advice the advice
     */
    public void removeAdvice(final String advice) {

        advices.remove(advice);
    }

    /**
     * Gets the rule.
     *
     * @return the rule
     */
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
     * Returns the priority.
     *
     * @return the priority
     */
    public int getPriority() {

        return priority;
    }

    /**
     * Sets the priority.
     *
     * @param priority the priority
     */
    public void setPriority(final int priority) {

        this.priority = priority;
    }

    /**
     * Returns the group.
     *
     * @return the group
     */
    public int getGroup() {

        return this.group;
    }

    /**
     * Sets the group.
     *
     * @param group the group
     */
    public void setGroup(final int group) {

        this.group = group;

    }

    /**
     * Returns the block.
     *
     * @return the block
     */
    public int getBlock() {

        return this.block;
    }

    /**
     * Sets the block.
     *
     * @param block the new block
     */
    public void setBlock(final int block) {

        this.block = block;

    }

    /**
     * Returns the module id.
     *
     * @return the module id
     */
    public String getModuleId() {

        return this.moduleId;
    }

    /**
     * Sets the module id.
     *
     * @param module id the new module id
     */
    public void setModuleId(final String moduleId) {

        this.moduleId = moduleId;

    }    
    
    /**
     * Lazy init.
     */
    @Override
    public void lazyInit() {

        // N/A
    }

    // /**
    // * Hash code.
    // *
    // * @return the int
    // */
    // /* see superclass */
    // @Override
    // public int hashCode() {
    //
    // final int prime = 31;
    // int result = 1;
    // result = prime * result + ((advices == null) ? 0 : advices.hashCode());
    // result = prime * result + block;
    // result = prime * result + group;
    // result = prime * result + priority;
    //
    // // note: use map record id instead of map record to prevent hashCode()
    // // circular reference chain
    // result = prime * result + ((mapRecord.getId() == null) ? 0 : mapRecord.getId().hashCode());
    // result = prime * result + ((relation == null) ? 0 : relation.hashCode());
    // result = prime * result + ((rule == null) ? 0 : rule.hashCode());
    // result = prime * result + ((toId == null) ? 0 : toId.hashCode());
    // result = prime * result + ((toName == null) ? 0 : toName.hashCode());
    //
    // return result;
    // }
    //
    // /**
    // * Equals.
    // *
    // * @param obj the obj
    // * @return true, if successful
    // */
    // /* see superclass */
    // @Override
    // public boolean equals(final Object obj) {
    //
    // if (this == obj)
    // return true;
    // if (obj == null)
    // return false;
    // if (getClass() != obj.getClass())
    // return false;
    // final MapEntry other = (MapEntry) obj;
    // if (advices == null) {
    // if (other.advices != null)
    // return false;
    // } else if (!advices.equals(other.advices))
    // return false;
    // if (block != other.block)
    // return false;
    // if (group != other.group)
    // return false;
    // if (priority != other.priority)
    // return false;
    //
    // // note: only compare map record id, otherwise equals() circular
    // // reference chain
    // if (mapRecord.getId() == null) {
    // if (other.mapRecord.getId() != null)
    // return false;
    // } else if (!mapRecord.getId().equals(other.mapRecord.getId()))
    // return false;
    // if (relation == null) {
    // if (other.relation != null)
    // return false;
    // } else if (!relation.equals(other.relation))
    // return false;
    // if (rule == null) {
    // if (other.rule != null)
    // return false;
    // } else if (!rule.equals(other.rule))
    // return false;
    // if (toId == null) {
    // if (other.toId != null)
    // return false;
    // } else if (!toId.equals(other.toId))
    // return false;
    // if (toName == null) {
    // if (other.toName != null)
    // return false;
    // } else if (!toName.equals(other.toName))
    // return false;
    // return true;
    // }
    //
    // /**
    // * To string.
    // *
    // * @return the string
    // */
    // /* see superclass */
    // @Override
    // public String toString() {
    //
    // return "MapEntry [id=" + super.getId() + ", mapRecord=" + (mapRecord == null ? "" : mapRecord.getId()) + ", mapAdvices="
    // + (advices == null ? "null" : advices) + ", targetId=" + toId + ", targetName=" + toName + ", rule=" + rule + ", priority=" + priority
    // + ", mapRelation=" + (relation == null ? "null" : relation) + ", block=" + block + ", group=" + group + "]";
    // }
    //
    // /**
    // * Checks if is equivalent.
    // *
    // * @param me the me
    // * @return true, if is equivalent
    // */
    // public boolean isEquivalent(final MapEntry me) {
    //
    // // if comparison entry is null, return false
    // if (me == null) {
    // return false;
    // }
    //
    // // targets must be equal
    // final String id1 = this.toId == null ? "" : this.toId;
    // final String id2 = me.getTargetId() == null ? "" : me.getTargetId();
    // if (!id1.equals(id2)) {
    // return false;
    // }
    //
    // // rules must be identical
    // if (this.rule == null && me.getRule() != null) {
    // return false;
    // }
    // if (this.rule != null && !this.rule.equals(me.getRule())) {
    // return false;
    // }
    //
    // // relation must be identical
    // if (this.relation != null) {
    //
    // // if both non-null, return false if non equal
    // if (me.getMapRelation() != null) {
    // if (!this.relation.equals(me.getMapRelation())) {
    // return false;
    // }
    //
    // // return false if this relation is non-null and me's relation
    // // is null
    // } else {
    // return false;
    // }
    //
    // // if this relation is null and me's relation is non-null, return
    // // false
    // } else if (me.getMapRelation() != null) {
    // return false;
    // }
    // // advices must be identical
    // if (this.advices == null && me.getMapAdvices() != null) {
    // return false;
    // } else if (this.advices != null && me.getMapAdvices() == null) {
    // return false;
    // } else if (advices != null && advices.size() != me.getMapAdvices().size()) {
    // return false;
    // } else if (advices != null) {
    // for (final MapAdvice ma : this.advices) {
    // if (!me.getMapAdvices().contains(ma)) {
    // return false;
    // }
    // }
    // }
    //
    // // additional map entry info must be identical
    // if (this.additionalMapEntryInfos == null && me.getAdditionalMapEntryInfos() != null) {
    // return false;
    // } else if (this.additionalMapEntryInfos != null && me.getAdditionalMapEntryInfos() == null) {
    // return false;
    // } else if (additionalMapEntryInfos != null && additionalMapEntryInfos.size() != me.getAdditionalMapEntryInfos().size()) {
    // return false;
    // } else if (additionalMapEntryInfos != null) {
    // for (final AdditionalMapEntryInfo mi : this.additionalMapEntryInfos) {
    // if (!me.getAdditionalMapEntryInfos().contains(mi)) {
    // return false;
    // }
    // }
    // }
    //
    // return true;
    // }

}
