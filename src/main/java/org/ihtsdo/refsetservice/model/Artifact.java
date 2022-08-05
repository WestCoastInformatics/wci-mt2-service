/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a project.
 */
@Entity
@Table(name = "artifacts")
@Schema(description = "Represents an artifact")
@JsonInclude(Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class Artifact extends AbstractHasModified {

    /** The entity type. */
    @Column(nullable = false, length = 64)
    private String entityType;

    /** The entity id. */
    @Column(nullable = false, length = 64)
    private String entityId;

    /** The fileName. */
    @Column(nullable = false, length = 500)
    private String fileName;

    /** The fileName. */
    @Column(nullable = false, length = 500)
    private String storedFileName;

    /** The fileName. */
    @Column(nullable = false, length = 10)
    private String fileType;

    /** The fileType. */
    @Column(nullable = true, length = 4000)
    private String description;

    /** URL for artifact download. */
    @Transient
    private String downloadUrl;

    /**
     * Instantiates an empty {@link Artifact}.
     */
    public Artifact() {

        // n/a
    }

    /**
     * Instantiates a {@link Artifact} from the specified parameters.
     *
     * @param entityType the entity type
     * @param entityId the entity id
     * @param fileName the file name
     * @param fileType the file type
     * @param description the description
     */
    public Artifact(final String entityType, final String entityId, final String fileName, final String fileType, final String description) {

        this.entityType = entityType;
        this.entityId = entityId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.description = description;
    }

    /**
     * Instantiates a {@link Artifact} from the specified parameters.
     *
     * @param other the other
     */
    public Artifact(final Artifact other) {

        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final Artifact other) {

        super.populateFrom(other);
        this.description = other.getDescription();

    }

    /**
     * Returns the entity type.
     *
     * @return the entityType
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getEntityType() {

        return entityType;
    }

    /**
     * Sets the entity type.
     *
     * @param entityType the entityType to set
     */
    public void setEntityType(String entityType) {

        this.entityType = entityType;
    }

    /**
     * Returns the entity id.
     *
     * @return the entityId
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getEntityId() {

        return entityId;
    }

    /**
     * Sets the entity id.
     *
     * @param entityId the entityId to set
     */
    public void setEntityId(String entityId) {

        this.entityId = entityId;
    }

    /**
     * Returns the fileName.
     *
     * @return the fileName
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "fileNameSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getFileName() {

        return fileName;
    }

    /**
     * Sets the fileName.
     *
     * @param fileName the fileName to set
     */
    public void setFileName(final String fileName) {

        this.fileName = fileName;
    }

    /**
     * Returns the stored fileName.
     *
     * @return the fileName
     */
    @JsonIgnore
    public String getStoredFileName() {

        return storedFileName;
    }

    /**
     * Sets the fileName.
     *
     * @param storedFileName the stored file name
     */
    public void setStoredFileName(final String storedFileName) {

        this.storedFileName = storedFileName;
    }

    /**
     * Returns the fileType.
     *
     * @return the fileType
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "fileTypeSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getFileType() {

        return fileType;
    }

    /**
     * Sets the fileType.
     *
     * @param fileType the fileType to set
     */
    public void setFileType(final String fileType) {

        this.fileType = fileType;
    }

    /**
     * Returns the description.
     *
     * @return the description
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "descriptionSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getDescription() {

        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the description
     */
    public void setDescription(final String description) {

        this.description = description;
    }

    /**
     * Sets the download url.
     *
     * @param downloadUrl the download url
     */
    public void setDownloadUrl(final String downloadUrl) {

        this.downloadUrl = downloadUrl;
    }

    /**
     * Returns the download url.
     *
     * @return the download url
     */
    @JsonGetter()
    public String getDownloadUrl() {

        return downloadUrl;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((entityId == null) ? 0 : entityId.hashCode());
        result = prime * result + ((entityType == null) ? 0 : entityType.hashCode());
        result = prime * result + ((fileName == null) ? 0 : fileName.hashCode());
        result = prime * result + ((fileType == null) ? 0 : fileType.hashCode());
        result = prime * result + ((storedFileName == null) ? 0 : storedFileName.hashCode());
        return result;
    }

    /* see superclass */
    @Override
    public boolean equals(final Object obj) {

        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof Artifact)) {
            return false;
        }
        final Artifact other = (Artifact) obj;
        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }
        if (entityId == null) {
            if (other.entityId != null) {
                return false;
            }
        } else if (!entityId.equals(other.entityId)) {
            return false;
        }
        if (entityType == null) {
            if (other.entityType != null) {
                return false;
            }
        } else if (!entityType.equals(other.entityType)) {
            return false;
        }
        if (fileName == null) {
            if (other.fileName != null) {
                return false;
            }
        } else if (!fileName.equals(other.fileName)) {
            return false;
        }
        if (fileType == null) {
            if (other.fileType != null) {
                return false;
            }
        } else if (!fileType.equals(other.fileType)) {
            return false;
        }
        if (storedFileName == null) {
            if (other.storedFileName != null) {
                return false;
            }
        } else if (!storedFileName.equals(other.storedFileName)) {
            return false;
        }
        return true;
    }

    /**
     * To log string.
     *
     * @return the string
     */
    public String toLogString() {

        return "ARTIFACT [entityType=" + entityType + ", entityId=" + entityId + ", fileName=" + fileName + ", fileType=" + fileType + ", modified=" + getModified() + ", modified=" + getModifiedBy()
            + ", storedFileName=" + storedFileName + "]";
    }

    /* see superclass */
    @Override
    public void lazyInit() {

        // TODO Auto-generated method stub

    }

}
