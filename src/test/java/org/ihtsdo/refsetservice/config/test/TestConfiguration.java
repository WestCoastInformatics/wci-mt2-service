/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.config.test;

import java.util.ArrayList;

import javax.annotation.PreDestroy;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;

/**
 * The Class TestConfiguration.
 */

@Configuration
@DependsOn({
    "propertyUtility", "customMigrationStrategy"
})
public class TestConfiguration {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(TestConfiguration.class);

    /** the Spring environment variable. */
    // @Autowired
    // private ConfigurableEnvironment env;

    /** Flag to indicate if test data has been loaded. */
    private static boolean dataLoaded = false;

    /** The refset test data. */
    private static ArrayList<Refset> refsetList = new ArrayList<>();

    /** The organization test data. */
    private static ArrayList<Organization> organizationList = new ArrayList<>();

    /** The project test data. */
    private static ArrayList<Project> projectList = new ArrayList<>();

    /** The definition test data. */
    private static ArrayList<DefinitionClause> definitionList = new ArrayList<>();

    /** The edition test data. */
    private static ArrayList<Edition> editionList = new ArrayList<>();

    /** The file path for refset test data. */
    private final String refsetDataFile = "src/test/resources/testdata/refsets.txt";

    /** The file path for organization test data. */
    private final String organizationDataFile = "src/test/resources/testdata/organizations.txt";

    /** The file path for project test data. */
    private final String projectDataFile = "src/test/resources/testdata/projects.txt";

    /** The file path for definition test data. */
    private final String definitionDataFile = "src/test/resources/testdata/definitionClauses.txt";

    /** The file path for edition test data. */
    private final String editionDataFile = "src/test/resources/testdata/editions.txt";

    /**
     * Instantiates an empty {@link TestConfiguration}.
     */
    public TestConfiguration() {

        LOG.debug("Creating instance of class TestConfiguration");
    }

    /**
     * On application startup add data needed for tests.
     * 
     * @throws Exception the exception
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadTestData() throws Exception {

        // if (false) { // !dataLoaded
        //
        // final List<String> refsetsJson = FileUtility.readFileToArray(refsetDataFile);
        // final List<String> organizationsJson = FileUtility.readFileToArray(organizationDataFile);
        // final List<String> projectsJson = FileUtility.readFileToArray(projectDataFile);
        // final List<String> definitionsJson = FileUtility.readFileToArray(definitionDataFile);
        // final List<String> editionsJson = FileUtility.readFileToArray(editionDataFile);
        //
        // try (final TerminologyService service = new TerminologyService()) {
        //
        // service.setModifiedBy("TestConfiguration");
        // service.setModifiedFlag(true);
        //
        // for (final String organizationJson : organizationsJson) {
        //
        // final Organization organization = ModelUtility.fromJson(organizationJson, Organization.class);
        //
        // // Add an object
        // service.add(organization);
        // organizationList.add(organization);
        // LOG.info("Organization " + organization.getName() + " successfully added");
        // }
        //
        // for (final String editionJson : editionsJson) {
        //
        // final Edition edition = ModelUtility.fromJson(editionJson, Edition.class);
        //
        // // Add an object
        // service.add(edition);
        // editionList.add(edition);
        // edition.setOrganization(organizationList.get(0));
        // LOG.info("Edition " + edition.getName() + " successfully added");
        // }
        //
        // for (final String projectJson : projectsJson) {
        //
        // final Project project = ModelUtility.fromJson(projectJson, Project.class);
        // project.setEdition(editionList.get(0));
        //
        // // Add an object
        // service.add(project);
        // projectList.add(project);
        // LOG.info("Project " + project.getName() + " successfully added");
        // }
        //
        // for (final String definitionJson : definitionsJson) {
        //
        // final DefinitionClause definition = ModelUtility.fromJson(definitionJson, DefinitionClause.class);
        //
        // // Add an object
        // service.add(definition);
        // definitionList.add(definition);
        // LOG.info("Definition " + definition.getValue() + " successfully added");
        // }
        //
        // for (final String refsetJson : refsetsJson) {
        //
        // final Refset refset = ModelUtility.fromJson(refsetJson, Refset.class);
        // refset.setProject(projectList.get(0));
        //
        // if (refset.getType().equals("intensional")) {
        // refset.getDefinitionClauses().addAll(definitionList);
        // }
        //
        // // Add an object
        // service.add(refset);
        // refsetList.add(refset);
        // LOG.info("Refset " + refset.getRefsetId() + " successfully added");
        // }
        //
        // dataLoaded = true;
        // }
        // }
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

                for (final Refset refset : refsetList) {

                    final String id = refset.getRefsetId();

                    // remove an object
                    service.remove(refset);
                    LOG.info("Refset " + id + " successfully removed");
                }
                for (final Project project : projectList) {

                    final String name = project.getName();

                    // remove an object
                    service.remove(project);
                    LOG.info("Project " + name + " successfully removed");
                }

                for (final Organization organization : organizationList) {

                    final String name = organization.getName();

                    // remove an object
                    service.remove(organization);
                    LOG.info("Organization " + name + " successfully removed");
                }

                for (final Edition edition : editionList) {

                    final String name = edition.getName();

                    // remove an object
                    service.remove(edition);
                    LOG.info("Edition " + name + " successfully removed");
                }

                for (final DefinitionClause definition : definitionList) {

                    final String name = definition.getValue();

                    // remove an object
                    service.remove(definition);
                    LOG.info("Definition " + name + " successfully removed");
                }

                refsetList.clear();
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
