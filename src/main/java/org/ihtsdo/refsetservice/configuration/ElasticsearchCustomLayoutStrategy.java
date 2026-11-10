/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.configuration;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.search.backend.elasticsearch.index.layout.IndexLayoutStrategy;
import org.ihtsdo.refsetservice.util.PropertyUtility;

/**
 * Sets up ability to use indexing prefixes with Elasticsearch.
 */
public class ElasticsearchCustomLayoutStrategy implements IndexLayoutStrategy {

    /** The config properties. */
    private final Properties properties = PropertyUtility.getProperties();

    /** The unique index key pattern. */
    private static final Pattern UNIQUE_KEY_PATTERN = Pattern.compile("(.*)-\\d+-\\d+-\\d+");

    /**
     * Creates the initial elasticsearch index name.
     *
     * @param hibernateSearchIndexName the hibernate search index name
     * @return the string
     */
    @Override
    public String createInitialElasticsearchIndexName(final String hibernateSearchIndexName) {

        return properties.getProperty("app.elasticsearch.index.prefix") + hibernateSearchIndexName;
    }

    /**
     * Creates the write alias.
     *
     * @param hibernateSearchIndexName the hibernate search index name
     * @return the string
     */
    @Override
    public String createWriteAlias(final String hibernateSearchIndexName) {

        return properties.getProperty("app.elasticsearch.index.prefix") + hibernateSearchIndexName + "-write";
    }

    /**
     * Creates the read alias.
     *
     * @param hibernateSearchIndexName the hibernate search index name
     * @return the string
     */
    @Override
    public String createReadAlias(final String hibernateSearchIndexName) {

        return properties.getProperty("app.elasticsearch.index.prefix") + hibernateSearchIndexName + "-read";
    }

    /**
     * Extract unique key from hibernate search index name.
     *
     * @param hibernateSearchIndexName the hibernate search index name
     * @return the string
     */
    @Override
    public String extractUniqueKeyFromHibernateSearchIndexName(final String hibernateSearchIndexName) {

        return hibernateSearchIndexName;
    }

    /**
     * Extract unique key from elasticsearch index name.
     *
     * @param elasticsearchIndexName the elasticsearch index name
     * @return the string
     */
    @Override
    public String extractUniqueKeyFromElasticsearchIndexName(final String elasticsearchIndexName) {

        final Matcher matcher = UNIQUE_KEY_PATTERN.matcher(elasticsearchIndexName);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unrecognized index name: " + elasticsearchIndexName);
        }

        return matcher.group(1);
    }
}
