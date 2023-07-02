/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Discussion Topic.
 */
@Entity
@Table(name = "discussion_threads")
@Schema(description = "A discussion thread for a refset or refset memeber.")
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class DiscussionThread extends AbstractHasModified {

    /** serialVersionUID. */
    private static final long serialVersionUID = -3062800846649092242L;

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DiscussionThread.class);

    /** The subject. */
    @Column(nullable = false, length = 4000)
    private String subject;

    /** The discussion type. */
    @Column(nullable = false, length = 20)
    private String type;

    /** the internal ID of the refset. */
    @Column(nullable = false, length = 64)
    private String refsetInternalId;

    /** the concept ID of the member if this is a member type. */
    @Column(nullable = true, length = 64)
    private String conceptId;

    /** The posts. */
    @OneToMany(cascade = CascadeType.ALL, targetEntity = DiscussionPost.class, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("created ASC")
    private List<DiscussionPost> posts = new ArrayList<>();

    /** Indicate if thread is private. */
    @Column(nullable = false)
    private boolean privateThread;

    /** The visibility of the thread. */
    @Column(nullable = false, length = 64)
    private String visibility;

    /** The status of the thread. */
    @Column(nullable = false, length = 64)
    private String status;

    /** The username that resolved the thread. */
    @Column(nullable = true, length = 205)
    private String resolvedBy;

    /** The date of the last post in the thread. */
    @Transient
    private Date lastPost;

    /** The number of replies in the thread. */
    @Transient
    private int numberReplies;

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
    public void setSubject(final String subject) {

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
    public void setType(final String type) {

        this.type = type;
    }

    /**
     * Returns the internal ID of the refset.
     *
     * @return the internal ID of the refset.
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getRefsetInternalId() {

        return refsetInternalId;
    }

    /**
     * Sets the internal ID of the refset.
     *
     * @param refsetInternalId the internal ID of the refset.
     */
    public void setRefsetInternalId(final String refsetInternalId) {

        this.refsetInternalId = refsetInternalId;
    }

    /**
     * Returns the concept ID of the member if this is a member type.
     *
     * @return the the concept ID of the member.
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getConceptId() {

        return conceptId;
    }

    /**
     * Sets the concept ID of the member if this is a member type.
     *
     * @param conceptId the concept ID of the member.
     */
    public void setConceptId(final String conceptId) {

        this.conceptId = conceptId;
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
    public void setPosts(final List<DiscussionPost> posts) {

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
    public void setPrivateThread(final boolean privateThread) {

        this.privateThread = privateThread;
    }

    /**
     * Get the visibility of the thread.
     *
     * @return the visibility of the thread
     */
    public String getVisibility() {

        return visibility;
    }

    /**
     * Sets the visibility of the thread.
     *
     * @param visibility the visibility of the thread
     */
    public void setVisibility(final String visibility) {

        this.visibility = visibility;
    }

    /**
     * Get the status of the thread.
     *
     * @return status the visibility of the thread
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getStatus() {

        return status;
    }

    /**
     * Sets the status of the thread.
     *
     * @param status the visibility of the thread
     */
    public void setStatus(final String status) {

        this.status = status;
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
    public void setResolvedBy(final String resolvedBy) {

        this.resolvedBy = resolvedBy;
    }

    /**
     * Returns the date of the last post in the thread.
     *
     * @return the date of the last post in the thread
     */
    @JsonGetter()
    public Date getLastPost() {

        return lastPost;
    }

    /**
     * Sets the date of the last post in the thread.
     *
     * @param lastPost the date of the last post in the thread
     */
    public void setLastPost(final Date lastPost) {

        this.lastPost = lastPost;
    }

    /**
     * Returns the number of replies in the thread.
     *
     * @return The number of replies in the thread
     */
    @JsonGetter()
    public int getNumberReplies() {

        return numberReplies;
    }

    /**
     * Sets the number of replies in the thread.
     *
     * @param numberReplies the number of replies in the thread
     */
    public void setNumberReplies(final int numberReplies) {

        this.numberReplies = numberReplies;
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final DiscussionThread other) {

        super.populateFrom(other);
        refsetInternalId = other.getRefsetInternalId();
        conceptId = other.getConceptId();
        posts = other.getPosts();
        subject = other.getSubject();
        type = other.getType();
        status = other.getStatus();
        resolvedBy = other.getResolvedBy();
        privateThread = other.isPrivateThread();
        visibility = other.getVisibility();
        lastPost = other.getLastPost();
        numberReplies = other.getNumberReplies();

    }

    /**
     * Patch from.
     *
     * @param other the other
     */
    public void patchFrom(final DiscussionThread other) {

        // super.populateFrom(other);
        // Only these field can be patched
        status = other.getStatus();
        resolvedBy = other.getResolvedBy();
        privateThread = other.isPrivateThread();
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((conceptId == null) ? 0 : conceptId.hashCode());
        result = prime * result + ((lastPost == null) ? 0 : lastPost.hashCode());
        result = prime * result + numberReplies;
        result = prime * result + ((posts == null) ? 0 : posts.hashCode());
        result = prime * result + (privateThread ? 1231 : 1237);
        result = prime * result + ((refsetInternalId == null) ? 0 : refsetInternalId.hashCode());
        result = prime * result + ((resolvedBy == null) ? 0 : resolvedBy.hashCode());
        result = prime * result + ((status == null) ? 0 : status.hashCode());
        result = prime * result + ((subject == null) ? 0 : subject.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((visibility == null) ? 0 : visibility.hashCode());
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
        if (!(obj instanceof DiscussionThread)) {
            return false;
        }
        DiscussionThread other = (DiscussionThread) obj;
        if (conceptId == null) {
            if (other.conceptId != null) {
                return false;
            }
        } else if (!conceptId.equals(other.conceptId)) {
            return false;
        }
        if (lastPost == null) {
            if (other.lastPost != null) {
                return false;
            }
        } else if (!lastPost.equals(other.lastPost)) {
            return false;
        }
        if (numberReplies != other.numberReplies) {
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
        if (refsetInternalId == null) {
            if (other.refsetInternalId != null) {
                return false;
            }
        } else if (!refsetInternalId.equals(other.refsetInternalId)) {
            return false;
        }
        if (resolvedBy == null) {
            if (other.resolvedBy != null) {
                return false;
            }
        } else if (!resolvedBy.equals(other.resolvedBy)) {
            return false;
        }
        if (status == null) {
            if (other.status != null) {
                return false;
            }
        } else if (!status.equals(other.status)) {
            return false;
        }
        if (subject == null) {
            if (other.subject != null) {
                return false;
            }
        } else if (!subject.equals(other.subject)) {
            return false;
        }
        if (type == null) {
            if (other.type != null) {
                return false;
            }
        } else if (!type.equals(other.type)) {
            return false;
        }
        if (visibility == null) {
            if (other.visibility != null) {
                return false;
            }
        } else if (!visibility.equals(other.visibility)) {
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

        // n/a

    }

}
