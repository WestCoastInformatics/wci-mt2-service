/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
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
        result = CrowdGroupNameAlgorithm.generateCrowdGroupName("WCI Edition", "SNOMEDCT-WCI", "WCI Testing Project", "author", false);
        assertThat(result).isEqualTo("rt2-wciedition-snomedctwci-wtp-author");

        result = CrowdGroupNameAlgorithm.generateCrowdGroupName("Netherlands Extension", "SNOMEDCT-NL", "WCI Testing Project", "admin", false);
        assertThat(result).isEqualTo("rt2-netherlandsextension-snomedctnl-wtp-admin");

        result = CrowdGroupNameAlgorithm.generateCrowdGroupName("Belgian Extension", "SNOMEDCT-BE", "Belgian Edition Upgrade dedicated UAT Training Project",
            "reviewer", false);
        assertThat(result).isEqualTo("rt2-belgianextension-snomedctbe-beudutp-reviewer");

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.generateCrowdGroupName(" ", " ", " ", " ", false);
        });

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.generateCrowdGroupName(" ", " ", " b ", " ", false);
        });

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.generateCrowdGroupName(" ", " a ", "  ", " ", false);
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
        assertThat(result).isEqualTo("affiliatetest1");

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

        result = CrowdGroupNameAlgorithm.getEditionString("SNOMEDCT-US");
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
        assertThat(result).isEqualTo("wtp");

        result = CrowdGroupNameAlgorithm.getProjectString("Vietnam Extension dedicated UAT Training Project");
        assertThat(result).isEqualTo("vedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Swiss Edition dedicated UAT Training Project");
        assertThat(result).isEqualTo("sedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Irish Edition dedicated UAT Training Project");
        assertThat(result).isEqualTo("iedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("SNOMED International Project");
        assertThat(result).isEqualTo("sip");

        result = CrowdGroupNameAlgorithm.getProjectString("Spanish Edition dedicated UAT Training Project");
        assertThat(result).isEqualTo("sedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Danish Edition dedicated UAT Training Project");
        assertThat(result).isEqualTo("dedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("New Zealand Refset Project");
        assertThat(result).isEqualTo("nzrp");

        result = CrowdGroupNameAlgorithm.getProjectString("Estonian NRC Project");
        assertThat(result).isEqualTo("enp");

        result = CrowdGroupNameAlgorithm.getProjectString("Belgian Edition Upgrade dedicated UAT Training Project");
        assertThat(result).isEqualTo("beudutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Estonian Edition dedicated UAT Training Project");
        assertThat(result).isEqualTo("eedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("International Edition dedicated UAT Training Project");
        assertThat(result).isEqualTo("iedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Korean Extension dedicated UAT Training Project");
        assertThat(result).isEqualTo("kedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Traditional Medicine dedicated UAT Training Project");
        assertThat(result).isEqualTo("tmdutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Austrian Extension dedicated UAT Training Project");
        assertThat(result).isEqualTo("aedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Norwegian Edition dedicated UAT Training Project");
        assertThat(result).isEqualTo("nedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Belgian Extension dedicated UAT Training Project");
        assertThat(result).isEqualTo("bedutp");

        result = CrowdGroupNameAlgorithm.getProjectString(" dedicated UAT Training Project");
        assertThat(result).isEqualTo("dutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Common German Translation User Group dedicated UAT Training Project");
        assertThat(result).isEqualTo("cgtugdutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Swedish Edition dedicated UAT Training Project");
        assertThat(result).isEqualTo("sedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Belgian Extension Project");
        assertThat(result).isEqualTo("bep");

        result = CrowdGroupNameAlgorithm.getProjectString("SNOMED International WIP Project");
        assertThat(result).isEqualTo("siwp");

        result = CrowdGroupNameAlgorithm.getProjectString("Default project for United States Edition");
        assertThat(result).isEqualTo("dpfuse");

        result = CrowdGroupNameAlgorithm.getProjectString("Inera National Refset Project");
        assertThat(result).isEqualTo("inrp");

        result = CrowdGroupNameAlgorithm.getProjectString("Armenian Extension dedicated UAT Training Project");
        assertThat(result).isEqualTo("aedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Sweden NRC Project");
        assertThat(result).isEqualTo("snp");

        result = CrowdGroupNameAlgorithm.getProjectString("United States Edition dedicated UAT Training Project");
        assertThat(result).isEqualTo("usedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("New Zealand Edition dedicated UAT Training Project");
        assertThat(result).isEqualTo("nzedutp");

        result = CrowdGroupNameAlgorithm.getProjectString("Default project for Belgian Extension");
        assertThat(result).isEqualTo("dpfbe");

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
