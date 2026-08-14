/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest.test;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.refsetservice.helpers.MapUserRole;
import org.ihtsdo.refsetservice.helpers.WorkflowType;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapAdvice;
import org.ihtsdo.refsetservice.model.MapAgeRange;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapRelation;
import org.ihtsdo.refsetservice.model.MapUser;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.MapAdviceController;
import org.ihtsdo.refsetservice.rest.MapProjectController;
import org.ihtsdo.refsetservice.rest.MapRelationController;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.EditionService;
import org.ihtsdo.refsetservice.util.ResultList;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

// TODO: Auto-generated Javadoc
/**
 * The Class MapProjectControllerUnitTest.
 */
@SpringBootTest
@ActiveProfiles("test")
public class MapProjectControllerUnitTest {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapProjectControllerUnitTest.class);

    /**
     * Test create get update.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateGetUpdate() throws Exception {

        final MapProjectController mapProjectController = new MapProjectController();
        final MapAdviceController mapAdivceController = new MapAdviceController();
        final MapRelationController mapRelationController = new MapRelationController();

        final Edition edition = new Edition();
        edition.setName("edition name");
        edition.setShortName("short name");
        edition.setAbbreviation("ab");
        edition.setBranch("branch");
        edition.setMaintainerType("maintainer type");

        final User tempUser = new User("testUser", "testPassword", "testRole", "testOrganization", "testEmail", null);
        final Edition savedEdition = EditionService.createEdition(tempUser, edition);

        final MapAdvice mapAdvice = new MapAdvice();
        mapAdvice.setName("name");
        mapAdvice.setDetail("detail");
        mapAdvice.setAllowableForNullTarget(false);
        mapAdvice.setComputed(false);
        final ResponseEntity<MapAdvice> newMapAdvice = mapAdivceController.createMapAdvice(mapAdvice);
        assertNotNull(newMapAdvice);

        final MapRelation mapRelation = new MapRelation();
        mapRelation.setTerminologyId("term");
        mapRelation.setName("name");
        mapRelation.setAbbreviation("abbreviation");
        mapRelation.setAllowableForNullTarget(false);
        mapRelation.setComputed(false);
        final ResponseEntity<MapRelation> newMapRelation = mapRelationController.createMapRelation(mapRelation);
        assertNotNull(newMapRelation);

        final String suffix = String.valueOf(System.currentTimeMillis());
        final MapUser mapLeadUser = new MapUser();
        mapLeadUser.setUserName("mapleadusername-" + suffix);
        mapLeadUser.setName("map lead name");
        mapLeadUser.setApplicationRole(MapUserRole.LEAD);
        mapLeadUser.setEmail("mapleademail@none.none");
        final MapUser newMapLeadUser = createMapUser(mapLeadUser);

        final MapUser mapSpecialistUser = new MapUser();
        mapSpecialistUser.setUserName("mapspecialistusername-" + suffix);
        mapSpecialistUser.setName("map specialist name");
        mapSpecialistUser.setApplicationRole(MapUserRole.SPECIALIST);
        mapSpecialistUser.setEmail("mapspecialistemail@none.none");
        final MapUser newMapSpecialistUser = createMapUser(mapSpecialistUser);

        final MapProject mapProject = new MapProject();

        mapProject.setName("map project");
        mapProject.setDescription("map project description");
        mapProject.setPrivateProject(false);
        mapProject.setPrimaryContactEmail("dummy@unittest.com");
        mapProject.setSourceTerminology("map project terminology");
        mapProject.setSourceTerminologyVersion("1.0");
        mapProject.setDestinationTerminology("map project destination terminology");
        mapProject.setDestinationTerminologyVersion("1.0");
        mapProject.setEditingCycleBeginDate(new Date());
        mapProject.setLatestPublicationDate(new Date());
        mapProject.setRefSetId("refset id");
        mapProject.setModuleId("module id");
        mapProject.setRefSetName("refset name");
        mapProject.setRuleBased(false);
        mapProject.setTeamBased(false);
        mapProject.setPublished(false);
        mapProject.setUseTags(false);
        mapProject.setMapNotesPublic(false);
        mapProject.setReverseMapPattern(false);

        mapProject.setEdition(savedEdition);
        mapProject.setWorkflowType(WorkflowType.SIMPLE_PATH);

        final Set<MapAgeRange> ageRanges = new HashSet<>();
        final MapAgeRange ageRange = new MapAgeRange();
        ageRange.setName("age range name");
        mapProject.setPresetAgeRanges(ageRanges); // Set<MapAgeRange>

        final Set<MapUser> mapLeadUsers = new HashSet<>();
        mapLeadUsers.add(newMapLeadUser);
        mapProject.setMapLeads(mapLeadUsers); // Set<MapUser>

        final Set<MapUser> mapSpecialistUsers = new HashSet<>();
        mapSpecialistUsers.add(newMapSpecialistUser);
        mapProject.setMapSpecialists(mapSpecialistUsers); // Set<MapUser>

        final Set<MapAdvice> mapAdvices = new HashSet<>();
        mapAdvices.add(newMapAdvice.getBody());
        mapProject.setMapAdvices(mapAdvices); // Set<MapAdvice>

        // final Set<AdditionalMapEntryInfo> additionalMapEntryInfos = new HashSet<>();
        // final AdditionalMapEntryInfo additionalMapEntryInfo = new AdditionalMapEntryInfo();
        // additionalMapEntryInfo.setKey("key");
        // additionalMapEntryInfo.setValue("value");
        // additionalMapEntryInfo.setMapProject(mapProject);
        // additionalMapEntryInfos.add(additionalMapEntryInfo);
        mapProject.setAdditionalMapEntryInfos(null); // Set<AdditionalMapEntryInfo>

        final Set<MapRelation> mapRelations = new HashSet<>();
        mapRelations.add(newMapRelation.getBody());
        mapProject.setMapRelations(mapRelations); // Set<MapRelation>

        // nulls for now
        mapProject.setMapPrinciples(null); // Set<MapPrinciple>
        mapProject.setMapReportDefinitions(null); // Set<MapReportDefinition>
        mapProject.setScopeConcepts(null); // Set<String>
        mapProject.setScopeExcludedConcepts(null); // Set<String>
        mapProject.setErrorMessages(null); // Set<String>
        mapProject.setMapRefsetPattern(null); // MapRefsetPattern

        LOG.info("Map Project before create: {}", mapProject.toString());
        final ResponseEntity<MapProject> newMapProject = mapProjectController.createMapProject(mapProject);

        LOG.info("Map Project after create: {}", newMapProject.getBody());
        assertNotNull(newMapProject.getBody());
        
        assertNotNull(newMapProject.getBody().getId());
        assertEquals(newMapProject.getBody().getName(), mapProject.getName());
        assertEquals(newMapProject.getBody().getDescription(), mapProject.getDescription());

        final String mapProjectId = newMapProject.getBody().getId();
        final ResponseEntity<MapProject> fetchedMapProject = mapProjectController.getMapProject(mapProjectId, true);
        assertNotNull(fetchedMapProject.getBody());
        assertEquals(1, fetchedMapProject.getBody().getMapLeads().size());
        assertEquals(1, fetchedMapProject.getBody().getMapSpecialists().size());
        assertEquals(newMapLeadUser.getUserName(), fetchedMapProject.getBody().getMapLeads().iterator().next().getUserName());
        assertEquals(newMapSpecialistUser.getUserName(), fetchedMapProject.getBody().getMapSpecialists().iterator().next().getUserName());

        final ResponseEntity<ResultList<MapUser>> mapProjectUsers = mapProjectController.getMapProjectUsers(mapProjectId);
        assertNotNull(mapProjectUsers.getBody());
        assertEquals(2, mapProjectUsers.getBody().getItems().size());

    }
    
    
    
    
    
    

    /**
     * Creates the map user.
     *
     * @param userName the user name
     * @param name the name
     * @return the map user
     * @throws Exception the exception
     */
    private MapUser createMapUser(final MapUser mapUser) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setTransactionPerOperation(false);
            service.beginTransaction();
            service.setModifiedBy("unittest");
            final MapUser newUser = service.add(mapUser);
            service.commit();

            return newUser;
        } catch (final Exception e) {
            LOG.error("Error creating map user", e);
            throw e;
        }

    }

}
