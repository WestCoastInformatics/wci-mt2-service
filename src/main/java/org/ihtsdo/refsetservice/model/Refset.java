
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
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @Column(nullable = false, length = 256)
    private String refsetId;

    /** The project ID. */
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @Column(nullable = false, length = 256)
    private String projectId;

    /** The name. */
    @Fields({
            @Field(index = Index.YES, analyze = Analyze.YES, store = Store.NO),
            @Field(name = "nameSort", index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    })
    @Column(nullable = false, length = 4000)
    @SortableField(forField = "nameSort")
    private String name;

    /** The refset type. */
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @Column(nullable = false, length = 256)
    @SortableField
    private String type;

    /** The version status. */
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @Column(nullable = false, length = 256)
    @SortableField
    private String versionStatus;
    
    /** The organization. */
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @Column(nullable = false, length = 256)
    @SortableField
    private String organization;

    /** The version date. */
    @Column(nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
    @DateBridge(resolution = Resolution.SECOND, encoding = EncodingType.STRING)
    private Date versionDate;

    /** The version narrative. */
    @Column(nullable = true, length = 10000)
    private String narrative;

    /** The version status. */
    @Column(nullable = true, length = 10000)
    private String versionNotes;

    /** The private flag. */
    @FieldBridge(impl = BooleanBridge.class)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @Column(nullable = false)
    private boolean privateRefset;

    /** The local set flag. */
    @FieldBridge(impl = BooleanBridge.class)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @Column(nullable = false)
    private boolean localSet;
    
    /** The flag for if a user can download this refset. */
    @Transient
    @FieldBridge(impl = BooleanBridge.class)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    private boolean downloadable;
    
    /** The flag for if a user can see the feedback for this refset. */
    @Transient
    @FieldBridge(impl = BooleanBridge.class)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    private boolean feedbackVisible;

    /** The module ID. */
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
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

    /** The tags. */
    @ElementCollection
    @Field(analyze = Analyze.NO, store = Store.YES)
    @IndexedEmbedded
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
        projectId = other.getProjectId();
        tags = other.getTags();
        versionDate = other.getVersionDate();
        versionNotes = other.getVersionNotes();
        versionStatus = other.getVersionStatus();
        edition = other.getEdition();
        definitionClauses = other.getDefinitionClauses();
        externalUrl = other.getExternalUrl();
        moduleId = other.getModuleId();
    }

    /**
     * Returns the refset ID.
     *
     * @return the refset ID
     */
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
     * @return the versionDate
     */
    public Date getVersionDate() {
        return versionDate;
    }

    /**
     * @param versionDate the versionDate to set
     */
    public void setVersionDate(Date versionDate) {
        this.versionDate = versionDate;
    }

    /**
     * @return the narrative
     */
    public String getNarrative() {
        return narrative;
    }

    /**
     * @param narrative the narrative to set
     */
    public void setNarrative(String narrative) {
        this.narrative = narrative;
    }

    /**
     * @return the versionNotes
     */
    public String getVersionNotes() {
        return versionNotes;
    }

    /**
     * @param versionNotes the versionNotes to set
     */
    public void setVersionNotes(String versionNotes) {
        this.versionNotes = versionNotes;
    }

    /**
     * @return the projectId
     */
    public String getProjectId() {
        return projectId;
    }

    /**
     * @param projectId the projectId to set
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    /**
     * @return the isPrivateRefset
     */
    public boolean isPrivateRefset() {
        return privateRefset;
    }

    /**
     * @param privateRefset the isPrivateRefset to set
     */
    public void setPrivateRefset(boolean privateRefset) {
        this.privateRefset = privateRefset;
    }

    /**
     * @return the tags
     */
    public Set<String> getTags() {

        if (tags == null) {
            tags = new HashSet<>();
        }

        return tags;
    }

    /**
     * @param tags the tags to set
     */
    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    /**
     * @return the edition
     */
    @JsonSerialize(contentAs = Edition.class)
    @JsonDeserialize(contentAs = Edition.class)
    public Edition getEdition() {
        return edition;
    }

    /**
     * @param edition the edition to set
     */
    public void setEdition(Edition edition) {
        this.edition = edition;
    }

    /**
     * Returns the edition country.
     *
     * @return the to code
     */
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
    private String getEditionCountry() {
        return edition == null ? null : edition.getCountry();
    }

    /**
     * Returns the edition country.
     *
     * @return the to code
     */
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    private String getEditionName() {
        return edition == null ? null : edition.getName();
    }

    /**
     * @return the localSet
     */
    public boolean isLocalSet() {
        return localSet;
    }

    /**
     * @param localSet the localSet to set
     */
    public void setLocalSet(boolean localSet) {
        this.localSet = localSet;
    }

    /**
     * @return the moduleId
     */
    public String getModuleId() {
        return moduleId;
    }

    /**
     * @param moduleId the moduleId to set
     */
    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    /**
     * @return the externalUrl
     */
    public String getExternalUrl() {
        return externalUrl;
    }

    /**
     * @param externalUrl the externalUrl to set
     */
    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    /**
     * @return the definitionClauses
     */
    public List<DefinitionClause> getDefinitionClauses() {

        if (definitionClauses == null) {
            definitionClauses = new ArrayList<>();
        }

        return definitionClauses;
    }

    /**
     * @param definitionClauses the definitionClauses to set
     */
    public void setDefinitionClauses(List<DefinitionClause> definitionClauses) {
        this.definitionClauses = definitionClauses;
    }

    /**
     * @return the organization
     */
    public String getOrganization() {
        return organization;
    }

    /**
     * @param organization the organization to set
     */
    public void setOrganization(String organization) {
        this.organization = organization;
    }

    /**
     * @return the downloadable
     */
    public boolean isDownloadable() {
        return downloadable;
    }

    /**
     * @param downloadable the downloadable to set
     */
    public void setDownloadable(boolean downloadable) {
        this.downloadable = downloadable;
    }

    /**
     * @return the feedbackVisible
     */
    public boolean isFeedbackVisible() {
        return feedbackVisible;
    }

    /**
     * @param feedbackVisible the feedbackVisible to set
     */
    public void setFeedbackVisible(boolean feedbackVisible) {
        this.feedbackVisible = feedbackVisible;
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
        result = prime * result + ((projectId == null) ? 0 : projectId.hashCode());
        result = prime * result + ((versionDate == null) ? 0 : versionDate.hashCode());
        result = prime * result + ((versionStatus == null) ? 0 : versionStatus.hashCode());
        result = prime * result + ((narrative == null) ? 0 : narrative.hashCode());
        result = prime * result + ((versionNotes == null) ? 0 : versionNotes.hashCode());
        result = prime * result + ((moduleId == null) ? 0 : moduleId.hashCode());
        result = prime * result + ((externalUrl == null) ? 0 : externalUrl.hashCode());
        result = prime * result + ((organization == null) ? 0 : organization.hashCode());
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

        if (projectId == null) {
            if (other.projectId != null) {
                return false;
            }
        } else if (!projectId.equals(other.projectId)) {
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
        
        if (organization == null) {
            if (other.organization != null) {
                return false;
            }
        } else if (!organization.equals(other.organization)) {
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

    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub

    }
}
