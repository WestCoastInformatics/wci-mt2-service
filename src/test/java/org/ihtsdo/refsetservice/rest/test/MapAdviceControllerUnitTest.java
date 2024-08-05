package org.ihtsdo.refsetservice.rest.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.ihtsdo.refsetservice.model.MapAdvice;
import org.ihtsdo.refsetservice.rest.MapAdviceController;
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
public class MapAdviceControllerUnitTest extends BaseTest {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapAdviceControllerUnitTest.class);

    @Test
    public void testCreateGetUpdate() throws Exception {

        final MapAdviceController controller = new MapAdviceController();

        final MapAdvice mapAdvice = new MapAdvice();
        mapAdvice.setName("name");
        mapAdvice.setDetail("detail");
        mapAdvice.setAllowableForNullTarget(false);
        mapAdvice.setComputed(false);

        LOG.info("Map Relation new: {}", mapAdvice.toString());
        assertNotNull(mapAdvice);

        final ResponseEntity<MapAdvice> newMapAdvice = controller.createMapAdvice(mapAdvice);
        LOG.info("Map Relation after create: {}", newMapAdvice.getBody());
        assertNotNull(mapAdvice);

        // test get
        final ResponseEntity<MapAdvice> mapAdvice2 = controller.getMapAdvice(newMapAdvice.getBody().getId());
        LOG.info("Map Relation get : {}", mapAdvice2.toString());

        // is the same?
        assertEquals(newMapAdvice, mapAdvice2);

        final MapAdvice mapAdviceForUpdate = mapAdvice2.getBody();

        // update
        mapAdviceForUpdate.setName("new name");
        mapAdviceForUpdate.setDetail("new detail");
        mapAdviceForUpdate.setAllowableForNullTarget(true);
        mapAdviceForUpdate.setComputed(true);

        final ResponseEntity<MapAdvice> updatedMapAdvice = controller.updateMapAdvice(mapAdviceForUpdate.getId(), mapAdviceForUpdate);

        assertEquals(mapAdviceForUpdate, updatedMapAdvice.getBody());
        LOG.info("mapAdvice after update: {}", updatedMapAdvice.getBody());

    }

    @Test
    public void testCreateUpdateFailures() throws Exception {

        final MapAdviceController controller = new MapAdviceController();

        final MapAdvice mapAdvice = new MapAdvice();
        mapAdvice.setName(null);
        mapAdvice.setDetail("detail 1");
        mapAdvice.setAllowableForNullTarget(false);
        mapAdvice.setComputed(false);

        // on create
        assertThrows(Exception.class, () -> controller.createMapAdvice(mapAdvice));

        mapAdvice.setDetail(null);
        mapAdvice.setName("name 1");

        assertThrows(Exception.class, () -> controller.createMapAdvice(mapAdvice));

        mapAdvice.setDetail("detail 1");

        final ResponseEntity<MapAdvice> newMapAdvice = controller.createMapAdvice(mapAdvice);

        assertNotNull(newMapAdvice);

        final MapAdvice mapAdviceForUpdate = newMapAdvice.getBody();

        // on update
        mapAdviceForUpdate.setName(null);

        assertThrows(Exception.class, () -> controller.updateMapAdvice(mapAdviceForUpdate.getId(), mapAdviceForUpdate));

        mapAdviceForUpdate.setName("detail 1");
        mapAdviceForUpdate.setDetail(null);

        assertThrows(Exception.class, () -> controller.updateMapAdvice(mapAdviceForUpdate.getId(), mapAdviceForUpdate));

    }

    @Test
    public void testUnique() throws Exception {

        final MapAdviceController controller = new MapAdviceController();
        final String notUniqueName = "not unique name";
        final String notUniqueDetail = "not unique detail";

        final MapAdvice mapAdvice1 = new MapAdvice();
        mapAdvice1.setAllowableForNullTarget(false);
        mapAdvice1.setComputed(false);

        final MapAdvice mapAdvice2 = new MapAdvice();
        mapAdvice2.setAllowableForNullTarget(false);
        mapAdvice2.setComputed(false);

        mapAdvice1.setName(notUniqueName);
        mapAdvice1.setDetail(notUniqueDetail);

        final ResponseEntity<MapAdvice> newMapAdvice = controller.createMapAdvice(mapAdvice1);
        assertNotNull(newMapAdvice);

        assertThrows(Exception.class, () -> controller.createMapAdvice(mapAdvice2));

        mapAdvice2.setName("unique name");

        assertThrows(Exception.class, () -> controller.createMapAdvice(mapAdvice2));

        mapAdvice2.setDetail("unique detail");

        final ResponseEntity<MapAdvice> newMapAdvice2 = controller.createMapAdvice(mapAdvice2);

        newMapAdvice2.getBody().setName(notUniqueName);

        assertThrows(Exception.class, () -> controller.createMapAdvice(newMapAdvice2.getBody()));

    }

    @Test
    public void testSearch() throws Exception {

        final MapAdviceController controller = new MapAdviceController();

        final MapAdvice mapAdvice = new MapAdvice();
        mapAdvice.setName("search name 1");
        mapAdvice.setDetail("detail 2");
        mapAdvice.setAllowableForNullTarget(false);
        mapAdvice.setComputed(false);

        final ResponseEntity<MapAdvice> newMapAdvice = controller.createMapAdvice(mapAdvice);
        assertNotNull(newMapAdvice);

        final MapAdvice mapAdvice2 = new MapAdvice();
        mapAdvice2.setName("search name 2");
        mapAdvice2.setDetail("detail 3");
        mapAdvice2.setAllowableForNullTarget(false);
        mapAdvice2.setComputed(false);

        final ResponseEntity<MapAdvice> newMapAdvice2 = controller.createMapAdvice(mapAdvice2);
        assertNotNull(newMapAdvice2);

        final SearchParameters searchParameters = new SearchParameters();
        final BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(searchParameters, "searchParameters");
        final ResponseEntity<ResultList<MapAdvice>> result = controller.getMapAdvices(searchParameters, bindingResult);

        assertNotNull(result);
        LOG.info("Search result: {}", result.getBody());
        // has more from previous tests
        assertTrue(result.getBody().getItems().size() > 2);
        // int count = result.getBody().getItems().size();

        final MapAdvice mapAdviceForUpdate = newMapAdvice2.getBody();
        mapAdviceForUpdate.setActive(false);

        final ResponseEntity<MapAdvice> updatedMapAdvice = controller.updateMapAdvice(mapAdviceForUpdate.getId(), mapAdviceForUpdate);
        assertNotNull(updatedMapAdvice);

        // searchParameters.setActiveOnly(true);
        // final ResponseEntity<ResultList<MapAdvice>> result2 = controller.getMapAdvices(searchParameters, bindingResult);
        // assertNotNull(result2);
        // LOG.info("Search result2: {}", result2.getBody());
        // assertTrue(result2.getBody().getItems().size() == count - 1);

        searchParameters.setQuery("name:search name 1");
        final ResponseEntity<ResultList<MapAdvice>> result3 = controller.getMapAdvices(searchParameters, bindingResult);
        assertNotNull(result3);
        LOG.info("Search result3: {}", result3.getBody());
        assertEquals(1, result3.getBody().getItems().size());

    }

}
