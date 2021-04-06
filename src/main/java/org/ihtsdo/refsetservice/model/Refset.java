
package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.AnalyzerDef;
import org.hibernate.search.annotations.DateBridge;
import org.hibernate.search.annotations.EncodingType;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.FieldBridge;
import org.hibernate.search.annotations.Fields;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.Resolution;
import org.hibernate.search.annotations.SortableField;
import org.hibernate.search.annotations.Store;
import org.hibernate.search.annotations.TokenizerDef;
import org.hibernate.search.bridge.builtin.BooleanBridge;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

// TODO: Auto-generated Javadoc
/**
 * Represents a refset.
 * 
 */
@Entity
// @JsonInclude(Include.NON_EMPTY)
// @JsonIgnoreProperties(ignoreUnknown = true)
@AnalyzerDef(name = "whitespace",
        tokenizer = @TokenizerDef(factory = WhitespaceTokenizerFactory.class))
@Table(name = "refsets")
@Indexed
public class Refset extends AbstractHasModified implements Comparable<Refset> {

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

    /** The version date. */
    @Column(nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    private Date versionDate;

    /** The version narrative. */
    @Column(nullable = true, length = 10000)
    private String narrative;

    /** The version status. */
    @Column(nullable = true, length = 10000)
    private String versionNotes;

    /** The private flag. */
    @Column(nullable = false)
    private boolean privateRefset;

    /** The local set flag. */
    @Column(nullable = false)
    private boolean localSet;

    /** The flag for if a user can download this refset. */
    @Transient
    private boolean downloadable;

    /** The flag for if a user can see the feedback for this refset. */
    @Transient
    private boolean feedbackVisible;

    /** The module ID. */
    @Column(nullable = false, length = 256)
    private String moduleId;

    /** The external URL. */
    @Column(nullable = true, length = 4000)
    private String externalUrl;

    /** The edition. */
    @ManyToOne(targetEntity = Edition.class)
    @JoinColumn(nullable = true)
    @Fetch(FetchMode.JOIN)
    private Edition edition;

    /** The project. */
    @ManyToOne(targetEntity = Project.class)
    @JoinColumn(nullable = true)
    @Fetch(FetchMode.JOIN)
    private Project project;

    /** The tags. */
    @ElementCollection
    private Set<String> tags = new HashSet<String>();

    /** The definition clauses. */
    @IndexedEmbedded(targetElement = DefinitionClause.class)
    // @Fetch(FetchMode.JOIN)
    @OneToMany(cascade = CascadeType.ALL, targetEntity = DefinitionClause.class,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DefinitionClause> definitionClauses = new ArrayList<>();

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
        tags = other.getTags();
        versionDate = other.getVersionDate();
        versionNotes = other.getVersionNotes();
        versionStatus = other.getVersionStatus();
        edition = other.getEdition();
        project = other.getProject();
        definitionClauses = other.getDefinitionClauses();
        externalUrl = other.getExternalUrl();
        moduleId = other.getModuleId();
    }

    /**
     * Returns the refset ID.
     *
     * @return the refset ID
     */
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
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
    @Fields({
            @Field(index = Index.YES, analyze = Analyze.YES, store = Store.NO),
            @Field(name = "nameSort", index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    })
    @SortableField(forField = "nameSort")
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
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
    public String getType() {
        return type;
    }

    /**
     * Sets the type.
     *
     * @param type the type
     */
    public void setType(final String type) {
        this.type = type;
    }

    /**
     * Returns the version status.
     *
     * @return the version status
     */
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
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
     * Gets the version date.
     *
     * @return the versionDate
     */
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
    @DateBridge(resolution = Resolution.SECOND, encoding = EncodingType.STRING)
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
    @FieldBridge(impl = BooleanBridge.class)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
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
     * Gets the tags.
     *
     * @return the tags
     */
    @Field(analyze = Analyze.NO, store = Store.NO)
    @IndexedEmbedded
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
        return edition;
    }

    /**
     * Sets the edition.
     *
     * @param edition the edition to set
     */
    public void setEdition(final Edition edition) {
        this.edition = edition;
    }

    /**
     * Returns the edition name.
     *
     * @return the edition name
     */
    @Fields({
            @Field(index = Index.YES, analyze = Analyze.YES, store = Store.NO),
            @Field(name = "editionNameSort", index = Index.YES, analyze = Analyze.NO,
                    store = Store.NO)
    })
    @SortableField(forField = "editionNameSort")
    public String getEditionName() {
        return edition == null ? null : edition.getName();
    }

    /**
     * Sets the edition name.
     *
     * @param editionName the edition name to set
     */
    public void setEditionName(final String editionName) {
        this.edition.setName(editionName);
    }

    /**
     * Returns the edition short name.
     *
     * @return the edition short name
     */
    @Fields({
            @Field(index = Index.YES, analyze = Analyze.YES, store = Store.NO),
            @Field(name = "editionShortNameSort", index = Index.YES, analyze = Analyze.NO,
                    store = Store.NO)
    })
    @SortableField(forField = "editionShortNameSort")
    public String getEditionShortName() {
        return edition == null ? null : edition.getShortName();
    }

    /**
     * Sets the edition short name.
     *
     * @param editionShortNameSort the new edition short name
     */
    public void setEditionShortName(final String editionShortNameSort) {
        this.edition.setShortName(editionShortNameSort);
    }

    /**
     * Checks if is local set.
     *
     * @return the localSet
     */
    @FieldBridge(impl = BooleanBridge.class)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
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
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
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
        this.definitionClauses = definitionClauses;
    }

    /**
     * Checks if is downloadable.
     *
     * @return the downloadable
     */
    @FieldBridge(impl = BooleanBridge.class)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    public boolean isDownloadable() {
        return downloadable;
    }

    /**
     * Sets the downloadable.
     *
     * @param downloadable the downloadable to set
     */
    public void setDownloadable(final boolean downloadable) {
        this.downloadable = downloadable;
    }

    /**
     * Checks if is feedback visible.
     *
     * @return the feedbackVisible
     */
    @FieldBridge(impl = BooleanBridge.class)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    public boolean isFeedbackVisible() {
        return feedbackVisible;
    }

    /**
     * Sets the feedback visible.
     *
     * @param feedbackVisible the feedbackVisible to set
     */
    public void setFeedbackVisible(final boolean feedbackVisible) {
        this.feedbackVisible = feedbackVisible;
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
     * Returns the project name.
     *
     * @return the project name
     */
    // @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    // @SortableField
    private String getProjectName() {
        return project == null ? null : project.getName();
    }

    /**
     * Returns the organization name.
     *
     * @return the organization name
     */
    @Fields({
            @Field(index = Index.YES, analyze = Analyze.YES, store = Store.NO),
            @Field(name = "organizationNameSort", index = Index.YES, analyze = Analyze.NO,
                    store = Store.NO)
    })
    @SortableField(forField = "organizationNameSort")
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

        this.project.getOrganization().setName(organizationName);
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
        result = prime * result + ((narrative == null) ? 0 : narrative.hashCode());
        result = prime * result + ((versionNotes == null) ? 0 : versionNotes.hashCode());
        result = prime * result + ((moduleId == null) ? 0 : moduleId.hashCode());
        result = prime * result + ((externalUrl == null) ? 0 : externalUrl.hashCode());
        result = prime * result + ((project == null) ? 0 : project.hashCode());
        result = prime * result + (privateRefset ? 1 : 0);
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

        if (moduleId == null) {
            if (other.moduleId != null) {
                return false;
            }
        } else if (!moduleId.equals(other.moduleId)) {
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
