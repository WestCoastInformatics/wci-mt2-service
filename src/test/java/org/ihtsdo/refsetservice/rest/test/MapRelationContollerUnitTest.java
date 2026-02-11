package org.ihtsdo.refsetservice.rest.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.ihtsdo.refsetservice.model.MapRelation;
import org.ihtsdo.refsetservice.rest.MapRelationController;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.validation.BeanPropertyBindingResult;

@SpringBootTest
@ActiveProfiles("test")
public class MapRelationContollerUnitTest extends BaseTest {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapRelationContollerUnitTest.class);

    @Test
    public void testCreateGetUpdate() throws Exception {

        final MapRelationController controller = new MapRelationController();

        final MapRelation mapRelation = new MapRelation();
        mapRelation.setTerminologyId("MR term");
        mapRelation.setName("MR name");
        mapRelation.setAbbreviation("MR abbreviation");
        mapRelation.setAllowableForNullTarget(false);
        mapRelation.setComputed(false);

        LOG.info("Map Relation new: {}", mapRelation.toString());
        assertNotNull(mapRelation);

        final ResponseEntity<MapRelation> newMapRelation = controller.createMapRelation(mapRelation);
        assertNotNull(newMapRelation);
        assertEquals(newMapRelation.getStatusCode(), org.springframework.http.HttpStatus.CREATED);
        LOG.info("Map Relation after create: {}", newMapRelation.getBody());

        // test get
        final ResponseEntity<MapRelation> mapRelation2 = controller.getMapRelation(newMapRelation.getBody().getId());
        assertNotNull(mapRelation2);
        assertEquals(mapRelation2.getStatusCode(), org.springframework.http.HttpStatus.OK);
        LOG.info("Map Relation get : {}", mapRelation2.getBody());

        // is the same?
        assertEquals(newMapRelation.getBody(), mapRelation2.getBody());

        final MapRelation mapRelationForUpdate = mapRelation2.getBody();

        // update
        mapRelationForUpdate.setTerminologyId("MR new term");
        mapRelationForUpdate.setName("MR new name");
        mapRelationForUpdate.setAbbreviation("MR new abbreviation");
        mapRelationForUpdate.setAllowableForNullTarget(true);
        mapRelationForUpdate.setComputed(true);

        final ResponseEntity<MapRelation> updatedMapRelation = controller.updateMapRelation(mapRelationForUpdate.getId(), mapRelationForUpdate);
        assertNotNull(updatedMapRelation);
        assertEquals(updatedMapRelation.getStatusCode(), org.springframework.http.HttpStatus.OK);
        assertEquals(mapRelationForUpdate, updatedMapRelation.getBody());
        LOG.info("mapRelation after update: {}", updatedMapRelation.getBody());

    }

    @Test
    public void testCreateUpdateFailures() throws Exception {

        final MapRelationController controller = new MapRelationController();

        final MapRelation mapRelation = new MapRelation();
        mapRelation.setTerminologyId(null);
        mapRelation.setName("name 1");
        mapRelation.setAbbreviation("abbreviation 1");
        mapRelation.setAllowableForNullTarget(false);
        mapRelation.setComputed(false);

        // on create
        assertThrows(Exception.class, () -> controller.createMapRelation(mapRelation));

        mapRelation.setTerminologyId("term");
        mapRelation.setName(null);

        assertThrows(Exception.class, () -> controller.createMapRelation(mapRelation));

        mapRelation.setName("name 1");

        final ResponseEntity<MapRelation> newMapRelation = controller.createMapRelation(mapRelation);
        assertNotNull(newMapRelation.getBody());

        final MapRelation mapRelationForUpdate = newMapRelation.getBody();

        // on update
        mapRelationForUpdate.setTerminologyId(null);

        assertThrows(Exception.class, () -> controller.updateMapRelation(mapRelationForUpdate.getId(), mapRelationForUpdate));

        mapRelationForUpdate.setTerminologyId("term");
        mapRelationForUpdate.setName(null);

        assertThrows(Exception.class, () -> controller.updateMapRelation(mapRelationForUpdate.getId(), mapRelationForUpdate));

    }

    @Test
    public void testUnique() throws Exception {

        final MapRelationController controller = new MapRelationController();
        final String notUniqueName = "not unique name";

        final MapRelation mapRelation = new MapRelation();
        mapRelation.setTerminologyId("term");
        mapRelation.setName(notUniqueName);
        mapRelation.setAbbreviation("abbreviation 1");
        mapRelation.setAllowableForNullTarget(false);
        mapRelation.setComputed(false);

        final ResponseEntity<MapRelation> newMapRelation = controller.createMapRelation(mapRelation);

        assertNotNull(newMapRelation.getBody());

        final MapRelation mapRelation2 = new MapRelation();
        mapRelation2.setTerminologyId("term");
        mapRelation2.setName(notUniqueName);
        mapRelation2.setAbbreviation("abbreviation 1");
        mapRelation2.setAllowableForNullTarget(false);
        mapRelation2.setComputed(false);

        assertThrows(Exception.class, () -> controller.createMapRelation(mapRelation2));

        mapRelation2.setName("unique name");

        final ResponseEntity<MapRelation> newMapRelation2 = controller.createMapRelation(mapRelation2);

        newMapRelation2.getBody().setName(notUniqueName);

        assertThrows(Exception.class, () -> controller.createMapRelation(newMapRelation2.getBody()));

    }

    @Test
    public void testSearch() throws Exception {

        final MapRelationController controller = new MapRelationController();

        final MapRelation mapRelation = new MapRelation();
        mapRelation.setTerminologyId("search term 1");
        mapRelation.setName("search name 1");
        mapRelation.setAbbreviation("abbreviation 1");
        mapRelation.setAllowableForNullTarget(false);
        mapRelation.setComputed(false);

        final ResponseEntity<MapRelation> newMapRelation = controller.createMapRelation(mapRelation);
        assertNotNull(newMapRelation);

        final MapRelation mapRelation2 = new MapRelation();
        mapRelation2.setTerminologyId("search term 2");
        mapRelation2.setName("search name 2");
        mapRelation2.setAbbreviation("abbreviation 2");
        mapRelation2.setAllowableForNullTarget(false);
        mapRelation2.setComputed(false);

        final ResponseEntity<MapRelation> newMapRelation2 = controller.createMapRelation(mapRelation2);
        assertNotNull(newMapRelation2);

        final SearchParameters searchParameters = new SearchParameters();
        final BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(searchParameters, "searchParameters");
        final ResponseEntity<ResultList<MapRelation>> result = controller.getMapRelations(searchParameters, bindingResult);

        assertNotNull(result);
        LOG.info("Search result: {}", result.getBody());
        // has more from previous tests
        assertTrue(result.getBody().getItems().size() > 2);
        // int count = result.getBody().getItems().size();

        final MapRelation mapRelationForUpdate = newMapRelation2.getBody();
        mapRelationForUpdate.setActive(false);

        final ResponseEntity<MapRelation> updatedMapRelation = controller.updateMapRelation(mapRelationForUpdate.getId(), mapRelationForUpdate);
        assertNotNull(updatedMapRelation);

        // searchParameters.setActiveOnly(true);
        // final ResponseEntity<ResultList<MapRelation>> result2 = controller.getMapRelations(searchParameters, bindingResult);
        // assertNotNull(result2);
        // LOG.info("Search result2: {}", result2.getBody());
        // assertTrue(result2.getBody().getItems().size() == count - 1);

        searchParameters.setQuery("name:search name 1");
        final ResponseEntity<ResultList<MapRelation>> result3 = controller.getMapRelations(searchParameters, bindingResult);
        assertNotNull(result3);
        LOG.info("Search result3: {}", result3.getBody());
        assertEquals(1, result3.getBody().getItems().size());

    }

}
