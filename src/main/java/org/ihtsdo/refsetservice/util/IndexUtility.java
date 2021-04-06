
package org.ihtsdo.refsetservice.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.FieldComparatorSource;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.hibernate.search.SearchFactory;
import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Fields;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.elasticsearch.ElasticsearchQueries;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.jpa.Search;
import org.hibernate.search.query.engine.spi.QueryDescriptor;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs utility functions relating to Lucene indexes and Hibernate Search.
 */
public final class IndexUtility {

    /**
     * Instantiates an empty {@link IndexUtility}.
     */
    private IndexUtility() {
        // n/a
    }

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(IndexUtility.class);

    /** The sort field analyzed map. */
    private static Map<String, Map<String, Boolean>> sortFieldAnalyzedMap = new HashMap<>();

    /** The string field names map. */
    private static Map<Class<?>, Set<String>> stringFieldNames = new HashMap<>();

    /** The field names map. */
    private static Map<Class<?>, Set<String>> allFieldNames = new HashMap<>();

    /** The all fields map. */
    private static Map<Class<?>, java.lang.reflect.Field[]> allFields = new HashMap<>();

    /** The all fields map. */
    private static Map<Class<?>, java.lang.reflect.Method[]> allMethods = new HashMap<>();

    // Initialize the field names maps
    static {
        try {
            final Map<String, Class<?>> reindexMap = new HashMap<>();
            final String indexProp =
                    PropertyUtility.getProperties().getProperty("app.entity_packages");

            if (indexProp == null) {
                throw new Exception("Property app.entity_packages must be present.");
            }
            final String[] packages = indexProp.split(";");
            final Reflections reflections =
                    new Reflections(new ConfigurationBuilder().forPackages(packages));
            for (final Class<?> clazz : reflections.getTypesAnnotatedWith(Indexed.class)) {
                reindexMap.put(clazz.getSimpleName(), clazz);
            }
            final Class<?>[] classes = reindexMap.values().toArray(new Class<?>[0]);

            for (final Class<?> clazz : classes) {
                stringFieldNames.put(clazz, IndexUtility.getIndexedFieldNames(clazz, true));
                allFieldNames.put(clazz, IndexUtility.getIndexedFieldNames(clazz, false));
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the indexed field names for a given class.
     *
     * @param clazz the clazz
     * @param stringOnly the string only flag
     * @return the indexed field names
     * @throws Exception the exception
     */
    public static Set<String> getIndexedFieldNames(final Class<?> clazz, final boolean stringOnly)
        throws Exception {

        // If already initialized, return computed values
        if (stringOnly && stringFieldNames.containsKey(clazz)) {
            return stringFieldNames.get(clazz);
        }
        if (!stringOnly && allFieldNames.containsKey(clazz)) {
            return allFieldNames.get(clazz);
        }

        // Avoid ngram and sort fields (these have special uses)
        final Set<String> exclusions = new HashSet<>();
        // exclusions.add("Sort");
        exclusions.add("nGram");
        exclusions.add("NGram");

        // When looking for default fields, exclude definitions and branches
        final Set<String> stringExclusions = new HashSet<>();
        stringExclusions.add("definitions");
        stringExclusions.add("branch");

        final Set<String> fieldNames = new HashSet<>();

        // first cycle over all methods
        for (final Method m : getAllMethods(clazz)) {

            // if no annotations, skip
            if (m.getAnnotations().length == 0) {
                continue;
            }

            // check for @IndexedEmbedded
            if (m.isAnnotationPresent(IndexedEmbedded.class)) {
                final IndexedEmbedded annotation = m.getAnnotation(IndexedEmbedded.class);
                final Class<?> jpaType = annotation.targetElement();
                if (jpaType == null) {
                    throw new Exception("Unable to determine jpa type, @IndexedEmbedded must use "
                            + "targetElement");
                }
                for (final String embeddedField : getIndexedFieldNames(jpaType, stringOnly)) {
                    fieldNames.add(annotation.prefix() + embeddedField);
                }
            }

            // determine if there's a fieldBridge (which converts the field)
            boolean hasFieldBridge = false;
            if (m.isAnnotationPresent(Field.class)) {
                if (!m.getAnnotation(Field.class).bridge().impl().toString().equals("void")) {
                    hasFieldBridge = true;
                }
            }

            // for non-embedded fields, only process strings
            // This is because we're handling string based query here
            // Other fields can always be used with fielded query clauses
            if (stringOnly && !hasFieldBridge && !m.getReturnType().equals(String.class)) {
                continue;
            }

            // check for @Field annotation
            if (m.isAnnotationPresent(Field.class)) {
                final String fieldName = getFieldNameFromMethod(m, m.getAnnotation(Field.class));
                fieldNames.add(fieldName);
            }

            // check for @Fields annotation
            if (m.isAnnotationPresent(Fields.class)) {
                for (final Field field : m.getAnnotation(Fields.class).value()) {
                    final String fieldName = getFieldNameFromMethod(m, field);

                    fieldNames.add(fieldName);
                }
            }
        }

        // second cycle over all fields
        for (final java.lang.reflect.Field f : getAllFields(clazz)) {
            // check for @IndexedEmbedded
            if (f.isAnnotationPresent(IndexedEmbedded.class)) {

                // Assumes field is a collection, and has a OneToMany,
                // ManyToMany, or
                // ManyToOne
                // annotation
                Class<?> jpaType = null;
                if (f.isAnnotationPresent(OneToMany.class)) {
                    jpaType = f.getAnnotation(OneToMany.class).targetEntity();
                } else if (f.isAnnotationPresent(ManyToMany.class)) {
                    jpaType = f.getAnnotation(ManyToMany.class).targetEntity();
                } else if (f.isAnnotationPresent(ManyToOne.class)) {
                    jpaType = f.getAnnotation(ManyToOne.class).targetEntity();
                } else if (f.isAnnotationPresent(OneToOne.class)) {
                    jpaType = f.getAnnotation(OneToOne.class).targetEntity();
                } else if (List.class.isAssignableFrom(f.getType())
                        || Set.class.isAssignableFrom(f.getType())) {

                    final String fieldName = getFieldNameFromField(f, f.getAnnotation(Field.class));
                    fieldNames.add(fieldName);
                    continue;
                } else {
                    throw new Exception(
                            "Unable to determine jpa type, @IndexedEmbedded must be used with "
                                    + "@OneToOne, @OneToMany, @ManyToOne, or @ManyToMany ");

                }

                for (final String embeddedField : getIndexedFieldNames(jpaType, stringOnly)) {
                    fieldNames.add(f.getName() + "." + embeddedField);
                }
            }

            // determine if there's a fieldBridge (which converts the field)
            boolean hasFieldBridge = false;
            if (f.isAnnotationPresent(Field.class)) {
                if (f.getAnnotation(Field.class).bridge().impl().toString().equals("void")) {
                    hasFieldBridge = true;
                }
            }

            // for non-embedded fields, only process strings
            if (stringOnly && !hasFieldBridge && !f.getType().equals(String.class)) {
                continue;
            }

            // check for @Field annotation
            if (f.isAnnotationPresent(Field.class)) {
                final String fieldName = getFieldNameFromField(f, f.getAnnotation(Field.class));
                fieldNames.add(fieldName);
            }

            // check for @Fields annotation
            if (f.isAnnotationPresent(Fields.class)) {
                for (final Field field : f.getAnnotation(Fields.class).value()) {
                    final String fieldName = getFieldNameFromField(f, field);
                    fieldNames.add(fieldName);
                }
            }

        }

        // Apply filters
        final Set<String> filteredFieldNames = new HashSet<>();
        OUTER: for (final String fieldName : fieldNames) {
            for (final String exclusion : exclusions) {
                if (fieldName.contains(exclusion)) {
                    continue OUTER;
                }
            }
            for (final String exclusion : stringExclusions) {
                if (stringOnly && fieldName.contains(exclusion)) {
                    continue OUTER;
                }
            }
            filteredFieldNames.add(fieldName);
        }

        // Always add "id"
        filteredFieldNames.add("id");
        return filteredFieldNames;
    }

    /**
     * Helper function to get a field name from a method and annotation.
     *
     * @param m the reflected, annotated method, assumed to be of form
     *            getFieldName()
     * @param annotationField the annotation field
     * @return the indexed field name
     */
    public static String getFieldNameFromMethod(final Method m, final Field annotationField) {
        // iannotationField annotationFieldield has a speciannotationFieldied
        // name,
        // use that
        if (annotationField != null && annotationField.name() != null
                && !annotationField.name().isEmpty()) {
            return annotationField.name();
        }

        // otherwise, assume method name of form getannotationFieldName
        // where the desired value is annotationFieldName
        if (m.getName().startsWith("get")) {
            return StringUtils.uncapitalize(m.getName().substring(3));
        } else if (m.getName().startsWith("is")) {
            return StringUtils.uncapitalize(m.getName().substring(2));
        } else if (m.getName().startsWith("set")) {
            return StringUtils.uncapitalize(m.getName().substring(3));
        } else {
            return m.getName();
        }

    }

    /**
     * Helper function get a field name from reflected Field and annotation.
     *
     * @param annotatedField the reflected, annotated field
     * @param annotationField the field annotation
     * @return the indexed field name
     */
    private static String getFieldNameFromField(final java.lang.reflect.Field annotatedField,
        final Field annotationField) {
        if (annotationField.name() != null && !annotationField.name().isEmpty()) {
            return annotationField.name();
        }

        return annotatedField.getName();
    }

    /**
     * Returns the all fields.
     *
     * @param type the type
     * @return the all fields
     */
    public static java.lang.reflect.Field[] getAllFields(final Class<?> type) {

        // If already initialized, return computed values
        if (allFields.containsKey(type)) {
            return allFields.get(type);
        }

        if (type.getSuperclass() != null) {
            final java.lang.reflect.Field[] allFieldsArray =
                    ArrayUtils.addAll(getAllFields(type.getSuperclass()), type.getDeclaredFields());
            allFields.put(type, allFieldsArray);
            return allFieldsArray;
        }
        final java.lang.reflect.Field[] allFieldsArray = type.getDeclaredFields();
        allFields.put(type, allFieldsArray);
        return allFieldsArray;
    }

    /**
     * Returns the all methods.
     *
     * @param type the type
     * @return the all methods
     */
    public static java.lang.reflect.Method[] getAllMethods(final Class<?> type) {

        // If already initialized, return computed values
        if (allMethods.containsKey(type)) {
            return allMethods.get(type);
        }

        if (type.getSuperclass() != null) {
            final java.lang.reflect.Method[] allMethodsArray = ArrayUtils
                    .addAll(getAllMethods(type.getSuperclass()), type.getDeclaredMethods());
            allMethods.put(type, allMethodsArray);
            return allMethodsArray;
        }
        final java.lang.reflect.Method[] allMethodsArray = type.getDeclaredMethods();
        allMethods.put(type, allMethodsArray);
        return allMethodsArray;
    }

    /**
     * Returns the name analyzed pairs from annotation.
     *
     * @param clazz the clazz
     * @param sortField the sort field
     * @return the name analyzed pairs from annotation
     * @throws NoSuchMethodException the no such method exception
     * @throws SecurityException the security exception
     */
    public static Map<String, Boolean> getNameAnalyzedPairsFromAnnotation(final Class<?> clazz,
        final String sortField) throws NoSuchMethodException, SecurityException {
        final String key = clazz.getName() + "." + sortField;
        if (sortFieldAnalyzedMap.containsKey(key)) {
            return sortFieldAnalyzedMap.get(key);
        }

        // initialize the name->analyzed pair map
        final Map<String, Boolean> nameAnalyzedPairs = new HashMap<>();

        final Method m = clazz.getMethod(
                "get" + sortField.substring(0, 1).toUpperCase() + sortField.substring(1),
                new Class<?>[] {});

        final Set<org.hibernate.search.annotations.Field> annotationFields = new HashSet<>();

        // check for Field annotation
        if (m.isAnnotationPresent(org.hibernate.search.annotations.Field.class)) {
            annotationFields.add(m.getAnnotation(org.hibernate.search.annotations.Field.class));
        }

        // check for Fields annotation
        if (m.isAnnotationPresent(org.hibernate.search.annotations.Fields.class)) {
            // add all specified fields
            for (final org.hibernate.search.annotations.Field f : m
                    .getAnnotation(org.hibernate.search.annotations.Fields.class).value()) {
                annotationFields.add(f);
            }
        }

        // cycle over discovered fields and put name and analyze == YES into map
        for (final org.hibernate.search.annotations.Field f : annotationFields) {
            nameAnalyzedPairs.put(f.name(), f.analyze().equals(Analyze.YES) ? true : false);
        }

        sortFieldAnalyzedMap.put(key, nameAnalyzedPairs);

        return nameAnalyzedPairs;
    }

    /**
     * Apply pfs to lucene query2.
     *
     * @param clazz the clazz
     * @param query the query
     * @param pfs the pfs
     * @param manager the manager
     * @return the full text query
     * @throws Exception the exception
     */
    public static FullTextQuery applyPfsToLuceneQuery(final Class<?> clazz, final String query,
        final PfsParameter pfs, final EntityManager manager) throws Exception {

        FullTextQuery fullTextQuery = null;

        // Build up the query
        final StringBuilder pfsQuery = new StringBuilder();
        pfsQuery.append(StringUtility.isEmpty(query) ? "*:*" : query);
        // Set up the "full text query"
        final FullTextEntityManager fullTextEntityManager =
                Search.getFullTextEntityManager(manager);

        // construct the query
        final String finalQuery = (pfsQuery.toString().startsWith(" AND "))
                ? pfsQuery.toString().substring(5) : pfsQuery.toString();

        // Directory indexmanager
        if (!PropertyUtility.getProperties()
                .getProperty("spring.jpa.properties.hibernate.search.default.indexmanager").trim()
                .equals("elasticsearch")) {

            final SearchFactory searchFactory = fullTextEntityManager.getSearchFactory();
            Query luceneQuery;
            @SuppressWarnings("resource")
            final QueryParser queryParser = new MultiFieldQueryParser(
                    IndexUtility.getIndexedFieldNames(clazz, true).toArray(new String[] {}),
                    searchFactory.getAnalyzer(clazz));

            logger.debug("    query = " + finalQuery + ", " + pfs);
            BooleanQuery.setMaxClauseCount(200000);
            try {
                luceneQuery = queryParser.parse(finalQuery);
            } catch (final ParseException e) {
                throw new LocalException("Unable to parse query = " + finalQuery + ", " + pfs, e);
            }

            // CONSIDER: re-enable this at some point
            // // Validate query terms
            // luceneQuery = luceneQuery
            // .rewrite(fullTextEntityManager.getSearchFactory().getIndexReaderAccessor().open(clazz));
            // final Set<Term> terms = new HashSet<>();
            // luceneQuery.extractTerms(terms);
            // for (final Term t : terms) {
            // if (t.field() != null && !t.field().isEmpty()
            // && !IndexUtility.getIndexedFieldNames(clazz,
            // false).contains(t.field()))
            // {
            // throw new ParseException("Query references invalid field name " +
            // t.field() + ", "
            // + IndexUtility.getIndexedFieldNames(clazz, false));
            // }
            // }

            fullTextQuery = fullTextEntityManager.createFullTextQuery(luceneQuery, clazz);
        }

        // elasticsearch index manager
        else if (PropertyUtility.getProperties()
                .getProperty("spring.jpa.properties.hibernate.search.default.indexmanager").trim()
                .equals("elasticsearch")) {

            // Need to escape double-quotes for the json
            final String json = "{\"query\": {\"query_string\" : { " + "\"query\" : \""
                    + StringEscapeUtils.escapeJson(finalQuery) + "\"} } }";
            final QueryDescriptor qd = ElasticsearchQueries.fromJson(json);
            logger.debug("    query = " + finalQuery + ", " + pfs);
            fullTextQuery = fullTextEntityManager.createFullTextQuery(qd, clazz);
        }

        // Unknown indexmanager type
        else {
            throw new Exception(
                    "Unsupported spring.jpa.properties.hibernate.search.default.indexmanager = "
                            + PropertyUtility.getProperties().getProperty(
                                    "spring.jpa.properties.hibernate.search.default.indexmanager"));
        }

        // Handle sort and paging parameters
        if (pfs != null) {
            // if start index and max results are set, set paging
            if (pfs.getOffset() >= 0 && pfs.getLimit() >= 0) {
                fullTextQuery.setFirstResult(pfs.getOffset());
                fullTextQuery.setMaxResults(pfs.getLimit());
            }

            if (pfs.getSort() != null && !pfs.getSort().isEmpty()
                    && pfs.getSort().equals("RANDOM")) {

                // Randomly sort
                final Sort sort = new Sort(new SortField("", new FieldComparatorSource() {

                    /* see superclass */
                    @Override
                    public FieldComparator<Long> newComparator(final String fieldname,
                        final int numHits, final int sortPos, final boolean reversed) {
                        return new RandomOrderFieldComparator(numHits, fieldname, null);
                    }

                }));

                fullTextQuery.setSort(sort);

                // if sort specified (single or multi-field sort), set sorting
            } else if ((pfs.getSortFields() != null && !pfs.getSortFields().isEmpty())
                    || (pfs.getSort() != null && !pfs.getSort().isEmpty())) {

                // convenience container for sort field names (from either
                // method)
                List<String> sortFieldNames = null;

                // use multiple-field sort before backwards-compatible
                // single-field sort
                if (pfs.getSortFields() != null && !pfs.getSortFields().isEmpty()) {
                    sortFieldNames = pfs.getSortFields();
                } else {
                    sortFieldNames = new ArrayList<>();
                    sortFieldNames.add(pfs.getSort());
                }

                // the constructed sort fields to sort on
                final List<SortField> sortFields = new ArrayList<>();

                for (final String sortFieldName : sortFieldNames) {

                    // the computed string name of the indexed field to sort by
                    String sortFieldStr = null;

                    // if a subfield search (e.g. FIELD1.FIELD2) skip
                    // preconditions
                    if (sortFieldName.contains(".")) {
                        sortFieldStr = sortFieldName;
                    }

                    // otherwise, check preconditions
                    else {

                        final Map<String, Boolean> nameToAnalyzedMap = IndexUtility
                                .getNameAnalyzedPairsFromAnnotation(clazz, sortFieldName);

                        // check existence of the annotated get[OffsetName]()
                        // method
                        if (nameToAnalyzedMap.size() == 0) {
                            throw new Exception(clazz.getName()
                                    + " does not have declared, annotated method for field "
                                    + sortFieldName);
                        }

                        // first, check explicit [OffsetName]Sort index
                        if (nameToAnalyzedMap.get(sortFieldName + "Sort") != null
                                && !nameToAnalyzedMap.get(sortFieldName + "Sort")) {
                            sortFieldStr = sortFieldName + "Sort";
                        }

                        // next check the default name (rendered as ""), if not
                        // analyzed,
                        // use
                        // this as sort
                        else if (nameToAnalyzedMap.get("") != null
                                && nameToAnalyzedMap.get("").equals(false)) {
                            sortFieldStr = sortFieldName;
                        }

                        // if an indexed sort field could not be found, throw
                        // exception
                        if (sortFieldStr == null) {
                            throw new Exception("Could not retrieve a non-analyzed Field "
                                    + "annotation for get method for variable name "
                                    + sortFieldName);
                        }
                    }

                    // construct the sort field object
                    SortField sortField = null;

                    // check for LONG fields
                    if (sortFieldStr.toLowerCase().endsWith("longsort")) {
                        sortField = new SortField(sortFieldStr, SortField.Type.LONG,
                                !pfs.isAscending());
                    }

                    // otherwise, sort by STRING value
                    else {
                        sortField = new SortField(sortFieldStr, SortField.Type.STRING,
                                !pfs.isAscending());
                    }

                    // add the field
                    sortFields.add(sortField);
                }

                final SortField[] sfs = sortFields.toArray(new SortField[] {});
                fullTextQuery.setSort(new Sort(sfs));

            }

        }
        return fullTextQuery;
    }

    /**
     * Sets the max window size on an index for returning large elasticsearch
     * queries.
     *
     * @param index the elasticsearch index
     * @throws Exception the exception
     */
    public static void setMaxWindowSize(final String index) throws Exception {

        // Only do this if indexs use elasticsearch
        if (PropertyUtility.getProperties()
                .getProperty("spring.jpa.properties.hibernate.search.default.indexmanager").trim()
                .equals("elasticsearch")) {

            // First call and verify the index exists
            final String esHost = PropertyUtility.getProperties()
                    .getProperty("hibernate.search.default.elasticsearch.host");
            final String esUrl = esHost + "/" + index + "/_search";
            final Client client = ClientBuilder.newClient();
            WebTarget target = client.target(esUrl);
            try (Response response = target.request().get()) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    final String resultString = response.readEntity(String.class);

                    // if the index isn't found it then just log it and exit the
                    // function
                    if (resultString.contains("index_not_found_exception")) {
                        logger.warn("Elastic Search index not found = " + index);
                        return;
                    }

                    logger.error("Unexpected attempt to search index = " + index);
                    throw new WebApplicationException(response.readEntity(String.class),
                            response.getStatus());
                }
            }

            // Then, POST to make the max result window change
            final String postUrl = esHost + "/" + index + "/_settings";
            final String requestBody = "{\"index\" : {\"max_result_window\" : 2500000}}";
            target = client.target(postUrl);

            try (Response response = target.request().put(Entity.json(requestBody))) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    logger.error("Unexpected attempt to set max index size = " + index);
                    throw new WebApplicationException(response.readEntity(String.class),
                            response.getStatus());
                }
            }
        }
    }

}
