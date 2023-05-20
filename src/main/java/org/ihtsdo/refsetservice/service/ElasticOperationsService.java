
package org.ihtsdo.refsetservice.service;

import java.io.IOException;
import java.util.List;

import org.ihtsdo.refsetservice.model.Metric;

/**
 * The service for performing index related operations on Elasticsearch.
 *
 * @author Arun
 */
public interface ElasticOperationsService {

    /**
     * create index using the given index name.
     *
     * @param indexName the index name
     * @param force whether index is to be deleted, if already present
     * @return if the index was created
     * @throws IOException the io exception
     */
    boolean createIndex(String indexName, boolean force) throws IOException;

    /**
     * load objects.
     *
     * @param objects the list of objects
     * @param index the index name
     * @param type the type name
     * @param clazz the clazz
     * @throws IOException the io exception
     */
    @SuppressWarnings("rawtypes")
    void bulkIndex(List objects, String index, String type, Class clazz) throws IOException;

    /**
     * load object.
     *
     * @param object the object
     * @param index the index name
     * @param type the type name
     * @param clazz the clazz
     * @throws IOException the io exception
     */
    void index(Object object, String index, String type, @SuppressWarnings("rawtypes") Class clazz) throws IOException;

    /**
     * load metrics.
     *
     * @param metric the metric to load
     * @param index the index name
     * @throws IOException the io exception
     */
    void loadMetric(Metric metric, String index) throws IOException;

    /**
     * get the instance of {@code ElasticsearchOperations}.
     *
     * @return the instance of {@code ElasticsearchOperations}
     */
    // ElasticsearchOperations getElasticsearchOperations();

    /**
     * delete the index.
     *
     * @param index the index name
     * @return {@literal true} if the index was deleted
     */
    boolean deleteIndex(String index);

}
