
package org.ihtsdo.refsetservice.util;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CaseFormat;

/**
 * Utility class for interacting with Strings.
 */
public final class StringUtility {

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(StringUtility.class);

    /** The Constant PUNCTUATION. */
    public static final String PUNCTUATION = " \t-({[)}]_!@#%&*\\:;\"',.?/~+=|<>$`^";

    /** The Constant NORM_PUNCTUATION. */
    public static final String NORM_PUNCTUATION = " \t-{}_!@#%&*\\:;,?/~+=|<>$`^";

    /** The Constant PUNCTUATION_REGEX. */
    public static final String PUNCTUATION_REGEX =
            "[ \\t\\-\\(\\{\\[\\)\\}\\]_!@#%&\\*\\\\:;\\\"',\\.\\?\\/~\\+=\\|<>$`^]";

    /** The Constant NORM_PUNCTUATION_REGEX. */
    public static final String NORM_PUNCTUATION_REGEX = "[ \\t\\-{}_!@#%&\\*\\\\:;,?/~+=|<>$`^]";

    /**
     * Instantiates an empty {@link StringUtility}.
     */
    private StringUtility() {
        // n/a
    }

    /**
     * To arabic.
     *
     * @param number the number
     * @return the int
     * @throws Exception the exception
     */
    public static int toArabic(final String number) throws Exception {
        if (number.isEmpty()) {
            return 0;
        }
        if (number.startsWith("M")) {
            return 1000 + toArabic(number.substring(1));
        }
        if (number.startsWith("CM")) {
            return 900 + toArabic(number.substring(2));
        }
        if (number.startsWith("D")) {
            return 500 + toArabic(number.substring(1));
        }
        if (number.startsWith("CD")) {
            return 400 + toArabic(number.substring(2));
        }
        if (number.startsWith("C")) {
            return 100 + toArabic(number.substring(1));
        }
        if (number.startsWith("XC")) {
            return 90 + toArabic(number.substring(2));
        }
        if (number.startsWith("L")) {
            return 50 + toArabic(number.substring(1));
        }
        if (number.startsWith("XL")) {
            return 40 + toArabic(number.substring(2));
        }
        if (number.startsWith("X")) {
            return 10 + toArabic(number.substring(1));
        }
        if (number.startsWith("IX")) {
            return 9 + toArabic(number.substring(2));
        }
        if (number.startsWith("V")) {
            return 5 + toArabic(number.substring(1));
        }
        if (number.startsWith("IV")) {
            return 4 + toArabic(number.substring(2));
        }
        if (number.startsWith("I")) {
            return 1 + toArabic(number.substring(1));
        }
        throw new Exception("something bad happened");
    }

    /**
     * Indicates whether or not roman numeral is the case.
     *
     * @param number the number
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isRomanNumeral(final String number) {
        return number.matches("^M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$");
    }

    /**
     * Converts string field to case-insensitive string of tokens with
     * punctuation removed For example, "HIV Infection" becomes "hiv infection",
     * while "1,2-hydroxy" becomes "1 2 hydroxy".
     *
     * @param value the value
     * @return the string
     */
    public static String normalize(final String value) {
        return ltrimNonAlpaNumeric(value).toLowerCase().replaceAll(PUNCTUATION_REGEX, " ")
                .replaceAll("\\s+", " ").trim();
    }

    /**
     * Trim non alpa numeric.
     *
     * @param value the value
     * @return the string
     */
    public static String trimNonAlpaNumeric(final String value) {
        return value.replaceFirst("^[^\\p{IsAlphabetic}\\p{IsDigit}]*", "")
                .replaceFirst("[^\\p{IsAlphabetic}\\p{IsDigit}]*$", "");
    }

    /**
     * Ltrim non alpa numeric.
     *
     * @param value the value
     * @return the string
     */
    public static String ltrimNonAlpaNumeric(final String value) {
        return value.replaceFirst("^[^\\p{IsAlphabetic}\\p{IsDigit}]*", "").trim();
    }

    /**
     * Reverse.
     *
     * @param string the string
     * @return the string
     */
    public static String reverse(final String string) {
        final List<String> list = Arrays.asList(string.split(" "));
        Collections.reverse(list);
        return FieldedStringTokenizer.join(list, " ").trim();
    }

    /**
     * Wordind.
     *
     * @param name the name
     * @return the list
     */
    public static List<String> wordind(final String name) {
        final String[] tokens = FieldedStringTokenizer.split(name, PUNCTUATION);
        return Arrays.asList(tokens).stream().filter(s -> s.length() > 0)
                .collect(Collectors.toList());
    }

    /**
     * Capitalize.
     *
     * @param value the value
     * @return the string
     */
    public static String capitalize(final String value) {
        if (StringUtility.isEmpty(value)) {
            return value;
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    /**
     * Capitalize each word.
     *
     * @param value the value
     * @return the string
     */
    public static String capitalizeEachWord(final String value) {
        if (StringUtility.isEmpty(value)) {
            return value;
        }
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        for (final String word : FieldedStringTokenizer.split(value, " ")) {
            if (i++ > 0) {
                sb.append(" ");
            }
            sb.append(capitalize(word));
        }
        return sb.toString();
    }

    /**
     * Un camel case.
     *
     * @param str the str
     * @return the string
     */
    public static String unCamelCase(final String str) {
        // insert a space between lower & upper
        return capitalize(str.replaceAll("([a-z])([A-Z])", "$1 $2")
                // space before last upper in a sequence followed by lower
                .replaceAll("\\b([A-Z]+)([A-Z])([a-z])", "$1 $2$3"));
    }

    /**
     * Camel case.
     *
     * @param str the str
     * @return the string
     */
    public static String camelCase(final String str) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, str.replaceAll(" ", "_"));
    }

    /**
     * Substr.
     *
     * @param string the string
     * @param len the len
     * @return the string
     */
    public static String substr(final String string, final int len) {
        return string.substring(0, Math.min(len, string.length()))
                + (string.length() > len ? "..." : "");
    }

    /**
     * Mask.
     *
     * @param string the string
     * @param start the start
     * @param end the end
     * @return the string
     */
    public static String mask(final String string, final int start, final int end) {
        return string.substring(0, start) + StringUtils.repeat("X", end - start)
                + string.substring(end);
    }

    /**
     * Indicates whether or not uuid is the case.
     *
     * @param uuid the uuid
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isUuid(final String uuid) {
        return uuid != null && uuid.matches(
                "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$");
    }

    /**
     * Indicates whether or not email is the case.
     *
     * @param email the email
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isEmail(final String email) {
        return email != null && email.matches("^[A-Za-z0-9\\.+]+@[A-Za-z0-9.-]+\\.[A-Za-z0-9.-]+$");
    }

    /**
     * Compose url.
     *
     * @param clauses the clauses
     * @return the string
     * @throws Exception the exception
     */
    public static String composeQueryString(final Map<String, String> clauses) throws Exception {
        final StringBuilder sb = new StringBuilder();
        for (final String key : clauses.keySet()) {
            // Skip empty key or value
            if (StringUtility.isEmpty(key)) {
                continue;
            }
            if (StringUtility.isEmpty(clauses.get(key))) {
                continue;
            }
            if (sb.length() > 1) {
                sb.append("&");
            }
            sb.append(key).append("=");
            final String value = clauses.get(key);
            if (value.matches("^[0-9a-zA-Z\\-\\.]*$")) {
                sb.append(value);
            } else {
                sb.append(URLEncoder.encode(value, "UTF-8").replaceAll("\\+", "%20"));
            }
        }
        return (sb.length() > 0 ? "?" + sb.toString() : "");
    }

    /**
     * Compose query from a list of possibly empty/null clauses and an operator
     * (typically OR or AND).
     *
     * @param operator the operator
     * @param clauses the clauses
     * @return the string
     */
    public static String composeQuery(final String operator, final List<String> clauses) {
        final StringBuilder sb = new StringBuilder();
        if (operator.equals("OR")) {
            sb.append("(");
        }
        for (final String clause : clauses) {
            if (StringUtility.isEmpty(clause)) {
                continue;
            }
            if (sb.length() > 0 && !operator.equals("OR")) {
                sb.append(" ").append(operator).append(" ");
            }
            if (sb.length() > 1 && operator.equals("OR")) {
                sb.append(" ").append(operator).append(" ");
            }
            sb.append(clause);
        }
        if (operator.equals("OR")) {
            sb.append(")");
        }
        if (operator.equals("OR") && sb.toString().equals("()")) {
            return "";
        }

        return sb.toString();
    }

    /**
     * Compose query.
     *
     * @param operator the operator
     * @param clauses the clauses
     * @return the string
     */
    public static String composeQuery(final String operator, final String... clauses) {
        final StringBuilder sb = new StringBuilder();
        if (operator.equals("OR")) {
            sb.append("(");
        }
        for (final String clause : clauses) {
            if (StringUtility.isEmpty(clause)) {
                continue;
            } else if (sb.length() > 0 && !operator.equals("OR")) {
                sb.append(" ").append(operator).append(" ");
            } else if (sb.length() > 1 && operator.equals("OR")) {
                sb.append(" ").append(operator).append(" ");
            }

            sb.append(clause);
        }
        if (operator.equals("OR")) {
            sb.append(")");
        }
        if (operator.equals("OR") && sb.toString().equals("()")) {
            return "";
        }

        return sb.toString();
    }

    /**
     * Compose clause.
     *
     * @param fieldName the field name
     * @param fieldValue the field value
     * @param escapeValue - whether the value can have characters that need to
     *            be escaped
     * @return the string
     * @throws Exception the exception
     */
    public static String composeClause(final String fieldName, final String fieldValue,
        final boolean escapeValue) throws Exception {

        if (!StringUtility.isEmpty(fieldValue)) {
            if (escapeValue) {
                return fieldName + ":\"" + QueryParserBase.escape(fieldValue) + "\"";
            } else {
                return fieldName + ":" + fieldValue;
            }
        } else {
            return "NOT " + fieldName + ":[* TO *]";
        }
    }

    /**
     * Indicates whether or not a string is empty.
     *
     * @param str the str
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isEmpty(final String str) {
        return str == null || str.isEmpty();
    }
}
