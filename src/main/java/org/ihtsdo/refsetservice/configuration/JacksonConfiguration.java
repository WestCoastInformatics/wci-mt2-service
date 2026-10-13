
package org.ihtsdo.refsetservice.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.JacksonModule;

/**
 * Jackson Configuration.
 */
@Configuration
public class JacksonConfiguration {

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
