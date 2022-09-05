
package org.ihtsdo.refsetservice.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility for interacting with domain objects. TODO: clean this up and
 * reconcile with NormUtility (push all logic here).
 */
public final class ModelUtility {

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(ModelUtility.class);

    /** The resource dependency map. */
    private static Map<String, Set<String>> resourceDependencyMap = new HashMap<>();

    /** The Constant DEFAULT. */
    public static final String DEFAULT = "DEFAULT";

    static {
        resourceDependencyMap.put("Provider", asSet("Program"));
        resourceDependencyMap.put("Program",
                asSet("Endpoint", "Order", "Participant", "Template", "Schedule"));
        resourceDependencyMap.put("Endpoint", asSet());
        resourceDependencyMap.put("Order", asSet());
        resourceDependencyMap.put("Participant", asSet("Device"));
        resourceDependencyMap.put("Device", asSet("Observation", "FhirResource"));
    }

    /**
     * Instantiates an empty {@link ConfigUtility}.
     */
    private ModelUtility() {
        // n/a
    }

    /**
     * Both or neither null.
     *
     * @param a the a
     * @param b the b
     * @return true, if successful
     */
    public static boolean bothOrNeitherNull(final Object a, final Object b) {
        return (a == null && b == null) || (a != null && b != null);
    }

    /**
     * Returns the name from class by stripping package and putting spaces where
     * CamelCase is used.
     *
     * @param clazz the clazz
     * @return the name from class
     */
    public static String getNameFromClass(final Class<?> clazz) {
        return clazz.getName().substring(clazz.getName().lastIndexOf('.') + 1)
                .replaceAll(String.format("%s|%s|%s", "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])", "(?<=[A-Za-z])(?=[^A-Za-z])"), " ");
    }

    /**
     * Returns the min date.
     *
     * @param dates the dates
     * @return the min date
     */
    public static Date getMinDate(final Date... dates) {
        final Set<Date> set = new HashSet<>();
        for (final Date date : dates) {
            if (date != null) {
                set.add(date);
            }
        }
        return Collections.min(set);
    }

    /**
     * Returns the max date.
     *
     * @param dates the dates
     * @return the max date
     */
    public static Date getMaxDate(final Date... dates) {
        final Set<Date> set = new HashSet<>();
        for (final Date date : dates) {
            if (date != null) {
                set.add(date);
            }
        }
        return Collections.max(set);
    }

    /**
     * Compare null safe.
     *
     * @param a the a
     * @param b the b
     * @return true, if successful
     */
    public static boolean equalsNullSafe(final Object a, final Object b) {
        if ((a == null && b != null) || (a != null && b == null)) {
            return false;
        }
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    /**
     * Equals null match.
     *
     * @param a the a
     * @param b the b
     * @return true, if successful
     */
    public static boolean equalsNullMatch(final Object a, final Object b) {
        if ((a == null && b != null) || (a != null && b == null)) {
            return true;
        }
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    /**
     * First not null.
     *
     * @param <T> the
     * @param values the values
     * @return the t
     */
    public static <T> T firstNotNull(@SuppressWarnings("unchecked") final T... values) {
        for (final T t : values) {
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    /**
     * Returns the dependent resource types.
     *
     * @param type the type
     * @return the dependent resource types
     */
    public static Set<String> getDependentResourceTypes(final String type) {
        final Set<String> result = resourceDependencyMap.get(type);
        return result == null ? new HashSet<>() : result;
    }

    /**
     * Returns the graph for json.
     *
     * @param <T> the generic type
     * @param json the json
     * @param graphClass the graph class
     * @return the graph for json
     * @throws Exception the exception
     */
    public static <T> T toJson(final String json, final Class<T> graphClass) throws Exception {
        if (StringUtility.isEmpty(json)) {
            return null;
        }
        final ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, graphClass);

    }

    /**
     * Returns the graph for json node.
     *
     * @param <T> the
     * @param json the json
     * @param graphClass the graph class
     * @return the graph for json node
     * @throws Exception the exception
     */
    public static <T> T fromJson(final String json, final Class<T> graphClass) throws Exception {
        if (json == null) {
            return null;
        }
        final ObjectMapper mapper = new ObjectMapper();
        return mapper.treeToValue(mapper.readTree(json), graphClass);
    }

    /**
     * Returns the graph for json. sample usage:
     * 
     * @param <T> the
     * @param json the json
     * @param typeRef the type ref
     * @return the graph for json
     * @throws Exception the exception
     */
    public static <T> T fromJson(final String json, final TypeReference<T> typeRef)
        throws Exception {
        if (StringUtility.isEmpty(json)) {
            return null;
        }
        final InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        final ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(in, typeRef);
    }

    /**
     * Returns the json for graph.
     *
     * @param object the object
     * @return the json for graph
     * @throws Exception the exception
     */
    public static String toJson(final Object object) throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(object);
    }

    /**
     * To json.
     *
     * @param string the string
     * @return the json node
     * @throws Exception the exception
     */
    public static JsonNode toJsonNode(final String string) throws Exception {
        return new ObjectMapper().readTree(string);
    }

    /**
     * Pretty format json.
     *
     * @param input the input
     * @return the string
     * @throws JsonProcessingException the json processing exception
     */
    public static String prettyFormatJson(final Object input) throws JsonProcessingException {
        final ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(input);
    }

    /**
     * Pretty format json.
     *
     * @param input the input
     * @return the string
     * @throws Exception the exception
     */
    public static String prettyFormatJson(final String input) throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        return prettyFormatJson(mapper.readTree(input));
    }

    /**
     * Json copy.
     *
     * @param <T> the
     * @param o the o
     * @param graphClass the graph class
     * @return the t
     * @throws Exception the exception
     */
    public static <T> T jsonCopy(final Object o, final Class<T> graphClass) throws Exception {
        final String json = toJson(o);
        return fromJson(json, graphClass);
    }
    
    /**
     * Json copy.
     *
     * @param <T> the
     * @param o the o
     * @param typeRef the type reference
     * @return the t
     * @throws Exception the exception
     */
    public static <T> T jsonCopy(final Object o, final TypeReference<T> typeRef) throws Exception {
        final String json = toJson(o);
        return fromJson(json, typeRef);
    }

    /**
     * Reflection sort.
     *
     * @param <T> the
     * @param classes the classes
     * @param clazz the clazz
     * @param sortField the sort field
     * @throws Exception the exception
     */
    public static <T> void reflectionSort(final List<T> classes, final Class<T> clazz,
        final String sortField) throws Exception {

        final Method getMethod = clazz.getMethod(
                "get" + sortField.substring(0, 1).toUpperCase() + sortField.substring(1));
        if (getMethod.getReturnType().isAssignableFrom(Comparable.class)) {
            throw new Exception("Referenced sort field is not comparable");
        }
        Collections.sort(classes, new Comparator<T>() {
            @SuppressWarnings({
                    "rawtypes", "unchecked"
            })
            @Override
            public int compare(final T o1, final T o2) {
                try {
                    final Comparable f1 = (Comparable) getMethod.invoke(o1, new Object[] {});
                    final Comparable f2 = (Comparable) getMethod.invoke(o2, new Object[] {});
                    return f1.compareTo(f2);
                } catch (final Exception e) {
                    // do nothing
                }
                return 0;
            }
        });
    }

    /**
     * Indicates whether or not empty is the case.
     *
     * @param collection the collection
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isEmpty(final Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * As map.
     *
     * @param values the values
     * @return the map
     */
    public static Map<String, String> asMap(final String... values) {
        final Map<String, String> map = new HashMap<>();
        if (values.length % 2 != 0) {
            throw new RuntimeException("Unexpected odd number of parameters");
        }
        for (int i = 0; i < values.length; i += 2) {
            map.put(values[i], values[i + 1]);
        }
        return map;
    }

    /**
     * As list.
     *
     * @param values the values
     * @return the list
     */
    public static List<String> asList(final String[] values) {
        return new ArrayList<String>(Arrays.asList(values));
    }

    /**
     * As list.
     *
     * @param <T> the
     * @param values the values
     * @return the list
     */
    public static <T> List<T> asList(@SuppressWarnings("unchecked") final T... values) {
        final List<T> list = new ArrayList<>(values.length);
        for (final T value : values) {
            if (value != null) {
                list.add(value);
            }
        }
        return list;
    }

    /**
     * As set.
     *
     * @param <T> the
     * @param values the values
     * @return the sets the
     */
    public static <T> Set<T> asSet(@SuppressWarnings("unchecked") final T... values) {
        final Set<T> set = new HashSet<>(values.length);
        for (final T value : values) {
            if (value != null) {
                set.add(value);
            }
        }
        return set;
    }

    /**
     * As set.
     *
     * @param values the values
     * @return the list
     * @throws Exception the exception
     */
    public static Set<String> asSet(final String[] values) throws Exception {
        return new HashSet<String>(Arrays.asList(values));
    }

}
