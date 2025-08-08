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

import com.fasterxml.jackson.annotation.JsonInclude;
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
 @JsonInclude(JsonInclude.Include.NON_NULL)
public class Concept extends AbstractHasModified implements Comparable<Concept> {

  /** The code. */
  private String code;

  /** The UUID of the membership. */
  private String memberId;

  /** The name. */
  private String name;

  /** The Fully Specified Name. */
  private String fsn;

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

  /** The flag for if a user can see the history for this concept. */
  private boolean historyVisible;

  /** The flag for if a user can see the feedback for this concept. */
  private boolean feedbackVisible;

  /** The flag for if concept is defined or primitive. */
  private boolean defined;

  /** The flag for if concept membership has been released. */
  private boolean released;

  /**
   * What kind of intensional refset definition exception type is this concept.
   * (definition/inclusion/exclusion)
   */
  private String definitionExceptionType;

  /** The internal ID of intensional refset definition exception. */
  private String definitionExceptionId;

  // Members below are filled in when open Concept Details screen only (for now)
  /** A list of the parents of this concept. */
  private List<Concept> parents = new ArrayList<>();

  /** Does this concept have ancestors that are members of the refset. */
  private boolean hasAncestorRefsetMembers;

  /** A list of the children of this concept. */
  private List<Concept> children = new ArrayList<>();

  /** A list of the parents of this concept as ConceptRef objects (lightweight). */
  private List<ConceptRef> parentRefs = new ArrayList<>();

  /** A list of the children of this concept as ConceptRef objects (lightweight). */
  private List<ConceptRef> childRefs = new ArrayList<>();

  /** Does this concept have descendants that are members of the refset. */
  private boolean hasDescendantRefsetMembers;

  /** The count of discussions for this item. */
  private int openDiscussionCount;

  /** The count of discussions for this item. */
  private int resolvedDiscussionCount;

  /** The role groups. */
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
    memberId = other.getMemberId();
    name = other.getName();
    fsn = other.getFsn();
    terminology = other.getTerminology();
    version = other.getVersion();
    memberEffectiveTime = other.getMemberEffectiveTime();
    parents = other.getParents();
    parentRefs = other.getParentRefs();
    hasAncestorRefsetMembers = other.getHasAncestorRefsetMembers();
    children = other.getChildren();
    childRefs = other.getChildRefs();
    hasDescendantRefsetMembers = other.getHasDescendantRefsetMembers();
    memberOfRefset = other.isMemberOfRefset();
    definitionExceptionType = other.getDefinitionExceptionType();
    definitionExceptionId = other.getDefinitionExceptionId();
    hasChildren = other.getHasChildren();
    roleGroups = other.getRoleGroups();
    defined = other.isDefined();
    released = other.isReleased();
    descriptions = other.getDescriptions();
    openDiscussionCount = other.getOpenDiscussionCount();
    resolvedDiscussionCount = other.getResolvedDiscussionCount();
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
   * Returns the fsn.
   *
   * @return the fsn
   */
  public String getFsn() {

    return fsn;
  }

  /**
   * Sets the fsn.
   *
   * @param fsn the fsn
   */
  public void setFsn(final String fsn) {

    this.fsn = fsn;
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
  public void setMemberEffectiveTime(final Date memberEffectiveTime) {

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
  public void setDescriptions(final List<Map<String, String>> descriptions) {

    this.descriptions = descriptions;
  }

  /**
   * Returns the open discussion count.
   *
   * @return the discussion count
   */
  public int getOpenDiscussionCount() {

    return openDiscussionCount;
  }

  /**
   * Sets the open discussion count.
   *
   * @param openDiscussionCount the open discussion count
   */
  public void setOpenDiscussionCount(final int openDiscussionCount) {

    this.openDiscussionCount = openDiscussionCount;
  }

  /**
   * Returns the resolved discussion count.
   *
   * @return the discussion count
   */
  public int getResolvedDiscussionCount() {

    return resolvedDiscussionCount;
  }

  /**
   * Sets the discussion count.
   *
   * @param resolvedDiscussionCount the resolved discussion count
   */
  public void setResolvedDiscussionCount(final int resolvedDiscussionCount) {

    this.resolvedDiscussionCount = resolvedDiscussionCount;
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
  public void setMemberOfRefset(final boolean memberOfRefset) {

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
  public void setHasChildren(final boolean hasChildren) {

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
  public void setParents(final List<Concept> parents) {

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
  public void setHasAncestorRefsetMembers(final boolean hasAncestorRefsetMembers) {

    this.hasAncestorRefsetMembers = hasAncestorRefsetMembers;
  }

  /**
   * Returns the definition exception type.
   *
   * @return the definition exception type
   */
  public String getDefinitionExceptionType() {

    return definitionExceptionType;
  }

  /**
   * Sets the definition exception type.
   *
   * @param definitionExceptionType the definition exception type
   */
  public void setDefinitionExceptionType(final String definitionExceptionType) {

    this.definitionExceptionType = definitionExceptionType;
  }

  /**
   * Returns the definition exception ID.
   *
   * @return the definition exception ID
   */
  public String getDefinitionExceptionId() {

    return definitionExceptionId;
  }

  /**
   * Sets the definition exception ID.
   *
   * @param definitionExceptionId the definition exception ID
   */
  public void setDefinitionExceptionId(final String definitionExceptionId) {

    this.definitionExceptionId = definitionExceptionId;
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
  public void setChildren(final List<Concept> children) {

    this.children = children;
  }

  /**
   * Gets the parent refs.
   *
   * @return the parent refs
   */
  public List<ConceptRef> getParentRefs() {

    if (parentRefs == null) {
      parentRefs = new ArrayList<>();
    }

    return parentRefs;
  }

  /**
   * Sets the parent refs.
   *
   * @param parentRefs the parent refs to set
   */
  public void setParentRefs(final List<ConceptRef> parentRefs) {

    this.parentRefs = parentRefs;
  }

  /**
   * Gets the child refs.
   *
   * @return the child refs
   */
  public List<ConceptRef> getChildRefs() {

    if (childRefs == null) {
      childRefs = new ArrayList<>();
    }

    return childRefs;
  }

  /**
   * Sets the child refs.
   *
   * @param childRefs the child refs to set
   */
  public void setChildRefs(final List<ConceptRef> childRefs) {

    this.childRefs = childRefs;
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
  public void setHasDescendantRefsetMembers(final boolean hasDescendantRefsetMembers) {

    this.hasDescendantRefsetMembers = hasDescendantRefsetMembers;
  }

  /**
   * Returns the role groups.
   *
   * @return the role groups
   */
  public Map<Integer, List<String>> getRoleGroups() {

    return roleGroups;
  }

  /**
   * Sets the role groups.
   *
   * @param map the map
   */
  public void setRoleGroups(final Map<Integer, List<String>> map) {

    this.roleGroups = map;
  }

  /**
   * Indicates whether or not defined is the case.
   *
   * @return the isDefined
   */
  public boolean isDefined() {

    return defined;
  }

  /**
   * Sets the defined.
   *
   * @param isDefined the isDefined to set
   */
  public void setDefined(final boolean isDefined) {

    this.defined = isDefined;
  }

  /**
   * Indicates whether or not released is the case.
   *
   * @return has the concept been released
   */
  public boolean isReleased() {

    return released;
  }

  /**
   * Sets the released.
   *
   * @param released the value to set the released flag to
   */
  public void setReleased(final boolean released) {

    this.released = released;
  }

  /**
   * Hash code.
   *
   * @return the int
   */
  /* see superclass */ @Override
  public int hashCode() {

    final int prime = 31;
    int result = 1;
    result = prime * result + ((childRefs == null) ? 0 : childRefs.hashCode());
    result = prime * result + ((children == null) ? 0 : children.hashCode());
    result = prime * result + ((code == null) ? 0 : code.hashCode());
    result = prime * result + ((memberId == null) ? 0 : memberId.hashCode());
    result = prime * result + (defined ? 1231 : 1237);
    result =
        prime * result + ((definitionExceptionId == null) ? 0 : definitionExceptionId.hashCode());
    result = prime * result
        + ((definitionExceptionType == null) ? 0 : definitionExceptionType.hashCode());
    result = prime * result + ((descriptions == null) ? 0 : descriptions.hashCode());
    result = prime * result + (feedbackVisible ? 1231 : 1237);
    result = prime * result + ((fsn == null) ? 0 : fsn.hashCode());
    result = prime * result + (hasAncestorRefsetMembers ? 1231 : 1237);
    result = prime * result + (hasChildren ? 1231 : 1237);
    result = prime * result + (hasDescendantRefsetMembers ? 1231 : 1237);
    result = prime * result + (historyVisible ? 1231 : 1237);
    result = prime * result + ((memberEffectiveTime == null) ? 0 : memberEffectiveTime.hashCode());
    result = prime * result + (memberOfRefset ? 1231 : 1237);
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((parents == null) ? 0 : parents.hashCode());
    result = prime * result + ((parentRefs == null) ? 0 : parentRefs.hashCode());
    result = prime * result + (released ? 1231 : 1237);
    result = prime * result + ((roleGroups == null) ? 0 : roleGroups.hashCode());
    result = prime * result + ((terminology == null) ? 0 : terminology.hashCode());
    result = prime * result + ((version == null) ? 0 : version.hashCode());
    result = prime * result + openDiscussionCount;
    result = prime * result + resolvedDiscussionCount;
    result =
        prime * result + ((super.getModified() == null) ? 0 : super.getModifiedBy().hashCode());
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

    if (getClass() != obj.getClass()) {
      return false;
    }

    final Concept other = (Concept) obj;

    if (childRefs == null) {
      if (other.childRefs != null) {
        return false;
      }

    } else if (!childRefs.equals(other.childRefs)) {
      return false;
    }

    if (children == null) {
      if (other.children != null) {
        return false;
      }

    } else if (!children.equals(other.children)) {
      return false;
    }

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

    if (defined != other.defined) {
      return false;
    }

    if (definitionExceptionId == null) {
      if (other.definitionExceptionId != null) {
        return false;
      }

    } else if (!definitionExceptionId.equals(other.definitionExceptionId)) {
      return false;
    }

    if (definitionExceptionType == null) {
      if (other.definitionExceptionType != null) {
        return false;
      }

    } else if (!definitionExceptionType.equals(other.definitionExceptionType)) {
      return false;
    }

    if (descriptions == null) {
      if (other.descriptions != null) {
        return false;
      }

    } else if (!descriptions.equals(other.descriptions)) {
      return false;
    }

    if (feedbackVisible != other.feedbackVisible) {
      return false;
    }

    if (fsn == null) {
      if (other.fsn != null) {
        return false;
      }

    } else if (!fsn.equals(other.fsn)) {
      return false;
    }

    if (hasAncestorRefsetMembers != other.hasAncestorRefsetMembers) {
      return false;
    }

    if (hasChildren != other.hasChildren) {
      return false;
    }

    if (hasDescendantRefsetMembers != other.hasDescendantRefsetMembers) {
      return false;
    }

    if (historyVisible != other.historyVisible) {
      return false;
    }

    if (memberEffectiveTime == null) {
      if (other.memberEffectiveTime != null) {
        return false;
      }

    } else if (!memberEffectiveTime.equals(other.memberEffectiveTime)) {
      return false;
    }

    if (memberOfRefset != other.memberOfRefset) {
      return false;
    }

    if (name == null) {
      if (other.name != null) {
        return false;
      }

    } else if (!name.equals(other.name)) {
      return false;
    }

    if (parentRefs == null) {
      if (other.parentRefs != null) {
        return false;
      }

    } else if (!parentRefs.equals(other.parentRefs)) {
      return false;
    }

    if (parents == null) {
      if (other.parents != null) {
        return false;
      }

    } else if (!parents.equals(other.parents)) {
      return false;
    }

    if (released != other.released) {
      return false;
    }

    if (roleGroups == null) {
      if (other.roleGroups != null) {
        return false;
      }

    } else if (!roleGroups.equals(other.roleGroups)) {
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

    if (other.openDiscussionCount != openDiscussionCount) {
      return false;
    }

    if (other.resolvedDiscussionCount != resolvedDiscussionCount) {
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
    // n/a

  }
}
