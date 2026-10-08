package org.ihtsdo.refsetservice.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.hibernate.collection.spi.PersistentSet;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 writes uninitialized Hibernate collections as null.
 */
public class JacksonConfigurationTest {

    /**
     * Uninitialized lazy set does not throw when the session is closed.
     *
     * @throws Exception the exception
     */
    @Test
    public void uninitializedCollectionSerializesAsNull() throws Exception {

        final Holder holder = new Holder();
        holder.setErrorMessages(new PersistentSet<>());
        final JsonMapper mapper = JsonMapper.builder().addModule(new tools.jackson.datatype.hibernate7.Hibernate7Module()).build();
        final String json = mapper.writeValueAsString(holder);
        assertTrue(json.contains("\"errorMessages\":null"), json);
    }

    /**
     * Map set JSON keeps the version-status label the UI filters on, and the id used for mappings.
     *
     * @throws Exception the exception
     */
    @Test
    public void mapSetVersionStatusSerializesAsLabel() throws Exception {

        final MapSet mapSet = new MapSet();
        mapSet.setId("map-set-id");
        mapSet.setVersionStatus(VersionStatus.IN_DEVELOPMENT);
        final JsonMapper mapper = JsonMapper.builder().addModule(new tools.jackson.datatype.hibernate7.Hibernate7Module()).build();
        final String json = mapper.writeValueAsString(mapSet);
        assertTrue(json.contains("\"id\":\"map-set-id\""), json);
        assertTrue(json.contains("\"versionStatus\":\"IN DEVELOPMENT\""), json);
        assertEquals(VersionStatus.IN_DEVELOPMENT, mapper.readValue("\"IN DEVELOPMENT\"", VersionStatus.class));
        assertEquals(VersionStatus.IN_DEVELOPMENT, mapper.readValue("\"IN_DEVELOPMENT\"", VersionStatus.class));
    }

    /**
     * Holder.
     */
    public static class Holder {

        /** The error messages. */
        private Set<String> errorMessages;

        /**
         * Returns the error messages.
         *
         * @return the error messages
         */
        public Set<String> getErrorMessages() {
            return errorMessages;
        }

        /**
         * Sets the error messages.
         *
         * @param errorMessages the error messages
         */
        public void setErrorMessages(final Set<String> errorMessages) {
            this.errorMessages = errorMessages;
        }
    }
}
