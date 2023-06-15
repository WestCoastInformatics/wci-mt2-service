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

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Class InviteRequest.
 */
@Entity
@Table(name = "invite_requests")
@Schema(description = "Represents an invitation")
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class InviteRequest extends AbstractHasModified {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /** The action. */
    @Column(nullable = false)
    private String action;

    /** The requester. */
    @Column(nullable = false)
    private String requester;

    /** The recipient email. */
    @Column(nullable = false)
    private String recipientEmail;

    /** The payload. */
    @Column(nullable = true)
    private String payload;

    /** The response. */
    @Column(nullable = true)
    private String response;

    /** The response date. */
    @Column(nullable = true)
    private Date responseDate;

    /**
     * Instantiates an empty {@link InviteRequest}.
     */
    public InviteRequest() {

        // n/a
    }

    /**
     * Instantiates a {@link InviteRequest} from the specified parameters.
     *
     * @param action the action
     * @param requester the requester
     * @param recipientEmail the recipient email
     * @param payload the payload
     */
    public InviteRequest(final String action, final String requester, final String recipientEmail, final String payload) {

        this.action = action;
        this.requester = requester;
        this.recipientEmail = recipientEmail;
        this.payload = payload;
    }

    /**
     * Instantiates a {@link InviteRequest} from the specified parameters.
     *
     * @param other the other
     */
    public InviteRequest(final InviteRequest other) {

        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final InviteRequest other) {

        super.populateFrom(other);
        this.action = other.getAction();
        this.requester = other.getRequester();
        this.recipientEmail = other.getRecipientEmail();
        this.payload = other.getPayload();
        this.response = other.getResponse();
        this.responseDate = other.getResponseDate();
    }

    /**
     * Returns the action.
     *
     * @return the action
     */
    public String getAction() {

        return action;
    }

    /**
     * Sets the action.
     *
     * @param action the action to set
     */
    public void setAction(final String action) {

        this.action = action;
    }

    /**
     * Returns the requester.
     *
     * @return the requester
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "nameSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getRequester() {

        return requester;
    }

    /**
     * Sets the requester.
     *
     * @param requester the requester to set
     */
    public void setRequester(final String requester) {

        this.requester = requester;
    }

    /**
     * Returns the recipient email.
     *
     * @return the recipientEmail
     */
    public String getRecipientEmail() {

        return recipientEmail;
    }

    /**
     * Sets the recipient email.
     *
     * @param recipientEmail the recipientEmail to set
     */
    public void setRecipientEmail(final String recipientEmail) {

        this.recipientEmail = recipientEmail;
    }

    /**
     * Returns the payload.
     *
     * @return the payload
     */
    public String getPayload() {

        return payload;
    }

    /**
     * Sets the payload.
     *
     * @param payload the payload to set
     */
    public void setPayload(final String payload) {

        this.payload = payload;
    }

    /**
     * Returns the response.
     *
     * @return the response
     */
    public String getResponse() {

        return response;
    }

    /**
     * Sets the response.
     *
     * @param response the response to set
     */
    public void setResponse(final String response) {

        this.response = response;
    }

    /**
     * Returns the response date.
     *
     * @return the responseDate
     */
    public Date getResponseDate() {

        return responseDate;
    }

    /**
     * Sets the response date.
     *
     * @param responseDate the responseDate to set
     */
    public void setResponseDate(final Date responseDate) {

        this.responseDate = responseDate;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((action == null) ? 0 : action.hashCode());
        result = prime * result + ((payload == null) ? 0 : payload.hashCode());
        result = prime * result + ((recipientEmail == null) ? 0 : recipientEmail.hashCode());
        result = prime * result + ((requester == null) ? 0 : requester.hashCode());
        result = prime * result + ((response == null) ? 0 : response.hashCode());
        result = prime * result + ((responseDate == null) ? 0 : responseDate.hashCode());
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
        if (!(obj instanceof InviteRequest)) {
            return false;
        }
        final InviteRequest other = (InviteRequest) obj;
        if (action == null) {
            if (other.action != null) {
                return false;
            }
        } else if (!action.equals(other.action)) {
            return false;
        }
        if (payload == null) {
            if (other.payload != null) {
                return false;
            }
        } else if (!payload.equals(other.payload)) {
            return false;
        }
        if (recipientEmail == null) {
            if (other.recipientEmail != null) {
                return false;
            }
        } else if (!recipientEmail.equals(other.recipientEmail)) {
            return false;
        }
        if (requester == null) {
            if (other.requester != null) {
                return false;
            }
        } else if (!requester.equals(other.requester)) {
            return false;
        }
        if (response == null) {
            if (other.response != null) {
                return false;
            }
        } else if (!response.equals(other.response)) {
            return false;
        }
        if (responseDate == null) {
            if (other.responseDate != null) {
                return false;
            }
        } else if (!responseDate.equals(other.responseDate)) {
            return false;
        }
        return true;
    }

    /* see superclass */
    @Override
    public String toString() {

        return "InviteRequest [action=" + action + ", requester=" + requester + ", recipientEmail=" + recipientEmail + ", payload=" + payload + ", response="
            + response + ", responseDate=" + responseDate + "]";
    }

    @Override
    public void lazyInit() {

        // n/a
    }

}
