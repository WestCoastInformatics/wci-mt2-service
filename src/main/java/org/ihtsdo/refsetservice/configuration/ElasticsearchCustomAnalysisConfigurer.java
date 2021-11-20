package org.ihtsdo.refsetservice.configuration;

import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurationContext;
import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurer;

/**
 * Configure analyzers for the Hibernate Search Elasticsearch backend.
 */
public class ElasticsearchCustomAnalysisConfigurer implements ElasticsearchAnalysisConfigurer {

    @Override
    public void configure(ElasticsearchAnalysisConfigurationContext context) {
        context.normalizer( "lowercase" ).custom().tokenFilters( "lowercase", "asciifolding" );
    }
}
