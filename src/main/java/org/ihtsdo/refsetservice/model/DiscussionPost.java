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

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Post for a discussion.
 */
@Entity
@Table(name = "discussion_posts")
@Schema(description = "A post of a discussion thread")
@JsonInclude(Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class DiscussionPost extends AbstractHasModified {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = -4303326622967031897L;

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(DiscussionPost.class);

    /** The message. */
    @Column(nullable = false, length = 4000)
    private String message;

    /** Indicate if post is private. */
    @Column(nullable = false)
    private boolean privatePost;

    /**
     * Returns the message.
     *
     * @return the message
     */
    public String getMessage() {

        return message;
    }

    /**
     * Sets the message.
     *
     * @param message the message to set
     */
    public void setMessage(String message) {

        this.message = message;
    }

    /**
     * Indicates whether or not private post is the case.
     *
     * @return the privatePost
     */
    public boolean isPrivatePost() {

        return privatePost;
    }

    /**
     * Sets the private post.
     *
     * @param privatePost the privatePost to set
     */
    public void setPrivatePost(boolean privatePost) {

        this.privatePost = privatePost;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((message == null) ? 0 : message.hashCode());
        result = prime * result + (privatePost ? 1231 : 1237);
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
        DiscussionPost other = (DiscussionPost) obj;
        if (message == null) {
            if (other.message != null) {
                return false;
            }
        } else if (!message.equals(other.message)) {
            return false;
        }
        if (privatePost != other.privatePost) {
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
