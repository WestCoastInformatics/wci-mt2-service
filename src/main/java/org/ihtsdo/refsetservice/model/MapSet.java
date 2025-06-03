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
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Type;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Class MapSet.
 */
@Entity
@Schema(description = "Represents a map set")
@Table(name = "map_sets", uniqueConstraints = {
    @UniqueConstraint(columnNames = {
        "name"
    })
})
@Indexed
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapSet extends AbstractHasModified {

    /** The ref set code. */
    @Column(nullable = false, length = 255)
    private String refSetCode;

    /** The ref set name. */
    @Column(nullable = false, length = 255)
    private String refSetName;

    /** The module id. */
    @Column(nullable = true)
    private String moduleId;

    /** The name. */
    @Column(nullable = false)
    private String name;

    /** The version status. */
    @Convert(converter = VersionStatusConverter.class)
    @JsonSerialize(using = VersionStatusJsonSerializer.class)
    @Column(nullable = false, length = 256)
    private VersionStatus versionStatus;

    /** The workflow status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 256)
    private WorkflowStatus workflowStatus;

    /** The branch path. */
    @Column(nullable = false)
    private String branchPath;

    /** The version. */
    @Column(nullable = false)
    private String version;

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

    /** The branch path. */
    @Column(nullable = false)
    private String fromTerminology;

    /** The from version. */
    @Column(nullable = false)
    private String fromVersion;

    /** The from branch path. */
    @Column(nullable = false)
    private String fromBranchPath;

    /** The to terminology. */
    @Column(nullable = false)
    private String toTerminology;

    /** The to version. */
    @Column(nullable = false)
    private String toVersion;

    /** The to branch path. */
    @Column(nullable = false)
    private String toBranchPath;

    /** The latest published version flag. */
    @Column(nullable = true)
    private boolean latestPublishedVersion;

    /** The has version in development. */
    @Column(nullable = true)
    private boolean hasVersionInDevelopment;

    /** The assigned user. */
    @Column(nullable = true)
    private String assignedUser;

    /** The refset scope count. */
    @Column(nullable = false)
    private int scopeCount = -1;

    /** The refset scope count. */
    @Column(nullable = false)
    private int mappedCount = -1;

    /** The edit branch ID. */
    @Column(nullable = true, length = 256)
    private String editBranchId;

    /** The map branch ID. */
    @Column(nullable = true, length = 256)
    private String mapBranchId;

    /** The project. */
    @ManyToOne(targetEntity = Project.class)
    @JoinColumn(nullable = true)
    @Fetch(FetchMode.JOIN)
    private Project project;


    /** The flag for if a user can see the feedback for this refset. */
    @Transient
    private boolean feedbackVisible;

    /** The list of user roles for this refset. */
    @Transient
    private List<String> roles;

    /** The flag for if the refset is locked due to an edit. */
    @Transient
    private boolean locked = false;

    // TODO: Review to see if we still need
    /** The date of the terminology version this refset is based on. */
    @Transient
    private String terminologyVersionDate;

    /** The flag for if the refset was published in the last edition version. */
    @Transient
    private boolean basedOnLatestVersion = false;

    /**
     * The flag to display a warning when first editing if the refset was last published more than one edition version prior.
     */
    @Transient
    private boolean upgradeWarning = false;

    /** The list of actions available for the user to perform on this refset. */
    @Transient
    private List<String> availableActions;

    /** The ID of the parent of the underlying refset concept. */
    @Transient
    private String parentConceptId;

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

//    /** The value to use for the 'INCLUSION' intensional definition exception type. */
//    @Transient
//    public static final String INCLUSION = "INCLUSION";
//
//    /** The value to use for the 'EXCLUSION' intensional definition exception type. */
//    @Transient
//    public static final String EXCLUSION = "EXCLUSION";

    /**
     * Default constructor.
     */
    public MapSet() {

        // n/a
    }

    /**
     * Instantiates a {@link MapSet} from the specified parameters.
     *
     * @param mapSet the map set
     */
    public MapSet(final MapSet mapSet) {

        super();
        super.setId(mapSet.getId());
        this.refSetCode = mapSet.getRefSetCode();
        this.refSetName = mapSet.getRefSetName();
        this.moduleId = mapSet.getModuleId();
        this.name = mapSet.getName();
        this.branchPath = mapSet.getBranchPath();
        this.fromTerminology = mapSet.getFromTerminology();
        this.fromVersion = mapSet.getFromVersion();
        this.fromBranchPath = mapSet.getFromBranchPath();
        this.toTerminology = mapSet.getToTerminology();
        this.toVersion = mapSet.getToVersion();
        this.toBranchPath = mapSet.getToBranchPath();
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
     * Gets the name.
     *
     * @return the name
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
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
     * Returns the version.
     *
     * @return the version
     */
    public String getVersion() {

        return version;
    }

    /**
     * Sets the version.
     *
     * @param version the version
     */
    public void setVersion(final String version) {

        this.version = version;
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
     * Gets the branch path.
     *
     * @return the branch path
     */
    public String getBranchPath() {

        return branchPath;
    }

    /**
     * Sets the branch path.
     *
     * @param branchPath the new branch path
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
     * Gets the from terminology.
     *
     * @return the from terminology
     */
    public String getFromTerminology() {

        return fromTerminology;
    }

    /**
     * Sets the from terminology.
     *
     * @param fromTerminology the new from terminology
     */
    public void setFromTerminology(final String fromTerminology) {

        this.fromTerminology = fromTerminology;
    }

    /**
     * Gets the from version.
     *
     * @return the from version
     */
    public String getFromVersion() {

        return fromVersion;
    }

    /**
     * Sets the from version.
     *
     * @param fromVersion the new from version
     */
    public void setFromVersion(final String fromVersion) {

        this.fromVersion = fromVersion;
    }

    /**
     * Gets the from branch path.
     *
     * @return the from branch path
     */
    public String getFromBranchPath() {

        return fromBranchPath;
    }

    /**
     * Sets the from branch path.
     *
     * @param fromBranchPath the new from branch path
     */
    public void setFromBranchPath(final String fromBranchPath) {

        this.fromBranchPath = fromBranchPath;
    }

    /**
     * Gets the to terminology.
     *
     * @return the to terminology
     */
    public String getToTerminology() {

        return toTerminology;
    }

    /**
     * Sets the to terminology.
     *
     * @param toTerminology the new to terminology
     */
    public void setToTerminology(final String toTerminology) {

        this.toTerminology = toTerminology;
    }

    /**
     * Gets the to version.
     *
     * @return the to version
     */
    public String getToVersion() {

        return toVersion;
    }

    /**
     * Sets the to version.
     *
     * @param toVersion the new to version
     */
    public void setToVersion(final String toVersion) {

        this.toVersion = toVersion;
    }

    /**
     * Gets the to branch path.
     *
     * @return the to branch path
     */
    public String getToBranchPath() {

        return toBranchPath;
    }

    /**
     * Sets the to branch path.
     *
     * @param toBranchPath the new to branch path
     */
    public void setToBranchPath(final String toBranchPath) {

        this.toBranchPath = toBranchPath;
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
     * Gets the flag for if the mapset was published in the last edition version.
     *
     * @return the based on latest version flag
     */
    @JsonGetter()
    public boolean isBasedOnLatestVersion() {

        return basedOnLatestVersion;
    }

    /**
     * Sets the flag for if the mapset was published in the last edition version.
     *
     * @param basedOnLatestVersion the based on latest version flag
     */
    public void setBasedOnLatestVersion(final boolean basedOnLatestVersion) {

        this.basedOnLatestVersion = basedOnLatestVersion;
    }

    /**
     * Gets the flag to display a warning when first editing if the mapset was last published more than one edition version prior.
     *
     * @return the upgrade warning flag
     */
    @JsonGetter()
    public boolean getUpgradeWarning() {

        return upgradeWarning;
    }

    /**
     * Sets the flag to display a warning when first editing if the mapset was last published more than one edition version prior.
     *
     * @param upgradeWarning the upgrade warning flag
     */
    public void setUpgradeWarning(final boolean upgradeWarning) {

        this.upgradeWarning = upgradeWarning;
    }

    /**
     * Gets the edition branch.
     *
     * @return the edition branch
     */
    // for compile, need to verify if still needed for MapSetWorkflow
    public String getEditionBranch() {
        return "";
    }

    /**
     * Gets the refset branch id.
     *
     * @return the refset branch id
     */
    // for compile, need to verify if still needed for MapSetWorkflow
    public String getRefsetBranchId() {
        return "";
    }

    /**
     * Checks if is local set.
     *
     * @return true, if is local set
     */
    // for compile, need to verify if still needed for MapSetWorkflow
    public boolean isLocalSet() {
        return true;
    }

    /**
     * Gets the edition short name.
     *
     * @return the edition short name
     */
    public String getEditionShortName() {
        return "";
    }
    
    public boolean isInUpgrade() {
        return false;
    }
    
    public void setInUpgrade(boolean inUpgrade) {
        // n/a
    }
    
    public boolean isInInactivate() {
        return false;
    }
    
    public void setInInactivate(boolean inInactivate) {
        // n/a
    }
    

    /**
     * Lazy init.
     */
    @Override
    public void lazyInit() {

        // n/a
    }

}
