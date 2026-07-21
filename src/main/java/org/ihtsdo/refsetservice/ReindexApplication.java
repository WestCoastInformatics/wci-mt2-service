/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice;

import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Command-line entry point to rebuild Hibernate Search / Elasticsearch indexes from the database.
 * <p>
 * Usage:
 * <ul>
 * <li>{@code ./gradlew reindex} — reindex all {@code @Indexed} entities</li>
 * <li>{@code ./gradlew reindex -PindexedObjects=MapSet} — reindex only MapSet</li>
 * <li>{@code ./gradlew reindex -PindexedObjects=MapSet,Refset} — reindex selected entities</li>
 * </ul>
 * Prefer running with the main MT2 service stopped (or before opening traffic) so searches do not hit
 * empty indexes during {@code dropAndCreate} / mass indexing.
 */
@SpringBootApplication(scanBasePackageClasses = PropertyUtility.class, exclude = {
    FlywayAutoConfiguration.class, ElasticsearchRestClientAutoConfiguration.class, DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
public class ReindexApplication {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ReindexApplication.class);

    /**
     * Instantiates an empty {@link ReindexApplication}.
     */
    protected ReindexApplication() {
        // Spring Boot entry point
    }

    /**
     * Application entry point.
     *
     * @param args optional indexed object simple names (comma-separated). {@code reindex} or {@code all} means all entities.
     * @throws Exception the exception
     */
    public static void main(final String[] args) throws Exception {

        final ConfigurableApplicationContext context =
            new SpringApplicationBuilder(ReindexApplication.class).web(WebApplicationType.NONE).logStartupInfo(true).run(args);

        try {
            final String indexedObjects = resolveIndexedObjects(args);
            LOG.info("Starting Elasticsearch reindex for: {}",
                indexedObjects == null || indexedObjects.isBlank() ? "ALL indexed entities" : indexedObjects);

            // Avoid TerminologyService constructor's one-shot reindex so we control clear + scope explicitly.
            TerminologyService.disableStartupReindex();

            try (TerminologyService service = new TerminologyService()) {
                service.clearLuceneIndexes();
                service.computeLuceneIndexes(blankToNull(indexedObjects));
            }

            LOG.info("Elasticsearch reindex completed successfully");
        } catch (final Exception e) {
            LOG.error("Elasticsearch reindex failed", e);
            System.exit(1);
        } finally {
            context.close();
        }
    }

    /**
     * Resolves the indexed-objects argument.
     *
     * @param args command-line args
     * @return comma-separated simple class names, or null for all
     */
    private static String resolveIndexedObjects(final String[] args) {

        if (args == null || args.length == 0) {
            return null;
        }

        final String first = args[0].trim();
        if (first.isEmpty() || "reindex".equalsIgnoreCase(first) || "all".equalsIgnoreCase(first)) {
            return null;
        }
        return first;
    }

    /**
     * Converts blank strings to null.
     *
     * @param value the value
     * @return null if blank, otherwise value
     */
    private static String blankToNull(final String value) {

        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
