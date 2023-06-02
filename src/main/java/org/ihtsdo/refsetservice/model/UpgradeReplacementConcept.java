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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

/**
 * Represents an inactive concept during the refset upgrade process.
 * 
 */
@Entity
@Table(name = "upgrade_replacement_concepts")
public class UpgradeReplacementConcept extends AbstractHasModified {

    /** The code. */
    @Column(nullable = false, length = 256)
    private String code;

    /** The UUID of the membership. */
    @Column(nullable = true, length = 256)
    private String memberId;

    /** The descriptions. */
    @Column(nullable = false, length = 10000)
    @Type(type = "text")
    private String descriptions;

    /** The reason for replacement. */
    @Column(nullable = false, length = 256)
    private String reason;

    /** The flag showing if this concept is already an existing member of the refset. */
    @Column(nullable = false)
    private boolean existingMember;

    /** The flag showing if this concept has been added as a replacement member. */
    @Column(nullable = false)
    private boolean added;

    /**
     * Instantiates an empty {@link UpgradeReplacementConcept}.
     */
    public UpgradeReplacementConcept() {

        // n/a
    }

    /**
     * Instantiates a {@link UpgradeReplacementConcept} from the specified parameters.
     *
     * @param other the other
     */
    public UpgradeReplacementConcept(final UpgradeReplacementConcept other) {

        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final UpgradeReplacementConcept other) {

        super.populateFrom(other);
        code = other.getCode();
        memberId = other.getMemberId();
        descriptions = other.getDescriptions();
        reason = other.getReason();
        existingMember = other.isExistingMember();
        added = other.isAdded();
    }

    /**
     * Returns the code.
     *
     * @return the code
     */
    public String getCode() {

        return code;
    }

    /**
     * Sets the code.
     *
     * @param code the code
     */
    public void setCode(final String code) {

        this.code = code;
    }

    /**
     * Returns the UUID of the membership.
     *
     * @return the member ID
     */
    public String getMemberId() {

        return memberId;
    }

    /**
     * Sets the UUID of the membership.
     *
     * @param memberId the member ID
     */
    public void setMemberId(final String memberId) {

        this.memberId = memberId;
    }

    /**
     * Returns the descriptions.
     *
     * @return the descriptions
     */
    public String getDescriptions() {

        return descriptions;
    }

    /**
     * Sets the descriptions.
     *
     * @param descriptions the descriptions
     */
    public void setDescriptions(final String descriptions) {

        this.descriptions = descriptions;
    }

    /**
     * Returns the reason.
     *
     * @return the reason
     */
    public String getReason() {

        return reason;
    }

    /**
     * Sets the reason.
     *
     * @param reason the reason
     */
    public void setReason(final String reason) {

        this.reason = reason;
    }

    /**
     * Checks if this concept is already an existing member of the refset.
     *
     * @return the existingMember flag
     */
    public boolean isExistingMember() {

        return existingMember;
    }

    /**
     * Sets the flag showing if this concept is already an existing member of the refset.
     *
     * @param existingMember the flag value to set
     */
    public void setExistingMember(final boolean existingMember) {

        this.existingMember = existingMember;
    }

    /**
     * Checks if this concept has been added as a replacement member.
     *
     * @return the added flag
     */
    public boolean isAdded() {

        return added;
    }

    /**
     * Sets the flag showing if this concept has been added as a replacement member.
     *
     * @param added the flag to set
     */
    public void setAdded(final boolean added) {

        this.added = added;
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
        result = prime * result + ((code == null) ? 0 : code.hashCode());
        result = prime * result + ((memberId == null) ? 0 : memberId.hashCode());
        result = prime * result + ((descriptions == null) ? 0 : descriptions.hashCode());
        result = prime * result + (existingMember ? 1 : 0);
        result = prime * result + (added ? 1 : 0);
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

        final UpgradeReplacementConcept other = (UpgradeReplacementConcept) obj;

        if (code == null) {
            if (other.code != null) {
                return false;
            }
        } else if (!code.equals(other.code)) {
            return false;
        }

        if (memberId == null) {
            if (other.memberId != null) {
                return false;
            }
        } else if (!memberId.equals(other.memberId)) {
            return false;
        }

        if (descriptions == null) {
            if (other.descriptions != null) {
                return false;
            }
        } else if (!descriptions.equals(other.descriptions)) {
            return false;
        }

        if (reason == null) {
            if (other.reason != null) {
                return false;
            }
        } else if (!reason.equals(other.reason)) {
            return false;
        }

        if (existingMember != other.existingMember) {
            return false;
        }

        if (added != other.added) {
            return false;
        }

        return true;
    }

    /**
     * Lazy init.
     */
    @Override
    public void lazyInit() {

        // n/a

    }
}
