/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.util;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

/**
 * The Class CrowdGroupNameGenerator.
 */
public class CrowdGroupNameAlgorithm {

    /**
     * Generate crowd group name.
     *
     * @param organizationName the organization name
     * @param projectName the project name
     * @param role the role
     * @return the string
     * @throws Exception the exception
     */
    public static String generateCrowdGroupName(final String organizationName, final String projectName, final String role) throws Exception {

        return generateCrowdGroupName(organizationName, projectName, role, false);
    }

    /**
     * Generate crowd group name.
     *
     * @param editionName the edition name
     * @param projectName the project name
     * @param role the role
     * @param adminRole the admin role
     * @return the string
     * @throws Exception the exception
     */
    public static String generateCrowdGroupName(final String editionName, final String projectName, final String role, final boolean adminRole) throws Exception {

        if (StringUtils.isAnyBlank(editionName, projectName, role)) {
            throw new Exception("Parameters cannot be empty or null");
        }

        final StringBuilder groupName = new StringBuilder();
        groupName.append("rt2-");
        groupName.append(getEditionString(editionName)).append("-");

        if (!adminRole) {
            groupName.append(getProjectString(projectName)).append("-");
        } else {
            groupName.append(projectName).append("-");
        }

        groupName.append(role.toLowerCase());

        return groupName.toString();
    }
    
    /**
     * Builds the crowd group name.
     *
     * @param editionName the edition name
     * @param crowdProjectId the crowd project id
     * @param role the role
     * @return the string
     * @throws Exception the exception
     */
    public static String buildCrowdGroupName(final String editionName, final String crowdProjectId, final String role) throws Exception {

        if (StringUtils.isAnyBlank(editionName, crowdProjectId, role)) {
            throw new Exception("Parameters cannot be empty or null");
        }

        final StringBuilder groupName = new StringBuilder();
        groupName.append("rt2-");
        groupName.append(getEditionString(editionName)).append("-");
        groupName.append(crowdProjectId).append("-");
        groupName.append(role.toLowerCase());

        return groupName.toString();
    }

    /**
     * Returns the edition string.
     *
     * @param editionName the edition name
     * @return the organization string
     * @throws Exception the exception
     */
    public static String getEditionString(final String editionName) throws Exception {

        if (StringUtils.isAnyBlank(editionName)) {
            throw new Exception("Edition name cannot be null or empty.");
        }

        return editionName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase().trim();
    }

    /**
     * Returns the project string.
     *
     * @param projectName the project name
     * @return the project string
     * @throws Exception the exception
     */
    public static String getProjectString(final String projectName) throws Exception {

        if (StringUtils.isAnyBlank(projectName)) {
            throw new Exception("Project name cannot be null or empty.");
        }

        final String project = Arrays.stream(projectName.trim().split(" ")).map(s -> s.substring(0, 1)).collect(Collectors.joining()).replaceAll("[^a-zA-Z0-9]", "");

        return project.toLowerCase().trim();
    }

}
