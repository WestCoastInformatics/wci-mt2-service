package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.MapNote;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;

/**
 * Unit test for {@link Mapping}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class MappingUnitTest extends BaseTest {

    private Mapping object;

    @BeforeEach
    public void setup() throws Exception {

        object = new Mapping();
    }

    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.exclude("mapEntries");
        tester.exclude("mapNotes");
        tester.exclude("descriptions");
        tester.test();
    }

    @Test
    public void testModelEqualsHashcode() throws Exception {

        final EqualsHashcodeTester tester = new EqualsHashcodeTester(object);
        tester.exclude("id");
        tester.exclude("created");
        tester.exclude("modified");
        tester.exclude("modifiedBy");
        tester.exclude("active");
        tester.include("code");
        tester.include("name");
        tester.include("mapSetId");
        tester.exclude("mapEntries");
        tester.exclude("mapNotes");
        tester.exclude("descriptions");
        assertTrue(tester.testIdentityFieldEquals());
        assertTrue(tester.testNonIdentityFieldEquals());
        assertTrue(tester.testIdentityFieldNotEquals());
        assertTrue(tester.testIdentityFieldHashcode());
        assertTrue(tester.testNonIdentityFieldHashcode());
        assertTrue(tester.testIdentityFieldDifferentHashcode());
    }

    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.exclude("mapEntries");
        tester.exclude("mapNotes");
        tester.exclude("descriptions");
        assertTrue(tester.testJsonSerialization());
    }

    /**
     * Hibernate5Module skips {@code @Transient} fields unless they have {@code @JsonGetter}.
     * Hydrated map notes must still appear in API JSON.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMapNotesSerializedWhenPresent() throws Exception {

        final Mapping mapping = new Mapping();
        mapping.setCode("10000006");
        final MapNote note = new MapNote();
        note.setSourceConceptCode("10000006");
        note.setNote("This is a note I'm adding");
        mapping.getMapNotes().add(note);

        final JsonNode node = hibernateObjectMapper().readTree(hibernateObjectMapper().writeValueAsString(mapping));
        assertTrue(node.has("mapNotes"), node.toString());
        assertTrue(node.get("mapNotes").isArray());
        assertEquals(1, node.get("mapNotes").size());
        assertTrue(node.get("mapNotes").get(0).get("note").asText().contains("This is a note I'm adding"));
    }

    /**
     * Empty map notes stay omitted because {@link Mapping} uses NON_EMPTY inclusion.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEmptyMapNotesOmitted() throws Exception {

        final Mapping mapping = new Mapping();
        mapping.setCode("10000006");

        final JsonNode node = hibernateObjectMapper().readTree(hibernateObjectMapper().writeValueAsString(mapping));
        assertFalse(node.has("mapNotes"), node.toString());
    }

    /**
     * Object mapper matching production {@code JacksonConfiguration} (Hibernate5Module).
     *
     * @return the object mapper
     */
    private static ObjectMapper hibernateObjectMapper() {

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Hibernate5Module());
        return mapper;
    }
}
