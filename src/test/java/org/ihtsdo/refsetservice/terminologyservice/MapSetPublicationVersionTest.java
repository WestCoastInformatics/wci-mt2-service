/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the source version stamped onto a map set when publication completes.
 */
public class MapSetPublicationVersionTest {

    @Test
    public void extensionUsesPublicationDateForBaseContentAndSourceBranch() {

        final MapSet mapSet = publishedMapSet("SNOMEDCT-NO", "Norwegian Directorate of Health", "MAIN/SNOMEDCT-NO");
        mapSet.setBaseContentVersion("2025-12-01 SNOMEDCT-NO");
        mapSet.setFromBranchPath("MAIN/SNOMEDCT-NO/2025-12-15");
        mapSet.setInternationalContentVersion("2025-12-01 SNOMED CT core");

        MapSetWorkflowService.applyPublishedSourceVersion(mapSet, "2026-01-15");

        assertEquals("2026-01-15 SNOMEDCT-NO", mapSet.getBaseContentVersion());
        assertEquals("MAIN/SNOMEDCT-NO/2026-01-15", mapSet.getFromBranchPath());
    }

    @Test
    public void derivativeKeepsInternationalDependencyDate() {

        final MapSet mapSet = publishedMapSet("SNOMEDCT", "SNOMED International", "MAIN");
        mapSet.setBaseContentVersion("2025-12-15 SNOMEDCT");
        mapSet.setFromBranchPath("MAIN/2025-12-15");
        mapSet.setInternationalContentVersion("2026-01-15 SNOMED CT core");

        MapSetWorkflowService.applyPublishedSourceVersion(mapSet, "2026-02-01");

        assertEquals("2026-01-15 SNOMEDCT", mapSet.getBaseContentVersion());
        assertEquals("MAIN/2026-02-01", mapSet.getFromBranchPath());
    }

    @Test
    public void projectEditionIsUsedWhenMapProjectHasNone() {

        final MapSet mapSet = new MapSet();
        mapSet.setBaseContentVersion("2025-12-15 SNOMEDCT-NO");
        mapSet.setFromBranchPath("MAIN/SNOMEDCT-NO/2025-12-15");
        mapSet.setProject(projectWithEdition("SNOMEDCT-NO", "Norwegian Directorate of Health", "MAIN/SNOMEDCT-NO"));

        MapSetWorkflowService.applyPublishedSourceVersion(mapSet, "2026-01-15");

        assertEquals("2026-01-15 SNOMEDCT-NO", mapSet.getBaseContentVersion());
        assertEquals("MAIN/SNOMEDCT-NO/2026-01-15", mapSet.getFromBranchPath());
    }

    private static MapSet publishedMapSet(final String shortName, final String organizationName, final String branch) {

        final MapSet mapSet = new MapSet();
        final MapProject mapProject = new MapProject();
        mapProject.setEdition(edition(shortName, organizationName, branch));
        mapSet.setMapProject(mapProject);
        return mapSet;
    }

    private static Project projectWithEdition(final String shortName, final String organizationName, final String branch) {

        final Project project = new Project();
        project.setEdition(edition(shortName, organizationName, branch));
        return project;
    }

    private static Edition edition(final String shortName, final String organizationName, final String branch) {

        final Organization organization = new Organization();
        organization.setName(organizationName);
        final Edition edition = new Edition();
        edition.setShortName(shortName);
        edition.setBranch(branch);
        edition.setOrganization(organization);
        return edition;
    }
}
