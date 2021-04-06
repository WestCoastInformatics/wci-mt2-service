
package org.ihtsdo.refsetservice.configuration;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * Swagger configuration.
 */
@Configuration
@EnableSwagger2
public class SwaggerConfiguration {

    /**
     * Api.
     *
     * @return the docket
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Bean
    public Docket api() throws IOException {

        return new Docket(DocumentationType.SWAGGER_2)
                // Disable default responses (e.g. 401, 403)
                .useDefaultResponseMessages(false)

                .select().apis(RequestHandlerSelectors.any())
                // .paths(Predicates.or(PathSelectors.ant("/api/v1/**"),
                // PathSelectors.ant("/version/**")))
                .build().apiInfo(apiInfo());

    }

    /**
     * Api info.
     *
     * @return the api info
     * @throws IOException Signals that an I/O exception has occurred.
     */
    ApiInfo apiInfo() throws IOException {
        return new ApiInfoBuilder().title("IHTSDO Refset Service")
                .description("Swagger description for this service")
                .license("The Apache License, Version 2.0")
                .licenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html")
                .termsOfServiceUrl("terms-of-service url").version("0.0.1")
                .contact(new Contact("SNOMED International", "https://www.snomed.org/",
                        "info@snomed.org"))
                .build();
    }

}
