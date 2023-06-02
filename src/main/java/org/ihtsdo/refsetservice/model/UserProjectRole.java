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

import org.apache.commons.lang3.StringUtils;

/**
 * The User Project Role.
 */
public class UserProjectRole {

    /** The application. */
    private String application;

    /** The organization. */
    private String organization;

    /** The project. */
    private String project;

    /** The role. */
    private String role;

    /**
     * Instantiates an empty {@link UserProjectRole}.
     */
    public UserProjectRole() {

    }

    /**
     * Returns the application.
     *
     * @return the application
     */
    public String getApplication() {

        return application;
    }

    /**
     * Instantiates a {@link UserProjectRole} from the specified parameters.
     *
     * @param application the application
     * @param organization the organization
     * @param project the project
     * @param role the role
     */
    public UserProjectRole(final String application, final String organization, final String project, final String role) {

        super();
        this.application = application;
        this.organization = organization;
        this.project = project;
        this.role = role;
    }

    /**
     * Instantiates a {@link UserProjectRole} from the specified parameters.
     *
     * @param crowdGroupString the crowd group string
     * @throws Exception the exception
     */
    public UserProjectRole(final String crowdGroupString) throws Exception {

        super();

        if (StringUtils.isBlank(crowdGroupString)) {
            throw new Exception("Crowd group name cannot be null or empty");
        }

        final String[] projectsRole = crowdGroupString.split("-");

        if (projectsRole.length < 2) {
            throw new Exception("Crowd group name does not have enough parts");
        }

        // should have 3 -, if only 2 rt2 was removed.
        final boolean hasApplication = ("rt2".equalsIgnoreCase(projectsRole[0]));

        if (!hasApplication) {
            this.organization = projectsRole[0];
            this.project = projectsRole[1];
            this.role = projectsRole[1];
        } else {
            this.organization = projectsRole[1];
            this.project = projectsRole[2];
            this.role = projectsRole[3];
        }

    }

    /**
     * Sets the application.
     *
     * @param application the application to set
     */
    public void setApplication(final String application) {

        this.application = application;
    }

    /**
     * Returns the organization.
     *
     * @return the organization
     */
    public String getOrganization() {

        return organization;
    }

    /**
     * Sets the organization.
     *
     * @param organization the organization to set
     */
    public void setOrganization(final String organization) {

        this.organization = organization;
    }

    /**
     * Returns the project.
     *
     * @return the project
     */
    public String getProject() {

        return project;
    }

    /**
     * Sets the project.
     *
     * @param project the project to set
     */
    public void setProject(final String project) {

        this.project = project;
    }

    /**
     * Returns the role.
     *
     * @return the role
     */
    public String getRole() {

        return role;
    }

    /**
     * Sets the role.
     *
     * @param role the role to set
     */
    public void setRole(final String role) {

        this.role = role;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((application == null) ? 0 : application.hashCode());
        result = prime * result + ((organization == null) ? 0 : organization.hashCode());
        result = prime * result + ((project == null) ? 0 : project.hashCode());
        result = prime * result + ((role == null) ? 0 : role.hashCode());
        return result;
    }

    /* see superclass */
    @Override
    public boolean equals(final Object obj) {

        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof UserProjectRole)) {
            return false;
        }
        final UserProjectRole other = (UserProjectRole) obj;
        if (application == null) {
            if (other.application != null) {
                return false;
            }
        } else if (!application.equals(other.application)) {
            return false;
        }
        if (organization == null) {
            if (other.organization != null) {
                return false;
            }
        } else if (!organization.equals(other.organization)) {
            return false;
        }
        if (project == null) {
            if (other.project != null) {
                return false;
            }
        } else if (!project.equals(other.project)) {
            return false;
        }
        if (role == null) {
            if (other.role != null) {
                return false;
            }
        } else if (!role.equals(other.role)) {
            return false;
        }
        return true;
    }

    /* see superclass */
    @Override
    public String toString() {

        return "UserProjectRole [application=" + application + ", organization=" + organization + ", project=" + project + ", role=" + role + "]";
    }

}
