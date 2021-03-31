
package org.ihtsdo.refsetservice.config;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PreDestroy;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * The Class TestConfiguration.
 */

@Configuration
@DependsOn({
        "propertyUtility", "customMigrationStrategy"
})
public class TestConfiguration {

    /** The logger. */
    private static final Logger logger = LoggerFactory.getLogger(TestConfiguration.class);

    /** the Spring environment variable. */
    @Autowired
    private ConfigurableEnvironment env;

    /** Flag to indicate if test data has been loaded. */
    static boolean dataLoaded = false;

    /** The refset test data. */
    private static ArrayList<Refset> refsetList = new ArrayList<>();

    /** The concept test data. */
    private static ArrayList<Concept> conceptList = new ArrayList<>();
    
    /** The organization test data. */
    private static ArrayList<Organization> organizationList = new ArrayList<>();
    
    /** The project test data. */
    private static ArrayList<Project> projectList = new ArrayList<>();

    /** The file path for refset test data. */
    private final String refsetDataFile = "src/test/resources/testdata/refsets.txt";

    /** The file path for concept test data. */
    private final String conceptDataFile = "src/test/resources/testdata/concepts.txt";
    
    /** The file path for organization test data. */
    private final String organizationDataFile = "src/test/resources/testdata/organizations.txt";
    
    /** The file path for project test data. */
    private final String projectDataFile = "src/test/resources/testdata/projects.txt";

    /**
     * Instantiates an empty {@link TestConfiguration}.
     */
    public TestConfiguration() {
        logger.debug("Creating instance of class TestConfiguration");
    }

    /**
     * On application startup add data needed for tests.
     * 
     * @throws Exception the exception
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadTestData() throws Exception {

        if (!dataLoaded) {

            List<String> refsetsJson = FileUtility.readFileToArray(refsetDataFile);
            List<String> conceptsJson = FileUtility.readFileToArray(conceptDataFile);
            List<String> organizationsJson = FileUtility.readFileToArray(organizationDataFile);
            List<String> projectsJson = FileUtility.readFileToArray(organizationDataFile);

            try (final TerminologyService service = new TerminologyService()) {

                service.setModifiedBy("test");
                service.setModifiedFlag(true);
                
                for (String organizationJson : organizationsJson) {

                    Organization organization = ModelUtility.fromJson(organizationJson, Organization.class);
                    
                    // Add an object
                    service.add(organization);
                    organizationList.add(organization);
                    logger.info("Organization " + organization.getName() + " successfully added");
                }
                
                for (String projectJson : projectsJson) {

                    Project project = ModelUtility.fromJson(projectJson, Project.class);
                    project.setOrganization(organizationList.get(0));
                    
                    // Add an object
                    service.add(project);
                    projectList.add(project);
                    logger.info("Project " + project.getName() + " successfully added");
                }

                for (String refsetJson : refsetsJson) {

                    Refset refset = ModelUtility.fromJson(refsetJson, Refset.class);
                    refset.setProject(projectList.get(0));

                    // Add an object
                    service.add(refset);
                    refsetList.add(refset);
                    logger.info("Refset " + refset.getRefsetId() + " successfully added");
                }

                for (String conceptJson : conceptsJson) {

                    Concept concept = ModelUtility.fromJson(conceptJson, Concept.class);

                    // Add an object
                    service.add(concept);
                    conceptList.add(concept);
                    logger.info("Concept " + concept.getName() + " successfully added");
                }

                dataLoaded = true;
            }
        }
    }

    /**
     * On application shutdown clear any test data that was added.
     * 
     * @throws Exception the exception
     */
    @PreDestroy
    public void clearTestData() throws Exception {

        if (dataLoaded) {

            try (final TerminologyService service = new TerminologyService()) {

                for (Refset refset : refsetList) {

                    final String id = refset.getRefsetId();

                    // remove an object
                    service.remove(refset);
                    logger.info("Refset " + id + " successfully removed");
                }

                for (Concept concept : conceptList) {

                    final String name = concept.getName();

                    // remove an object
                    service.remove(concept);
                    logger.info("Concept " + name + " successfully removed");
                }
                
                for (Project project : projectList) {

                    final String name = project.getName();

                    // remove an object
                    service.remove(project);
                    logger.info("Project " + name + " successfully removed");
                }
                
                for (Organization organization : organizationList) {
                    
                    final String name = organization.getName();
                    
                    // remove an object
                    service.remove(organization);
                    logger.info("Organization " + name + " successfully removed");
                }

                refsetList.clear();
                conceptList.clear();
                dataLoaded = false;
            }
        }
    }

    /**
     * Servlet web server factory.
     *
     * @return the servlet web server factory
     */
    @Bean
    public ServletWebServerFactory servletWebServerFactory() {
        return new TomcatServletWebServerFactory();
    }
}
