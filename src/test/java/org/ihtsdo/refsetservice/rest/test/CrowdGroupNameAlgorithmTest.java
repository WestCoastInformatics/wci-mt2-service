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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class CrowdGroupNameAlgorithmTest.
 */
public class CrowdGroupNameAlgorithmTest {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(CrowdGroupNameAlgorithmTest.class);

    
    @Test
    public void testGenerateName() throws Exception {

        String result = "";
        result = CrowdGroupNameAlgorithm.generateName("SNOMEDCT-WCI", "WCI Testing Project", "author");
        assertThat(result).isEqualTo("rt2-snomedctwci-wtp-authors");
        
        result = CrowdGroupNameAlgorithm.generateName("SNOMEDCT-AT", "WCI Testing Project", "admin");
        assertThat(result).isEqualTo("rt2-snomedctat-wtp-aaa");
        
        result = CrowdGroupNameAlgorithm.generateName("SNOMEDCT-BE", "Belgian Edition Upgrade dedicated UAT Training Project", "reviewer");
        assertThat(result).isEqualTo("rt2-snomedctbe-beudutp-admin");
        
        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.generateName(" ", " ", " ");
        });
        
        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.generateName(" ", " b ", " ");
        });
        
        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.generateName(" a ", "  ", " ");
        });
        
    }
    
    @Test
    public void testOrganization() throws Exception {

        String result = "";

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT");
        assertThat(result).isEqualTo("snomedct");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-AM");
        assertThat(result).isEqualTo("snomedctam");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-AT");
        assertThat(result).isEqualTo("snomedctat");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-BE");
        assertThat(result).isEqualTo("snomedctbe");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-BEUPD");
        assertThat(result).isEqualTo("snomedctbeupd");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-CDE");
        assertThat(result).isEqualTo("snomedctcde");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-CH");
        assertThat(result).isEqualTo("snomedctch");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-COMDE");
        assertThat(result).isEqualTo("snomedctcomde");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-DK");
        assertThat(result).isEqualTo("snomedctdk");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-EE");
        assertThat(result).isEqualTo("snomedctee");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-ES");
        assertThat(result).isEqualTo("snomedctes");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-IE");
        assertThat(result).isEqualTo("snomedctie");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-KR");
        assertThat(result).isEqualTo("snomedctkr");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-NO");
        assertThat(result).isEqualTo("snomedctno");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-NZ");
        assertThat(result).isEqualTo("snomedctnz");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-SE");
        assertThat(result).isEqualTo("snomedctse");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-TM");
        assertThat(result).isEqualTo("snomedcttm");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-US");
        assertThat(result).isEqualTo("snomedctus");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-VN");
        assertThat(result).isEqualTo("snomedctvn");

        result = CrowdGroupNameAlgorithm.getOrganizationString("SNOMEDCT-WCI");
        assertThat(result).isEqualTo("snomedctwci");

        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.getOrganizationString("");
        });
        
        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.getOrganizationString(null);
        });
        
        assertThrows(Exception.class, () -> {
            CrowdGroupNameAlgorithm.getOrganizationString("    ");
        });

    }

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
