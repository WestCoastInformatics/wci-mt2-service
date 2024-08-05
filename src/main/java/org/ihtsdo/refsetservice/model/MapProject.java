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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.helpers.MapRefsetPattern;
import org.ihtsdo.refsetservice.helpers.RelationStyle;
import org.ihtsdo.refsetservice.helpers.WorkflowType;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.micrometer.core.instrument.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * enabled implementation of {@link MapProject}.
 */
@Entity
@Table(name = "map_projects", uniqueConstraints = {
    @UniqueConstraint(columnNames = {
        "name"
    })
})
@Schema(description = "Represents a mapping project")
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class MapProject extends AbstractHasModified implements Copyable<MapProject>, ValidateCrud<MapProject> {

    /** The name. */
    @Column(nullable = false)
    private String name;

    /** The description. */
    @Column(nullable = true, length = 4000)
    private String description;

    /** The edition the project is based upon. */
    @ManyToOne(targetEntity = Edition.class)
    @JoinColumn(nullable = true)
    @Fetch(FetchMode.JOIN)
    private Edition edition;

    /** The private flag. */
    @Column(nullable = false)
    private boolean privateProject;

    /** email for primary contact. */
    @Column(nullable = true, length = 255)
    private String primaryContactEmail;

    /** Whether this project's map notes are viewable by public roles. */
    @Column(unique = false, nullable = false)
    private boolean mapNotesPublic = false;

    /**
     * Indicates whether there is group structure for map records of this project.
     */
    @Column(unique = false, nullable = false)
    private boolean groupStructure = false;

    /** Indicates if the map project has been published. */
    @Column(unique = false, nullable = false)
    private boolean published = false;

    /** Indicates if tags are used for this project. */
    @Column(unique = false, nullable = false)
    private boolean useTags = false;

    /**
     * Indicates what type of workflow to use for this project, defaults to conflict review.
     */
    @Enumerated(EnumType.STRING)
    private WorkflowType workflowType = null;

    /** The ref set id. */
    private String refSetId;

    /** The module id. */
    @Column(nullable = true)
    private String moduleId;

    /** The ref set name. */
    private String refSetName;

    /** The editing cycle begin date. */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = true)
    private Date editingCycleBeginDate;

    /** The latest publication date. */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = true)
    private Date latestPublicationDate;

    /** The source terminology. */
    @Column(nullable = false)
    private String sourceTerminology;

    /** The source terminology version. */
    @Column(nullable = false)
    private String sourceTerminologyVersion;

    /** The destination terminology. */
    @Column(nullable = false)
    private String destinationTerminology;

    /** The destination terminology version. */
    @Column(nullable = false)
    private String destinationTerminologyVersion;

    /** The RF2 refset pattern for this map project. */
    @Enumerated(EnumType.STRING)
    private MapRefsetPattern mapRefsetPattern = null;

    /** The RF2 refset pattern for this map project. */
    @Column(nullable = true)
    private Boolean reverseMapPattern = false;

    /** The relation behavior. */
    @Enumerated(EnumType.STRING)
    private RelationStyle mapRelationStyle = null;

    /** The mapping principle document name. */
    @Column(nullable = true)
    private String mapPrincipleSourceDocumentName;

    /** The mapping principle document. */
    @Column(nullable = true)
    private String mapPrincipleSourceDocument;

    /** Flag for whether this project is rule based. */
    @Column(nullable = false)
    private boolean ruleBased;

    /** The preset age ranges. */
    @ManyToMany(targetEntity = MapAgeRange.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "map_projects_map_age_ranges", joinColumns = @JoinColumn(name = "map_projects_id"))
    private Set<MapAgeRange> presetAgeRanges = new HashSet<>();

    /** The map leads. */
    @ManyToMany(targetEntity = MapUser.class, fetch = FetchType.LAZY)
    @JoinTable(name = "map_projects_map_leads", joinColumns = @JoinColumn(name = "map_projects_id"), inverseJoinColumns = @JoinColumn(name = "map_users_id"))
    // @IndexedEmbedded
    private Set<MapUser> mapLeads = new HashSet<>();

    /** The map specialists. */
    @ManyToMany(targetEntity = MapUser.class, fetch = FetchType.LAZY)
    @JoinTable(name = "map_projects_map_specialists", joinColumns = @JoinColumn(name = "map_projects_id"),
        inverseJoinColumns = @JoinColumn(name = "map_users_id"))
    // @IndexedEmbedded
    private Set<MapUser> mapSpecialists = new HashSet<>();

    /** The allowable map principles for this MapProject. */
    @ManyToMany(targetEntity = MapPrinciple.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "map_projects_map_principles", joinColumns = @JoinColumn(name = "map_projects_id"))
    // @IndexedEmbedded
    private Set<MapPrinciple> mapPrinciples = new HashSet<>();

    /** The allowable map advices for this MapProject. */
    @ManyToMany(targetEntity = MapAdvice.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "map_projects_map_advices", joinColumns = @JoinColumn(name = "map_projects_id"))
    // @IndexedEmbedded
    private Set<MapAdvice> mapAdvices = new HashSet<>();

    /** The allowable additional map entry info for this MapProject. */
    @ManyToMany(targetEntity = AdditionalMapEntryInfo.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "map_projects_additional_map_entry_infos", joinColumns = @JoinColumn(name = "map_projects_id"))
    // @IndexedEmbedded
    private Set<AdditionalMapEntryInfo> additionalMapEntryInfos = new HashSet<>();

    /** The allowable map relations for this MapProject. */
    @ManyToMany(targetEntity = MapRelation.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "map_projects_map_relations", joinColumns = @JoinColumn(name = "map_projects_id"))
    // @IndexedEmbedded
    private Set<MapRelation> mapRelations = new HashSet<>();

    /** The allowable report definitions for this MapProject. */
    @ManyToMany(targetEntity = MapReportDefinition.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "map_projects_report_definitions", joinColumns = @JoinColumn(name = "map_projects_id"))
    // @IndexedEmbedded
    private Set<MapReportDefinition> mapReportDefinitions = new HashSet<>();

    /** The concepts in scope for this project. */
    @ElementCollection
    @CollectionTable(name = "map_projects_scope_concepts", joinColumns = @JoinColumn(name = "id"))
    @Column(name = "scope_concepts", nullable = true)
    private Set<String> scopeConcepts = new HashSet<>();

    /** The concepts excluded from scope of this project. */
    @ElementCollection
    @CollectionTable(name = "map_projects_scope_excluded_concepts", joinColumns = @JoinColumn(name = "id"))
    @Column(name = "scope_excluded_concepts", nullable = true)
    private Set<String> scopeExcludedConcepts = new HashSet<>();

    /** Indicates if descendants of the scope are included in the scope. */
    @Column(unique = false, nullable = false)
    private boolean scopeDescendantsFlag = false;

    /**
     * Indicates if descendants of the excluded scope are excluded from the scope.
     */
    @Column(unique = false, nullable = false)
    private boolean scopeExcludedDescendantsFlag = false;

    /** The error messages allowed for this project. */
    @ElementCollection
    @CollectionTable(name = "map_projects_error_messages", joinColumns = @JoinColumn(name = "id"))
    @Column(name = "error_messages", nullable = true)
    private Set<String> errorMessages = new HashSet<>();

    /** The propagated flag. */
    @Column(unique = false, nullable = false)
    private boolean propagatedFlag = false;

    /** The propagation descendant threshold. */
    @Column(nullable = true)
    private Integer propagationDescendantThreshold;

    /** The team based. */
    @Column(nullable = false)
    private boolean teamBased = false;

    /** The of roles for this project. */
    @Transient
    private List<String> roles;

    /** The member list. */
    @Transient
    private List<User> memberList;

    /** team id and name. */
    @Transient
    private Set<IdName> teamDetails;

    /**
     * Default constructor.
     */
    public MapProject() {

        // n/a
    }

    /**
     * Populate from.
     *
     * @param mapProject the map project
     */
    public void populateFrom(final MapProject mapProject) {

        super.populateFrom(mapProject);
        this.name = mapProject.getName();
        this.description = mapProject.getDescription();
        this.privateProject = mapProject.isPrivateProject();
        this.primaryContactEmail = mapProject.getPrimaryContactEmail();
        this.edition = mapProject.getEdition();
        this.roles = mapProject.getRoles();
        this.memberList = mapProject.getMemberList();
        this.teamDetails = mapProject.getTeamDetails();
        this.mapNotesPublic = isMapNotesPublic();
        this.groupStructure = mapProject.isGroupStructure();
        this.published = mapProject.isPublished();
        this.useTags = mapProject.isUseTags();
        this.refSetId = mapProject.getRefSetId();
        this.moduleId = mapProject.getModuleId();
        this.refSetName = mapProject.getRefSetName();
        this.sourceTerminology = mapProject.getSourceTerminology();
        this.sourceTerminologyVersion = mapProject.getSourceTerminologyVersion();
        this.destinationTerminology = mapProject.getDestinationTerminology();
        this.destinationTerminologyVersion = mapProject.getDestinationTerminologyVersion();
        this.mapRefsetPattern = mapProject.getMapRefsetPattern();
        this.reverseMapPattern = mapProject.getReverseMapPattern();
        this.mapRelationStyle = mapProject.getMapRelationStyle();
        this.mapPrincipleSourceDocument = mapProject.getMapPrincipleSourceDocument();
        this.mapPrincipleSourceDocumentName = mapProject.getMapPrincipleSourceDocumentName();
        this.ruleBased = mapProject.isRuleBased();
        this.presetAgeRanges = mapProject.getPresetAgeRanges();
        this.mapLeads = mapProject.getMapLeads();
        this.mapSpecialists = mapProject.getMapSpecialists();
        this.mapPrinciples = mapProject.getMapPrinciples();
        this.mapAdvices = mapProject.getMapAdvices();
        this.additionalMapEntryInfos = mapProject.getAdditionalMapEntryInfos();
        this.mapRelations = mapProject.getMapRelations();
        this.scopeConcepts = mapProject.getScopeConcepts();
        this.scopeExcludedConcepts = mapProject.getScopeExcludedConcepts();
        this.scopeDescendantsFlag = mapProject.isScopeDescendantsFlag();
        this.scopeExcludedDescendantsFlag = mapProject.isScopeExcludedDescendantsFlag();
        this.errorMessages = mapProject.getErrorMessages();
        this.propagatedFlag = mapProject.isPropagatedFlag();
        this.propagationDescendantThreshold = mapProject.getPropagationDescendantThreshold();
        this.workflowType = mapProject.getWorkflowType();
        this.latestPublicationDate = mapProject.getLatestPublicationDate();
        this.editingCycleBeginDate = mapProject.getEditingCycleBeginDate();
        this.teamBased = mapProject.isTeamBased();
    }

    /**
     * Instantiates a {@link MapProject} from the specified parameters.
     *
     * @param mapProject the map project
     */
    public MapProject(final MapProject mapProject) {

        populateFrom(mapProject);

    }

    /**
     * Gets the map leads.
     *
     * @return the map leads
     */
    public Set<MapUser> getMapLeads() {

        return mapLeads;
    }

    /**
     * Sets the map leads.
     *
     * @param mapLeads the new map leads
     */
    public void setMapLeads(final Set<MapUser> mapLeads) {

        this.mapLeads = mapLeads;
    }

    /**
     * Adds the map lead.
     *
     * @param mapLead the map lead
     */
    public void addMapLead(final MapUser mapLead) {

        mapLeads.add(mapLead);
    }

    /**
     * Removes the map lead.
     *
     * @param mapLead the map lead
     */
    public void removeMapLead(final MapUser mapLead) {

        mapLeads.remove(mapLead);
    }

    /**
     * Gets the map specialists.
     *
     * @return the map specialists
     */
    public Set<MapUser> getMapSpecialists() {

        return mapSpecialists;
    }

    /**
     * Sets the map specialists.
     *
     * @param mapSpecialists the new map specialists
     */
    public void setMapSpecialists(final Set<MapUser> mapSpecialists) {

        this.mapSpecialists = mapSpecialists;
    }

    /**
     * Adds the map specialist.
     *
     * @param mapSpecialist the map specialist
     */
    public void addMapSpecialist(final MapUser mapSpecialist) {

        mapSpecialists.add(mapSpecialist);
    }

    /**
     * Removes the map specialist.
     *
     * @param mapSpecialist the map specialist
     */
    public void removeMapSpecialist(final MapUser mapSpecialist) {

        mapSpecialists.remove(mapSpecialist);
    }

    /**
     * Gets the source terminology.
     *
     * @return the source terminology
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getSourceTerminology() {

        return sourceTerminology;
    }

    /**
     * Sets the source terminology.
     *
     * @param sourceTerminology the new source terminology
     */
    public void setSourceTerminology(final String sourceTerminology) {

        this.sourceTerminology = sourceTerminology;
    }

    /**
     * Gets the destination terminology.
     *
     * @return the destination terminology
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getDestinationTerminology() {

        return destinationTerminology;
    }

    /**
     * Sets the destination terminology.
     *
     * @param destinationTerminology the new destination terminology
     */
    public void setDestinationTerminology(final String destinationTerminology) {

        this.destinationTerminology = destinationTerminology;
    }

    /**
     * Gets the source terminology version.
     *
     * @return the source terminology version
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getSourceTerminologyVersion() {

        return sourceTerminologyVersion;
    }

    /**
     * Sets the source terminology version.
     *
     * @param sourceTerminologyVersion the new source terminology version
     */
    public void setSourceTerminologyVersion(final String sourceTerminologyVersion) {

        this.sourceTerminologyVersion = sourceTerminologyVersion;
    }

    /**
     * Gets the destination terminology version.
     *
     * @return the destination terminology version
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getDestinationTerminologyVersion() {

        return destinationTerminologyVersion;
    }

    /**
     * Sets the destination terminology version.
     *
     * @param destinationTerminologyVersion the new destination terminology version
     */
    public void setDestinationTerminologyVersion(final String destinationTerminologyVersion) {

        this.destinationTerminologyVersion = destinationTerminologyVersion;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
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
     * Checks if is private project.
     *
     * @return true, if is private project
     */
    public boolean isPrivateProject() {

        return privateProject;
    }

    /**
     * Sets the private project.
     *
     * @param privateProject the new private project
     */
    public void setPrivateProject(final boolean privateProject) {

        this.privateProject = privateProject;
    }

    /**
     * Checks if is map notes public.
     *
     * @return true, if is map notes public
     */
    public boolean isMapNotesPublic() {

        return mapNotesPublic;
    }

    /**
     * Sets the map notes public.
     *
     * @param mapNotesPublic the new map notes public
     */
    public void setMapNotesPublic(final boolean mapNotesPublic) {

        this.mapNotesPublic = mapNotesPublic;
    }

    /**
     * Checks if is group structure.
     *
     * @return true, if is group structure
     */
    public boolean isGroupStructure() {

        return groupStructure;
    }

    /**
     * Sets the group structure.
     *
     * @param groupStructure the new group structure
     */
    public void setGroupStructure(final boolean groupStructure) {

        this.groupStructure = groupStructure;
    }

    /**
     * Checks if is published.
     *
     * @return true, if is published
     */
    public boolean isPublished() {

        return published;
    }

    /**
     * Sets the published.
     *
     * @param published the new published
     */
    public void setPublished(final boolean published) {

        this.published = published;
    }

    /**
     * Checks if is use tags.
     *
     * @return true, if is use tags
     */
    public boolean isUseTags() {

        return useTags;
    }

    /**
     * Sets the use tags.
     *
     * @param useTags the new use tags
     */
    public void setUseTags(final boolean useTags) {

        this.useTags = useTags;
    }

    /**
     * Gets the workflow type.
     *
     * @return the workflow type
     */
    public WorkflowType getWorkflowType() {

        return workflowType;
    }

    /**
     * Sets the workflow type.
     *
     * @param workflowType the new workflow type
     */
    public void setWorkflowType(final WorkflowType workflowType) {

        this.workflowType = workflowType;
    }

    /**
     * Gets the ref set name.
     *
     * @return the ref set name
     */
    public String getRefSetName() {

        return this.refSetName;
    }

    /**
     * Sets the ref set name.
     *
     * @param refSetName the new ref set name
     */
    public void setRefSetName(final String refSetName) {

        this.refSetName = refSetName;

    }

    /**
     * Gets the editing cycle begin date.
     *
     * @return the editing cycle begin date
     */
    public Date getEditingCycleBeginDate() {

        if (editingCycleBeginDate == null) {
            return new Date(0);
        }
        return editingCycleBeginDate;
    }

    /**
     * Sets the editing cycle begin date.
     *
     * @param editingCycleBeginDate the new editing cycle begin date
     */
    public void setEditingCycleBeginDate(final Date editingCycleBeginDate) {

        this.editingCycleBeginDate = editingCycleBeginDate;
    }

    /**
     * Gets the latest publication date.
     *
     * @return the latest publication date
     */
    public Date getLatestPublicationDate() {

        if (latestPublicationDate == null) {
            return new Date(0);
        }
        return latestPublicationDate;
    }

    /**
     * Sets the latest publication date.
     *
     * @param latestPublicationDate the new latest publication date
     */
    public void setLatestPublicationDate(final Date latestPublicationDate) {

        this.latestPublicationDate = latestPublicationDate;
    }

    /**
     * Gets the ref set id.
     *
     * @return the ref set id
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getRefSetId() {

        return refSetId;
    }

    /**
     * Sets the ref set id.
     *
     * @param refSetId the new ref set id
     */
    public void setRefSetId(final String refSetId) {

        this.refSetId = refSetId;
    }

    /**
     * Gets the module id.
     *
     * @return the module id
     */
    public String getModuleId() {

        return moduleId;
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
     * Gets the map relation style.
     *
     * @return the map relation style
     */
    public RelationStyle getMapRelationStyle() {

        return mapRelationStyle;
    }

    /**
     * Gets the map principle source document name.
     *
     * @return the map principle source document name
     */
    public String getMapPrincipleSourceDocumentName() {

        return mapPrincipleSourceDocumentName;
    }

    /**
     * Sets the map principle source document name.
     *
     * @param mapPrincipleSourceDocumentName the new map principle source document name
     */
    public void setMapPrincipleSourceDocumentName(final String mapPrincipleSourceDocumentName) {

        this.mapPrincipleSourceDocumentName = mapPrincipleSourceDocumentName;
    }

    /**
     * Sets the map relation style.
     *
     * @param mapRelationStyle the new map relation style
     */
    public void setMapRelationStyle(final RelationStyle mapRelationStyle) {

        this.mapRelationStyle = mapRelationStyle;
    }

    /**
     * Checks if is rule based.
     *
     * @return true, if is rule based
     */
    public boolean isRuleBased() {

        return ruleBased;
    }

    /**
     * Sets the rule based.
     *
     * @param ruleBased the new rule based
     */
    public void setRuleBased(final boolean ruleBased) {

        this.ruleBased = ruleBased;
    }

    /**
     * Gets the map refset pattern.
     *
     * @return the map refset pattern
     */
    public MapRefsetPattern getMapRefsetPattern() {

        return mapRefsetPattern;
    }

    /**
     * Sets the map refset pattern.
     *
     * @param mapRefsetPattern the new map refset pattern
     */
    public void setMapRefsetPattern(final MapRefsetPattern mapRefsetPattern) {

        this.mapRefsetPattern = mapRefsetPattern;
    }

    /**
     * Gets the reverse map pattern.
     *
     * @return the reverse map pattern
     */
    public Boolean getReverseMapPattern() {

        return reverseMapPattern;
    }

    /**
     * Sets the reverse map pattern.
     *
     * @param reverseMapPattern the new reverse map pattern
     */
    public void setReverseMapPattern(final Boolean reverseMapPattern) {

        this.reverseMapPattern = reverseMapPattern;
    }

    /**
     * Gets the map advices.
     *
     * @return the map advices
     */
    public Set<MapAdvice> getMapAdvices() {

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
     * Gets the additional map entry infos.
     *
     * @return the additional map entry infos
     */
    public Set<AdditionalMapEntryInfo> getAdditionalMapEntryInfos() {

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
     * Adds the additional map entry info.
     *
     * @param additionalMapEntryInfo the additional map entry info
     */
    public void addAdditionalMapEntryInfo(final AdditionalMapEntryInfo additionalMapEntryInfo) {

        additionalMapEntryInfos.add(additionalMapEntryInfo);
    }

    /**
     * Removes the additional map entry info.
     *
     * @param additionalMapEntryInfo the additional map entry info
     */
    public void removeAdditionalMapEntryInfo(final AdditionalMapEntryInfo additionalMapEntryInfo) {

        additionalMapEntryInfos.remove(additionalMapEntryInfo);
    }

    /**
     * Gets the map principles.
     *
     * @return the map principles
     */
    public Set<MapPrinciple> getMapPrinciples() {

        return mapPrinciples;
    }

    /**
     * Sets the map principles.
     *
     * @param mapPrinciples the new map principles
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
     * Gets the scope concepts.
     *
     * @return the scope concepts
     */
    public Set<String> getScopeConcepts() {

        return scopeConcepts;
    }

    /**
     * Sets the scope concepts.
     *
     * @param scopeConcepts the new scope concepts
     */
    public void setScopeConcepts(final Set<String> scopeConcepts) {

        this.scopeConcepts = scopeConcepts;
    }

    /**
     * Adds the scope concept.
     *
     * @param terminologyId the terminology id
     */
    public void addScopeConcept(final String terminologyId) {

        this.scopeConcepts.add(terminologyId);
    }

    /**
     * Removes the scope concept.
     *
     * @param terminologyId the terminology id
     */
    public void removeScopeConcept(final String terminologyId) {

        this.scopeConcepts.remove(terminologyId);
    }

    /**
     * Checks if is scope descendants flag.
     *
     * @return true, if is scope descendants flag
     */
    public boolean isScopeDescendantsFlag() {

        return scopeDescendantsFlag;
    }

    /**
     * Sets the scope descendants flag.
     *
     * @param flag the new scope descendants flag
     */
    public void setScopeDescendantsFlag(final boolean flag) {

        scopeDescendantsFlag = flag;
    }

    /**
     * Gets the scope excluded concepts.
     *
     * @return the scope excluded concepts
     */
    public Set<String> getScopeExcludedConcepts() {

        return scopeExcludedConcepts;
    }

    /**
     * Sets the scope excluded concepts.
     *
     * @param scopeExcludedConcepts the new scope excluded concepts
     */
    public void setScopeExcludedConcepts(final Set<String> scopeExcludedConcepts) {

        this.scopeExcludedConcepts = scopeExcludedConcepts;
    }

    /**
     * Adds the scope excluded concept.
     *
     * @param terminologyId the terminology id
     */
    public void addScopeExcludedConcept(final String terminologyId) {

        this.scopeExcludedConcepts.add(terminologyId);
    }

    /**
     * Removes the scope excluded concept.
     *
     * @param terminologyId the terminology id
     */
    public void removeScopeExcludedConcept(final String terminologyId) {

        this.scopeExcludedConcepts.remove(terminologyId);
    }

    /**
     * Checks if is scope excluded descendants flag.
     *
     * @return true, if is scope excluded descendants flag
     */
    public boolean isScopeExcludedDescendantsFlag() {

        return scopeExcludedDescendantsFlag;
    }

    /**
     * Sets the scope excluded descendants flag.
     *
     * @param flag the new scope excluded descendants flag
     */
    public void setScopeExcludedDescendantsFlag(final boolean flag) {

        scopeExcludedDescendantsFlag = flag;
    }

    /**
     * Gets the preset age ranges.
     *
     * @return the preset age ranges
     */
    public Set<MapAgeRange> getPresetAgeRanges() {

        return this.presetAgeRanges;
    }

    /**
     * Sets the preset age ranges.
     *
     * @param ageRanges the new preset age ranges
     */
    public void setPresetAgeRanges(final Set<MapAgeRange> ageRanges) {

        this.presetAgeRanges = ageRanges;
    }

    /**
     * Adds the preset age range.
     *
     * @param ageRange the age range
     */
    public void addPresetAgeRange(final MapAgeRange ageRange) {

        this.presetAgeRanges.add(ageRange);
    }

    /**
     * Removes the preset age range.
     *
     * @param ageRange the age range
     */
    public void removePresetAgeRange(final MapAgeRange ageRange) {

        this.presetAgeRanges.remove(ageRange);
    }

    /**
     * Gets the map relations.
     *
     * @return the map relations
     */
    public Set<MapRelation> getMapRelations() {

        return mapRelations;
    }

    /**
     * Sets the map relations.
     *
     * @param mapRelations the new map relations
     */
    public void setMapRelations(final Set<MapRelation> mapRelations) {

        this.mapRelations = mapRelations;
    }

    /**
     * Adds the map relation.
     *
     * @param mr the mr
     */
    public void addMapRelation(final MapRelation mr) {

        this.mapRelations.add(mr);

    }

    /**
     * Removes the map relation.
     *
     * @param mr the mr
     */
    public void removeMapRelation(final MapRelation mr) {

        this.mapRelations.remove(mr);

    }

    /**
     * Gets the error messages.
     *
     * @return the error messages
     */
    public Set<String> getErrorMessages() {

        return errorMessages;
    }

    /**
     * Sets the error messages.
     *
     * @param errorMessages the new error messages
     */
    public void setErrorMessages(final Set<String> errorMessages) {

        this.errorMessages = errorMessages;
    }

    /**
     * Sets the map principle source document.
     *
     * @param mapPrincipleSourceDocument the new map principle source document
     */
    public void setMapPrincipleSourceDocument(final String mapPrincipleSourceDocument) {

        this.mapPrincipleSourceDocument = mapPrincipleSourceDocument;
    }

    /**
     * Gets the map principle source document.
     *
     * @return the map principle source document
     */
    public String getMapPrincipleSourceDocument() {

        return mapPrincipleSourceDocument;
    }

    /**
     * Returns the propagation descendant threshold.
     *
     * @return the propagation descendant threshold
     */
    public Integer getPropagationDescendantThreshold() {

        return propagationDescendantThreshold;
    }

    /**
     * Sets the propagation descendant threshold.
     *
     * @param propagationDescendantThreshold the propagation descendant threshold
     */
    public void setPropagationDescendantThreshold(final Integer propagationDescendantThreshold) {

        this.propagationDescendantThreshold = propagationDescendantThreshold;
    }

    /**
     * Indicates whether or not propagated flag is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public boolean isPropagatedFlag() {

        return propagatedFlag;
    }

    /**
     * Sets the propagated flag.
     *
     * @param propagatedFlag the propagated flag
     */

    public void setPropagatedFlag(final boolean propagatedFlag) {

        this.propagatedFlag = propagatedFlag;
    }

    /**
     * Gets the map report definitions.
     *
     * @return the map report definitions
     */
    public Set<MapReportDefinition> getMapReportDefinitions() {

        return mapReportDefinitions;
    }

    /**
     * Sets the map report definitions.
     *
     * @param mapReportDefinitions the new map report definitions
     */
    public void setMapReportDefinitions(final Set<MapReportDefinition> mapReportDefinitions) {

        this.mapReportDefinitions = mapReportDefinitions;
    }

    /**
     * Adds the map report definition.
     *
     * @param mapMapReportDefinition the map map report definition
     */
    public void addMapReportDefinition(final MapReportDefinition mapMapReportDefinition) {

        mapReportDefinitions.add(mapMapReportDefinition);
    }

    /**
     * Removes the map report definition.
     *
     * @param mapMapReportDefinition the map map report definition
     */
    public void removeMapReportDefinition(final MapReportDefinition mapMapReportDefinition) {

        mapReportDefinitions.remove(mapMapReportDefinition);
    }

    /**
     * Checks if is team based.
     *
     * @return true, if is team based
     */
    public boolean isTeamBased() {

        return teamBased;
    }

    /**
     * Sets the team based.
     *
     * @param teamBased the new team based
     */
    public void setTeamBased(final boolean teamBased) {

        this.teamBased = teamBased;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {

        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the new description
     */
    public void setDescription(final String description) {

        this.description = description;
    }

    /**
     * Gets the edition.
     *
     * @return the edition
     */
    public Edition getEdition() {

        return edition;
    }

    /**
     * Sets the edition.
     *
     * @param edition the new edition
     */
    public void setEdition(final Edition edition) {

        this.edition = edition;
    }

    /**
     * Gets the primary contact email.
     *
     * @return the primary contact email
     */
    public String getPrimaryContactEmail() {

        return primaryContactEmail;
    }

    /**
     * Sets the primary contact email.
     *
     * @param primaryContactEmail the new primary contact email
     */
    public void setPrimaryContactEmail(final String primaryContactEmail) {

        this.primaryContactEmail = primaryContactEmail;
    }

    /**
     * Returns the roles.
     *
     * @return the roles
     */
    @JsonGetter()
    public List<String> getRoles() {

        if (roles == null) {

            roles = new ArrayList<>();
        }

        return roles;

    }

    /**
     * Sets the roles.
     *
     * @param roles the roles
     */
    public void setRoles(final List<String> roles) {

        this.roles = roles;
    }

    /**
     * Returns the member list.
     *
     * @return the member list
     */
    @JsonGetter()
    public List<User> getMemberList() {

        if (memberList == null) {

            memberList = new ArrayList<>();
        }

        return memberList;
    }

    /**
     * Sets the member list.
     *
     * @param memberList the member list
     */
    public void setMemberList(final List<User> memberList) {

        this.memberList = memberList;
    }

    /**
     * Returns the team details.
     *
     * @return the team details
     */
    @JsonGetter()
    public Set<IdName> getTeamDetails() {

        if (teamDetails == null) {

            teamDetails = new HashSet<>();
        }

        return teamDetails;
    }

    /**
     * Sets the team details.
     *
     * @param teamDetails the team details
     */
    public void setTeamDetails(final Set<IdName> teamDetails) {

        this.teamDetails = teamDetails;
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
        result = prime * result + Objects.hash(additionalMapEntryInfos, description, destinationTerminology, destinationTerminologyVersion,
            editingCycleBeginDate, edition, errorMessages, groupStructure, latestPublicationDate, mapAdvices, mapLeads, mapNotesPublic,
            mapPrincipleSourceDocument, mapPrincipleSourceDocumentName, mapPrinciples, mapRefsetPattern, mapRelationStyle, mapRelations, mapReportDefinitions,
            mapSpecialists, memberList, moduleId, name, presetAgeRanges, primaryContactEmail, privateProject, propagatedFlag, propagationDescendantThreshold,
            published, refSetId, refSetName, reverseMapPattern, roles, ruleBased, scopeConcepts, scopeDescendantsFlag, scopeExcludedConcepts,
            scopeExcludedDescendantsFlag, sourceTerminology, sourceTerminologyVersion, teamBased, teamDetails, useTags, workflowType);
        return result;
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    @Override
    public boolean equals(Object obj) {

        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        MapProject other = (MapProject) obj;
        return Objects.equals(additionalMapEntryInfos, other.additionalMapEntryInfos) && Objects.equals(description, other.description)
            && Objects.equals(destinationTerminology, other.destinationTerminology)
            && Objects.equals(destinationTerminologyVersion, other.destinationTerminologyVersion)
            && Objects.equals(editingCycleBeginDate, other.editingCycleBeginDate) && Objects.equals(edition, other.edition)
            && Objects.equals(errorMessages, other.errorMessages) && groupStructure == other.groupStructure
            && Objects.equals(latestPublicationDate, other.latestPublicationDate) && Objects.equals(mapAdvices, other.mapAdvices)
            && Objects.equals(mapLeads, other.mapLeads) && mapNotesPublic == other.mapNotesPublic
            && Objects.equals(mapPrincipleSourceDocument, other.mapPrincipleSourceDocument)
            && Objects.equals(mapPrincipleSourceDocumentName, other.mapPrincipleSourceDocumentName) && Objects.equals(mapPrinciples, other.mapPrinciples)
            && mapRefsetPattern == other.mapRefsetPattern && mapRelationStyle == other.mapRelationStyle && Objects.equals(mapRelations, other.mapRelations)
            && Objects.equals(mapReportDefinitions, other.mapReportDefinitions) && Objects.equals(mapSpecialists, other.mapSpecialists)
            && Objects.equals(memberList, other.memberList) && Objects.equals(moduleId, other.moduleId) && Objects.equals(name, other.name)
            && Objects.equals(presetAgeRanges, other.presetAgeRanges) && Objects.equals(primaryContactEmail, other.primaryContactEmail)
            && privateProject == other.privateProject && propagatedFlag == other.propagatedFlag
            && Objects.equals(propagationDescendantThreshold, other.propagationDescendantThreshold) && published == other.published
            && Objects.equals(refSetId, other.refSetId) && Objects.equals(refSetName, other.refSetName)
            && Objects.equals(reverseMapPattern, other.reverseMapPattern) && Objects.equals(roles, other.roles) && ruleBased == other.ruleBased
            && Objects.equals(scopeConcepts, other.scopeConcepts) && scopeDescendantsFlag == other.scopeDescendantsFlag
            && Objects.equals(scopeExcludedConcepts, other.scopeExcludedConcepts) && scopeExcludedDescendantsFlag == other.scopeExcludedDescendantsFlag
            && Objects.equals(sourceTerminology, other.sourceTerminology) && Objects.equals(sourceTerminologyVersion, other.sourceTerminologyVersion)
            && teamBased == other.teamBased && Objects.equals(teamDetails, other.teamDetails) && useTags == other.useTags && workflowType == other.workflowType;
    }

    /**
     * Patch from.
     *
     * @param other the other
     */
    @Override
    public void patchFrom(final MapProject other) {

        // Only these field can be patched
        // TODO: Populate fields
        name = other.getName();

    }

    /**
     * To string.
     *
     * @return the string
     */
    /* see superclass */
    @Override
    public String toString() {

        try {

            return ModelUtility.toJson(this);
        } catch (final Exception e) {

            return e.getMessage();
        }

    }

    /**
     * Lazy init.
     */
    /* see superclass */
    @Override
    public void lazyInit() {

        // n/a

    }

    /**
     * Validate add.
     *
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public void validateAdd() throws Exception {

        if (getId() != null) {

            throw new Exception("Unexpected non-null id");
        }

        if (getEdition() == null) {

            throw new Exception("Unexpected null/empty edition");
        }

        if (StringUtils.isBlank(getName())) {

            throw new Exception("Unexpected null/empty name");
        }

    }

    /**
     * Validate update.
     *
     * @param other the other
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public void validateUpdate(final MapProject other) throws Exception {

        if (StringUtils.isBlank(getId())) {

            throw new Exception("Unexpected null/empty id");
        }

        if (getEdition() == null) {

            throw new Exception("Unexpected null/empty edition");
        }

        if (StringUtils.isBlank(getName())) {

            throw new Exception("Unexpected null/empty name");
        }

    }

    /**
     * Validate delete.
     *
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public void validateDelete() throws Exception {

        if (StringUtils.isBlank(getId())) {

            throw new Exception("Unexpected null/empty id");
        }

    }

}
