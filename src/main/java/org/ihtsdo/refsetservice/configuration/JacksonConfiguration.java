
package org.ihtsdo.refsetservice.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate7.Hibernate7Module;

/**
 * Jackson Configuration.
 */
@Configuration
public class JacksonConfiguration {

    /**
     * Object mapper.
     *
     * @return the object mapper
     */
    @Bean
    public ObjectMapper objectMapper() {

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Hibernate7Module());
        return mapper;
    }
}
