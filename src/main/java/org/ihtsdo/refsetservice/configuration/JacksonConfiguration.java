
package org.ihtsdo.refsetservice.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;

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
        mapper.setSerializationInclusion(Include.NON_EMPTY);
        mapper.registerModule(new Hibernate5Module());
        return mapper;
    }
}