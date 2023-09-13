/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.apache.lucene.queryparser.classic.QueryParser;
import org.hibernate.search.backend.elasticsearch.ElasticsearchExtension;
import org.hibernate.search.backend.elasticsearch.index.ElasticsearchIndexManager;
import org.hibernate.search.backend.elasticsearch.metamodel.ElasticsearchIndexDescriptor;
import org.hibernate.search.backend.lucene.LuceneExtension;
import org.hibernate.search.backend.lucene.index.LuceneIndexManager;
import org.hibernate.search.engine.backend.index.IndexManager;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.sort.SearchSort;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.mapping.SearchMapping;
import org.hibernate.search.mapper.orm.scope.SearchScope;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
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

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(IndexUtility.class);

    /** The sort field analyzed map. */
    private static Map<String, Map<String, Boolean>> sortFieldAnalyzedMap = new HashMap<>();

    /** The string field names map. */
    private static Map<Class<?>, Set<String>> stringFieldNames = new HashMap<>();

    /** The field names map. */
    private static Map<Class<?>, Set<String>> allFieldNames = new HashMap<>();

    /** The date field names map. */
    private static Map<Class<?>, Set<String>> dateFieldNames = new HashMap<>();

    /** The all fields map. */
    private static Map<Class<?>, java.lang.reflect.Field[]> allFields = new HashMap<>();

    /** The all fields map. */
    private static Map<Class<?>, java.lang.reflect.Method[]> allMethods = new HashMap<>();

    /** The all fields map. */
    private static List<Class<?>> dateClasses =
        Arrays.asList(Date.class, LocalDate.class, LocalDateTime.class, ZonedDateTime.class, Instant.class, OffsetDateTime.class);

    // Initialize the field names maps
    static {
        try {

            final Map<String, Class<?>> reindexMap = new HashMap<>();
            final String indexProp = PropertyUtility.getProperties().getProperty("app.entity_packages");

            if (indexProp == null) {
                throw new Exception("Property app.entity_packages must be present.");
            }
            final String[] packages = indexProp.split(";");
            final Reflections reflections = new Reflections(new ConfigurationBuilder().forPackages(packages));
            for (final Class<?> clazz : reflections.getTypesAnnotatedWith(Indexed.class)) {
                reindexMap.put(clazz.getSimpleName(), clazz);
            }
            final Class<?>[] classes = reindexMap.values().toArray(new Class<?>[0]);

            for (final Class<?> clazz : classes) {
                stringFieldNames.put(clazz, IndexUtility.getIndexedFieldNames(clazz, "string"));
                allFieldNames.put(clazz, IndexUtility.getIndexedFieldNames(clazz, "all"));
                dateFieldNames.put(clazz, IndexUtility.getIndexedFieldNames(clazz, "date"));
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the indexed field names for a given class.
     *
     * @param clazz the clazz
     * @param returnOnly the return only
     * @return the indexed field names
     * @throws Exception the exception
     */
    public static Set<String> getIndexedFieldNames(final Class<?> clazz, final String returnOnly) throws Exception {

        // If already initialized, return computed values
        if (returnOnly.equals("string") && stringFieldNames.containsKey(clazz)) {
            return stringFieldNames.get(clazz);
        }

        else if (returnOnly.equals("all") && allFieldNames.containsKey(clazz)) {
            return allFieldNames.get(clazz);
        }

        else if (returnOnly.equals("date") && dateFieldNames.containsKey(clazz)) {
            return dateFieldNames.get(clazz);
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
                final Class<?> jpaType = annotation.targetType();

                if (jpaType == null) {
                    throw new Exception("Unable to determine jpa type, @IndexedEmbedded must use " + "targetType");
                }

                for (final String embeddedField : getIndexedFieldNames(jpaType, returnOnly)) {
                    fieldNames.add(embeddedField);
                }
            }

            // determine if there's a fieldBridge (which converts the field)
            final boolean hasFieldBridge = false;

            // if (doesMethodHaveFieldAnnotation(m)) {
            //
            // // check all field annotations
            // for (final Object annotationField : getMethodAnnotations(m)) {
            //
            // if (doesAnnotationFieldHaveBridge(annotationField)) {
            //
            // hasFieldBridge = true;
            // break;
            // }
            // }
            // }

            // for non-embedded fields, only process strings
            // This is because we're handling string based query here
            // Other fields can always be used with fielded query clauses
            if (returnOnly.equals("string") && !hasFieldBridge && !m.getReturnType().equals(String.class)) {
                continue;

            } else if (returnOnly.equals("date") && !hasFieldBridge && !dateClasses.contains(m.getReturnType())) {
                continue;
            }

            // check for Fields annotation
            if (doesMethodHaveFieldAnnotation(m)) {

                // add all specified fields
                for (final Object annotationField : getMethodAnnotations(m)) {

                    final String fieldName = getFieldNameFromMethod(m, annotationField);
                    fieldNames.add(fieldName);
                }
            }
        }

        // second cycle over all fields
        for (final Field f : getAllFields(clazz)) {
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
                } else if (List.class.isAssignableFrom(f.getType()) || Set.class.isAssignableFrom(f.getType())) {

                    final String fieldName = getFieldNameFromField(f, getFieldAnnotation(f));
                    fieldNames.add(fieldName);
                    continue;
                } else {
                    throw new Exception(
                        "Unable to determine jpa type, @IndexedEmbedded must be used with " + "@OneToOne, @OneToMany, @ManyToOne, or @ManyToMany ");

                }

                for (final String embeddedField : getIndexedFieldNames(jpaType, returnOnly)) {
                    fieldNames.add(f.getName() + "." + embeddedField);
                }
            }

            // determine if there's a fieldBridge (which converts the field)
            final boolean hasFieldBridge = false;

            // if (doesFieldHaveFieldAnnotation(f)) {
            //
            // // check all field annotations
            // for (final Object annotationField : getFieldAnnotations(f)) {
            //
            // if (doesAnnotationFieldHaveBridge(annotationField)) {
            //
            // hasFieldBridge = true;
            // break;
            // }
            // }
            // }

            // for non-embedded fields, only process strings
            if (returnOnly.equals("string") && !hasFieldBridge && !f.getType().equals(String.class)) {
                continue;

            } else if (returnOnly.equals("date") && !hasFieldBridge && !dateClasses.contains(f.getType())) {
                continue;
            }

            // check for Fields annotation
            if (doesFieldHaveFieldAnnotation(f)) {

                // add all specified fields
                for (final Object annotationField : getFieldAnnotations(f)) {

                    final String fieldName = getFieldNameFromField(f, annotationField);
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
                if (returnOnly.equals("string") && fieldName.contains(exclusion)) {
                    continue OUTER;
                }
            }
            filteredFieldNames.add(fieldName);
        }

        // Always add "id" unless looking for only dates
        if (!returnOnly.equals("date")) {
            filteredFieldNames.add("id");
        }

        return filteredFieldNames;
    }

    /**
     * Helper function to get a field name from a method and annotation.
     *
     * @param method the reflected, annotated method, assumed to be of form getFieldName()
     * @param annotationField the annotation field
     * @return the indexed field name
     */
    public static String getFieldNameFromMethod(final Method method, final Object annotationField) {

        final String annotationName = getFieldName(annotationField);

        // first see if the annotationField has a name
        if (annotationName != null && !annotationName.isEmpty()) {
            return annotationName;
        }

        // otherwise, assume method name of form getannotationFieldName
        // where the desired value is annotationFieldName
        if (method.getName().startsWith("get")) {
            return StringUtils.uncapitalize(method.getName().substring(3));
        } else if (method.getName().startsWith("is")) {
            return StringUtils.uncapitalize(method.getName().substring(2));
        } else if (method.getName().startsWith("set")) {
            return StringUtils.uncapitalize(method.getName().substring(3));
        } else {
            return method.getName();
        }

    }

    /**
     * Helper function get a field name from reflected Field and annotation.
     *
     * @param annotatedField the reflected, annotated field
     * @param annotationField the field annotation
     * @return the indexed field name
     */
    private static String getFieldNameFromField(final Field annotatedField, final Object annotationField) {

        final String annotationName = getFieldName(annotationField);

        if (annotationName != null && !annotationName.isEmpty()) {
            return annotationName;
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
            final java.lang.reflect.Field[] allFieldsArray = ArrayUtils.addAll(getAllFields(type.getSuperclass()), type.getDeclaredFields());
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
            final java.lang.reflect.Method[] allMethodsArray = ArrayUtils.addAll(getAllMethods(type.getSuperclass()), type.getDeclaredMethods());
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
    public static Map<String, Boolean> getNameAnalyzedPairsFromAnnotation(final Class<?> clazz, final String sortField)
        throws NoSuchMethodException, SecurityException {

        final String key = clazz.getName() + "." + sortField;
        if (sortFieldAnalyzedMap.containsKey(key)) {
            return sortFieldAnalyzedMap.get(key);
        }

        // initialize the name->analyzed pair map
        final Map<String, Boolean> nameAnalyzedPairs = new HashMap<>();

        final Method m = clazz.getMethod("get" + sortField.substring(0, 1).toUpperCase() + sortField.substring(1), new Class<?>[] {});

        final Set<Object> annotationFields = new HashSet<>();

        // check for Fields annotation
        if (doesMethodHaveFieldAnnotation(m)) {

            // add all specified fields
            for (final Object f : getMethodAnnotations(m)) {
                annotationFields.add(f);
            }
        }

        // cycle over discovered fields and put name and analyze == YES into map
        for (final Object annotationField : annotationFields) {
            nameAnalyzedPairs.put(getFieldName(annotationField), isFieldAnalyzed(annotationField));
        }

        sortFieldAnalyzedMap.put(key, nameAnalyzedPairs);

        return nameAnalyzedPairs;
    }

    /**
     * Returns if a method has a field annotation.
     *
     * @param method the method to check
     * @return if the method has a field annotation
     */
    private static boolean doesMethodHaveFieldAnnotation(final Method method) {

        return (method.isAnnotationPresent(FullTextField.class) || method.isAnnotationPresent(GenericField.class)
            || method.isAnnotationPresent(KeywordField.class));
    }

    /**
     * Returns if a field has a field annotation.
     *
     * @param field the field to check
     * @return if the field has a field annotation
     */
    private static boolean doesFieldHaveFieldAnnotation(final Field field) {

        return (field.isAnnotationPresent(FullTextField.class) || field.isAnnotationPresent(GenericField.class)
            || field.isAnnotationPresent(KeywordField.class));
    }

    /**
     * Returns if a field is set to be analyzed.
     *
     * @param field the field to check
     * @return if a field is set to be analyzed
     */
    private static boolean isFieldAnalyzed(final Object field) {

        if (field.getClass() == FullTextField.class) {

            final FullTextField castedField = (FullTextField) field;

            return (castedField.analyzer() != null && !castedField.analyzer().equals("") && !castedField.analyzer().equals("default"));

        } else {

            return false;
        }
    }

    /**
     * Returns the name of a field annotation.
     *
     * @param field the field to get the name of
     * @return the field name
     */
    private static String getFieldName(final Object field) {

        if (field instanceof FullTextField) {
            return ((FullTextField) field).name();

        } else if (field instanceof KeywordField) {
            return ((KeywordField) field).name();

        } else {
            return ((GenericField) field).name();
        }
    }

    /**
     * Returns the name of a field annotation.
     *
     * @param field the field to get the name of
     * @return the field name
     */
    @SuppressWarnings("unused")
    private static boolean doesAnnotationFieldHaveBridge(final Object field) {

        if (field instanceof FullTextField) {

            return (((FullTextField) field).valueBridge().toString().equals("void"));

        } else if (field instanceof KeywordField) {

            return (((KeywordField) field).valueBridge().toString().equals("void"));

        } else {

            return (((GenericField) field).valueBridge().toString().equals("void"));
        }
    }

    /**
     * Returns the field annotation from a method.
     *
     * @param method the method to get annotation from
     * @return the field annotation
     */
    @SuppressWarnings("unused")
    private static Object getMethodAnnotation(final Method method) {

        if (method.isAnnotationPresent(FullTextField.class)) {
            return method.getAnnotation(FullTextField.class);

        } else if (method.isAnnotationPresent(KeywordField.class)) {
            return method.getAnnotation(KeywordField.class);

        } else {
            return method.getAnnotation(GenericField.class);
        }
    }

    /**
     * Returns all the field annotations from a method.
     *
     * @param method the method to get annotations from
     * @return a set of field annotations
     */
    private static Set<Object> getMethodAnnotations(final Method method) {

        final Set<Object> annotations = new HashSet<>();

        if (method.isAnnotationPresent(FullTextField.class)) {
            annotations.add(method.getAnnotation(FullTextField.class));
        }

        if (method.isAnnotationPresent(KeywordField.class)) {
            annotations.add(method.getAnnotation(KeywordField.class));
        }

        if (method.isAnnotationPresent(GenericField.class)) {
            annotations.add(method.getAnnotation(GenericField.class));
        }

        return annotations;
    }

    /**
     * Returns the field annotation from a field.
     *
     * @param field the field to get annotation from
     * @return the field annotation
     */
    private static Object getFieldAnnotation(final Field field) {

        if (field.isAnnotationPresent(FullTextField.class)) {
            return field.getAnnotation(FullTextField.class);

        } else if (field.isAnnotationPresent(KeywordField.class)) {
            return field.getAnnotation(KeywordField.class);

        } else {
            return field.getAnnotation(GenericField.class);
        }
    }

    /**
     * Returns all the field annotations from a field.
     *
     * @param field the field to get annotations from
     * @return a set of field annotations
     */
    private static Set<Object> getFieldAnnotations(final Field field) {

        final Set<Object> annotations = new HashSet<>();

        if (field.isAnnotationPresent(FullTextField.class)) {
            annotations.add(field.getAnnotation(FullTextField.class));
        }

        if (field.isAnnotationPresent(KeywordField.class)) {
            annotations.add(field.getAnnotation(KeywordField.class));
        }

        if (field.isAnnotationPresent(GenericField.class)) {
            annotations.add(field.getAnnotation(GenericField.class));
        }

        return annotations;
    }

    /**
     * Apply pfs to lucene query2.
     *
     * @param <T> the
     * @param clazz the clazz
     * @param query the query
     * @param pfs the pfs
     * @param manager the manager
     * @param projections the names of projections to use
     * @return the full text query
     * @throws Exception the exception
     */
    public static <T> SearchResult<T> applyPfsToLuceneQuery(final Class<T> clazz, final String query, final PfsParameter pfs, final EntityManager manager,
        final List<String> projections) throws Exception {

        final SearchSession searchSession = Search.session(manager);
        final SearchMapping mapping = Search.mapping(manager.getEntityManagerFactory());

        // Build up the query
        final StringBuilder pfsQuery = new StringBuilder();
        pfsQuery.append(StringUtility.isEmpty(query) ? "*:*" : query);

        // Set up the "full text query"

        // construct the query
        String finalQuery = (pfsQuery.toString().startsWith(" AND ")) ? pfsQuery.toString().substring(5) : pfsQuery.toString();

        SearchResult<T> result;
        final SearchScope<T> scope = searchSession.scope(clazz);
        final SearchPredicateFactory predicateFactory = scope.predicate();
        SearchPredicate predicate;

        final Set<String> dateFieldNames = IndexUtility.getIndexedFieldNames(clazz, "date");

        // Directory indexmanager
        if (!PropertyUtility.getProperties().getProperty("spring.jpa.properties.hibernate.search.backend.type").trim().equals("elasticsearch")) {

            @SuppressWarnings("resource")
			final QueryParser queryParser = new MultiFieldQueryParser(IndexUtility.getIndexedFieldNames(clazz, "string").toArray(new String[] {}),
                mapping.indexedEntity(clazz).indexManager().unwrap(LuceneIndexManager.class).searchAnalyzer());

            predicate = predicateFactory.extension(LuceneExtension.get()).fromLuceneQuery(queryParser.parse(finalQuery)).toPredicate();
        }

        // elasticsearch index manager
        else if (PropertyUtility.getProperties().getProperty("spring.jpa.properties.hibernate.search.backend.type").trim().equals("elasticsearch")) {

            String fullQueryString = "";

            boolean hasDate = false;
            for (final String dateFieldName : dateFieldNames) {
                if (!hasDate && finalQuery.contains(dateFieldName + ":")) {
                    hasDate = true;
                    continue;
                }
            }

            if (hasDate) {

                final String booleanQueryFormat = "{ \"bool\": { \"must\": [ #QUERY_STRING#, #DATE_RANGES# ] } }";
                final String dateQueryFormat = "{\"range\": {\"#DATE_FIELD_NAME#\": {\"gte\": \"#DATE#\",\"lte\": \"#DATE#\",\"format\": \"uuuu-MM-dd\"}}}";

                final Map<String, String> dateSegments = new HashMap<>();
                // remove date segments from string and add to list.
                for (final String dateFieldName : dateFieldNames) {

                    if (finalQuery.contains(dateFieldName + ":")) {
                        final int startPosition = finalQuery.indexOf(dateFieldName);
                        // 11 = : + number of characters in date (10)
                        final int endPosition = startPosition + dateFieldName.length() + 11;
                        final String dateSubString = finalQuery.substring(startPosition, endPosition);
                        final String[] dateQuery = dateSubString.split(":");

                        dateSegments.put(dateSubString, dateQueryFormat.replace("#DATE_FIELD_NAME#", dateQuery[0]).replace("#DATE#", dateQuery[1]));
                    }
                }

                String dateFreeQueryString = finalQuery;
                String dateRangeQueryString = "";

                for (final Map.Entry<String, String> dateSegment : dateSegments.entrySet()) {

                    String joinString = "";

                    if (dateFreeQueryString.contains(" AND " + dateSegment.getKey())) {
                        joinString = " AND ";

                    } else if (dateFreeQueryString.contains(" OR " + dateSegment.getKey())) {
                        joinString = " OR ";
                    }

                    dateFreeQueryString = dateFreeQueryString.replace(joinString + dateSegment.getKey(), "");
                    dateRangeQueryString += dateSegment.getValue() + " , ";
                }

                dateRangeQueryString = StringUtils.removeEnd(dateRangeQueryString, " , ");

                // Remove leading AND OR and clear out empty query_string
                dateFreeQueryString = dateFreeQueryString.replace("() AND", "").replace("( AND", " (").replace("( OR", " (").replace("() OR", "");
                dateFreeQueryString = StringUtils.removeStart(dateFreeQueryString, " AND ");
                dateFreeQueryString = StringUtils.removeStart(dateFreeQueryString, " OR ");

                final String queryString = "{\"query_string\":{\"default_operator\": \"AND\", \"analyze_wildcard\": true, \"query\":\""
                    + StringEscapeUtils.escapeJson(dateFreeQueryString) + "\"}}";

                fullQueryString = booleanQueryFormat.replace("#QUERY_STRING#", queryString).replace("#DATE_RANGES#", dateRangeQueryString.toString());

            } else {

                // Remove leading AND OR and clear out empty query_string
                finalQuery = finalQuery.replace("() AND", "").replace("( AND", " (").replace("( OR", " (").replace("() OR", "");
                finalQuery = StringUtils.removeStart(finalQuery, " AND ");
                finalQuery = StringUtils.removeStart(finalQuery, " OR ");

                fullQueryString = "{\"query_string\":{\"default_operator\": \"AND\", \"analyze_wildcard\": true, \"query\":\""
                    + StringEscapeUtils.escapeJson(finalQuery) + "\"}}";
            }

            // Need to escape double-quotes for the json
            predicate = predicateFactory.extension(ElasticsearchExtension.get()).fromJson(fullQueryString).toPredicate();
        }

        // Unknown indexmanager type
        else {
            throw new Exception("Unsupported spring.jpa.properties.hibernate.search.backend.type = "
                + PropertyUtility.getProperties().getProperty("spring.jpa.properties.hibernate.search.backend.type"));
        }

        // the constructed sort fields to sort on
        final List<SearchSort> sortFields = new ArrayList<>();

        // Handle sort and paging parameters
        if (pfs != null) {

            if ((pfs.getSortFields() != null && !pfs.getSortFields().isEmpty()) || (pfs.getSort() != null && !pfs.getSort().isEmpty())) {

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

                for (String sortFieldName : sortFieldNames) {

                    // the computed string name of the indexed field to sort by
                    String sortFieldStr = null;
                    String sortDirection = null;
                    SearchSort searchSort;

                    if (sortFieldName.contains(" asc")) {

                        sortFieldName = sortFieldName.replace(" asc", "");
                        sortDirection = "asc";

                    } else if (sortFieldName.contains(" desc")) {

                        sortFieldName = sortFieldName.replace(" desc", "");
                        sortDirection = "desc";
                    }

                    // if a subfield search (e.g. FIELD1.FIELD2) skip
                    // preconditions
                    if (sortFieldName.contains(".")) {
                        sortFieldStr = sortFieldName;
                    }

                    // otherwise, check preconditions
                    else {

                        final Map<String, Boolean> nameToAnalyzedMap = IndexUtility.getNameAnalyzedPairsFromAnnotation(clazz, sortFieldName);

                        // check existence of the annotated get[OffsetName]()
                        // method
                        if (nameToAnalyzedMap.size() == 0) {
                            throw new Exception(clazz.getName() + " does not have declared, annotated method for field " + sortFieldName);
                        }

                        // first, check explicit [OffsetName]Sort index
                        if (nameToAnalyzedMap.get(sortFieldName + "Sort") != null && !nameToAnalyzedMap.get(sortFieldName + "Sort")) {
                            sortFieldStr = sortFieldName + "Sort";
                        }

                        // next check the default name (rendered as ""), if not
                        // analyzed,
                        // use
                        // this as sort
                        else if (nameToAnalyzedMap.get("") != null && nameToAnalyzedMap.get("").equals(false)) {
                            sortFieldStr = sortFieldName;
                        }

                        // if an indexed sort field could not be found, throw
                        // exception
                        if (sortFieldStr == null) {
                            throw new Exception("Could not retrieve a non-analyzed Field " + "annotation for get method for variable name " + sortFieldName);
                        }
                    }

                    if (sortDirection != null && sortDirection.equals("asc")) {
                        searchSort = scope.sort().field(sortFieldStr).asc().toSort();

                    } else if (sortDirection != null && sortDirection.equals("desc")) {
                        searchSort = scope.sort().field(sortFieldStr).desc().toSort();

                    } else if (pfs.isAscending()) {
                        searchSort = scope.sort().field(sortFieldStr).asc().toSort();
                    } else {
                        searchSort = scope.sort().field(sortFieldStr).desc().toSort();
                    }

                    // add the field
                    sortFields.add(searchSort);
                }
            }
        }

        // the constructed projections
        // SearchProjection<T> projectionSelect = (SearchProjection<T>) scope.projection().score().toProjection();
        //
        // if (projections.contains("score")) {
        // projectionSelect = (SearchProjection<T>) scope.projection().score().toProjection();
        //
        // } else if (projections.contains("entity")) {
        // projectionSelect = scope.projection().entity().toProjection();
        //
        // } else if (projections.contains("id")) {
        // projectionSelect = (SearchProjection<T>) scope.projection().entityReference().toProjection();
        // }

        final SearchQuery<T> searchQuery = searchSession.search(scope)
            // .select(projectionSelect)
            .where(predicate).sort(f -> f.composite(sortBuilder -> {
                for (final SearchSort sortField : sortFields) {
                    sortBuilder.add(sortField);
                }
            })).toQuery();

        // LOG.debug("###*********### ElasticSearch QueryString: " + searchQuery.queryString());

        // if start index and max results are set, set paging
        if (pfs != null && pfs.getOffset() >= 0 && pfs.getLimit() >= 0) {
            result = searchQuery.fetch(pfs.getOffset(), pfs.getLimit());
        } else {
            result = searchQuery.fetch(0, 200000);
        }

        return result;
    }

    /**
     * Add wildcard suffixes to a query.
     *
     * @param <T> the
     * @param query the query to modify
     * @param clazz the clazz
     * @return the modified query
     * @throws Exception the exception
     */
    public static <T> String addWildcardsToQuery(final String query, final Class<T> clazz) throws Exception {

        if (query == null || query.equals("")) {
            return query;
        }

        String wildcardQuery = query;
        final Pattern regex =
            Pattern.compile("[a-zA-Z0-9_]+:[\"\\s]*([" + Pattern.quote("+@$.#=&|><!{}[]^~*?\\/") + "\\-a-zA-Z0-9_\\s]*?)(?:\\)|\\sAND?|\\sOR|\"|$)");
        final Matcher regexMatcher = regex.matcher(wildcardQuery);
        final Set<String> stringFieldNames = IndexUtility.getIndexedFieldNames(clazz, "string");
        int matchIndexCounter = 0;

        // remove specific fields that should not have wildcards applied
        stringFieldNames
            .removeAll(Arrays.asList("editionShortName", "editionBranch", "id", "organizationId", "projectId", "editBranchId", "refsetBranchId", "moduleId"));

        while (regexMatcher.find()) {

            // only add wildcards to String fields
            if (stringFieldNames.stream().anyMatch(field -> {
                return regexMatcher.group(0).contains(field + ":");
            })) {

                LOG.debug("******** WILDCARD MATCH: " + regexMatcher.group(1));
                // if the end position of the match isn't the end of the string then append a wildcard between the match and the rest of the string
                if (regexMatcher.end(1) < wildcardQuery.length() - 1) {
                    wildcardQuery = wildcardQuery.substring(0, regexMatcher.end(1) + matchIndexCounter) + "*"
                        + wildcardQuery.substring(regexMatcher.end(1) + matchIndexCounter);
                } else {
                    wildcardQuery = wildcardQuery.replace(")", "*)");
                }

                matchIndexCounter++;

                // wildcardQuery = wildcardQuery.replace(regexMatcher.group(1), regexMatcher.group(1) + "*");
            }
        }

        return wildcardQuery;
    }

    /**
     * Sets the max window size on an index for returning large elasticsearch queries.
     *
     * @param index the elasticsearch index
     * @param manager the manager
     * @throws Exception the exception
     */
    public static void setMaxWindowSize(final String index, final EntityManager manager) throws Exception {

        // Only do this if indexs use elasticsearch
        if (PropertyUtility.getProperties().getProperty("spring.jpa.properties.hibernate.search.backend.type").trim().equals("elasticsearch")) {

            final SearchMapping mapping = Search.mapping(manager.getEntityManagerFactory());
            final IndexManager indexManager = mapping.indexManager(index);
            final ElasticsearchIndexManager esIndexManager = indexManager.unwrap(ElasticsearchIndexManager.class);
            final ElasticsearchIndexDescriptor descriptor = esIndexManager.descriptor();
            final String indexReadName = descriptor.readName();
            final String indexWriteName = descriptor.writeName();

            // First call and verify the index exists
            final String esProtocol = PropertyUtility.getProperties().getProperty("spring.jpa.properties.hibernate.search.backend.protocol");
            final String esHost = PropertyUtility.getProperties().getProperty("spring.jpa.properties.hibernate.search.backend.hosts");
            final String esUrl = esProtocol + "://" + esHost + "/" + indexReadName + "/_search";
            final Client client = ClientBuilder.newClient();
            WebTarget target = client.target(esUrl);
            try (Response response = target.request().get()) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    final String resultString = response.readEntity(String.class);

                    // if the index isn't found it then just log it and exit the
                    // function
                    if (resultString.contains("index_not_found_exception")) {
                        LOG.warn("Elastic Search index not found = " + index);
                        return;
                    }

                    LOG.error("Unexpected attempt to search index = " + index);
                    throw new WebApplicationException(response.readEntity(String.class), response.getStatus());
                }
            }

            // Then, POST to make the max result window change
            final String postUrl = esProtocol + "://" + esHost + "/" + indexWriteName + "/_settings";
            final String requestBody = "{\"index\" : {\"max_result_window\" : 2500000}}";
            target = client.target(postUrl);

            try (Response response = target.request().put(Entity.json(requestBody))) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    LOG.error("Unexpected attempt to set max index size = " + index);
                    throw new WebApplicationException(response.readEntity(String.class), response.getStatus());
                }
            }
        }
    }

}
