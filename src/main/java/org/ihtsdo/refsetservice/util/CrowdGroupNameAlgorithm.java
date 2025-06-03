/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.util;

import org.apache.commons.lang3.StringUtils;

/**
 * The Class CrowdGroupNameGenerator.
 */
public final class CrowdGroupNameAlgorithm {

    /** The Constant AFFILIATE_STRING. */
    private static final String AFFILIATE_STRING = "affiliate";

    /** The Constant AFFILIATE_ORGANIZATION_ID_DELIMITER. */
    public static final String AFFILIATE_ORGANIZATION_ID_DELIMITER = "_";

    /** The Constant APP_PREFIX. */
    private static final String APP_PREFIX = "rt2-";

    /**
     * Instantiates an empty {@link CrowdGroupNameAlgorithm}.
     */
    private CrowdGroupNameAlgorithm() {

        // n/a
    }

    /**
     * Generate crowd group name.
     *
     * @param organizationCrowdId the organization crowd Id
     * @param editionName the edition name
     * @param projectName the project name
     * @param role the role
     * @param useProjectNameAsIs the should the project name be used as passed in (a crowd ID or "all"), or should it be generated
     * @return the string
     * @throws Exception the exception
     */
    public static String generateCrowdGroupName(final String organizationCrowdId, final String editionName, final String projectName, final String role,
        final boolean useProjectNameAsIs) throws Exception {

        if (StringUtils.isAnyBlank(editionName, projectName, role)) {

            throw new Exception("Parameters cannot be empty or null");
        }

        final StringBuilder groupName = new StringBuilder();
        groupName.append(APP_PREFIX);
        groupName.append(organizationCrowdId).append("-");
        groupName.append(getEditionString(editionName)).append("-");

        if (!useProjectNameAsIs) {

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
     * @param organizationCrowdId the organization crowd Id
     * @param editionName the edition name
     * @param crowdProjectId the crowd project id
     * @param role the role
     * @return the string
     * @throws Exception the exception
     */
    public static String buildCrowdGroupName(final String organizationCrowdId, final String editionName, final String crowdProjectId, final String role)
        throws Exception {

        if (StringUtils.isAnyBlank(organizationCrowdId, editionName, crowdProjectId, role)) {

            throw new Exception("Parameters cannot be empty or null.");
        }

        final StringBuilder groupName = new StringBuilder();
        groupName.append(APP_PREFIX);
        groupName.append(organizationCrowdId).append("-");
        groupName.append(getEditionString(editionName)).append("-");
        groupName.append(crowdProjectId).append("-");
        groupName.append(role.toLowerCase());

        return groupName.toString();
    }

    /**
     * Returns the organization string.
     *
     * @param organizationName the organization name
     * @return the organization string
     * @throws Exception the exception
     */
    public static String getCrowdIdFromOrganizationName(final String organizationName) throws Exception {

        if (StringUtils.isAnyBlank(organizationName)) {

            throw new Exception("Organization name cannot be null or empty.");
        }

        return organizationName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase().trim();
    }

    /**
     * Returns the edition string.
     *
     * @param editionShortName the edition short name
     * @return the organization string
     * @throws Exception the exception
     */
    public static String getEditionString(final String editionShortName) throws Exception {

        if (StringUtils.isAnyBlank(editionShortName)) {

            throw new Exception("Edition name cannot be null or empty.");
        }

        if (isAffiliateEdition(editionShortName)) {

            // Ensure affiliate short name's Org ID portion is removed before returning the editionName
            return editionShortName.substring(0, editionShortName.toLowerCase().indexOf(AFFILIATE_STRING) + AFFILIATE_STRING.length())
                .replaceAll("[^a-zA-Z0-9]", "").toLowerCase().trim();
        }

        return editionShortName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase().trim();
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

        return projectName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase().trim();
    }

    /**
     * Indicates whether or not affiliate edition is the case.
     *
     * @param editionName the edition name
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isAffiliateEdition(final String editionName) {

        return editionName.toLowerCase().contains(AFFILIATE_STRING);
    }

    /**
     * Generate affiliate short name.
     *
     * @param shortName the short name
     * @param organizationId the organization id
     * @return the string
     */
    public static String generateAffiliateShortName(String shortName, String organizationId) {

        return shortName + AFFILIATE_ORGANIZATION_ID_DELIMITER + organizationId;
    }
}
