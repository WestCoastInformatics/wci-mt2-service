
package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

// TODO: Auto-generated Javadoc
/**
 * Represents a concept with a code from a terminology.
 * 
 * <pre>
 * {
 *   "code" : "C3224",
 *   "name" : "Melanoma",
 * }
 * </pre>
 */

public class Concept extends AbstractHasModified implements Comparable<Concept> {

    /** The code. */
    private String code;

    /** The name. */
    private String name;

    /** The terminology. */
    private String terminology;

    /** The version. */
    private String version;

    /** The member status. */
    private boolean memberStatus;

    /** The member effective time. */
    private Date memberEffectiveTime;

    /** The descriptions. */
    private List<Map<String, String>> descriptions = new ArrayList<>();

    /**
     * Instantiates an empty {@link Concept}.
     */
    public Concept() {
        // n/a
    }

    /**
     * Instantiates a {@link Concept} from the specified parameters.
     *
     * @param code the code
     */
    public Concept(final String code) {
        this.code = code;
    }

    /**
     * Instantiates a {@link Concept} from the specified parameters.
     *
     * @param terminology the terminology
     * @param code the code
     * @param name the name
     */
    public Concept(final String terminology, final String code, final String name) {
        this.terminology = terminology;
        this.code = code;
        this.name = name;
    }

    /**
     * Instantiates a {@link Concept} from the specified parameters.
     *
     * @param other the other
     */
    public Concept(final Concept other) {
        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final Concept other) {
        super.populateFrom(other);
        code = other.getCode();
        name = other.getName();
        terminology = other.getTerminology();
        version = other.getVersion();
        memberEffectiveTime = other.getMemberEffectiveTime();
        memberStatus = other.isMemberStatus();
        descriptions = other.getDescriptions();
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
     * Returns the terminology.
     *
     * @return the terminology
     */
    public String getTerminology() {
        return terminology;
    }

    /**
     * Sets the terminology.
     *
     * @param terminology the terminology
     */
    public void setTerminology(final String terminology) {
        this.terminology = terminology;
    }

    /**
     * Returns the version.
     *
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the version.
     *
     * @param version the version
     */
    public void setVersion(final String version) {
        this.version = version;
    }

    /**
     * Checks if is member status.
     *
     * @return true, if is member status
     */
    public boolean isMemberStatus() {
        return memberStatus;
    }

    /**
     * Sets the member status.
     *
     * @param memberStatus the new member status
     */
    public void setMemberStatus(boolean memberStatus) {
        this.memberStatus = memberStatus;
    }

    /**
     * Gets the member effective time.
     *
     * @return the member effective time
     */
    public Date getMemberEffectiveTime() {
        return memberEffectiveTime;
    }

    /**
     * Sets the member effective time.
     *
     * @param memberEffectiveTime the new member effective time
     */
    public void setMemberEffectiveTime(Date memberEffectiveTime) {
        this.memberEffectiveTime = memberEffectiveTime;
    }

    /**
     * Gets the descriptions.
     *
     * @return the descriptions
     */
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
     * Hash code.
     *
     * @return the int
     */
    /* see superclass */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((code == null) ? 0 : code.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((terminology == null) ? 0 : terminology.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        result = prime * result + (memberStatus ? 1 : 0);
        result = prime * result
                + ((memberEffectiveTime == null) ? 0 : memberEffectiveTime.hashCode());
        result = prime * result + ((descriptions == null) ? 0 : descriptions.hashCode());
        return result;
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    /* see superclass */
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
        final Concept other = (Concept) obj;
        if (code == null) {
            if (other.code != null) {
                return false;
            }
        } else if (!code.equals(other.code)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (terminology == null) {
            if (other.terminology != null) {
                return false;
            }
        } else if (!terminology.equals(other.terminology)) {
            return false;
        }
        if (version == null) {
            if (other.version != null) {
                return false;
            }
        } else if (!version.equals(other.version)) {
            return false;
        }

        if (memberEffectiveTime == null) {
            if (other.memberEffectiveTime != null) {
                return false;
            }
        } else if (!memberEffectiveTime.equals(other.memberEffectiveTime)) {
            return false;
        }

        if (descriptions == null) {
            if (other.descriptions != null) {
                return false;
            }
        } else if (!descriptions.equals(other.descriptions)) {
            return false;
        }

        if (memberStatus != other.memberStatus) {
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
    /* see superclass */
    @Override
    public int compareTo(final Concept o) {
        // Handle null
        return (name + code).compareToIgnoreCase(o.getName() + o.getCode());
    }

    /**
     * Lazy init.
     */
    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub

    }

}
