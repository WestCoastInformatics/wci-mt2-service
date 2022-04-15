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

/**
 * The Class CrowdGroup.
 */
public class CrowdGroup {

    /**  The application. */
    private String application;

    /**  The organization. */
    private String organization;

    /**  The project. */
    private String project;
    
    /**  The project. */
    private String projectDescription;

    /**  The crowd group name. */
    private String crowdGroupName;

    /**
     * Instantiates an empty {@link CrowdGroup}.
     */
    public CrowdGroup() {

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
     * Sets the application.
     *
     * @param application the application to set
     */
    public void setApplication(String application) {

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
    public void setOrganization(String organization) {

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
    public void setProject(String project) {

        this.project = project;
    }

    /**
     * Returns the team.
     *
     * @return the team
     */
    public String getProjectDescription() {

        return projectDescription;
    }

    /**
     * Sets the team.
     *
     * @param projectDescription the project description
     */
    public void setProjectDescription(String projectDescription) {

        this.projectDescription = projectDescription;
    }

    /**
     * Returns the crowd group name.
     *
     * @return the crowdGroupName
     */
    public String getCrowdGroupName() {

        return crowdGroupName;
    }

    /**
     * Sets the crowd group name.
     *
     * @param crowdGroupName the crowdGroupName to set
     */
    public void setCrowdGroupName(String crowdGroupName) {

        this.crowdGroupName = crowdGroupName;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((application == null) ? 0 : application.hashCode());
        result = prime * result + ((crowdGroupName == null) ? 0 : crowdGroupName.hashCode());
        result = prime * result + ((organization == null) ? 0 : organization.hashCode());
        result = prime * result + ((project == null) ? 0 : project.hashCode());
        result = prime * result + ((projectDescription == null) ? 0 : projectDescription.hashCode());
        return result;
    }

    /* see superclass */
    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        CrowdGroup other = (CrowdGroup) obj;
        if (application == null) {
            if (other.application != null) {
                return false;
            }
        } else if (!application.equals(other.application)) {
            return false;
        }
        if (crowdGroupName == null) {
            if (other.crowdGroupName != null) {
                return false;
            }
        } else if (!crowdGroupName.equals(other.crowdGroupName)) {
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
        if (projectDescription == null) {
            if (other.projectDescription != null) {
                return false;
            }
        } else if (!projectDescription.equals(other.projectDescription)) {
            return false;
        }
        return true;
    }

}
