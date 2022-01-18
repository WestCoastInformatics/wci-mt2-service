
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
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
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
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

// TODO: Auto-generated Javadoc
/**
 * Represents a refset.
 * 
 */
@Entity
@Table(name = "refsets")
@Indexed
public class Refset extends AbstractHasModified implements Comparable<Refset> {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(Refset.class);
    
    /** The refset ID. */
    @Column(nullable = false, length = 256)
    private String refsetId;

    /** The name. */
    @Column(nullable = false, length = 4000)
    private String name;

    /** The refset type. */
    @Column(nullable = false, length = 256)
    private String type;
    
    /** The version status. */
    @Column(nullable = false, length = 256)
    private String versionStatus;
    
    /** The workflow status. */
    @Column(nullable = true, length = 256)
    private String workflowStatus;

    /** The version date. */
    @Column(nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    private Date versionDate;

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
    private boolean localSet;

    /** The latest version flag. */
    @Column(nullable = true)
    private boolean latestVersion;

    /** The assigned user. */
    @Column(nullable = true)
    private String assignedUser;
    
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
    
    /** The list of actions available for the user to perform on this refset. */
    @Transient
    private List<String> availableActions;
    
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

    /** The module ID. */
    @Column(nullable = false, length = 256)
    private String moduleId;
    
    /** The branch and version for originating content from when editing. */
    @Column(nullable = true, length = 256)
    private String editOriginBranchPath;

    /** The external URL. */
    @Column(nullable = true, length = 4000)
    private String externalUrl;

    /** The project. */
    @ManyToOne(targetEntity = Project.class)
    @JoinColumn(nullable = true)
    @Fetch(FetchMode.JOIN)
    private Project project;

    /** The tags. */
    @ElementCollection
    private Set<String> tags = new HashSet<String>();

    /** The definition clauses. */
    // @Fetch(FetchMode.JOIN)
    @OneToMany(cascade = CascadeType.ALL, targetEntity = DefinitionClause.class,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("created ASC")
    private List<DefinitionClause> definitionClauses = new ArrayList<>();
    
    /** The value to use for the 'published' version status. */
    @Transient
    public static final String PUBLISHED = "PUBLISHED";
    
    /** The value to use for the 'beta' version status. */
    @Transient
    public static final String BETA = "BETA";
    
    /** The value to use for the 'in development' version status. */
    @Transient
    public static final String IN_DEVELOPMENT = "IN DEVELOPMENT";
    
    /** The value to use for the 'INTENSIONAL' refset type. */
    @Transient
    public static final String INTENSIONAL = "INTENSIONAL";
    
    /** The value to use for the 'EXTENSIONAL' refset type. */
    @Transient
    public static final String EXTENSIONAL = "EXTENSIONAL";
    
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
     * @param terminology the terminology
     * @param code the code
     * @param name the name
     */
    public Refset(final String terminology, final String code, final String name) {
        this.type = terminology;
        this.refsetId = code;
        this.name = name;
    }

    /**
     * Instantiates a {@link Refset} from the specified parameters.
     *
     * @param other the other
     */
    public Refset(final Refset other) {
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
        versionNotes = other.getVersionNotes();
        workflowStatus = other.getWorkflowStatus();
        project = other.getProject();
        externalUrl = other.getExternalUrl();
        moduleId = other.getModuleId();
        editOriginBranchPath = other.getEditOriginBranchPath();
        assignedUser = other.getAssignedUser();
        privateRefset = other.isPrivateRefset();
        downloadable = other.isDownloadable();
        locked = other.isLocked();
        availableActions = other.getAvailableActions();
        parentConceptId = other.getParentConceptId();
        branchPath = other.getBranchPath();
        latestVersion = other.isLatestVersion();
        feedbackVisible = other.isFeedbackVisible();
        versionList = other.getVersionList();
        roles = other.getRoles();
        descriptions = other.getDescriptions();
        definitionClauses = new ArrayList<DefinitionClause>(other.getDefinitionClauses());
        tags = new HashSet<String>(other.getTags());
    }

    /**
     * Returns the refset ID.
     *
     * @return the refset ID
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "refsetIdSort", searchable = Searchable.YES, projectable = Projectable.NO,
            sortable = Sortable.YES)
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
    @GenericField(name = "nameSort", searchable = Searchable.YES, projectable = Projectable.NO,
            sortable = Sortable.YES)
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
    public String getType() {
        return type.toUpperCase();
    }

    /**
     * Sets the type.
     *
     * @param type the type
     */
    public void setType(final String type) {
        this.type = type.toUpperCase();
    }

    /**
     * Returns the version status.
     *
     * @return the version status
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "versionStatusSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getVersionStatus() {
        return versionStatus;
    }

    /**
     * Sets the version.
     *
     * @param versionStatus the version status
     */
    public void setVersionStatus(final String versionStatus) {
        this.versionStatus = versionStatus;
    }
    
    /**
     * Returns the workflow status.
     *
     * @return the workflow status
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "workflowStatusSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getWorkflowStatus() {
        return workflowStatus;
    }
    
    /**
     * Sets the workflow status.
     *
     * @param workflowStatus the workflow status
     */
    public void setWorkflowStatus(final String workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    /**
     * Gets the version date.
     *
     * @return the versionDate
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO,
            sortable = Sortable.YES)
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
    public void setDescriptions(List<Map<String, String>> descriptions) {
        this.descriptions = descriptions;
    }

    /**
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
     * @param versionList the versionList to set
     */
    public void setVersionList(List<Map<String, String>> versionList) {
        this.versionList = versionList;
    }
    
    /**
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
     * @param roles the roles
     */
    public void setRoles(List<String> roles) {
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
        
        if (project == null || project.getOrganization() == null  || project.getOrganization().getEdition() == null) {
            return null;
        } else {
            return project.getOrganization().getEdition();
        }
    }

    /**
     * Sets the edition.
     *
     * @param edition the edition to set
     */
    public void setEdition(final Edition edition) {
        
        if (project != null && project.getOrganization() != null) {
            project.getOrganization().setEdition(edition);
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
    @GenericField(searchable = Searchable.YES,
            projectable = Projectable.NO, sortable = Sortable.NO)
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = @ObjectPath({@PropertyValue(propertyName = "project"), @PropertyValue(propertyName = "organization"), @PropertyValue(propertyName = "edition")}))
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
    @GenericField(name = "editionNameSort", searchable = Searchable.YES,
            projectable = Projectable.NO, sortable = Sortable.YES)
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = @ObjectPath({@PropertyValue(propertyName = "project"), @PropertyValue(propertyName = "organization"), @PropertyValue(propertyName = "edition")}))
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
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = @ObjectPath({@PropertyValue(propertyName = "project"), @PropertyValue(propertyName = "organization"), @PropertyValue(propertyName = "edition")}))
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
     * Gets the branch and version for originating content from when editing.
     *
     * @return the editOriginBranchPath
     */
    public String getEditOriginBranchPath() {
        return editOriginBranchPath;
    }

    /**
     * Sets the branch and version for originating content from when editing.
     *
     * @param editOriginBranchPath the editOriginBranchPath to set
     */
    public void setEditOriginBranchPath(final String editOriginBranchPath) {
        this.editOriginBranchPath = editOriginBranchPath;
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
        
        Collections.sort(definitionClauses, (o1, o2) -> (o1.getCreated().compareTo(o2.getCreated())));
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
     * Gets the list of actions available for the user to perform on this refset.
     * 
     * @return the available actions
     */
    @JsonGetter()
    public List<String> getAvailableActions() {
        
        if (availableActions == null) {
            availableActions = new ArrayList<>();
        }
        
        return availableActions;
    }
    
    /**
     * Sets the list of actions available for the user to perform on this refset.
     * 
     * @param availableActions the available actions to set
     */
    public void setAvailableActions(List<String> availableActions) {
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
    @GenericField(name = "assignedUserSort", searchable = Searchable.YES, projectable = Projectable.NO,
            sortable = Sortable.YES)
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
     * Returns the project ID.
     *
     * @return the project ID
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    @IndexingDependency(derivedFrom = @ObjectPath({@PropertyValue(propertyName = "project")}))
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
     * Returns the organization name.
     *
     * @return the organization name
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "organizationNameSort", searchable = Searchable.YES,
            projectable = Projectable.NO, sortable = Sortable.YES)
    @IndexingDependency(derivedFrom = @ObjectPath({
            @PropertyValue(propertyName = "project"), @PropertyValue(propertyName = "organization")
    }))
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    public String getOrganizationName() {

        if (project == null || project.getOrganization() == null) {
            return null;
        } else {
            return project.getOrganization().getName();
        }
    }

    /**
     * Sets the organization name.
     *
     * @param organizationName the organization name to set
     */
    public void setOrganizationName(final String organizationName) {

        if (project != null && project.getOrganization() != null) {
            this.project.getOrganization().setName(organizationName);
        }

    }
    
    /**
     * @return the latestVersion
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public boolean isLatestVersion() {
        return latestVersion;
    }

    /**
     * @param latestVersion the latestVersion to set
     */
    public void setLatestVersion(boolean latestVersion) {
        this.latestVersion = latestVersion;
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
        result = prime * result + ((versionStatus == null) ? 0 : versionStatus.hashCode());
        result = prime * result + ((workflowStatus == null) ? 0 : workflowStatus.hashCode());
        result = prime * result + ((narrative == null) ? 0 : narrative.hashCode());
        result = prime * result + ((versionNotes == null) ? 0 : versionNotes.hashCode());
        result = prime * result + ((moduleId == null) ? 0 : moduleId.hashCode());
        result = prime * result + ((editOriginBranchPath == null) ? 0 : editOriginBranchPath.hashCode());
        result = prime * result + ((externalUrl == null) ? 0 : externalUrl.hashCode());
        result = prime * result + ((project == null) ? 0 : project.hashCode());
        result = prime * result + ((versionList == null) ? 0 : versionList.hashCode());
        result = prime * result + ((assignedUser == null) ? 0 : assignedUser.hashCode());
        result = prime * result + ((parentConceptId == null) ? 0 : parentConceptId.hashCode());
        result = prime * result + ((branchPath == null) ? 0 : branchPath.hashCode());
        result = prime * result + ((descriptions == null) ? 0 : descriptions.hashCode());
        result = prime * result + ((roles == null) ? 0 : roles.hashCode());
        result = prime * result + (privateRefset ? 1 : 0);
        result = prime * result + (downloadable ? 1 : 0);
        result = prime * result + (feedbackVisible ? 1 : 0);
        result = prime * result + (latestVersion ? 1 : 0);
        result = prime * result + (locked ? 1 : 0);
        result = prime * result + (localSet ? 1 : 0);
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
        
        if (workflowStatus == null) {
            if (other.workflowStatus != null) {
                return false;
            }
        } else if (!workflowStatus.equals(other.workflowStatus)) {
            return false;
        }

        if (versionDate == null) {
            if (other.versionDate != null) {
                return false;
            }
        } else if (!versionDate.equals(other.versionDate)) {
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
        
        if (editOriginBranchPath == null) {
            if (other.editOriginBranchPath != null) {
                return false;
            }
        } else if (!editOriginBranchPath.equals(other.editOriginBranchPath)) {
            return false;
        }

        if (externalUrl == null) {
            if (other.externalUrl != null) {
                return false;
            }
        } else if (!externalUrl.equals(other.externalUrl)) {
            return false;
        }

        if (privateRefset != other.privateRefset) {
            return false;
        }

        if (localSet != other.localSet) {
            return false;
        }

        if (latestVersion != other.latestVersion) {
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
        // TODO Auto-generated method stub

    }
}
