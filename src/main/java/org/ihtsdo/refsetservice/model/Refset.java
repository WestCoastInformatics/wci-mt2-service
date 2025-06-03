/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
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

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Type;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;
import org.ihtsdo.refsetservice.model.enums.RefsetType;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Represents a refset.
 *
 */
@Entity
@Table(name = "refsets")
@Indexed
public class Refset extends AbstractHasModified implements Comparable<Refset> {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /** The refset ID. */
    @Column(nullable = false, length = 256)
    private String refsetId;

    /** The name. */
    @Column(nullable = false, length = 4000)
    private String name;

    /** The refset type. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 256)
    private RefsetType type;

    /** The version status. */
    @Convert(converter = VersionStatusConverter.class)
    @JsonSerialize(using = VersionStatusJsonSerializer.class)
    @Column(nullable = false, length = 256)
    private VersionStatus versionStatus;

    /** The workflow status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 256)
    private WorkflowStatus workflowStatus;

    /** The version date. */
    @Column(nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    private Date versionDate;

    /** The last refset published release upon which the refset's content is based on. */
    @Column(nullable = false, length = 255)
    private String baseContentVersion;

    /** The international release upon which the refset's content is based on. */
    @Column(nullable = false, length = 255)
    private String internationalContentVersion;

    /** The version narrative. */
    @Column(nullable = true, length = 10000)
    @Type(type = "text")
    private String narrative;

    /** The version status. */
    @Column(nullable = true, length = 10000)
    @Type(type = "text")
    private String versionNotes;

    /** The private flag. */
    @Column(nullable = false)
    private boolean privateRefset;

    /** The local set flag. */
    @Column(nullable = false)
    private boolean localSet = false;

    /** The local set flag. */
    @Column(nullable = false)
    private boolean comboRefset;

    /** The latest published version flag. */
    @Column(nullable = true)
    private boolean latestPublishedVersion;

    /** Does this refset have a version in development (this is only true if this is the latest published version). */
    @Column(nullable = true)
    private boolean hasVersionInDevelopment;

    /** The assigned user. */
    @Column(nullable = true)
    private String assignedUser;

    /** The refset member count. */
    @Column(nullable = false)
    private int memberCount = -1;

    /** The edit branch ID. */
    @Column(nullable = true, length = 256)
    private String editBranchId;

    /** The refset branch ID. */
    @Column(nullable = true, length = 256)
    private String refsetBranchId;

    /** The name for a published version of a localset. */
    @Column(nullable = true, length = 256)
    private String localsetVersionName;

    /** The external URL. */
    @Column(nullable = true, length = 4000)
    private String externalUrl;

    /** The module ID. */
    @Column(nullable = false, length = 256)
    private String moduleId;

    /** The project. */
    @ManyToOne(targetEntity = Project.class)
    @JoinColumn(nullable = true)
    @Fetch(FetchMode.JOIN)
    private Project project;

    /** The refset is in upgrade. */
    @Column(nullable = false)
    private boolean inUpgrade;

    /** The refset is in inactivate. */
    @Column(nullable = false)
    private boolean inInactivate;

    /** The tags. */
    @ElementCollection
    private Set<String> tags = new HashSet<String>();

    /** The definition clauses. */
    @ManyToMany(fetch = FetchType.LAZY, cascade = {
        CascadeType.ALL
    })
    @JoinTable(name = "refsets_definition_clauses", joinColumns = {
        @JoinColumn(name = "Refset_id")
    }, inverseJoinColumns = {
        @JoinColumn(name = "definitionClauses_id")
    })
    @Fetch(FetchMode.JOIN)
    private List<DefinitionClause> definitionClauses = new ArrayList<>();

    /** The definition clauses. */
    /*
     * // @Fetch(FetchMode.JOIN)
     *
     * @ManyToMany(cascade = CascadeType.ALL, targetEntity = DefinitionClause.class, fetch = FetchType.LAZY)
     *
     * @OrderBy("created ASC") private List<DefinitionClause> definitionClauses = new ArrayList<>();
     */
    /** The flag for if a user can download this refset. */
    @Transient
    private boolean downloadable;

    /** The flag for if a user can see the feedback for this refset. */
    @Transient
    private boolean feedbackVisible;

    /** The list of user roles for this refset. */
    @Transient
    private List<String> roles;

    /** The flag for if the refset is locked due to an edit. */
    @Transient
    private boolean locked = false;

    /** The date of the terminology version this refset is based on. */
    @Transient
    private String terminologyVersionDate;

    /** The flag for if the refset was published in the last edition version. */
    @Transient
    private boolean basedOnLatestVersion = false;

    /** The flag to display a warning when first editing if the refset was last published more than one edition version prior. */
    @Transient
    private boolean upgradeWarning = false;

    /** The flag to indicate that this refset is included in search results in part because it matched member or alternate refset descriptions . */
    @Transient
    private boolean memberSearchMatch = false;

    /** The list of actions available for the user to perform on this refset. */
    @Transient
    private Set<WorkflowAction> availableActions;

    /** The ID of the parent of the underlying refset concept. */
    @Transient
    private String parentConceptId;

    /** The complete branch path of the refset. */
    @Transient
    private String branchPath;

    /** The descriptions. */
    @Transient
    private List<Map<String, String>> descriptions = new ArrayList<>();

    /** The flag for if a user can see the feedback for this refset. */
    @Transient
    private List<Map<String, String>> versionList;

    /** The count of discussions for this item. */
    @Transient
    private int openDiscussionCount;

    /** The count of discussions for this item. */
    @Transient
    private int resolvedDiscussionCount;

    /** The upgrade versions candidate. */
    @Transient
    public List<String> upgradeVersionsCandidate;

    /** The value to use for the 'INCLUSION' intensional definition exception type. */
    @Transient
    public static final String INCLUSION = "INCLUSION";

    /** The value to use for the 'EXCLUSION' intensional definition exception type. */
    @Transient
    public static final String EXCLUSION = "EXCLUSION";

    /**
     * Instantiates an empty {@link Refset}.
     */
    public Refset() {

        // n/a
    }

    /**
     * Instantiates a {@link Refset} from the specified parameters.
     *
     * @param code the code
     */
    public Refset(final String code) {

        this.refsetId = code;
    }

    /**
     * Instantiates a {@link Refset} from the specified parameters.
     *
     * @param type the type
     * @param code the code
     * @param name the name
     */
    public Refset(final RefsetType type, final String code, final String name) {

        this.type = type;
        this.refsetId = code;
        this.name = name;
    }

    /**
     * Instantiates a {@link Refset} from the specified parameters.
     *
     * @param other the other
     */
    public Refset(final Refset other) {

        // Avoid lazy init erros
        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final Refset other) {

        super.populateFrom(other);
        refsetId = other.getRefsetId();
        name = other.getName();
        type = other.getType();
        versionStatus = other.getVersionStatus();
        narrative = other.getNarrative();
        versionDate = other.getVersionDate();
        baseContentVersion = other.getBaseContentVersion();
        internationalContentVersion = other.getInternationalContentVersion();
        versionNotes = other.getVersionNotes();
        workflowStatus = other.getWorkflowStatus();
        project = other.getProject();
        externalUrl = other.getExternalUrl();
        moduleId = other.getModuleId();
        editBranchId = other.getEditBranchId();
        refsetBranchId = other.getRefsetBranchId();
        localsetVersionName = other.getLocalsetVersionName();
        assignedUser = other.getAssignedUser();
        memberCount = other.getMemberCount();
        privateRefset = other.isPrivateRefset();
        localSet = other.isLocalSet();
        comboRefset = other.isComboRefset();
        downloadable = other.isDownloadable();
        locked = other.isLocked();
        terminologyVersionDate = other.getTerminologyVersionDate();
        basedOnLatestVersion = other.isBasedOnLatestVersion();
        upgradeWarning = other.getUpgradeWarning();
        memberSearchMatch = other.isMemberSearchMatch();
        availableActions = other.getAvailableActions();
        parentConceptId = other.getParentConceptId();
        branchPath = other.getBranchPath();
        latestPublishedVersion = other.isLatestPublishedVersion();
        hasVersionInDevelopment = other.getHasVersionInDevelopment();
        feedbackVisible = other.isFeedbackVisible();
        versionList = other.getVersionList();
        roles = other.getRoles();
        descriptions = other.getDescriptions();
        tags = new HashSet<>(other.getTags());
        definitionClauses = new ArrayList<>();
        inUpgrade = other.isInUpgrade();
        inInactivate = other.isInInactivate();

        for (final DefinitionClause otherClause : other.getDefinitionClauses()) {
            definitionClauses.add(otherClause);
        }
    }
    
    /**
     * To branch details.
     *
     * @return the branch information
     */
    public BranchInformation toBranchDetails() {
        return new BranchInformation(this);
    }

    /**
     * Returns the refset ID.
     *
     * @return the refset ID
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "refsetIdSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getRefsetId() {

        return refsetId;
    }

    /**
     * Sets the refset ID.
     *
     * @param refsetId the refset ID
     */
    public void setRefsetId(final String refsetId) {

        this.refsetId = refsetId;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "nameSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getName() {

        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the name
     */
    public void setName(final String name) {

        this.name = name;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    @KeywordField(normalizer = "lowercase", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public RefsetType getType() {

        return type;
    }

    /**
     * Sets the type.
     *
     * @param type the type
     */
    public void setType(final RefsetType type) {

        this.type = type;
    }

    /**
     * Returns the version status.
     *
     * @return the version status
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "versionStatusSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public VersionStatus getVersionStatus() {

        return versionStatus;
    }

    /**
     * Sets the version.
     *
     * @param versionStatus the version status
     */
    public void setVersionStatus(final VersionStatus versionStatus) {

        this.versionStatus = versionStatus;
    }

    /**
     * Returns the workflow status.
     *
     * @return the workflow status
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "workflowStatusSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public WorkflowStatus getWorkflowStatus() {

        return workflowStatus;
    }

    /**
     * Sets the workflow status.
     *
     * @param workflowStatus the workflow status
     */
    public void setWorkflowStatus(final WorkflowStatus workflowStatus) {

        this.workflowStatus = workflowStatus;
    }

    /**
     * Gets the version date.
     *
     * @return the versionDate
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    // @DateBridge(resolution = Resolution.SECOND, encoding =
    // EncodingType.STRING)
    public Date getVersionDate() {

        return versionDate;
    }

    /**
     * Sets the version date.
     *
     * @param versionDate the versionDate to set
     */
    public void setVersionDate(final Date versionDate) {

        this.versionDate = versionDate;
    }

    /**
     * Gets the base content version.
     *
     * @return the base content version
     */
    public String getBaseContentVersion() {

        return baseContentVersion;
    }

    /**
     * Sets the base content version.
     *
     * @param baseContentVersion the new base content version
     */
    public void setBaseContentVersion(final String baseContentVersion) {

        this.baseContentVersion = baseContentVersion;
    }

    /**
     * Gets the international content version.
     *
     * @return the international content version
     */
    public String getInternationalContentVersion() {

        return internationalContentVersion;
    }

    /**
     * Sets the international content version.
     *
     * @param internationalContentVersion the new international content version
     */
    public void setInternationalContentVersion(final String internationalContentVersion) {

        this.internationalContentVersion = internationalContentVersion;
    }

    /**
     * Gets the narrative.
     *
     * @return the narrative
     */
    public String getNarrative() {

        return narrative;
    }

    /**
     * Sets the narrative.
     *
     * @param narrative the narrative to set
     */
    public void setNarrative(final String narrative) {

        this.narrative = narrative;
    }

    /**
     * Gets the version notes.
     *
     * @return the versionNotes
     */
    public String getVersionNotes() {

        return versionNotes;
    }

    /**
     * Sets the version notes.
     *
     * @param versionNotes the versionNotes to set
     */
    public void setVersionNotes(final String versionNotes) {

        this.versionNotes = versionNotes;
    }

    /**
     * Checks if is private refset.
     *
     * @return the isPrivateRefset
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public boolean isPrivateRefset() {

        return privateRefset;
    }

    /**
     * Sets the private refset.
     *
     * @param privateRefset the isPrivateRefset to set
     */
    public void setPrivateRefset(final boolean privateRefset) {

        this.privateRefset = privateRefset;
    }

    /**
     * Gets the descriptions.
     *
     * @return the descriptions
     */
    @JsonGetter()
    public List<Map<String, String>> getDescriptions() {

        return descriptions;
    }

    /**
     * Sets the descriptions.
     *
     * @param descriptions the descriptions
     */
    public void setDescriptions(final List<Map<String, String>> descriptions) {

        this.descriptions = descriptions;
    }

    /**
     * Returns the version list.
     *
     * @return the versionList
     */
    @JsonGetter()
    public List<Map<String, String>> getVersionList() {

        if (versionList == null) {

            versionList = new ArrayList<>();
        }

        return versionList;
    }

    /**
     * Sets the version list.
     *
     * @param versionList the versionList to set
     */
    public void setVersionList(final List<Map<String, String>> versionList) {

        this.versionList = versionList;
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
     * Gets the tags.
     *
     * @return the tags
     */
    @FullTextField(analyzer = "standard")
    // @IndexedEmbedded
    public Set<String> getTags() {

        if (tags == null) {

            tags = new HashSet<>();
        }

        return tags;
    }

    /**
     * Sets the tags.
     *
     * @param tags the tags to set
     */
    public void setTags(final Set<String> tags) {

        this.tags = tags;
    }

    /**
     * Gets the edition.
     *
     * @return the edition
     */
    @JsonSerialize(contentAs = Edition.class)
    @JsonDeserialize(contentAs = Edition.class)
    public Edition getEdition() {

        if (project == null || project.getEdition() == null) {

            return null;
        } else {

            return project.getEdition();
        }

    }

    /**
     * Returns the organization name.
     *
     * @return the organization name
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "organizationNameSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = @ObjectPath({
        @PropertyValue(propertyName = "project"), @PropertyValue(propertyName = "edition"), @PropertyValue(propertyName = "organization")
    }))
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    public String getOrganizationName() {

        if (project == null || project.getEdition() == null || project.getEdition().getOrganization() == null) {
            return null;
        } else {
            return project.getEdition().getOrganization().getName();
        }
    }

    /**
     * Returns the organization crowd Id.
     *
     * @return the organization crowd Id
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "organizationCrowdIdSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public String getOrganizationCrowdId() {

        // if (project == null || project.getEdition() == null || project.getEdition().getOrganization() == null) {
        // return null;
        // } else {
        // return project.getEdition().getOrganization().getCrowdId();
        // }
        return null;
    }

    /**
     * Sets the organization name.
     *
     * @param organizationName the organization name to set
     */
    public void setOrganizationName(final String organizationName) {

        if (project != null && project.getEdition() != null && project.getEdition().getOrganization() != null) {
            this.project.getEdition().getOrganization().setName(organizationName);
        }

    }

    /**
     * Sets the edition.
     *
     * @param edition the edition to set
     */
    public void setEdition(final Edition edition) {

        if (project != null) {

            project.setEdition(edition);
        }

    }

    /**
     * Returns the edition ID.
     *
     * @return the edition ID
     */
    public String getEditionId() {

        if (getEdition() != null) {

            return getEdition().getId();
        } else {

            return null;
        }

    }

    /**
     * Sets the edition ID.
     *
     * @param editionId the edition ID to set
     */
    public void setEditionId(final String editionId) {

        if (getEdition() != null) {

            getEdition().setId(editionId);
        }

    }

    /**
     * Returns the edition branch.
     *
     * @return the edition branch
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = @ObjectPath({
        @PropertyValue(propertyName = "project"), @PropertyValue(propertyName = "edition"), @PropertyValue(propertyName = "organization")
    }))
    public String getEditionBranch() {

        if (getEdition() != null) {

            return getEdition().getBranch();
        } else {

            return null;
        }

    }

    /**
     * Sets the edition branch.
     *
     * @param editionBranch the edition branch to set
     */
    public void setEditionBranch(final String editionBranch) {

        if (getEdition() != null) {

            getEdition().setBranch(editionBranch);
        }

    }

    /**
     * Returns the edition name.
     *
     * @return the edition name
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "editionNameSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = @ObjectPath({
        @PropertyValue(propertyName = "project"), @PropertyValue(propertyName = "edition"), @PropertyValue(propertyName = "organization")
    }))
    public String getEditionName() {

        if (getEdition() != null) {

            return getEdition().getName();
        } else {

            return null;
        }

    }

    /**
     * Sets the edition name.
     *
     * @param editionName the edition name to set
     */
    public void setEditionName(final String editionName) {

        if (getEdition() != null) {

            getEdition().setName(editionName);
        }

    }

    /**
     * Returns the edition short name.
     *
     * @return the edition short name
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = @ObjectPath({
        @PropertyValue(propertyName = "project"), @PropertyValue(propertyName = "edition"), @PropertyValue(propertyName = "organization")
    }))
    public String getEditionShortName() {

        if (getEdition() != null) {

            return getEdition().getShortName();
        } else {

            return null;
        }

    }

    /**
     * Sets the edition short name.
     *
     * @param editionShortName the new edition short name
     */
    public void setEditionShortName(final String editionShortName) {

        if (getEdition() != null) {

            getEdition().setShortName(editionShortName);
        }

    }

    /**
     * Checks if is local set.
     *
     * @return the localSet
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public boolean isLocalSet() {

        return localSet;
    }

    /**
     * Sets the local set.
     *
     * @param localSet the localSet to set
     */
    public void setLocalSet(final boolean localSet) {

        this.localSet = localSet;
    }

    /**
     * Checks if is combo refset.
     *
     * @return the combo refset flag
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public boolean isComboRefset() {

        return comboRefset;
    }

    /**
     * Sets the combo refset flag.
     *
     * @param comboRefset the combo refset flag to set
     */
    public void setComboRefset(final boolean comboRefset) {

        this.comboRefset = comboRefset;
    }

    /**
     * Gets the module id.
     *
     * @return the moduleId
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getModuleId() {

        return moduleId;
    }

    /**
     * Sets the module id.
     *
     * @param moduleId the moduleId to set
     */
    public void setModuleId(final String moduleId) {

        this.moduleId = moduleId;
    }

    /**
     * Gets the edit branch ID.
     *
     * @return the edit branch ID
     */
    public String getEditBranchId() {

        return editBranchId;
    }

    /**
     * Sets the edit branch ID.
     *
     * @param editBranchId the edit branch ID to set
     */
    public void setEditBranchId(final String editBranchId) {

        this.editBranchId = editBranchId;
    }

    /**
     * Gets the refset branch ID.
     *
     * @return the refset branch ID
     */
    public String getRefsetBranchId() {

        return refsetBranchId;
    }

    /**
     * Sets the refset branch ID.
     *
     * @param refsetBranchId the refset branch ID to set
     */
    public void setRefsetBranchId(final String refsetBranchId) {

        this.refsetBranchId = refsetBranchId;
    }

    /**
     * Gets the name for a published version of a localset.
     *
     * @return the localset version name
     */
    public String getLocalsetVersionName() {

        return localsetVersionName;
    }

    /**
     * Sets the name for a published version of a localset.
     *
     * @param localsetVersionName the localset version name to set
     */
    public void setLocalsetVersionName(final String localsetVersionName) {

        this.localsetVersionName = localsetVersionName;
    }

    /**
     * Gets the external url.
     *
     * @return the externalUrl
     */
    public String getExternalUrl() {

        return externalUrl;
    }

    /**
     * Sets the external url.
     *
     * @param externalUrl the externalUrl to set
     */
    public void setExternalUrl(final String externalUrl) {

        this.externalUrl = externalUrl;
    }

    /**
     * Gets the definition clauses.
     *
     * @return the definitionClauses
     */
    @IndexedEmbedded(targetType = DefinitionClause.class)
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    public List<DefinitionClause> getDefinitionClauses() {

        if (definitionClauses == null) {

            definitionClauses = new ArrayList<>();
        }

        return definitionClauses;
    }

    /**
     * Sets the definition clauses.
     *
     * @param definitionClauses the definitionClauses to set
     */
    public void setDefinitionClauses(final List<DefinitionClause> definitionClauses) {

        if (definitionClauses != null) {

            Collections.sort(definitionClauses, (o1, o2) -> (o1.getCreated().compareTo(o2.getCreated())));
        }

        this.definitionClauses = definitionClauses;
    }

    /**
     * Checks if the refset is downloadable.
     *
     * @return the downloadable
     */
    @JsonGetter()
    public boolean isDownloadable() {

        return downloadable;
    }

    /**
     * Sets the downloadable flag.
     *
     * @param downloadable the downloadable flag to set
     */
    public void setDownloadable(final boolean downloadable) {

        this.downloadable = downloadable;
    }

    /**
     * Gets the flag that shows if the refset is locked due to edits.
     *
     * @return the locked flag
     */
    @JsonGetter()
    public boolean isLocked() {

        return locked;
    }

    /**
     * Sets the flag that shows if the refset is locked due to edits.
     *
     * @param locked the locked flag
     */
    public void setLocked(final boolean locked) {

        this.locked = locked;
    }

    /**
     * Gets the flag to indicate that this refset is included in search results in part because it matched member or alternate refset descriptions.
     *
     * @return the member search match flag
     */
    @JsonGetter()
    public boolean isMemberSearchMatch() {

        return memberSearchMatch;
    }

    /**
     * Sets the flag to indicate that this refset is included in search results in part because it matched member or alternate refset descriptions.
     *
     * @param memberSearchMatch the member search match flag
     */
    public void setMemberSearchMatch(final boolean memberSearchMatch) {

        this.memberSearchMatch = memberSearchMatch;
    }

    /**
     * Returns the date of the terminology version this refset is based on.
     *
     * @return the terminology version date
     */
    @JsonGetter()
    public String getTerminologyVersionDate() {

        return terminologyVersionDate;
    }

    /**
     * Sets the date of the terminology version this refset is based on.
     *
     * @param terminologyVersionDate the terminology version date to set
     */
    public void setTerminologyVersionDate(final String terminologyVersionDate) {

        this.terminologyVersionDate = terminologyVersionDate;
    }

    /**
     * Gets the flag for if the refset was published in the last edition version.
     *
     * @return the based on latest version flag
     */
    @JsonGetter()
    public boolean isBasedOnLatestVersion() {

        return basedOnLatestVersion;
    }

    /**
     * Sets the flag for if the refset was published in the last edition version.
     *
     * @param basedOnLatestVersion the based on latest version flag
     */
    public void setBasedOnLatestVersion(final boolean basedOnLatestVersion) {

        this.basedOnLatestVersion = basedOnLatestVersion;
    }

    /**
     * Gets the flag to display a warning when first editing if the refset was last published more than one edition version prior.
     *
     * @return the upgrade warning flag
     */
    @JsonGetter()
    public boolean getUpgradeWarning() {

        return upgradeWarning;
    }

    /**
     * Sets the flag to display a warning when first editing if the refset was last published more than one edition version prior.
     *
     * @param upgradeWarning the upgrade warning flag
     */
    public void setUpgradeWarning(final boolean upgradeWarning) {

        this.upgradeWarning = upgradeWarning;
    }

    /**
     * Gets the list of actions available for the user to perform on this refset.
     *
     * @return the available actions
     */
    @JsonGetter()
    public Set<WorkflowAction> getAvailableActions() {

        if (availableActions == null) {

            availableActions = new HashSet<>();
        }

        return availableActions;
    }

    /**
     * Sets the list of actions available for the user to perform on this refset.
     *
     * @param availableActions the available actions to set
     */
    public void setAvailableActions(final Set<WorkflowAction> availableActions) {

        this.availableActions = availableActions;
    }

    /**
     * Checks if is feedback visible.
     *
     * @return the feedbackVisible flag
     */
    @JsonGetter()
    public boolean isFeedbackVisible() {

        return feedbackVisible;
    }

    /**
     * Sets the feedback visible flag.
     *
     * @param feedbackVisible the feedbackVisible flag to set
     */
    public void setFeedbackVisible(final boolean feedbackVisible) {

        this.feedbackVisible = feedbackVisible;
    }

    /**
     * Returns the ID of the parent of the underlying refset concept.
     *
     * @return the parent concept ID
     */
    @JsonGetter()
    public String getParentConceptId() {

        return parentConceptId;
    }

    /**
     * Sets the ID of the parent of the underlying refset concept.
     *
     * @param parentConceptId the parent concept ID to set
     */
    public void setParentConceptId(final String parentConceptId) {

        this.parentConceptId = parentConceptId;
    }

    /**
     * Returns the complete branch path of the refset.
     *
     * @return the branch path
     */
    @JsonGetter()
    public String getBranchPath() {

        return branchPath;
    }

    /**
     * Sets the complete branch path of the refset.
     *
     * @param branchPath the branch path to set
     */
    public void setBranchPath(final String branchPath) {

        this.branchPath = branchPath;
    }

    /**
     * Returns the user assigned to work on the refset.
     *
     * @return the assigned user
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "assignedUserSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getAssignedUser() {

        return assignedUser;
    }

    /**
     * Sets the user assigned to work on the refset.
     *
     * @param assignedUser the assigned user to set
     */
    public void setAssignedUser(final String assignedUser) {

        this.assignedUser = assignedUser;
    }

    /**
     * Returns the member count.
     *
     * @return the member count
     */
    public int getMemberCount() {

        return memberCount;
    }

    /**
     * Sets the member count.
     *
     * @param memberCount the member count to set
     */
    public void setMemberCount(final int memberCount) {

        this.memberCount = memberCount;
    }

    /**
     * Gets the project.
     *
     * @return the project
     */
    public Project getProject() {

        return project;
    }

    /**
     * Sets the project.
     *
     * @param project the project to set
     */
    public void setProject(final Project project) {

        this.project = project;
    }

    /**
     * Checks if is upgrade.
     *
     * @return the isUpgrade
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public boolean isInUpgrade() {

        return inUpgrade;
    }

    /**
     * Sets the upgrade.
     *
     * @param isUpgrade the isUpgrade to set
     */
    public void setInUpgrade(final boolean isUpgrade) {

        this.inUpgrade = isUpgrade;
    }

    /**
     * Checks if is inactivate.
     *
     * @return the isInactivate
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public boolean isInInactivate() {

        return inInactivate;
    }

    /**
     * Sets the inactivate.
     *
     * @param isInactivate the isInactivate to set
     */
    public void setInInactivate(final boolean isInactivate) {

        this.inInactivate = isInactivate;
    }

    /**
     * Returns the project ID.
     *
     * @return the project ID
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    @IndexingDependency(derivedFrom = @ObjectPath({
        @PropertyValue(propertyName = "project")
    }))
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    public String getProjectId() {

        return project == null ? null : project.getId();
    }

    /**
     * Sets the project ID.
     *
     * @param projectId the project ID to set
     */
    public void setProjectId(final String projectId) {

        if (project != null) {

            this.project.setId(projectId);
        } else {

            this.project = new Project();
            this.project.setId(projectId);
        }

    }

    /**
     * Indicates whether or not latest published version is the case.
     *
     * @return the latestPublishedVersion
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public boolean isLatestPublishedVersion() {

        return latestPublishedVersion;
    }

    /**
     * Sets the latest published version.
     *
     * @param latestPublishedVersion the latestPublishedVersion to set
     */
    public void setLatestPublishedVersion(final boolean latestPublishedVersion) {

        this.latestPublishedVersion = latestPublishedVersion;
    }

    /**
     * Returns the checks for version in development.
     *
     * @return Does this refset have a version in development (this is only true if this is the latest published version)
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public boolean getHasVersionInDevelopment() {

        return hasVersionInDevelopment;
    }

    /**
     * Sets the checks for version in development.
     *
     * @param hasVersionInDevelopment set if this refset has a version in development (this is only true if this is the latest published version)
     */
    public void setHasVersionInDevelopment(final boolean hasVersionInDevelopment) {

        this.hasVersionInDevelopment = hasVersionInDevelopment;
    }

    /**
     * Returns the open discussion count.
     *
     * @return the discussion count
     */
    @JsonGetter()
    public int getOpenDiscussionCount() {

        return openDiscussionCount;
    }

    /**
     * Sets the open discussion count.
     *
     * @param openDiscussionCount the open discussion count
     */
    public void setOpenDiscussionCount(final int openDiscussionCount) {

        this.openDiscussionCount = openDiscussionCount;
    }

    /**
     * Returns the resolved discussion count.
     *
     * @return the discussion count
     */
    @JsonGetter()
    public int getResolvedDiscussionCount() {

        return resolvedDiscussionCount;
    }

    /**
     * Sets the discussion count.
     *
     * @param resolvedDiscussionCount the resolved discussion count
     */
    public void setResolvedDiscussionCount(final int resolvedDiscussionCount) {

        this.resolvedDiscussionCount = resolvedDiscussionCount;
    }

    /**
     * Gets the upgrade versions candidate.
     *
     * @return the upgrade versions candidate
     */
    @JsonGetter()
    public List<String> getUpgradeVersionsCandidate() {

        if (upgradeVersionsCandidate == null) {
            upgradeVersionsCandidate = new ArrayList<>();
        }
        return upgradeVersionsCandidate;
    }

    /**
     * Sets the upgrade versions candidate.
     *
     * @param upgradeVersionsCandidate the new upgrade versions candidate
     */
    public void setUpgradeVersionsCandidate(final List<String> upgradeVersionsCandidate) {

        this.upgradeVersionsCandidate = upgradeVersionsCandidate;
    }
    
    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((refsetId == null) ? 0 : refsetId.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((versionDate == null) ? 0 : versionDate.hashCode());
        result = prime * result + ((baseContentVersion == null) ? 0 : baseContentVersion.hashCode());
        result = prime * result + ((internationalContentVersion == null) ? 0 : internationalContentVersion.hashCode());
        result = prime * result + ((versionStatus == null) ? 0 : versionStatus.hashCode());
        result = prime * result + ((workflowStatus == null) ? 0 : workflowStatus.hashCode());
        result = prime * result + ((narrative == null) ? 0 : narrative.hashCode());
        result = prime * result + ((versionNotes == null) ? 0 : versionNotes.hashCode());
        result = prime * result + ((moduleId == null) ? 0 : moduleId.hashCode());
        result = prime * result + ((editBranchId == null) ? 0 : editBranchId.hashCode());
        result = prime * result + ((refsetBranchId == null) ? 0 : refsetBranchId.hashCode());
        result = prime * result + ((localsetVersionName == null) ? 0 : localsetVersionName.hashCode());
        result = prime * result + ((externalUrl == null) ? 0 : externalUrl.hashCode());
        result = prime * result + ((project == null) ? 0 : project.hashCode());
        result = prime * result + ((versionList == null) ? 0 : versionList.hashCode());
        result = prime * result + ((assignedUser == null) ? 0 : assignedUser.hashCode());
        result = prime * result + ((parentConceptId == null) ? 0 : parentConceptId.hashCode());
        result = prime * result + ((branchPath == null) ? 0 : branchPath.hashCode());
        result = prime * result + ((descriptions == null) ? 0 : descriptions.hashCode());
        result = prime * result + ((roles == null) ? 0 : roles.hashCode());
        result = prime * result + ((terminologyVersionDate == null) ? 0 : terminologyVersionDate.hashCode());
        result = prime * result + memberCount;
        result = prime * result + openDiscussionCount;
        result = prime * result + resolvedDiscussionCount;
        result = prime * result + (privateRefset ? 1 : 0);
        result = prime * result + (downloadable ? 1 : 0);
        result = prime * result + (feedbackVisible ? 1 : 0);
        result = prime * result + (latestPublishedVersion ? 1 : 0);
        result = prime * result + (hasVersionInDevelopment ? 1 : 0);
        result = prime * result + (locked ? 1 : 0);
        result = prime * result + (memberSearchMatch ? 1 : 0);
        result = prime * result + (basedOnLatestVersion ? 1 : 0);
        result = prime * result + (upgradeWarning ? 1 : 0);
        result = prime * result + (localSet ? 1 : 0);
        result = prime * result + (comboRefset ? 1 : 0);
        result = prime * result + (inUpgrade ? 1 : 0);
        result = prime * result + (inInactivate ? 1 : 0);
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
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Refset other = (Refset) obj;
        if (refsetId == null) {
            if (other.refsetId != null) {
                return false;
            }
        } else if (!refsetId.equals(other.refsetId)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (type == null) {
            if (other.type != null) {
                return false;
            }
        } else if (!type.equals(other.type)) {
            return false;
        }
        if (versionStatus == null) {
            if (other.versionStatus != null) {
                return false;
            }
        } else if (!versionStatus.equals(other.versionStatus)) {
            return false;
        }
        if (workflowStatus == null) {
            if (other.workflowStatus != null) {
                return false;
            }
        } else if (!workflowStatus.equals(other.workflowStatus)) {
            return false;
        }
        if (assignedUser == null) {
            if (other.assignedUser != null) {
                return false;
            }
        } else if (!assignedUser.equals(other.assignedUser)) {
            return false;
        }
        if (parentConceptId == null) {
            if (other.parentConceptId != null) {
                return false;
            }
        } else if (!parentConceptId.equals(other.parentConceptId)) {
            return false;
        }
        if (branchPath == null) {
            if (other.branchPath != null) {
                return false;
            }
        } else if (!branchPath.equals(other.branchPath)) {
            return false;
        }
        if (descriptions == null) {
            if (other.descriptions != null) {
                return false;
            }
        } else if (!descriptions.equals(other.descriptions)) {
            return false;
        }
        if (versionDate == null) {
            if (other.versionDate != null) {
                return false;
            }
        } else if (!versionDate.equals(other.versionDate)) {
            return false;
        }
        if (baseContentVersion == null) {
            if (other.baseContentVersion != null) {
                return false;
            }
        } else if (!baseContentVersion.equals(other.baseContentVersion)) {
            return false;
        }
        if (internationalContentVersion == null) {
            if (other.internationalContentVersion != null) {
                return false;
            }
        } else if (!internationalContentVersion.equals(other.internationalContentVersion)) {
            return false;
        }
        if (narrative == null) {
            if (other.narrative != null) {
                return false;
            }
        } else if (!narrative.equals(other.narrative)) {
            return false;
        }
        if (versionNotes == null) {
            if (other.versionNotes != null) {
                return false;
            }
        } else if (!versionNotes.equals(other.versionNotes)) {
            return false;
        }
        if (roles == null) {
            if (other.roles != null) {
                return false;
            }
        } else if (!roles.equals(other.roles)) {
            return false;
        }
        if (moduleId == null) {
            if (other.moduleId != null) {
                return false;
            }
        } else if (!moduleId.equals(other.moduleId)) {
            return false;
        }
        if (editBranchId == null) {
            if (other.editBranchId != null) {
                return false;
            }
        } else if (!editBranchId.equals(other.editBranchId)) {
            return false;
        }
        if (refsetBranchId == null) {
            if (other.refsetBranchId != null) {
                return false;
            }
        } else if (!refsetBranchId.equals(other.refsetBranchId)) {
            return false;
        }
        if (localsetVersionName == null) {
            if (other.localsetVersionName != null) {
                return false;
            }
        } else if (!localsetVersionName.equals(other.localsetVersionName)) {
            return false;
        }
        if (externalUrl == null) {
            if (other.externalUrl != null) {
                return false;
            }
        } else if (!externalUrl.equals(other.externalUrl)) {
            return false;
        }
        if (terminologyVersionDate == null) {
            if (other.terminologyVersionDate != null) {
                return false;
            }
        } else if (!terminologyVersionDate.equals(other.terminologyVersionDate)) {
            return false;
        }
        if (memberCount != other.memberCount) {
            return false;
        }
        if (privateRefset != other.privateRefset) {
            return false;
        }
        if (localSet != other.localSet) {
            return false;
        }
        if (comboRefset != other.comboRefset) {
            return false;
        }
        if (latestPublishedVersion != other.latestPublishedVersion) {
            return false;
        }
        if (hasVersionInDevelopment != other.hasVersionInDevelopment) {
            return false;
        }
        if (downloadable != other.downloadable) {
            return false;
        }
        if (feedbackVisible != other.feedbackVisible) {
            return false;
        }
        if (locked != other.locked) {
            return false;
        }
        if (memberSearchMatch != other.memberSearchMatch) {
            return false;
        }
        if (basedOnLatestVersion != other.basedOnLatestVersion) {
            return false;
        }
        if (upgradeWarning != other.upgradeWarning) {
            return false;
        }
        if (other.openDiscussionCount != openDiscussionCount) {
            return false;
        }
        if (other.resolvedDiscussionCount != resolvedDiscussionCount) {
            return false;
        }
        if (inUpgrade != other.inUpgrade) {
            return false;
        }
        if (inInactivate != other.inInactivate) {
            return false;
        }
        return true;
    }

    /**
     * Compare to.
     *
     * @param o the o
     * @return the int
     */
    @Override
    public int compareTo(final Refset o) {

        // Handle null
        return (name + refsetId).compareToIgnoreCase(o.getName() + o.getRefsetId());
    }

    /**
     * Lazy init.
     */
    @Override
    public void lazyInit() {
        // n/a

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
}
