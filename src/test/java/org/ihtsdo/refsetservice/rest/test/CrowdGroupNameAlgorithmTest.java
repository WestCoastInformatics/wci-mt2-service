/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.junit.jupiter.api.Test;

/**
 * The Class CrowdGroupNameAlgorithmTest.
 */
public class CrowdGroupNameAlgorithmTest {

    /**
     * Test generate name.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGenerateName() throws Exception {

        String result = "";
        result = CrowdGroupNameAlgorithm.generateCrowdGroupName(CrowdGroupNameAlgorithm.getCrowdIdFromOrganizationName("WCI Edition"), "SNOMEDCT-WCI",
            "WCI Testing Project", "author", false);
        assertThat(result).isEqualTo("rt2-wciedition-snomedctwci-wcitestingproject-author");

        result = CrowdGroupNameAlgorithm.generateCrowdGroupName(CrowdGroupNameAlgorithm.getCrowdIdFromOrganizationName("Netherlands Extension"), "SNOMEDCT-NL",
            "WCI Testing Project", "admin", false);
        assertThat(result).isEqualTo("rt2-netherlandsextension-snomedctnl-wcitestingproject-admin");

        result = CrowdGroupNameAlgorithm.generateCrowdGroupName(CrowdGroupNameAlgorithm.getCrowdIdFromOrganizationName("Belgian Extension"), "SNOMEDCT-BE",
            "Belgian Edition Upgrade dedicated UAT Training Project", "reviewer", false);
        assertThat(result).isEqualTo("rt2-belgianextension-snomedctbe-belgianeditionupgradededicateduattrainingproject-reviewer");

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.generateCrowdGroupName(CrowdGroupNameAlgorithm.getCrowdIdFromOrganizationName(" "), " ", " ", " ", false);
        });

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.generateCrowdGroupName(CrowdGroupNameAlgorithm.getCrowdIdFromOrganizationName(" "), " ", " b ", " ", false);
        });

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.generateCrowdGroupName(CrowdGroupNameAlgorithm.getCrowdIdFromOrganizationName(" "), " a ", "  ", " ", false);
        });

    }

    /**
     * Test organization.
     *
     * @throws Exception the exception
     */
    @Test
    public void testOrganization() throws Exception {

        String result = "";

        result = CrowdGroupNameAlgorithm.getEditionString("WCI Edition");
        assertThat(result).isEqualTo("wciedition");

        result = CrowdGroupNameAlgorithm.getEditionString("Netherlands Extension");
        assertThat(result).isEqualTo("netherlandsextension");

        result = CrowdGroupNameAlgorithm.getEditionString("United States Edition");
        assertThat(result).isEqualTo("unitedstatesedition");

        result = CrowdGroupNameAlgorithm.getEditionString("Affiliate Test 1");
        assertThat(result).isEqualTo("affiliate");

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.getEditionString("");
        });

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.getEditionString(null);
        });

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.getEditionString("    ");
        });

    }

    /**
     * Test edition.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEdition() throws Exception {

        String result = "";

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT");
        assertThat(result).isEqualTo("snomedct");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-AM");
        assertThat(result).isEqualTo("snomedctam");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-AT");
        assertThat(result).isEqualTo("snomedctat");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-BE");
        assertThat(result).isEqualTo("snomedctbe");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-BEUPD");
        assertThat(result).isEqualTo("snomedctbeupd");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-CDE");
        assertThat(result).isEqualTo("snomedctcde");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-CH");
        assertThat(result).isEqualTo("snomedctch");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-COMDE");
        assertThat(result).isEqualTo("snomedctcomde");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-DK");
        assertThat(result).isEqualTo("snomedctdk");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-EE");
        assertThat(result).isEqualTo("snomedctee");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-ES");
        assertThat(result).isEqualTo("snomedctes");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-IE");
        assertThat(result).isEqualTo("snomedctie");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-KR");
        assertThat(result).isEqualTo("snomedctkr");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-NO");
        assertThat(result).isEqualTo("snomedctno");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-NZ");
        assertThat(result).isEqualTo("snomedctnz");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-SE");
        assertThat(result).isEqualTo("snomedctse");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-TM");
        assertThat(result).isEqualTo("snomedcttm");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-US");
        assertThat(result).isEqualTo("snomedctus");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-VN");
        assertThat(result).isEqualTo("snomedctvn");

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-WCI");
        assertThat(result).isEqualTo("snomedctwci");

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.getEditionString("");
        });

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.getEditionString(null);
        });

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.getEditionString("    ");
        });

    }

    /**
     * Test project.
     *
     * @throws Exception the exception
     */
    @Test
    public void testProject() throws Exception {

        String result = "";

        result = CrowdGroupNameAlgorithm.getProjectString("WCI Testing Project");
        assertThat(result).isEqualTo("wcitestingproject");

        result = CrowdGroupNameAlgorithm.getProjectString(" Belgian Edition Upgrade dedicated - # ^ $ UAT Training Project ");
        assertThat(result).isEqualTo("belgianeditionupgradededicateduattrainingproject");

        result = CrowdGroupNameAlgorithm.getProjectString(" dedicated UAT Training Project");
        assertThat(result).isEqualTo("dedicateduattrainingproject");

        result = CrowdGroupNameAlgorithm.getProjectString("a 7 a");
        assertThat(result).isEqualTo("a7a");

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.getProjectString("");
        });

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.getProjectString(null);
        });

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.getProjectString("    ");
        });

    }
}
