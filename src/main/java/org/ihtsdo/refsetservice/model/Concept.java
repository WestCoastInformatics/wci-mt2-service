
package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** The is this concept a member of the refset. */
    private boolean memberOfRefset;

    /** The member effective time. */
    private Date memberEffectiveTime;

    /** The is this concept a member of the refset. */
    private boolean hasChildren;

    /** The descriptions. */
    private List<Map<String, String>> descriptions = new ArrayList<>();

    // These next two booleans are NOT needed for tree
    /** The flag for if a user can see the history for this concept. */
    private boolean historyVisible;

    /** The flag for if a user can see the feedback for this concept. */
    private boolean feedbackVisible;

    /** The flag for if concept is defined or primitive. */
    private boolean defined;
    
    /** The flag for if concept membership has been released. */
    private boolean released;

    // Members below are filled in when open Concept Details screen only (for
    // now)
    /** A list of the parents of this concept. */
    private List<Concept> parents = new ArrayList<>();

    /**
     * Does this concept have ancestors that are members of the refset.
     */
    private boolean hasAncestorRefsetMembers;

    /** A list of the children of this concept. */
    private List<Concept> children = new ArrayList<>();

    /**
     * Does this concept have descendants that are members of the refset.
     */
    private boolean hasDescendantRefsetMembers;

    private Map<Integer, List<String>> roleGroups = new HashMap<>();

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
        parents = other.getParents();
        hasAncestorRefsetMembers = other.getHasAncestorRefsetMembers();
        children = other.getChildren();
        hasDescendantRefsetMembers = other.getHasDescendantRefsetMembers();
        memberOfRefset = other.isMemberOfRefset();
        hasChildren = other.getHasChildren();
        roleGroups = other.getRoleGroups();
        defined = other.isDefined();
        released = other.isReleased();
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
     * Checks if history is visible.
     *
     * @return the history visible flag
     */
    public boolean isHistoryVisible() {
        return historyVisible;
    }

    /**
     * Sets if history is visible.
     *
     * @param historyVisible the history to set
     */
    public void setHistoryVisible(final boolean historyVisible) {
        this.historyVisible = historyVisible;
    }

    /**
     * Checks if feedback is visible.
     *
     * @return the feedback visible flag
     */
    public boolean isFeedbackVisible() {
        return feedbackVisible;
    }

    /**
     * Sets if the feedback visible.
     *
     * @param feedbackVisible the feedbackVisible to set
     */
    public void setFeedbackVisible(final boolean feedbackVisible) {
        this.feedbackVisible = feedbackVisible;
    }

    /**
     * Checks if is member of refset.
     *
     * @return the memberOfRefset
     */
    public boolean isMemberOfRefset() {
        return memberOfRefset;
    }

    /**
     * Sets the member of refset.
     *
     * @param memberOfRefset the memberOfRefset to set
     */
    public void setMemberOfRefset(boolean memberOfRefset) {
        this.memberOfRefset = memberOfRefset;
    }

    /**
     * Gets the checks for children.
     *
     * @return the hasChildren
     */
    public boolean getHasChildren() {
        return hasChildren;
    }

    /**
     * Sets the checks for children.
     *
     * @param hasChildren the hasChildren to set
     */
    public void setHasChildren(boolean hasChildren) {
        this.hasChildren = hasChildren;
    }

    /**
     * Gets the parents.
     *
     * @return the parents
     */
    public List<Concept> getParents() {

        if (parents == null) {
            parents = new ArrayList<>();
        }

        return parents;
    }

    /**
     * Sets the parents.
     *
     * @param parents the parents to set
     */
    public void setParents(List<Concept> parents) {
        this.parents = parents;
    }

    /**
     * Gets the checks for parents refset members.
     *
     * @return the hasAncestorRefsetMembers
     */
    public boolean getHasAncestorRefsetMembers() {
        return hasAncestorRefsetMembers;
    }

    /**
     * Sets the checks for parents refset members.
     *
     * @param hasAncestorRefsetMembers the hasAncestorRefsetMembers to set
     */
    public void setHasAncestorRefsetMembers(boolean hasAncestorRefsetMembers) {
        this.hasAncestorRefsetMembers = hasAncestorRefsetMembers;
    }

    /**
     * Gets the children.
     *
     * @return the children
     */
    public List<Concept> getChildren() {

        if (children == null) {
            children = new ArrayList<>();
        }

        return children;
    }

    /**
     * Sets the children.
     *
     * @param children the children to set
     */
    public void setChildren(List<Concept> children) {
        this.children = children;
    }

    /**
     * Gets the checks for children refset members.
     *
     * @return the hasDescendantRefsetMembers
     */
    public boolean getHasDescendantRefsetMembers() {
        return hasDescendantRefsetMembers;
    }

    /**
     * Sets the checks for children refset members.
     *
     * @param hasDescendantRefsetMembers the hasDescendantRefsetMembers to set
     */
    public void setHasDescendantRefsetMembers(boolean hasDescendantRefsetMembers) {
        this.hasDescendantRefsetMembers = hasDescendantRefsetMembers;
    }

    public Map<Integer, List<String>> getRoleGroups() {
        return roleGroups;
    }

    public void setRoleGroups(Map<Integer, List<String>> map) {
        this.roleGroups = map;
    }

    /**
     * @return the isDefined
     */
    public boolean isDefined() {
        return defined;
    }

    /**
     * @param isDefined the isDefined to set
     */
    public void setDefined(boolean isDefined) {
        this.defined = isDefined;
    }
    
    /**
     * @return has the concept been released
     */
    public boolean isReleased() {
        return released;
    }
    
    /**
     * @param released the value to set the released flag to
     */
    public void setReleased(boolean released) {
        this.released = released;
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
        result = prime * result + ((children == null) ? 0 : children.hashCode());
        result = prime * result + ((parents == null) ? 0 : parents.hashCode());
        result = prime * result + ((roleGroups == null) ? 0 : roleGroups.hashCode());
        result = prime * result + (memberOfRefset ? 1 : 0);
        result = prime * result + (hasChildren ? 1 : 0);
        result = prime * result + (defined ? 1 : 0);
        result = prime * result + (released ? 1 : 0);
        result = prime * result + (hasDescendantRefsetMembers ? 1 : 0);
        result = prime * result + (hasAncestorRefsetMembers ? 1 : 0);
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

        if (children == null) {

            if (other.children != null) {
                return false;
            }
        } else if (!children.equals(other.children)) {
            return false;
        }

        if (parents == null) {

            if (other.parents != null) {
                return false;
            }
        } else if (!parents.equals(other.parents)) {
            return false;
        }

        if (roleGroups == null) {

            if (other.roleGroups != null) {
                return false;
            }
        } else if (!roleGroups.equals(other.roleGroups)) {
            return false;
        }

        if (hasDescendantRefsetMembers != other.hasDescendantRefsetMembers) {
            return false;
        }

        if (hasAncestorRefsetMembers != other.hasAncestorRefsetMembers) {
            return false;
        }

        if (memberOfRefset != other.memberOfRefset) {
            return false;
        }

        if (hasChildren != other.hasChildren) {
            return false;
        }

        if (defined != other.defined) {
            return false;
        }
        
        if (released != other.released) {
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
