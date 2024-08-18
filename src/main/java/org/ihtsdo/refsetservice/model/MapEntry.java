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
import java.util.Objects;
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
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
    
    /** The released. */
    @Column(nullable = false)
    private boolean released = false;

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
     * @param moduleId the new module id
     */
    public void setModuleId(final String moduleId) {

        this.moduleId = moduleId;

    }

    /**
     * Returns the released 
     * 
     * @return
     */
    public boolean isReleased() {
		return released;
	}

	/**
	 * Sets the released
	 * 
	 * @param released
	 */
	public void setReleased(boolean released) {
		this.released = released;
	}

	/**
     * Lazy init.
     */
    @Override
    public void lazyInit() {

        // N/A
    }

    @Override
    public int hashCode() {

        final int prime = 31;
        int result = super.hashCode();
        result =
            prime * result + Objects.hash(additionalMapEntryInfos, advices, block, group, moduleId, priority, relation, relationCode, rule, toCode, toName);
        return result;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        MapEntry other = (MapEntry) obj;
        return Objects.equals(additionalMapEntryInfos, other.additionalMapEntryInfos) && Objects.equals(advices, other.advices) && block == other.block
            && group == other.group && Objects.equals(moduleId, other.moduleId) && priority == other.priority && Objects.equals(relation, other.relation)
            && Objects.equals(relationCode, other.relationCode) && Objects.equals(rule, other.rule) && Objects.equals(toCode, other.toCode)
            && Objects.equals(toName, other.toName);
    }

    @Override
    public String toString() {

        try {
            return ModelUtility.toJson(this);
        } catch (final Exception e) {
            return e.getMessage();
        }
    }

}
