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

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Discussion Topic.
 */
@Entity
@Table(name = "discussion_threads")
@Schema(description = "A discussion thread for a refset or refset memeber.")
@JsonInclude(Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class DiscussionThread extends AbstractHasModified {

    /** serialVersionUID. */
    private static final long serialVersionUID = -3062800846649092242L;

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(DiscussionThread.class);

    /** The subject. */
    @Column(nullable = false, length = 4000)
    private String subject;

    /** The discussion type. */
    @Column(nullable = false, length = 20)
    private String type;

    /** primary objectKey to refset or refset member or another. */
    @Column(nullable = false, unique = true, length = 64)
    private String objectKey;

    /** The posts. */
    @OneToMany(cascade = CascadeType.ALL, targetEntity = DiscussionPost.class, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("created ASC")
    private List<DiscussionPost> posts = new ArrayList<>();

    /** Indicate if thread is private. */
    @Column(nullable = false)
    private boolean privateThread;

    /** Indicate if thread is resolved. */
    @Column(nullable = false)
    private boolean resolve;

    /** The resolved by. */
    @Column(nullable = true, length = 64)
    private String resolvedBy;

    /**
     * Returns the subject.
     *
     * @return the subject
     */
    public String getSubject() {

        return subject;
    }

    /**
     * Sets the subject.
     *
     * @param subject the subject to set
     */
    public void setSubject(String subject) {

        this.subject = subject;
    }

    /**
     * Returns the discussion type.
     *
     * @return the discussionType
     */
    @FullTextField(analyzer = "standard")
    public String getType() {

        return type;
    }

    /**
     * Sets the discussion type.
     *
     * @param type the discussion type
     */
    public void setType(String type) {

        this.type = type;
    }

    /**
     * Returns the objectKey.
     *
     * @return the objectKey
     */
    @FullTextField(analyzer = "standard")
    public String getObjectKey() {

        return objectKey;
    }

    /**
     * Sets the objectKey.
     *
     * @param objectKey the objectKey to set
     */
    public void setObjectKey(String objectKey) {

        this.objectKey = objectKey;
    }

    /**
     * Returns the posts.
     *
     * @return the posts
     */
    public List<DiscussionPost> getPosts() {

        if (posts == null) {
            posts = new ArrayList<DiscussionPost>();
        }
        return posts;
    }

    /**
     * Sets the posts.
     *
     * @param posts the posts to set
     */
    public void setPosts(List<DiscussionPost> posts) {

        this.posts = posts;
    }

    /**
     * Indicates whether or not private thread is the case.
     *
     * @return the privateThread
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public boolean isPrivateThread() {

        return privateThread;
    }

    /**
     * Sets the private thread.
     *
     * @param privateThread the privateThread to set
     */
    public void setPrivateThread(boolean privateThread) {

        this.privateThread = privateThread;
    }

    /**
     * Indicates whether or not resolve is the case.
     *
     * @return the resolve
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public boolean isResolve() {

        return resolve;
    }

    /**
     * Sets the resolve.
     *
     * @param resolve the resolve to set
     */
    public void setResolve(boolean resolve) {

        this.resolve = resolve;
    }

    /**
     * Returns the resolved by.
     *
     * @return the resolvedBy
     */
    public String getResolvedBy() {

        return resolvedBy;
    }

    /**
     * Sets the resolved by.
     *
     * @param resolvedBy the resolvedBy to set
     */
    public void setResolvedBy(String resolvedBy) {

        this.resolvedBy = resolvedBy;
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final DiscussionThread other) {

        super.populateFrom(other);
        objectKey = other.getObjectKey();
        posts = other.getPosts();
        subject = other.getSubject();
        type = other.getType();
        resolve = other.isResolve();
        resolvedBy = other.getResolvedBy();
        privateThread = other.isPrivateThread();

    }

    /**
     * Patch from.
     *
     * @param other the other
     */
    public void patchFrom(final DiscussionThread other) {

        // super.populateFrom(other);
        // Only these field can be patched
        resolve = other.isResolve();
        resolvedBy = other.getResolvedBy();
        privateThread = other.isPrivateThread();
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((objectKey == null) ? 0 : objectKey.hashCode());
        result = prime * result + ((posts == null) ? 0 : posts.hashCode());
        result = prime * result + (privateThread ? 1231 : 1237);
        result = prime * result + (resolve ? 1231 : 1237);
        result = prime * result + ((resolvedBy == null) ? 0 : resolvedBy.hashCode());
        result = prime * result + ((subject == null) ? 0 : subject.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    /* see superclass */
    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DiscussionThread other = (DiscussionThread) obj;
        if (objectKey == null) {
            if (other.objectKey != null) {
                return false;
            }
        } else if (!objectKey.equals(other.objectKey)) {
            return false;
        }
        if (posts == null) {
            if (other.posts != null) {
                return false;
            }
        } else if (!posts.equals(other.posts)) {
            return false;
        }
        if (privateThread != other.privateThread) {
            return false;
        }
        if (resolve != other.resolve) {
            return false;
        }
        if (resolvedBy == null) {
            if (other.resolvedBy != null) {
                return false;
            }
        } else if (!resolvedBy.equals(other.resolvedBy)) {
            return false;
        }
        if (subject == null) {
            if (other.subject != null) {
                return false;
            }
        } else if (!subject.equals(other.subject)) {
            return false;
        }
        if (type != other.type) {
            return false;
        }
        return true;
    }

    /* see superclass */
    @Override
    public String toString() {

        try {
            return ModelUtility.toJson(this);
        } catch (final Exception e) {
            return e.getMessage();
        }
    }

    /* see superclass */
    @Override
    public void lazyInit() {

        // TODO Auto-generated method stub

    }

}
