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
    private static final Pattern UNIQUE_KEY_PATTERN =
            Pattern.compile( "(.*)-\\d+-\\d+-\\d+" );
    
    @Override
    public String createInitialElasticsearchIndexName(String hibernateSearchIndexName) {

        return properties.getProperty("app.elasticsearch.index.prefix") 
                + hibernateSearchIndexName;
    }

    @Override
    public String createWriteAlias(String hibernateSearchIndexName) {
        return properties.getProperty("app.elasticsearch.index.prefix") 
                + hibernateSearchIndexName + "-write";
    }

    @Override
    public String createReadAlias(String hibernateSearchIndexName) {
        return properties.getProperty("app.elasticsearch.index.prefix") 
                + hibernateSearchIndexName + "-read";
    }

    @Override
    public String extractUniqueKeyFromHibernateSearchIndexName(
            String hibernateSearchIndexName) {
        return hibernateSearchIndexName;
    }

    @Override
    public String extractUniqueKeyFromElasticsearchIndexName(
            String elasticsearchIndexName) {
        
        Matcher matcher = UNIQUE_KEY_PATTERN.matcher( elasticsearchIndexName );
        
        if ( !matcher.matches() ) {
            throw new IllegalArgumentException(
                    "Unrecognized index name: " + elasticsearchIndexName
            );
        }
        
        return matcher.group( 1 );
    }
}
