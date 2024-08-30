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

import java.util.Date;
import java.util.Objects;

/**
 * The Class SnowstormFhirCodeSystem.
 */
public class SnowstormFhirCodeSystem {

    /** The full url. */
    private String fullUrl;

    /** The resource type. */
    private String resourceType;

    /** The id. */
    private String id;

    /** The url. */
    private String url;

    /** The version. */
    private String version;

    /** The name. */
    private String name;

    /** The status. */
    private String status;

    /** The date. */
    private Date date;

    /** The publisher. */
    private String publisher;

    /** The hierarchy meaning. */
    private String hierarchyMeaning;

    /** The compositional. */
    private boolean compositional;

    /** The content. */
    private String content;

    /**
     * Instantiates a new snowstorm fhir code system.
     */
    public SnowstormFhirCodeSystem() {

        super();
    }

    /**
     * Gets the full url.
     *
     * @return the full url
     */
    public String getFullUrl() {

        return fullUrl;
    }

    /**
     * Sets the full url.
     *
     * @param fullUrl the new full url
     */
    public void setFullUrl(String fullUrl) {

        this.fullUrl = fullUrl;
    }

    /**
     * Gets the resource type.
     *
     * @return the resource type
     */
    public String getResourceType() {

        return resourceType;
    }

    /**
     * Sets the resource type.
     *
     * @param resourceType the new resource type
     */
    public void setResourceType(String resourceType) {

        this.resourceType = resourceType;
    }

    /**
     * Gets the id.
     *
     * @return the id
     */
    public String getId() {

        return id;
    }

    /**
     * Sets the id.
     *
     * @param id the new id
     */
    public void setId(String id) {

        this.id = id;
    }

    /**
     * Gets the url.
     *
     * @return the url
     */
    public String getUrl() {

        return url;
    }

    /**
     * Sets the url.
     *
     * @param url the new url
     */
    public void setUrl(String url) {

        this.url = url;
    }

    /**
     * Gets the version.
     *
     * @return the version
     */
    public String getVersion() {

        return version;
    }

    /**
     * Sets the version.
     *
     * @param version the new version
     */
    public void setVersion(String version) {

        this.version = version;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {

        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the new name
     */
    public void setName(String name) {

        this.name = name;
    }

    /**
     * Gets the status.
     *
     * @return the status
     */
    public String getStatus() {

        return status;
    }

    /**
     * Sets the status.
     *
     * @param status the new status
     */
    public void setStatus(String status) {

        this.status = status;
    }

    /**
     * Gets the date.
     *
     * @return the date
     */
    public Date getDate() {

        return date;
    }

    /**
     * Sets the date.
     *
     * @param date the new date
     */
    public void setDate(Date date) {

        this.date = date;
    }

    /**
     * Gets the publisher.
     *
     * @return the publisher
     */
    public String getPublisher() {

        return publisher;
    }

    /**
     * Sets the publisher.
     *
     * @param publisher the new publisher
     */
    public void setPublisher(String publisher) {

        this.publisher = publisher;
    }

    /**
     * Gets the hierarchy meaning.
     *
     * @return the hierarchy meaning
     */
    public String getHierarchyMeaning() {

        return hierarchyMeaning;
    }

    /**
     * Sets the hierarchy meaning.
     *
     * @param hierarchyMeaning the new hierarchy meaning
     */
    public void setHierarchyMeaning(String hierarchyMeaning) {

        this.hierarchyMeaning = hierarchyMeaning;
    }

    /**
     * Checks if is compositional.
     *
     * @return true, if is compositional
     */
    public boolean isCompositional() {

        return compositional;
    }

    /**
     * Sets the compositional.
     *
     * @param compositional the new compositional
     */
    public void setCompositional(boolean compositional) {

        this.compositional = compositional;
    }

    /**
     * Gets the content.
     *
     * @return the content
     */
    public String getContent() {

        return content;
    }

    /**
     * Sets the content.
     *
     * @param content the new content
     */
    public void setContent(String content) {

        this.content = content;
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        return Objects.hash(compositional, content, date, fullUrl, hierarchyMeaning, id, name, publisher, resourceType, status, url, version);
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
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SnowstormFhirCodeSystem other = (SnowstormFhirCodeSystem) obj;
        return compositional == other.compositional && Objects.equals(content, other.content) && Objects.equals(date, other.date)
            && Objects.equals(fullUrl, other.fullUrl) && Objects.equals(hierarchyMeaning, other.hierarchyMeaning) && Objects.equals(id, other.id)
            && Objects.equals(name, other.name) && Objects.equals(publisher, other.publisher) && Objects.equals(resourceType, other.resourceType)
            && Objects.equals(status, other.status) && Objects.equals(url, other.url) && Objects.equals(version, other.version);
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {

        return "SnowstormFhirCodeSystem [fullUrl=" + fullUrl + ", resourceType=" + resourceType + ", id=" + id + ", url=" + url + ", version=" + version
            + ", name=" + name + ", status=" + status + ", date=" + date + ", publisher=" + publisher + ", hierarchyMeaning=" + hierarchyMeaning
            + ", compositional=" + compositional + ", content=" + content + "]";
    }

}
