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

import java.util.HashSet;
import java.util.Set;

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
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a user and roles.
 * 
 */
@Entity
@Table(name = "users")
@Schema(description = "Represents an application user.")
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class User extends AbstractHasModified
    implements Comparable<User>, Copyable<User>, ValidateCrud<User> {

  /** The username. */
  @Column(nullable = false, unique = true, length = 250)
  private String userName;

  /** The user's full name. */
  @Column(nullable = false, length = 250)
  private String name;

  /** The user's email. */
  @Column(nullable = false, length = 250)
  private String email;

  /** The user's title. */
  @Column(nullable = true, length = 250)
  private String title;

  /** The user's title. */
  @Column(nullable = true, length = 250)
  private String company;

  /** The auth token. */
  @Transient
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private String authToken;

  /** A list of the roles this user has. */
  @Transient
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Set<String> roles = new HashSet<>();

  /** The icon uri. */
  @Column(nullable = true, length = 255)
  private String iconUri;

  /** The teams. */
  @Transient
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Set<Team> teams = new HashSet<>();

  /** The admin role. */
  @Transient
  public static final String ROLE_ADMIN = "ADMIN";

  /** The lead role. */
  @Transient
  public static final String ROLE_LEAD = "LEAD";

  /** The reviewer role. */
  @Transient
  public static final String ROLE_REVIEWER = "REVIEWER";

  /** The author role. */
  @Transient
  public static final String ROLE_AUTHOR = "AUTHOR";

  /** The user role. */
  @Transient
  public static final String ROLE_USER = "USER";

  /** The user role. */
  @Transient
  public static final String ROLE_VIEWER = "VIEWER";

  /**
   * Instantiates an empty {@link User}.
   */
  public User() {

    // n/a
  }

  /**
   * Instantiates a {@link User} from the specified parameters.
   *
   * @param userName the username
   * @param name the user's full name
   * @param email the user's email
   * @param title the title
   * @param company the company
   * @param roles the roles this user has
   */
  public User(final String userName, final String name, final String email, final String title,
      final String company, final Set<String> roles) {

    this.userName = userName;
    this.name = name;
    this.email = email;
    this.title = title;
    this.company = company;
    this.roles = roles;
  }

  /**
   * Instantiates a {@link User} from the specified parameters.
   *
   * @param other the other
   */
  public User(final User other) {

    populateFrom(other);
  }

  /**
   * Populate from.
   *
   * @param other the other
   */
  @Override
  public void populateFrom(final User other) {

    super.populateFrom(other);
    userName = other.getUserName();
    name = other.getName();
    email = other.getEmail();
    title = other.getTitle();
    company = other.getCompany();
    roles = new HashSet<String>(other.getRoles());
    authToken = other.getAuthToken();
    iconUri = other.iconUri;
    teams = other.teams;
  }

  /**
   * Populate from.
   *
   * @param other the other
   */
  @Override
  public void patchFrom(final User other) {

    // super.populateFrom(other);
    userName = other.getUserName();
    name = other.getName();
    email = other.getEmail();
    title = other.getTitle();
    company = other.getCompany();
    roles = new HashSet<String>(other.getRoles());
    authToken = other.getAuthToken();
    iconUri = other.iconUri;
    teams = other.teams;
  }

  /**
   * Returns the userName.
   *
   * @return the userName
   */
  @FullTextField(analyzer = "standard")
  @GenericField(name = "nameSort", searchable = Searchable.YES, projectable = Projectable.NO,
      sortable = Sortable.YES)
  public String getUserName() {

    return userName;
  }

  /**
   * Sets the userName.
   *
   * @param userName the userName
   */
  public void setUserName(final String userName) {

    this.userName = userName;
  }

  /**
   * Returns the full name.
   *
   * @return the name
   */
  public String getName() {

    return name;
  }

  /**
   * Sets the full name.
   *
   * @param name the name
   */
  public void setName(final String name) {

    this.name = name;
  }

  /**
   * Returns the email.
   *
   * @return the email
   */
  @FullTextField(analyzer = "standard")
  @GenericField(name = "emailSort", searchable = Searchable.YES, projectable = Projectable.NO,
      sortable = Sortable.YES)
  public String getEmail() {

    return email;
  }

  /**
   * Sets the email.
   *
   * @param email the email
   */
  public void setEmail(final String email) {

    this.email = email;
  }

  /**
   * Returns the title.
   *
   * @return the title
   */
  public String getTitle() {

    return title;
  }

  /**
   * Sets the title.
   *
   * @param title the title
   */
  public void setTitle(final String title) {

    this.title = title;
  }

  /**
   * Sets the authentication token.
   *
   * @return the auth token
   */
  public String getAuthToken() {

    return authToken;
  }

  /**
   * Returns the authentication token.
   *
   * @param authToken the auth token
   */
  public void setAuthToken(final String authToken) {

    this.authToken = authToken;
  }

  /**
   * Gets the roles.
   *
   * @return the roles
   */
  public Set<String> getRoles() {

    if (roles == null) {

      roles = new HashSet<>();
    }

    return roles;
  }

  /**
   * Sets the roles.
   *
   * @param roles the roles to set
   */
  public void setRoles(final Set<String> roles) {

    this.roles = roles;
  }

  /**
   * Gets the company.
   *
   * @return the company
   */
  public String getCompany() {

    return company;
  }

  /**
   * Sets the company.
   *
   * @param company the company
   */
  public void setCompany(final String company) {

    this.company = company;
  }

  /**
   * Returns the icon URI.
   *
   * @return the icon URI
   */
  public String getIconUri() {

    return iconUri;
  }

  /**
   * Sets the icon URI.
   *
   * @param iconUri the icon uri
   */
  public void setIconUri(final String iconUri) {

    this.iconUri = iconUri;
  }

  /**
   * Returns the teams.
   *
   * @return the teams
   */
  @JsonGetter()
  public Set<Team> getTeams() {

    if (teams == null) {

      teams = new HashSet<>();
    }

    return teams;
  }

  /**
   * Sets the teams.
   *
   * @param teams the teams
   */
  public void setTeams(final Set<Team> teams) {

    this.teams = teams;
  }

  /**
   * Check if the user has the specified role on the project.
   *
   * @param roleToCheck the role to look for
   * @param project the project to check permissions against
   * @return if the user has the specified role on the refset
   * @throws Exception the exception
   */
  public boolean doesUserHavePermission(final String roleToCheck, final Project project)
    throws Exception {

    final String organizationName = project.getEdition().getOrganizationName();
    final String editionName = project.getEdition().getShortName();

    return checkPermission(roleToCheck, organizationName, editionName, project.getCrowdProjectId());
  }

  /**
   * Check if the user has the specified role on the organization or project.
   *
   * @param roleToCheck the role to look for
   * @param organizationName the organization name
   * @param editionName the edition short name to check permissions against
   * @param projectCrowdId the crowd ID of the project to check permissions
   *          against or null for org level permission
   * @return if the user has the specified role on the refset
   * @throws Exception the exception
   */
  public boolean checkPermission(final String roleToCheck, final String organizationName,
    final String editionName, final String projectCrowdId) throws Exception {

    try {

      String organizationCrowdName = null;
      String editionShortName = null;

      if (organizationName == null) {
        organizationCrowdName = "all";
      } else {
        organizationCrowdName = CrowdGroupNameAlgorithm.getOrganizationString(organizationName);
      }

      if (editionName != null) {
        editionShortName = CrowdGroupNameAlgorithm.getEditionString(editionName);
      }

      final String lowerCasedRoleToCheck = roleToCheck.toLowerCase();

      for (final String role : roles) {

        final String lowerCasedRole = role.toLowerCase();
        final int indexFirstHyphen = lowerCasedRole.indexOf("-");

        final String organizationPart = lowerCasedRole.substring(0, indexFirstHyphen);
        // LOG.debug("doesUserHavePermission organizationName: " +
        // organizationCrowdName + " ; organization part of role: " +
        // organizationPart);

        // first check the organization permissions
        if (organizationPart.equals("all") || organizationPart.equals(organizationCrowdName)) {

          final int indexSecondHyphen = lowerCasedRole.indexOf("-", indexFirstHyphen + 1);
          final String editionPart =
              lowerCasedRole.substring(indexFirstHyphen + 1, indexSecondHyphen);
          final String projectPart = lowerCasedRole.substring(indexSecondHyphen + 1,
              lowerCasedRole.indexOf("-", indexSecondHyphen + 1));
          // LOG.debug("doesUserHavePermission editionName: " + editionShortName
          // + " ; edition part of role: " + editionPart);
          // LOG.debug("doesUserHavePermission projectName: " + projectName + "
          // ; project part of role: " + projectPart);

          // then check the edition permissions against 1: all access, 2: org
          // level admin, 3: org level viewer, 4: edition level name
          if (editionPart.equals("all")
              || (editionShortName == null && projectPart.equals("all")
                  && roleToCheck.equals(ROLE_ADMIN))
              || (editionShortName == null && roleToCheck.equals(ROLE_VIEWER))
              || editionPart.equals(editionShortName)) {

            // then check the project level permissions against 1: all access,
            // 2: org level viewer, 3: project level project name
            if (projectPart.equals("all")
                || (projectCrowdId == null && roleToCheck.equals(ROLE_VIEWER))
                || (projectCrowdId != null && projectPart.equals(projectCrowdId))) {

              // LOG.debug("doesUserHavePermission lowerCasedRole: " +
              // lowerCasedRole + " ; lowerCasedRoleToCheck: " +
              // lowerCasedRoleToCheck);

              // last check for the role or if they have any permission at this
              // level they have the VIEWER role
              if (lowerCasedRole.endsWith("-all")
                  || lowerCasedRole.endsWith("-" + lowerCasedRoleToCheck)
                  || roleToCheck.equals(ROLE_VIEWER)) {

                // LOG.debug("doesUserHavePermission = true");
                return true;
              }
            }
          }
        }
      }

    } catch (final Exception e) {
      return false;
    }
    return false;
  }

  /* see superclass */
  @Override
  public int hashCode() {

    final int prime = 31;
    int result = 1;
    result = prime * result + ((authToken == null) ? 0 : authToken.hashCode());
    result = prime * result + ((company == null) ? 0 : company.hashCode());
    result = prime * result + ((email == null) ? 0 : email.hashCode());
    result = prime * result + ((iconUri == null) ? 0 : iconUri.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((roles == null) ? 0 : roles.hashCode());
    result = prime * result + ((teams == null) ? 0 : teams.hashCode());
    result = prime * result + ((title == null) ? 0 : title.hashCode());
    result = prime * result + ((userName == null) ? 0 : userName.hashCode());
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
    if (!(obj instanceof User)) {
      return false;
    }
    User other = (User) obj;
    if (authToken == null) {
      if (other.authToken != null) {
        return false;
      }
    } else if (!authToken.equals(other.authToken)) {
      return false;
    }
    if (company == null) {
      if (other.company != null) {
        return false;
      }
    } else if (!company.equals(other.company)) {
      return false;
    }
    if (email == null) {
      if (other.email != null) {
        return false;
      }
    } else if (!email.equals(other.email)) {
      return false;
    }
    if (iconUri == null) {
      if (other.iconUri != null) {
        return false;
      }
    } else if (!iconUri.equals(other.iconUri)) {
      return false;
    }
    if (name == null) {
      if (other.name != null) {
        return false;
      }
    } else if (!name.equals(other.name)) {
      return false;
    }
    if (roles == null) {
      if (other.roles != null) {
        return false;
      }
    } else if (!roles.equals(other.roles)) {
      return false;
    }
    if (teams == null) {
      if (other.teams != null) {
        return false;
      }
    } else if (!teams.equals(other.teams)) {
      return false;
    }
    if (title == null) {
      if (other.title != null) {
        return false;
      }
    } else if (!title.equals(other.title)) {
      return false;
    }
    if (userName == null) {
      if (other.userName != null) {
        return false;
      }
    } else if (!userName.equals(other.userName)) {
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

  /**
   * Compare to.
   *
   * @param o the o
   * @return the int
   */
  /* see superclass */
  @Override
  public int compareTo(final User o) {

    // Handle null
    return (name + roles.toString()).compareToIgnoreCase(o.getName() + o.getRoles().toString());
  }

  /**
   * Lazy init.
   */
  @Override
  public void lazyInit() {

    // n/a

  }

  /* see superclass */
  @Override
  public void validateAdd() throws Exception {

    // n/a

  }

  /* see superclass */
  @Override
  public void validateUpdate(final User other) throws Exception {

    // n/a

  }

  /* see superclass */
  @Override
  public void validateDelete() throws Exception {

    // n/a

  }
}
