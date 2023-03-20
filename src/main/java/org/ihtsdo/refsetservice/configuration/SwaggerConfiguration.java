
package org.ihtsdo.refsetservice.configuration;

import java.io.IOException;

import org.glassfish.jersey.internal.guava.Predicates;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
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
				.useDefaultResponseMessages(false).select()
				// Include local controllers only
				.apis(RequestHandlerSelectors.basePackage("org.ihtsdo.refsetservice.rest"))
				// Hide ErrorControllerHandler
				.paths(Predicates.not(PathSelectors.ant("/refsetservice/error")))
				// .paths(Predicates.or(PathSelectors.ant("/api/v1/**"),
				.build().apiInfo(apiInfo());
	}

	/**
	 * Api info.
	 *
	 * @return the api info
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	ApiInfo apiInfo() throws IOException {
		return new ApiInfoBuilder().title("IHTSDO Refset Service").description(
				"Documentation for the API endpoints used to support the SNOMED CT Reference Set Tool 2.0.\n" + "\n"
						+ "<b>Authentication Note</b>\n"
						+ "The application supports guest access, so many of the GET endpoints here work properly "
						+ "without requiring authentication.\n" + "\n"
						+ "PUT/POST endpoints that make changes will require being signed in before they will "
						+ "function properly.\n" + "\n"
						+ "To access authenticated endpoints through this swagger, you will need to first log "
						+ "into the SNOMED IMS service for this deployment (e.g. https://ims.ihtsdotools.org), "
						+ "then return here and use the Auth Controller authenticate/{username} call with your "
						+ "username. This call will check your verify your IMS cookie and create an application "
						+ "session for that cookie.\n" + "\n"
						+ "Subsequent calls to authenticated endpoints should then work properly.")
				.license("The Apache License, Version 2.0")
				.licenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html")
				.termsOfServiceUrl(
						"https://confluence.ihtsdotools.org/display/SCTCR/Content+Request+Service+Terms+of+Service")
				.version("0.0.1")
				.contact(new Contact("SNOMED International", "https://www.snomed.org/", "info@snomed.org")).build();
	}

}
