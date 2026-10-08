
package org.ihtsdo.refsetservice.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate7.Hibernate7Module;

import tools.jackson.databind.JacksonModule;

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

    /**
     * Hibernate module for the Jackson 3 mapper Spring MVC uses to write JSON. Uninitialized lazy collections are written as null.
     *
     * @return the module
     */
    @Bean
    public JacksonModule hibernate7JsonModule() {

        return new tools.jackson.datatype.hibernate7.Hibernate7Module();
    }
}
