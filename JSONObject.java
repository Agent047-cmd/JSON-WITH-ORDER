package json;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Pattern;

/*
 Copyright (c) 2002 JSON.org

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 The Software shall be used for Good, not Evil.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */


/**
 * A JSONObject is an unordered collection of name/value pairs. Its external
 * form is a string wrapped in curly braces with colons between the names and
 * values, and commas between the values and names. The internal form is an
 * object having <code>get</code> and <code>opt</code> methods for accessing
 * the values by name, and <code>put</code> methods for adding or replacing
 * values by name. The values can be any of these types: <code>Boolean</code>,
 * <code>JSONArray</code>, <code>JSONObject</code>, <code>Number</code>,
 * <code>String</code>, or the <code>JSONObject.NULL</code> object. A
 * JSONObject constructor can be used to convert an external form JSON text
 * into an internal form whose values can be retrieved with the
 * <code>get</code> and <code>opt</code> methods, or to convert values into a
 * JSON text using the <code>put</code> and <code>toString</code> methods. A
 * <code>get</code> method returns a value if one can be found, and throws an
 * exception if one cannot be found. An <code>opt</code> method returns a
 * default value instead of throwing an exception, and so is useful for
 * obtaining optional values.
 * <p>
 * The generic <code>get()</code> and <code>opt()</code> methods return an
 * object, which you can cast or query for type. There are also typed
 * <code>get</code> and <code>opt</code> methods that do type checking and type
 * coercion for you. The opt methods differ from the get methods in that they
 * do not throw. Instead, they return a specified value, such as null.
 * <p>
 * The <code>put</code> methods add or replace values in an object. For
 * example,
 *
 * <pre>
 * myString = new JSONObject()
 *         .put(&quot;JSON&quot;, &quot;Hello, World!&quot;).toString();
 * </pre>
 *
 * produces the string <code>{"JSON": "Hello, World"}</code>.
 * <p>
 * The texts produced by the <code>toString</code> methods strictly conform to
 * the JSON syntax rules. The constructors are more forgiving in the texts they
 * will accept:
 * <ul>
 * <li>An extra <code>,</code>&nbsp;<small>(comma)</small> may appear just
 * before the closing brace.</li>
 * <li>Strings may be quoted with <code>'</code>&nbsp;<small>(single
 * quote)</small>.</li>
 * <li>Strings do not need to be quoted at all if they do not begin with a
 * quote or single quote, and if they do not contain leading or trailing
 * spaces, and if they do not contain any of these characters:
 * <code>{ } [ ] / \ : , #</code> and if they do not look like numbers and
 * if they are not the reserved words <code>true</code>, <code>false</code>,
 * or <code>null</code>.</li>
 * </ul>
 *
 * @author JSON.org
 * @version 2016-08-15
 */
public class JSONObject {
    /**
     * JSONObject.NULL is equivalent to the value that JavaScript calls null,
     * whilst Java's null is equivalent to the value that JavaScript calls
     * undefined.
     */
    private static final class Null {

        /**
         * There is only intended to be a single instance of the NULL object,
         * so the clone method returns itself.
         *
         * @return NULL.
         */
        @Override
        protected final Object clone() {
            return this;
        }

        /**
         * A Null object is equal to the null value and to itself.
         *
         * @param object
         *            An object to test for nullness.
         * @return true if the object parameter is the JSONObject.NULL object or
         *         null.
         */
        @Override
        @SuppressWarnings("lgtm[java/unchecked-cast-in-equals]")
        public boolean equals(Object object) {
            return object == null || object == this;
        }
        /**
         * A Null object is equal to the null value and to itself.
         *
         * @return always returns 0.
         */
        @Override
        public int hashCode() {
            return 0;
        }

        /**
         * Get the "null" string value.
         *
         * @return The string "null".
         */
        @Override
        public String toString() {
            return "null";
        }
    }

    /**
     *  Regular Expression Pattern that matches JSON Numbers. This is primarily used for
     *  output to guarantee that we are always writing valid JSON.
     */
    static final Pattern NUMBER_PATTERN = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    /**
     * The map where the JSONObject's properties are kept.
     */
    private final Map<String, Object> map;

    /**
     * It is sometimes more convenient and less ambiguous to have a
     * <code>NULL</code> object than to use Java's <code>null</code> value.
     * <code>JSONObject.NULL.equals(null)</code> returns <code>true</code>.
     * <code>JSONObject.NULL.toString()</code> returns <code>"null"</code>.
     */
    public static final Object NULL = new Null();

    /**
     * Construct an empty JSONObject.
     */
    public JSONObject() {
        // HashMap is used on purpose to ensure that elements are unordered by
        // the specification.
        // JSON tends to be a portable transfer format to allows the container
        // implementations to rearrange their items for a faster element
        // retrieval based on associative access.
        // Therefore, an implementation mustn't rely on the order of the item.
        this.map = new LinkedHashMap<String, Object>();
    }

    /**
     * Construct a JSONObject from a subset of another JSONObject. An array of
     * strings is used to identify the keys that should be copied. Missing keys
     * are ignored.
     *
     * @param jo
     *            A JSONObject.
     * @param names
     *            An array of strings.
     */
    public JSONObject(JSONObject jo, String ... names) {
        this(names.length);
        for (int i = 0; i < names.length; i += 1) {
            try {
                this.putOnce(names[i], jo.opt(names[i]));
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Construct a JSONObject from a JSONTokener.
     *
     * @param x
     *            A JSONTokener object containing the source string.
     * @throws JSONException
     *             If there is a syntax error in the source string or a
     *             duplicated key.
     */
    public JSONObject(JSONTokener x) throws JSONException {
        this();
        char c;
        String key;

        if (x.nextClean() != '{') {
            throw x.syntaxError("A JSONObject text must begin with '{'");
        }
        for (;;) {
            char prev = x.getPrevious();
            c = x.nextClean();
            switch (c) {
            case 0:
                throw x.syntaxError("A JSONObject text must end with '}'");
            case '}':
                return;
            case '{':
            case '[':
                if(prev=='{') {
                    throw x.syntaxError("A JSON Object can not directly nest another JSON Object or JSON Array.");
                }
                // fall through
            default:
                x.back();
                key = x.nextValue().toString();
            }

            // The key is followed by ':'.

            c = x.nextClean();
            if (c != ':') {
                throw x.syntaxError("Expected a ':' after a key");
            }

            // Use syntaxError(..) to include error location

            if (key != null) {
                // Check if key exists
                if (this.opt(key) != null) {
                    // key already exists
                    throw x.syntaxError("Duplicate key \"" + key + "\"");
                }
                // Only add value if non-null
                Object value = x.nextValue();
                if (value!=null) {
                    this.put(key, value);
                }
            }

            // Pairs are separated by ','.

            switch (x.nextClean()) {
            case ';':
            case ',':
                if (x.nextClean() == '}') {
                    return;
                }
                x.back();
                break;
            case '}':
                return;
            default:
                throw x.syntaxError("Expected a ',' or '}'");
            }
        }
    }

    /**
     * Construct a JSONObject from a Map.
     *
     * @param m
     *            A map object that can be used to initialize the contents of
     *            the JSONObject.
     * @throws JSONException
     *            If a value in the map is non-finite number.
     * @throws NullPointerException
     *            If a key in the map is <code>null</code>
     */
    public JSONObject(Map<?, ?> m) {
        if (m == null) {
            this.map = new HashMap<String, Object>();
        } else {
            this.map = new HashMap<String, Object>(m.size());
        	for (final Entry<?, ?> e : m.entrySet()) {
        	    if(e.getKey() == null) {
        	        throw new NullPointerException("Null key.");
        	    }
                final Object value = e.getValue();
                if (value != null) {
                    this.map.put(String.valueOf(e.getKey()), wrap(value));
                }
            }
        }
    }

    /**
     * Construct a JSONObject from an Object using bean getters. It reflects on
     * all of the public methods of the object. For each of the methods with no
     * parameters and a name starting with <code>"get"</code> or
     * <code>"is"</code> followed by an uppercase letter, the method is invoked,
     * and a key and the value returned from the getter method are put into the
     * new JSONObject.
     * <p>
     * The key is formed by removing the <code>"get"</code> or <code>"is"</code>
     * prefix. If the second remaining character is not upper case, then the
     * first character is converted to lower case.
     * <p>
     * Methods that are <code>static</code>, return <code>void</code>,
     * have parameters, or are "bridge" methods, are ignored.
     * <p>
     * For example, if an object has a method named <code>"getName"</code>, and
     * if the result of calling <code>object.getName()</code> is
     * <code>"Larry Fine"</code>, then the JSONObject will contain
     * <code>"name": "Larry Fine"</code>.
     * <p>
     * The {@link JSONPropertyName} annotation can be used on a bean getter to
     * override key name used in the JSONObject. For example, using the object
     * above with the <code>getName</code> method, if we annotated it with:
     * <pre>
     * &#64;JSONPropertyName("FullName")
     * public String getName() { return this.name; }
     * </pre>
     * The resulting JSON object would contain <code>"FullName": "Larry Fine"</code>
     * <p>
     * Similarly, the {@link JSONPropertyName} annotation can be used on non-
     * <code>get</code> and <code>is</code> methods. We can also override key
     * name used in the JSONObject as seen below even though the field would normally
     * be ignored:
     * <pre>
     * &#64;JSONPropertyName("FullName")
     * public String fullName() { return this.name; }
     * </pre>
     * The resulting JSON object would contain <code>"FullName": "Larry Fine"</code>
     * <p>
     * The {@link JSONPropertyIgnore} annotation can be used to force the bean property
     * to not be serialized into JSON. If both {@link JSONPropertyIgnore} and
     * {@link JSONPropertyName} are defined on the same method, a depth comparison is
     * performed and the one closest to the concrete class being serialized is used.
     * If both annotations are at the same level, then the {@link JSONPropertyIgnore}
     * annotation takes precedent and the field is not serialized.
     * For example, the following declaration would prevent the <code>getName</code>
     * method from being serialized:
     * <pre>
     * &#64;JSONPropertyName("FullName")
     * &#64;JSONPropertyIgnore
     * public String getName() { return this.name; }
     * </pre>
     * <p>
     *
     * @param bean
     *            An object that has getter methods that should be used to make
     *            a JSONObject.
     */
    public JSONObject(Object bean) {
        this();
        this.populateMap(bean);
    }

    private JSONObject(Object bean, Set<Object> objectsRecord) {
        this();
        this.populateMap(bean, objectsRecord);
    }

    /**
     * Construct a JSONObject from an Object, using reflection to find the
     * public members. The resulting JSONObject's keys will be the strings from
     * the names array, and the values will be the field values associated with
     * those keys in the object. If a key is not found or not visible, then it
     * will not be copied into the new JSONObject.
     *
     * @param object
     *            An object that has fields that should be used to make a
     *            JSONObject.
     * @param names
     *            An array of strings, the names of the fields to be obtained
     *            from the object.
     */
    public JSONObject(Object object, String ... names) {
        this(names.length);
        Class<?> c = object.getClass();
        for (int i = 0; i < names.length; i += 1) {
            String name = names[i];
            try {
                this.putOpt(name, c.getField(name).get(object));
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Construct a JSONObject from a source JSON text string. This is the most
     * commonly used JSONObject constructor.
     *
     * @param source
     *            A string beginning with <code>{</code>&nbsp;<small>(left
     *            brace)</small> and ending with <code>}</code>
     *            &nbsp;<small>(right brace)</small>.
     * @exception JSONException
     *                If there is a syntax error in the source string or a
     *                duplicated key.
     */
    public JSONObject(String source) throws JSONException {
        this(new JSONTokener(source));
    }

    /**
     * Construct a JSONObject from a ResourceBundle.
     *
     * @param baseName
     *            The ResourceBundle base name.
     * @param locale
     *            The Locale to load the ResourceBundle for.
     * @throws JSONException
     *             If any JSONExceptions are detected.
     */
    public JSONObject(String baseName, Locale locale) throws JSONException {
        this();
        ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale,
                Thread.currentThread().getContextClassLoader());

// Iterate through the keys in the bundle.

        Enumeration<String> keys = bundle.getKeys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (key != null) {

// Go through the path, ensuring that there is a nested JSONObject for each
// segment except the last. Add the value using the last segment's name into
// the deepest nested JSONObject.

                String[] path = ((String) key).split("\\.");
                int last = path.length - 1;
                JSONObject target = this;
                for (int i = 0; i < last; i += 1) {
                    String segment = path[i];
                    JSONObject nextTarget = target.optJSONObject(segment);
                    if (nextTarget == null) {
                        nextTarget = new JSONObject();
                        target.put(segment, nextTarget);
                    }
                    target = nextTarget;
                }
                target.put(path[last], bundle.getString((String) key));
            }
        }
    }

    /**
     * Constructor to specify an initial capacity of the internal map. Useful for library
     * internal calls where we know, or at least can best guess, how big this JSONObject
     * will be.
     *
     * @param initialCapacity initial capacity of the internal map.
     */
    protected JSONObject(int initialCapacity){
        this.map = new HashMap<String, Object>(initialCapacity);
    }

    /**
     * Accumulate values under a key. It is similar to the put method except
     * that if there is already an object stored under the key then a JSONArray
     * is stored under the key to hold all of the accumulated values. If there
     * is already a JSONArray, then the new value is appended to it. In
     * contrast, the put method replaces the previous value.
     *
     * If only one value is accumulated that is not a JSONArray, then the result
     * will be the same as using put. But if multiple values are accumulated,
     * then the result will be like append.
     *
     * @param key
     *            A key string.
     * @param value
     *            An object to be accumulated under the key.
     * @return this.
     * @throws JSONException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject accumulate(String key, Object value) throws JSONException {
        testValidity(value);
        Object object = this.opt(key);
        if (object == null) {
            this.put(key,
                    value instanceof JSONArray ? new JSONArray().put(value)
                            : value);
        } else if (object instanceof JSONArray) {
            ((JSONArray) object).put(value);
        } else {
            this.put(key, new JSONArray().put(object).put(value));
        }
        return this;
    }

    /**
     * Append values to the array under a key. If the key does not exist in the
     * JSONObject, then the key is put in the JSONObject with its value being a
     * JSONArray containing the value parameter. If the key was already
     * associated with a JSONArray, then the value parameter is appended to it.
     *
     * @param key
     *            A key string.
     * @param value
     *            An object to be accumulated under the key.
     * @return this.
     * @throws JSONException
     *            If the value is non-finite number or if the current value associated with
     *             the key is not a JSONArray.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject append(String key, Object value) throws JSONException {
        testValidity(value);
        Object object = this.opt(key);
        if (object == null) {
            this.put(key, new JSONArray().put(value));
        } else if (object instanceof JSONArray) {
            this.put(key, ((JSONArray) object).put(value));
        } else {
            throw wrongValueFormatException(key, "JSONArray", null, null);
        }
        return this;
    }

    /**
     * Produce a string from a double. The string "null" will be returned if the
     * number is not finite.
     *
     * @param d
     *            A double.
     * @return A String.
     */
    public static String doubleToString(double d) {
        if (Double.isInfinite(d) || Double.isNaN(d)) {
            return "null";
        }

// Shave off trailing zeros and decimal point, if possible.

        String string = Double.toString(d);
        if (string.indexOf('.') > 0 && string.indexOf('e') < 0
                && string.indexOf('E') < 0) {
            while (string.endsWith("0")) {
                string = string.substring(0, string.length() - 1);
            }
            if (string.endsWith(".")) {
                string = string.substring(0, string.length() - 1);
            }
        }
        return string;
    }

    /**
     * Get the value object associated with a key.
     *
     * @param key
     *            A key string.
     * @return The object associated with the key.
     * @throws JSONException
     *             if the key is not found.
     */
    public Object get(String key) throws JSONException {
        if (key == null) {
            throw new JSONException("Null key.");
        }
        Object object = this.opt(key);
        if (object == null) {
            throw new JSONException("JSONObject[" + quote(key) + "] not found.");
        }
        return object;
    }

    /**
     * Get the enum value associated with a key.
     *
     * @param <E>
     *            Enum Type
     * @param clazz
     *           The type of enum to retrieve.
     * @param key
     *           A key string.
     * @return The enum value associated with the key
     * @throws JSONException
     *             if the key is not found or if the value cannot be converted
     *             to an enum.
     */
    public <E extends Enum<E>> E getEnum(Class<E> clazz, String key) throws JSONException {
        E val = optEnum(clazz, key);
        if(val==null) {
            // JSONException should really take a throwable argument.
            // If it did, I would re-implement this with the Enum.valueOf
            // method and place any thrown exception in the JSONException
            throw wrongValueFormatException(key, "enum of type " + quote(clazz.getSimpleName()), null);
        }
        return val;
    }

    /**
     * Get the boolean value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The truth.
     * @throws JSONException
     *             if the value is not a Boolean or the String "true" or
     *             "false".
     */
    public boolean getBoolean(String key) throws JSONException {
        Object object = this.get(key);
        if (object.equals(Boolean.FALSE)
                || (object instanceof String && ((String) object)
                        .equalsIgnoreCase("false"))) {
            return false;
        } else if (object.equals(Boolean.TRUE)
                || (object instanceof String && ((String) object)
                        .equalsIgnoreCase("true"))) {
            return true;
        }
        throw wrongValueFormatException(key, "Boolean", null);
    }

    /**
     * Get the BigInteger value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The numeric value.
     * @throws JSONException
     *             if the key is not found or if the value cannot
     *             be converted to BigInteger.
     */
    public BigInteger getBigInteger(String key) throws JSONException {
        Object object = this.get(key);
        BigInteger ret = objectToBigInteger(object, null);
        if (ret != null) {
            return ret;
        }
        throw wrongValueFormatException(key, "BigInteger", object, null);
    }

    /**
     * Get the BigDecimal value associated with a key. If the value is float or
     * double, the {@link BigDecimal#BigDecimal(double)} constructor will
     * be used. See notes on the constructor for conversion issues that may
     * arise.
     *
     * @param key
     *            A key string.
     * @return The numeric value.
     * @throws JSONException
     *             if the key is not found or if the value
     *             cannot be converted to BigDecimal.
     */
    public BigDecimal getBigDecimal(String key) throws JSONException {
        Object object = this.get(key);
        BigDecimal ret = objectToBigDecimal(object, null);
        if (ret != null) {
            return ret;
        }
        throw wrongValueFormatException(key, "BigDecimal", object, null);
    }

    /**
     * Get the double value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The numeric value.
     * @throws JSONException
     *             if the key is not found or if the value is not a Number
     *             object and cannot be converted to a number.
     */
    public double getDouble(String key) throws JSONException {
        final Object object = this.get(key);
        if(object instanceof Number) {
            return ((Number)object).doubleValue();
        }
        try {
            return Double.parseDouble(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(key, "double", e);
        }
    }

    /**
     * Get the float value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The numeric value.
     * @throws JSONException
     *             if the key is not found or if the value is not a Number
     *             object and cannot be converted to a number.
     */
    public float getFloat(String key) throws JSONException {
        final Object object = this.get(key);
        if(object instanceof Number) {
            return ((Number)object).floatValue();
        }
        try {
            return Float.parseFloat(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(key, "float", e);
        }
    }

    /**
     * Get the Number value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The numeric value.
     * @throws JSONException
     *             if the key is not found or if the value is not a Number
     *             object and cannot be converted to a number.
     */
    public Number getNumber(String key) throws JSONException {
        Object object = this.get(key);
        try {
            if (object instanceof Number) {
                return (Number)object;
            }
            return stringToNumber(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(key, "number", e);
        }
    }

    /**
     * Get the int value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The integer value.
     * @throws JSONException
     *             if the key is not found or if the value cannot be converted
     *             to an integer.
     */
    public int getInt(String key) throws JSONException {
        final Object object = this.get(key);
        if(object instanceof Number) {
            return ((Number)object).intValue();
        }
        try {
            return Integer.parseInt(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(key, "int", e);
        }
    }

    /**
     * Get the JSONArray value associated with a key.
     *
     * @param key
     *            A key string.
     * @return A JSONArray which is the value.
     * @throws JSONException
     *             if the key is not found or if the value is not a JSONArray.
     */
    public JSONArray getJSONArray(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof JSONArray) {
            return (JSONArray) object;
        }
        throw wrongValueFormatException(key, "JSONArray", null);
    }

    /**
     * Get the JSONObject value associated with a key.
     *
     * @param key
     *            A key string.
     * @return A JSONObject which is the value.
     * @throws JSONException
     *             if the key is not found or if the value is not a JSONObject.
     */
    public JSONObject getJSONObject(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof JSONObject) {
            return (JSONObject) object;
        }
        throw wrongValueFormatException(key, "JSONObject", null);
    }

    /**
     * Get the long value associated with a key.
     *
     * @param key
     *            A key string.
     * @return The long value.
     * @throws JSONException
     *             if the key is not found or if the value cannot be converted
     *             to a long.
     */
    public long getLong(String key) throws JSONException {
        final Object object = this.get(key);
        if(object instanceof Number) {
            return ((Number)object).longValue();
        }
        try {
            return Long.parseLong(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(key, "long", e);
        }
    }

    /**
     * Get an array of field names from a JSONObject.
     *
     * @param jo
     *            JSON object
     * @return An array of field names, or null if there are no names.
     */
    public static String[] getNames(JSONObject jo) {
        if (jo.isEmpty()) {
            return null;
        }
        return jo.keySet().toArray(new String[jo.length()]);
    }

    /**
     * Get an array of public field names from an Object.
     *
     * @param object
     *            object to read
     * @return An array of field names, or null if there are no names.
     */
    public static String[] getNames(Object object) {
        if (object == null) {
            return null;
        }
        Class<?> klass = object.getClass();
        Field[] fields = klass.getFields();
        int length = fields.length;
        if (length == 0) {
            return null;
        }
        String[] names = new String[length];
        for (int i = 0; i < length; i += 1) {
            names[i] = fields[i].getName();
        }
        return names;
    }

    /**
     * Get the string associated with a key.
     *
     * @param key
     *            A key string.
     * @return A string which is the value.
     * @throws JSONException
     *             if there is no string value for the key.
     */
    public String getString(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof String) {
            return (String) object;
        }
        throw wrongValueFormatException(key, "string", null);
    }

    /**
     * Determine if the JSONObject contains a specific key.
     *
     * @param key
     *            A key string.
     * @return true if the key exists in the JSONObject.
     */
    public boolean has(String key) {
        return this.map.containsKey(key);
    }

    /**
     * Increment a property of a JSONObject. If there is no such property,
     * create one with a value of 1 (Integer). If there is such a property, and if it is
     * an Integer, Long, Double, Float, BigInteger, or BigDecimal then add one to it.
     * No overflow bounds checking is performed, so callers should initialize the key
     * prior to this call with an appropriate type that can handle the maximum expected
     * value.
     *
     * @param key
     *            A key string.
     * @return this.
     * @throws JSONException
     *             If there is already a property with this name that is not an
     *             Integer, Long, Double, or Float.
     */
    public JSONObject increment(String key) throws JSONException {
        Object value = this.opt(key);
        if (value == null) {
            this.put(key, 1);
        } else if (value instanceof Integer) {
            this.put(key, ((Integer) value).intValue() + 1);
        } else if (value instanceof Long) {
            this.put(key, ((Long) value).longValue() + 1L);
        } else if (value instanceof BigInteger) {
            this.put(key, ((BigInteger)value).add(BigInteger.ONE));
        } else if (value instanceof Float) {
            this.put(key, ((Float) value).floatValue() + 1.0f);
        } else if (value instanceof Double) {
            this.put(key, ((Double) value).doubleValue() + 1.0d);
        } else if (value instanceof BigDecimal) {
            this.put(key, ((BigDecimal)value).add(BigDecimal.ONE));
        } else {
            throw new JSONException("Unable to increment [" + quote(key) + "].");
        }
        return this;
    }

    /**
     * Determine if the value associated with the key is <code>null</code> or if there is no
     * value.
     *
     * @param key
     *            A key string.
     * @return true if there is no value associated with the key or if the value
     *        is the JSONObject.NULL object.
     */
    public boolean isNull(String key) {
        return JSONObject.NULL.equals(this.opt(key));
    }

    /**
     * Get an enumeration of the keys of the JSONObject. Modifying this key Set will also
     * modify the JSONObject. Use with caution.
     *
     * @see Set#iterator()
     *
     * @return An iterator of the keys.
     */
    public Iterator<String> keys() {
        return this.keySet().iterator();
    }

    /**
     * Get a set of keys of the JSONObject. Modifying this key Set will also modify the
     * JSONObject. Use with caution.
     *
     * @see Map#keySet()
     *
     * @return A keySet.
     */
    public Set<String> keySet() {
        return this.map.keySet();
    }

    /**
     * Get a set of entries of the JSONObject. These are raw values and may not
     * match what is returned by the JSONObject get* and opt* functions. Modifying
     * the returned EntrySet or the Entry objects contained therein will modify the
     * backing JSONObject. This does not return a clone or a read-only view.
     *
     * Use with caution.
     *
     * @see Map#entrySet()
     *
     * @return An Entry Set
     */
    protected Set<Entry<String, Object>> entrySet() {
        return this.map.entrySet();
    }

    /**
     * Get the number of keys stored in the JSONObject.
     *
     * @return The number of keys in the JSONObject.
     */
    public int length() {
        return this.map.size();
    }

    /**
     * Removes all of the elements from this JSONObject.
     * The JSONObject will be empty after this call returns.
     */
    public void clear() {
        this.map.clear();
    }

    /**
     * Check if JSONObject is empty.
     *
     * @return true if JSONObject is empty, otherwise false.
     */
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    /**
     * Produce a JSONArray containing the names of the elements of this
     * JSONObject.
     *
     * @return A JSONArray containing the key strings, or null if the JSONObject
     *        is empty.
     */
    public JSONArray names() {
    	if(this.map.isEmpty()) {
    		return null;
    	}
        return new JSONArray(this.map.keySet());
    }

    /**
     * Produce a string from a Number.
     *
     * @param number
     *            A Number
     * @return A String.
     * @throws JSONException
     *             If n is a non-finite number.
     */
    public static String numberToString(Number number) throws JSONException {
        if (number == null) {
            throw new JSONException("Null pointer");
        }
        testValidity(number);

        // Shave off trailing zeros and decimal point, if possible.

        String string = number.toString();
        if (string.indexOf('.') > 0 && string.indexOf('e') < 0
                && string.indexOf('E') < 0) {
            while (string.endsWith("0")) {
                string = string.substring(0, string.length() - 1);
            }
            if (string.endsWith(".")) {
                string = string.substring(0, string.length() - 1);
            }
        }
        return string;
    }

    /**
     * Get an optional value associated with a key.
     *
     * @param key
     *            A key string.
     * @return An object which is the value, or null if there is no value.
     */
    public Object opt(String key) {
        return key == null ? null : this.map.get(key);
    }

    /**
     * Get the enum value associated with a key.
     *
     * @param <E>
     *            Enum Type
     * @param clazz
     *            The type of enum to retrieve.
     * @param key
     *            A key string.
     * @return The enum value associated with the key or null if not found
     */
    public <E extends Enum<E>> E optEnum(Class<E> clazz, String key) {
        return this.optEnum(clazz, key, null);
    }

    /**
     * Get the enum value associated with a key.
     *
     * @param <E>
     *            Enum Type
     * @param clazz
     *            The type of enum to retrieve.
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default in case the value is not found
     * @return The enum value associated with the key or defaultValue
     *            if the value is not found or cannot be assigned to <code>clazz</code>
     */
    public <E extends Enum<E>> E optEnum(Class<E> clazz, String key, E defaultValue) {
        try {
            Object val = this.opt(key);
            if (NULL.equals(val)) {
                return defaultValue;
            }
            if (clazz.isAssignableFrom(val.getClass())) {
                // we just checked it!
                @SuppressWarnings("unchecked")
                E myE = (E) val;
                return myE;
            }
            return Enum.valueOf(clazz, val.toString());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        } catch (NullPointerException e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional boolean associated with a key. It returns false if there
     * is no such key, or if the value is not Boolean.TRUE or the String "true".
     *
     * @param key
     *            A key string.
     * @return The truth.
     */
    public boolean optBoolean(String key) {
        return this.optBoolean(key, false);
    }

    /**
     * Get an optional boolean associated with a key. It returns the
     * defaultValue if there is no such key, or if it is not a Boolean or the
     * String "true" or "false" (case insensitive).
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return The truth.
     */
    public boolean optBoolean(String key, boolean defaultValue) {
        Object val = this.opt(key);
        if (NULL.equals(val)) {
            return defaultValue;
        }
        if (val instanceof Boolean){
            return ((Boolean) val).booleanValue();
        }
        try {
            // we'll use the get anyway because it does string conversion.
            return this.getBoolean(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional BigDecimal associated with a key, or the defaultValue if
     * there is no such key or if its value is not a number. If the value is a
     * string, an attempt will be made to evaluate it as a number. If the value
     * is float or double, then the {@link BigDecimal#BigDecimal(double)}
     * constructor will be used. See notes on the constructor for conversion
     * issues that may arise.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public BigDecimal optBigDecimal(String key, BigDecimal defaultValue) {
        Object val = this.opt(key);
        return objectToBigDecimal(val, defaultValue);
    }

    /**
     * @param val value to convert
     * @param defaultValue default value to return is the conversion doesn't work or is null.
     * @return BigDecimal conversion of the original value, or the defaultValue if unable
     *          to convert.
     */
    static BigDecimal objectToBigDecimal(Object val, BigDecimal defaultValue) {
        return objectToBigDecimal(val, defaultValue, true);
    }
    
    /**
     * @param val value to convert
     * @param defaultValue default value to return is the conversion doesn't work or is null.
     * @param exact When <code>true</code>, then {@link Double} and {@link Float} values will be converted exactly.
     *      When <code>false</code>, they will be converted to {@link String} values before converting to {@link BigDecimal}.
     * @return BigDecimal conversion of the original value, or the defaultValue if unable
     *          to convert.
     */
    static BigDecimal objectToBigDecimal(Object val, BigDecimal defaultValue, boolean exact) {
        if (NULL.equals(val)) {
            return defaultValue;
        }
        if (val instanceof BigDecimal){
            return (BigDecimal) val;
        }
        if (val instanceof BigInteger){
            return new BigDecimal((BigInteger) val);
        }
        if (val instanceof Double || val instanceof Float){
            if (!numberIsFinite((Number)val)) {
                return defaultValue;
            }
            if (exact) {
                return new BigDecimal(((Number)val).doubleValue());
            }else {
                // use the string constructor so that we maintain "nice" values for doubles and floats
                // the double constructor will translate doubles to "exact" values instead of the likely
                // intended representation
                return new BigDecimal(val.toString());
            }
        }
        if (val instanceof Long || val instanceof Integer
                || val instanceof Short || val instanceof Byte){
            return new BigDecimal(((Number) val).longValue());
        }
        // don't check if it's a string in case of unchecked Number subclasses
        try {
            return new BigDecimal(val.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional BigInteger associated with a key, or the defaultValue if
     * there is no such key or if its value is not a number. If the value is a
     * string, an attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public BigInteger optBigInteger(String key, BigInteger defaultValue) {
        Object val = this.opt(key);
        return objectToBigInteger(val, defaultValue);
    }

    /**
     * @param val value to convert
     * @param defaultValue default value to return is the conversion doesn't work or is null.
     * @return BigInteger conversion of the original value, or the defaultValue if unable
     *          to convert.
     */
    static BigInteger objectToBigInteger(Object val, BigInteger defaultValue) {
        if (NULL.equals(val)) {
            return defaultValue;
        }
        if (val instanceof BigInteger){
            return (BigInteger) val;
        }
        if (val instanceof BigDecimal){
            return ((BigDecimal) val).toBigInteger();
        }
        if (val instanceof Double || val instanceof Float){
            if (!numberIsFinite((Number)val)) {
                return defaultValue;
            }
            return new BigDecimal(((Number) val).doubleValue()).toBigInteger();
        }
        if (val instanceof Long || val instanceof Integer
                || val instanceof Short || val instanceof Byte){
            return BigInteger.valueOf(((Number) val).longValue());
        }
        // don't check if it's a string in case of unchecked Number subclasses
        try {
            // the other opt functions handle implicit conversions, i.e.
            // jo.put("double",1.1d);
            // jo.optInt("double"); -- will return 1, not an error
            // this conversion to BigDecimal then to BigInteger is to maintain
            // that type cast support that may truncate the decimal.
            final String valStr = val.toString();
            if(isDecimalNotation(valStr)) {
                return new BigDecimal(valStr).toBigInteger();
            }
            return new BigInteger(valStr);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional double associated with a key, or NaN if there is no such
     * key or if its value is not a number. If the value is a string, an attempt
     * will be made to evaluate it as a number.
     *
     * @param key
     *            A string which is the key.
     * @return An object which is the value.
     */
    public double optDouble(String key) {
        return this.optDouble(key, Double.NaN);
    }

    /**
     * Get an optional double associated with a key, or the defaultValue if
     * there is no such key or if its value is not a number. If the value is a
     * string, an attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public double optDouble(String key, double defaultValue) {
        Number val = this.optNumber(key);
        if (val == null) {
            return defaultValue;
        }
        final double doubleValue = val.doubleValue();
        // if (Double.isNaN(doubleValue) || Double.isInfinite(doubleValue)) {
        // return defaultValue;
        // }
        return doubleValue;
    }

    /**
     * Get the optional double value associated with an index. NaN is returned
     * if there is no value for the index, or if the value is not a number and
     * cannot be converted to a number.
     *
     * @param key
     *            A key string.
     * @return The value.
     */
    public float optFloat(String key) {
        return this.optFloat(key, Float.NaN);
    }

    /**
     * Get the optional double value associated with an index. The defaultValue
     * is returned if there is no value for the index, or if the value is not a
     * number and cannot be converted to a number.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default value.
     * @return The value.
     */
    public float optFloat(String key, float defaultValue) {
        Number val = this.optNumber(key);
        if (val == null) {
            return defaultValue;
        }
        final float floatValue = val.floatValue();
        // if (Float.isNaN(floatValue) || Float.isInfinite(floatValue)) {
        // return defaultValue;
        // }
        return floatValue;
    }

    /**
     * Get an optional int value associated with a key, or zero if there is no
     * such key or if the value is not a number. If the value is a string, an
     * attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @return An object which is the value.
     */
    public int optInt(String key) {
        return this.optInt(key, 0);
    }

    /**
     * Get an optional int value associated with a key, or the default if there
     * is no such key or if the value is not a number. If the value is a string,
     * an attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public int optInt(String key, int defaultValue) {
        final Number val = this.optNumber(key, null);
        if (val == null) {
            return defaultValue;
        }
        return val.intValue();
    }

    /**
     * Get an optional JSONArray associated with a key. It returns null if there
     * is no such key, or if its value is not a JSONArray.
     *
     * @param key
     *            A key string.
     * @return A JSONArray which is the value.
     */
    public JSONArray optJSONArray(String key) {
        Object o = this.opt(key);
        return o instanceof JSONArray ? (JSONArray) o : null;
    }

    /**
     * Get an optional JSONObject associated with a key. It returns null if
     * there is no such key, or if its value is not a JSONObject.
     *
     * @param key
     *            A key string.
     * @return A JSONObject which is the value.
     */
    public JSONObject optJSONObject(String key) { return this.optJSONObject(key, null); }

    /**
     * Get an optional JSONObject associated with a key, or the default if there
     * is no such key or if the value is not a JSONObject.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An JSONObject which is the value.
     */
    public JSONObject optJSONObject(String key, JSONObject defaultValue) {
        Object object = this.opt(key);
        return object instanceof JSONObject ? (JSONObject) object : defaultValue;
    }

    /**
     * Get an optional long value associated with a key, or zero if there is no
     * such key or if the value is not a number. If the value is a string, an
     * attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @return An object which is the value.
     */
    public long optLong(String key) {
        return this.optLong(key, 0);
    }

    /**
     * Get an optional long value associated with a key, or the default if there
     * is no such key or if the value is not a number. If the value is a string,
     * an attempt will be made to evaluate it as a number.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public long optLong(String key, long defaultValue) {
        final Number val = this.optNumber(key, null);
        if (val == null) {
            return defaultValue;
        }

        return val.longValue();
    }

    /**
     * Get an optional {@link Number} value associated with a key, or <code>null</code>
     * if there is no such key or if the value is not a number. If the value is a string,
     * an attempt will be made to evaluate it as a number ({@link BigDecimal}). This method
     * would be used in cases where type coercion of the number value is unwanted.
     *
     * @param key
     *            A key string.
     * @return An object which is the value.
     */
    public Number optNumber(String key) {
        return this.optNumber(key, null);
    }

    /**
     * Get an optional {@link Number} value associated with a key, or the default if there
     * is no such key or if the value is not a number. If the value is a string,
     * an attempt will be made to evaluate it as a number. This method
     * would be used in cases where type coercion of the number value is unwanted.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public Number optNumber(String key, Number defaultValue) {
        Object val = this.opt(key);
        if (NULL.equals(val)) {
            return defaultValue;
        }
        if (val instanceof Number){
            return (Number) val;
        }

        try {
            return stringToNumber(val.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get an optional string associated with a key. It returns an empty string
     * if there is no such key. If the value is not a string and is not null,
     * then it is converted to a string.
     *
     * @param key
     *            A key string.
     * @return A string which is the value.
     */
    public String optString(String key) {
        return this.optString(key, "");
    }

    /**
     * Get an optional string associated with a key. It returns the defaultValue
     * if there is no such key.
     *
     * @param key
     *            A key string.
     * @param defaultValue
     *            The default.
     * @return A string which is the value.
     */
    public String optString(String key, String defaultValue) {
        Object object = this.opt(key);
        return NULL.equals(object) ? defaultValue : object.toString();
    }

    /**
     * Populates the internal map of the JSONObject with the bean properties. The
     * bean can not be recursive.
     *
     * @see JSONObject#JSONObject(Object)
     *
     * @param bean
     *            the bean
     */
    private void populateMap(Object bean) {
        populateMap(bean, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()));
    }

    private void populateMap(Object bean, Set<Object> objectsRecord) {
        Class<?> klass = bean.getClass();

        // If klass is a System class then set includeSuperClass to false.

        boolean includeSuperClass = klass.getClassLoader() != null;

        Method[] methods = includeSuperClass ? klass.getMethods() : klass.getDeclaredMethods();
        for (final Method method : methods) {
            final int modifiers = method.getModifiers();
            if (Modifier.isPublic(modifiers)
                    && !Modifier.isStatic(modifiers)
                    && method.getParameterTypes().length == 0
                    && !method.isBridge()
                    && method.getReturnType() != Void.TYPE
                    && isValidMethodName(method.getName())) {
                final String key = getKeyNameFromMethod(method);
                if (key != null && !key.isEmpty()) {
                    try {
                        final Object result = method.invoke(bean);
                        if (result != null) {
                            // check cyclic dependency and throw error if needed
                            // the wrap and populateMap combination method is 
                            // itself DFS recursive
                            if (objectsRecord.contains(result)) {
                                throw recursivelyDefinedObjectException(key);
                            }
                            
                            objectsRecord.add(result);

                            this.map.put(key, wrap(result, objectsRecord));

                            objectsRecord.remove(result);

                            // we don't use the result anywhere outside of wrap
                            // if it's a resource we should be sure to close it
                            // after calling toString 
                            if (result instanceof Closeable) {
                                try {
                                    ((Closeable) result).close();
                                } catch (IOException ignore) {
                                }
                            }
                        }
                    } catch (IllegalAccessException ignore) {
                    } catch (IllegalArgumentException ignore) {
                    } catch (InvocationTargetException ignore) {
                    }
                }
            }
        }
    }

    private static boolean isValidMethodName(String name) {
        return !"getClass".equals(name) && !"getDeclaringClass".equals(name);
    }

    private static String getKeyNameFromMethod(Method method) {
        final int ignoreDepth = getAnnotationDepth(method, JSONPropertyIgnore.class);
        if (ignoreDepth > 0) {
            final int forcedNameDepth = getAnnotationDepth(method, JSONPropertyName.class);
            if (forcedNameDepth < 0 || ignoreDepth <= forcedNameDepth) {
                // the hierarchy asked to ignore, and the nearest name override
                // was higher or non-existent
                return null;
            }
        }
        JSONPropertyName annotation = getAnnotation(method, JSONPropertyName.class);
        if (annotation != null && annotation.value() != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        String key;
        final String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) {
            key = name.substring(3);
        } else if (name.startsWith("is") && name.length() > 2) {
            key = name.substring(2);
        } else {
            return null;
        }
        // if the first letter in the key is not uppercase, then skip.
        // This is to maintain backwards compatibility before PR406
        // (https://github.com/stleary/JSON-java/pull/406/)
        if (key.length() == 0 || Character.isLowerCase(key.charAt(0))) {
            return null;
        }
        if (key.length() == 1) {
            key = key.toLowerCase(Locale.ROOT);
        } else if (!Character.isUpperCase(key.charAt(1))) {
            key = key.substring(0, 1).toLowerCase(Locale.ROOT) + key.substring(1);
        }
        return key;
    }

    /**
     * Searches the class hierarchy to see if the method or it's super
     * implementations and interfaces has the annotation.
     *
     * @param <A>
     *            type of the annotation
     *
     * @param m
     *            method to check
     * @param annotationClass
     *            annotation to look for
     * @return the {@link Annotation} if the annotation exists on the current method
     *         or one of its super class definitions
     */
    private static <A extends Annotation> A getAnnotation(final Method m, final Class<A> annotationClass) {
        // if we have invalid data the result is null
        if (m == null || annotationClass == null) {
            return null;
        }

        if (m.isAnnotationPresent(annotationClass)) {
            return m.getAnnotation(annotationClass);
        }

        // if we've already reached the Object class, return null;
        Class<?> c = m.getDeclaringClass();
        if (c.getSuperclass() == null) {
            return null;
        }

        // check directly implemented interfaces for the method being checked
        for (Class<?> i : c.getInterfaces()) {
            try {
                Method im = i.getMethod(m.getName(), m.getParameterTypes());
                return getAnnotation(im, annotationClass);
            } catch (final SecurityException ex) {
                continue;
            } catch (final NoSuchMethodException ex) {
                continue;
            }
        }

        try {
            return getAnnotation(
                    c.getSuperclass().getMethod(m.getName(), m.getParameterTypes()),
                    annotationClass);
        } catch (final SecurityException ex) {
            return null;
        } catch (final NoSuchMethodException ex) {
            return null;
        }
    }

    /**
     * Searches the class hierarchy to see if the method or it's super
     * implementations and interfaces has the annotation. Returns the depth of the
     * annotation in the hierarchy.
     *
     * @param m
     *            method to check
     * @param annotationClass
     *            annotation to look for
     * @return Depth of the annotation or -1 if the annotation is not on the method.
     */
    private static int getAnnotationDepth(final Method m, final Class<? extends Annotation> annotationClass) {
        // if we have invalid data the result is -1
        if (m == null || annotationClass == null) {
            return -1;
        }

        if (m.isAnnotationPresent(annotationClass)) {
            return 1;
        }

        // if we've already reached the Object class, return -1;
        Class<?> c = m.getDeclaringClass();
        if (c.getSuperclass() == null) {
            return -1;
        }

        // check directly implemented interfaces for the method being checked
        for (Class<?> i : c.getInterfaces()) {
            try {
                Method im = i.getMethod(m.getName(), m.getParameterTypes());
                int d = getAnnotationDepth(im, annotationClass);
                if (d > 0) {
                    // since the annotation was on the interface, add 1
                    return d + 1;
                }
            } catch (final SecurityException ex) {
                continue;
            } catch (final NoSuchMethodException ex) {
                continue;
            }
        }

        try {
            int d = getAnnotationDepth(
                    c.getSuperclass().getMethod(m.getName(), m.getParameterTypes()),
                    annotationClass);
            if (d > 0) {
                // since the annotation was on the superclass, add 1
                return d + 1;
            }
            return -1;
        } catch (final SecurityException ex) {
            return -1;
        } catch (final NoSuchMethodException ex) {
            return -1;
        }
    }

    /**
     * Put a key/boolean pair in the JSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            A boolean which is the value.
     * @return this.
     * @throws JSONException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, boolean value) throws JSONException {
        return this.put(key, value ? Boolean.TRUE : Boolean.FALSE);
    }

    /**
     * Put a key/value pair in the JSONObject, where the value will be a
     * JSONArray which is produced from a Collection.
     *
     * @param key
     *            A key string.
     * @param value
     *            A Collection value.
     * @return this.
     * @throws JSONException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, Collection<?> value) throws JSONException {
        return this.put(key, new JSONArray(value));
    }

    /**
     * Put a key/double pair in the JSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            A double which is the value.
     * @return this.
     * @throws JSONException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, double value) throws JSONException {
        return this.put(key, Double.valueOf(value));
    }

    /**
     * Put a key/float pair in the JSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            A float which is the value.
     * @return this.
     * @throws JSONException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, float value) throws JSONException {
        return this.put(key, Float.valueOf(value));
    }

    /**
     * Put a key/int pair in the JSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            An int which is the value.
     * @return this.
     * @throws JSONException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, int value) throws JSONException {
        return this.put(key, Integer.valueOf(value));
    }

    /**
     * Put a key/long pair in the JSONObject.
     *
     * @param key
     *            A key string.
     * @param value
     *            A long which is the value.
     * @return this.
     * @throws JSONException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, long value) throws JSONException {
        return this.put(key, Long.valueOf(value));
    }

    /**
     * Put a key/value pair in the JSONObject, where the value will be a
     * JSONObject which is produced from a Map.
     *
     * @param key
     *            A key string.
     * @param value
     *            A Map value.
     * @return this.
     * @throws JSONException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, Map<?, ?> value) throws JSONException {
        return this.put(key, new JSONObject(value));
    }

    /**
     * Put a key/value pair in the JSONObject. If the value is <code>null</code>, then the
     * key will be removed from the JSONObject if it is present.
     *
     * @param key
     *            A key string.
     * @param value
     *            An object which is the value. It should be of one of these
     *            types: Boolean, Double, Integer, JSONArray, JSONObject, Long,
     *            String, or the JSONObject.NULL object.
     * @return this.
     * @throws JSONException
     *            If the value is non-finite number.
     * @throws NullPointerException
     *            If the key is <code>null</code>.
     */
    public JSONObject put(String key, Object value) throws JSONException {
        if (key == null) {
            throw new NullPointerException("Null key.");
        }
        if (value != null) {
            testValidity(value);
            this.map.put(key, value);
        } else {
            this.remove(key);
        }
        return this;
    }

    /**
     * Put a key/value pair in the JSONObject, but only if the key and the value
     * are both non-null, and only if there is not already a member with that
     * name.
     *
     * @param key
     *            key to insert into
     * @param value
     *            value to insert
     * @return this.
     * @throws JSONException
     *             if the key is a duplicate
     */
    public JSONObject putOnce(String key, Object value) throws JSONException {
        if (key != null && value != null) {
            if (this.opt(key) != null) {
                throw new JSONException("Duplicate key \"" + key + "\"");
            }
            return this.put(key, value);
        }
        return this;
    }

    /**
     * Put a key/value pair in the JSONObject, but only if the key and the value
     * are both non-null.
     *
     * @param key
     *            A key string.
     * @param value
     *            An object which is the value. It should be of one of these
     *            types: Boolean, Double, Integer, JSONArray, JSONObject, Long,
     *            String, or the JSONObject.NULL object.
     * @return this.
     * @throws JSONException
     *             If the value is a non-finite number.
     */
    public JSONObject putOpt(String key, Object value) throws JSONException {
        if (key != null && value != null) {
            return this.put(key, value);
        }
        return this;
    }

    /**
     * Creates a JSONPointer using an initialization string and tries to
     * match it to an item within this JSONObject. For example, given a
     * JSONObject initialized with this document:
     * <pre>
     * {
     *     "a":{"b":"c"}
     * }
     * </pre>
     * and this JSONPointer string:
     * <pre>
     * "/a/b"
     * </pre>
     * Then this method will return the String "c".
     * A JSONPointerException may be thrown from code called by this method.
     *
     * @param jsonPointer string that can be used to create a JSONPointer
     * @return the item matched by the JSONPointer, otherwise null
     */
    public Object query(String jsonPointer) {
        return query(new JSONPointer(jsonPointer));
    }
    /**
     * Uses a user initialized JSONPointer  and tries to
     * match it to an item within this JSONObject. For example, given a
     * JSONObject initialized with this document:
     * <pre>
     * {
     *     "a":{"b":"c"}
     * }
     * </pre>
     * and this JSONPointer:
     * <pre>
     * "/a/b"
     * </pre>
     * Then this method will return the String "c".
     * A JSONPointerException may be thrown from code called by this method.
     *
     * @param jsonPointer string that can be used to create a JSONPointer
     * @return the item matched by the JSONPointer, otherwise null
     */
    public Object query(JSONPointer jsonPointer) {
        return jsonPointer.queryFrom(this);
    }

    /**
     * Queries and returns a value from this object using {@code jsonPointer}, or
     * returns null if the query fails due to a missing key.
     *
     * @param jsonPointer the string representation of the JSON pointer
     * @return the queried value or {@code null}
     * @throws IllegalArgumentException if {@code jsonPointer} has invalid syntax
     */
    public Object optQuery(String jsonPointer) {
    	return optQuery(new JSONPointer(jsonPointer));
    }

    /**
     * Queries and returns a value from this object using {@code jsonPointer}, or
     * returns null if the query fails due to a missing key.
     *
     * @param jsonPointer The JSON pointer
     * @return the queried value or {@code null}
     * @throws IllegalArgumentException if {@code jsonPointer} has invalid syntax
     */
    public Object optQuery(JSONPointer jsonPointer) {
        try {
            return jsonPointer.queryFrom(this);
        } catch (JSONPointerException e) {
            return null;
        }
    }

    /**
     * Produce a string in double quotes with backslash sequences in all the
     * right places. A backslash will be inserted within &lt;/, producing
     * &lt;\/, allowing JSON text to be delivered in HTML. In JSON text, a
     * string cannot contain a control character or an unescaped quote or
     * backslash.
     *
     * @param string
     *            A String
     * @return A String correctly formatted for insertion in a JSON text.
     */
    public static String quote(String string) {
        StringWriter sw = new StringWriter();
        synchronized (sw.getBuffer()) {
            try {
                return quote(string, sw).toString();
            } catch (IOException ignored) {
                // will never happen - we are writing to a string writer
                return "";
            }
        }
    }

    public static Writer quote(String string, Writer w) throws IOException {
        if (string == null || string.isEmpty()) {
            w.write("\"\"");
            return w;
        }

        char b;
        char c = 0;
        String hhhh;
        int i;
        int len = string.length();

        w.write('"');
        for (i = 0; i < len; i += 1) {
            b = c;
            c = string.charAt(i);
            switch (c) {
            case '\\':
            case '"':
                w.write('\\');
                w.write(c);
                break;
            case '/':
                if (b == '<') {
                    w.write('\\');
                }
                w.write(c);
                break;
            case '\b':
                w.write("\\b");
                break;
            case '\t':
                w.write("\\t");
                break;
            case '\n':
                w.write("\\n");
                break;
            case '\f':
                w.write("\\f");
                break;
            case '\r':
                w.write("\\r");
                break;
            default:
                if (c < ' ' || (c >= '\u0080' && c < '\u00a0')
                        || (c >= '\u2000' && c < '\u2100')) {
                    w.write("\\u");
                    hhhh = Integer.toHexString(c);
                    w.write("0000", 0, 4 - hhhh.length());
                    w.write(hhhh);
                } else {
                    w.write(c);
                }
            }
        }
        w.write('"');
        return w;
    }

    /**
     * Remove a name and its value, if present.
     *
     * @param key
     *            The name to be removed.
     * @return The value that was associated with the name, or null if there was
     *         no value.
     */
    public Object remove(String key) {
        return this.map.remove(key);
    }

    /**
     * Determine if two JSONObjects are similar.
     * They must contain the same set of names which must be associated with
     * similar values.
     *
     * @param other The other JSONObject
     * @return true if they are equal
     */
    public boolean similar(Object other) {
        try {
            if (!(other instanceof JSONObject)) {
                return false;
            }
            if (!this.keySet().equals(((JSONObject)other).keySet())) {
                return false;
            }
            for (final Entry<String,?> entry : this.entrySet()) {
                String name = entry.getKey();
                Object valueThis = entry.getValue();
                Object valueOther = ((JSONObject)other).get(name);
                if(valueThis == valueOther) {
                	continue;
                }
                if(valueThis == null) {
                	return false;
                }
                if (valueThis instanceof JSONObject) {
                    if (!((JSONObject)valueThis).similar(valueOther)) {
                        return false;
                    }
                } else if (valueThis instanceof JSONArray) {
                    if (!((JSONArray)valueThis).similar(valueOther)) {
                        return false;
                    }
                } else if (valueThis instanceof Number && valueOther instanceof Number) {
                    if (!isNumberSimilar((Number)valueThis, (Number)valueOther)) {
                    	return false;
                    };
                } else if (!valueThis.equals(valueOther)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable exception) {
            return false;
        }
    }

    /**
     * Compares two numbers to see if they are similar.
     *
     * If either of the numbers are Double or Float instances, then they are checked to have
     * a finite value. If either value is not finite (NaN or &#177;infinity), then this
     * function will always return false. If both numbers are finite, they are first checked
     * to be the same type and implement {@link Comparable}. If they do, then the actual
     * {@link Comparable#compareTo(Object)} is called. If they are not the same type, or don't
     * implement Comparable, then they are converted to {@link BigDecimal}s. Finally the
     * BigDecimal values are compared using {@link BigDecimal#compareTo(BigDecimal)}.
     *
     * @param l the Left value to compare. Can not be <code>null</code>.
     * @param r the right value to compare. Can not be <code>null</code>.
     * @return true if the numbers are similar, false otherwise.
     */
    static boolean isNumberSimilar(Number l, Number r) {
        if (!numberIsFinite(l) || !numberIsFinite(r)) {
            // non-finite numbers are never similar
            return false;
        }

        // if the classes are the same and implement Comparable
        // then use the built in compare first.
        if(l.getClass().equals(r.getClass()) && l instanceof Comparable) {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            int compareTo = ((Comparable)l).compareTo(r);
            return compareTo==0;
        }

        // BigDecimal should be able to handle all of our number types that we support through
        // documentation. Convert to BigDecimal first, then use the Compare method to
        // decide equality.
        final BigDecimal lBigDecimal = objectToBigDecimal(l, null, false);
        final BigDecimal rBigDecimal = objectToBigDecimal(r, null, false);
        if (lBigDecimal == null || rBigDecimal == null) {
            return false;
        }
        return lBigDecimal.compareTo(rBigDecimal) == 0;
    }

    private static boolean numberIsFinite(Number n) {
        if (n instanceof Double && (((Double) n).isInfinite() || ((Double) n).isNaN())) {
            return false;
        } else if (n instanceof Float && (((Float) n).isInfinite() || ((Float) n).isNaN())) {
            return false;
        }
        return true;
    }

    /**
     * Tests if the value should be tried as a decimal. It makes no test if there are actual digits.
     *
     * @param val value to test
     * @return true if the string is "-0" or if it contains '.', 'e', or 'E', false otherwise.
     */
    protected static boolean isDecimalNotation(final String val) {
        return val.indexOf('.') > -1 || val.indexOf('e') > -1
                || val.indexOf('E') > -1 || "-0".equals(val);
    }

    /**
     * Converts a string to a number using the narrowest possible type. Possible
     * returns for this function are BigDecimal, Double, BigInteger, Long, and Integer.
     * When a Double is returned, it should always be a valid Double and not NaN or +-infinity.
     *
     * @param val value to convert
     * @return Number representation of the value.
     * @throws NumberFormatException thrown if the value is not a valid number. A public
     *      caller should catch this and wrap it in a {@link JSONException} if applicable.
     */
    protected static Number stringToNumber(final String val) throws NumberFormatException {
        char initial = val.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            // decimal representation
            if (isDecimalNotation(val)) {
                // Use a BigDecimal all the time so we keep the original
                // representation. BigDecimal doesn't support -0.0, ensure we
                // keep that by forcing a decimal.
                try {
                    BigDecimal bd = new BigDecimal(val);
                    if(initial == '-' && BigDecimal.ZERO.compareTo(bd)==0) {
                        return Double.valueOf(-0.0);
                    }
                    return bd;
                } catch (NumberFormatException retryAsDouble) {
                    // this is to support "Hex Floats" like this: 0x1.0P-1074
                    try {
                        Double d = Double.valueOf(val);
                        if(d.isNaN() || d.isInfinite()) {
                            throw new NumberFormatException("val ["+val+"] is not a valid number.");
                        }
                        return d;
                    } catch (NumberFormatException ignore) {
                        throw new NumberFormatException("val ["+val+"] is not a valid number.");
                    }
                }
            }
            // block items like 00 01 etc. Java number parsers treat these as Octal.
            if(initial == '0' && val.length() > 1) {
                char at1 = val.charAt(1);
                if(at1 >= '0' && at1 <= '9') {
                    throw new NumberFormatException("val ["+val+"] is not a valid number.");
                }
            } else if (initial == '-' && val.length() > 2) {
                char at1 = val.charAt(1);
                char at2 = val.charAt(2);
                if(at1 == '0' && at2 >= '0' && at2 <= '9') {
                    throw new NumberFormatException("val ["+val+"] is not a valid number.");
                }
            }
            // integer representation.
            // This will narrow any values to the smallest reasonable Object representation
            // (Integer, Long, or BigInteger)

            // BigInteger down conversion: We use a similar bitLength compare as
            // BigInteger#intValueExact uses. Increases GC, but objects hold
            // only what they need. i.e. Less runtime overhead if the value is
            // long lived.
            BigInteger bi = new BigInteger(val);
            if(bi.bitLength() <= 31){
                return Integer.valueOf(bi.intValue());
            }
            if(bi.bitLength() <= 63){
                return Long.valueOf(bi.longValue());
            }
            return bi;
        }
        throw new NumberFormatException("val ["+val+"] is not a valid number.");
    }

    /**
     * Try to convert a string into a number, boolean, or null. If the string
     * can't be converted, return the string.
     *
     * @param string
     *            A String. can not be null.
     * @return A simple JSON value.
     * @throws NullPointerException
     *             Thrown if the string is null.
     */
    // Changes to this method must be copied to the corresponding method in
    // the XML class to keep full support for Android
    public static Object stringToValue(String string) {
        if ("".equals(string)) {
            return string;
        }

        // check JSON key words true/false/null
        if ("true".equalsIgnoreCase(string)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(string)) {
            return Boolean.FALSE;
        }
        if ("null".equalsIgnoreCase(string)) {
            return JSONObject.NULL;
        }

        /*
         * If it might be a number, try converting it. If a number cannot be
         * produced, then the value will just be a string.
         */

        char initial = string.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            try {
                return stringToNumber(string);
            } catch (Exception ignore) {
            }
        }
        return string;
    }

    /**
     * Throw an exception if the object is a NaN or infinite number.
     *
     * @param o
     *            The object to test.
     * @throws JSONException
     *             If o is a non-finite number.
     */
    public static void testValidity(Object o) throws JSONException {
        if (o instanceof Number && !numberIsFinite((Number) o)) {
            throw new JSONException("JSON does not allow non-finite numbers.");
        }
    }

    /**
     * Produce a JSONArray containing the values of the members of this
     * JSONObject.
     *
     * @param names
     *            A JSONArray containing a list of key strings. This determines
     *            the sequence of the values in the result.
     * @return A JSONArray of values.
     * @throws JSONException
     *             If any of the values are non-finite numbers.
     */
    public JSONArray toJSONArray(JSONArray names) throws JSONException {
        if (names == null || names.isEmpty()) {
            return null;
        }
        JSONArray ja = new JSONArray();
        for (int i = 0; i < names.length(); i += 1) {
            ja.put(this.opt(names.getString(i)));
        }
        return ja;
    }

    /**
     * Make a JSON text of this JSONObject. For compactness, no whitespace is
     * added. If this would not result in a syntactically correct JSON text,
     * then null will be returned instead.
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     * </b>
     *
     * @return a printable, displayable, portable, transmittable representation
     *         of the object, beginning with <code>{</code>&nbsp;<small>(left
     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     *         brace)</small>.
     */
    @Override
    public String toString() {
        try {
            return this.toString(0);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Make a pretty-printed JSON text of this JSONObject.
     *
     * <p>If <pre>{@code indentFactor > 0}</pre> and the {@link JSONObject}
     * has only one key, then the object will be output on a single line:
     * <pre>{@code {"key": 1}}</pre>
     *
     * <p>If an object has 2 or more keys, then it will be output across
     * multiple lines: <pre>{@code {
     *  "key1": 1,
     *  "key2": "value 2",
     *  "key3": 3
     * }}</pre>
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     * </b>
     *
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @return a printable, displayable, portable, transmittable representation
     *         of the object, beginning with <code>{</code>&nbsp;<small>(left
     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     *         brace)</small>.
     * @throws JSONException
     *             If the object contains an invalid number.
     */
    public String toString(int indentFactor) throws JSONException {
        StringWriter w = new StringWriter();
        synchronized (w.getBuffer()) {
            return this.write(w, indentFactor, 0).toString();
        }
    }

    /**
     * Make a JSON text of an Object value. If the object has an
     * value.toJSONString() method, then that method will be used to produce the
     * JSON text. The method is required to produce a strictly conforming text.
     * If the object does not contain a toJSONString method (which is the most
     * common case), then a text will be produced by other means. If the value
     * is an array or Collection, then a JSONArray will be made from it and its
     * toJSONString method will be called. If the value is a MAP, then a
     * JSONObject will be made from it and its toJSONString method will be
     * called. Otherwise, the value's toString method will be called, and the
     * result will be quoted.
     *
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param value
     *            The value to be serialized.
     * @return a printable, displayable, transmittable representation of the
     *         object, beginning with <code>{</code>&nbsp;<small>(left
     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     *         brace)</small>.
     * @throws JSONException
     *             If the value is or contains an invalid number.
     */
    public static String valueToString(Object value) throws JSONException {
    	// moves the implementation to JSONWriter as:
    	// 1. It makes more sense to be part of the writer class
    	// 2. For Android support this method is not available. By implementing it in the Writer
    	//    Android users can use the writer with the built in Android JSONObject implementation.
        return JSONWriter.valueToString(value);
    }

    /**
     * Wrap an object, if necessary. If the object is <code>null</code>, return the NULL
     * object. If it is an array or collection, wrap it in a JSONArray. If it is
     * a map, wrap it in a JSONObject. If it is a standard property (Double,
     * String, et al) then it is already wrapped. Otherwise, if it comes from
     * one of the java packages, turn it into a string. And if it doesn't, try
     * to wrap it in a JSONObject. If the wrapping fails, then null is returned.
     *
     * @param object
     *            The object to wrap
     * @return The wrapped value
     */
    public static Object wrap(Object object) {
        return wrap(object, null);
    }

    private static Object wrap(Object object, Set<Object> objectsRecord) {
        try {
            if (NULL.equals(object)) {
                return NULL;
            }
            if (object instanceof JSONObject || object instanceof JSONArray
                    || NULL.equals(object) || object instanceof JSONString
                    || object instanceof Byte || object instanceof Character
                    || object instanceof Short || object instanceof Integer
                    || object instanceof Long || object instanceof Boolean
                    || object instanceof Float || object instanceof Double
                    || object instanceof String || object instanceof BigInteger
                    || object instanceof BigDecimal || object instanceof Enum) {
                return object;
            }

            if (object instanceof Collection) {
                Collection<?> coll = (Collection<?>) object;
                return new JSONArray(coll);
            }
            if (object.getClass().isArray()) {
                return new JSONArray(object);
            }
            if (object instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) object;
                return new JSONObject(map);
            }
            Package objectPackage = object.getClass().getPackage();
            String objectPackageName = objectPackage != null ? objectPackage
                    .getName() : "";
            if (objectPackageName.startsWith("java.")
                    || objectPackageName.startsWith("javax.")
                    || object.getClass().getClassLoader() == null) {
                return object.toString();
            }
            if (objectsRecord != null) {
                return new JSONObject(object, objectsRecord);
            }
            else {
                return new JSONObject(object);
            }
        }
        catch (JSONException exception) {
            throw exception;
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Write the contents of the JSONObject as JSON text to a writer. For
     * compactness, no whitespace is added.
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     * </b>
     * @param writer the writer object
     * @return The writer.
     * @throws JSONException if a called function has an error
     */
    public Writer write(Writer writer) throws JSONException {
        return this.write(writer, 0, 0);
    }

    static final Writer writeValue(Writer writer, Object value,
            int indentFactor, int indent) throws JSONException, IOException {
        if (value == null || value.equals(null)) {
            writer.write("null");
        } else if (value instanceof JSONString) {
            Object o;
            try {
                o = ((JSONString) value).toJSONString();
            } catch (Exception e) {
                throw new JSONException(e);
            }
            writer.write(o != null ? o.toString() : quote(value.toString()));
        } else if (value instanceof Number) {
            // not all Numbers may match actual JSON Numbers. i.e. fractions or Imaginary
            final String numberAsString = numberToString((Number) value);
            if(NUMBER_PATTERN.matcher(numberAsString).matches()) {
                writer.write(numberAsString);
            } else {
                // The Number value is not a valid JSON number.
                // Instead we will quote it as a string
                quote(numberAsString, writer);
            }
        } else if (value instanceof Boolean) {
            writer.write(value.toString());
        } else if (value instanceof Enum<?>) {
            writer.write(quote(((Enum<?>)value).name()));
        } else if (value instanceof JSONObject) {
            ((JSONObject) value).write(writer, indentFactor, indent);
        } else if (value instanceof JSONArray) {
            ((JSONArray) value).write(writer, indentFactor, indent);
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            new JSONObject(map).write(writer, indentFactor, indent);
        } else if (value instanceof Collection) {
            Collection<?> coll = (Collection<?>) value;
            new JSONArray(coll).write(writer, indentFactor, indent);
        } else if (value.getClass().isArray()) {
            new JSONArray(value).write(writer, indentFactor, indent);
        } else {
            quote(value.toString(), writer);
        }
        return writer;
    }

    static final void indent(Writer writer, int indent) throws IOException {
        for (int i = 0; i < indent; i += 1) {
            writer.write(' ');
        }
    }

    /**
     * Write the contents of the JSONObject as JSON text to a writer.
     *
     * <p>If <pre>{@code indentFactor > 0}</pre> and the {@link JSONObject}
     * has only one key, then the object will be output on a single line:
     * <pre>{@code {"key": 1}}</pre>
     *
     * <p>If an object has 2 or more keys, then it will be output across
     * multiple lines: <pre>{@code {
     *  "key1": 1,
     *  "key2": "value 2",
     *  "key3": 3
     * }}</pre>
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     * </b>
     *
     * @param writer
     *            Writes the serialized JSON
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @param indent
     *            The indentation of the top level.
     * @return The writer.
     * @throws JSONException if a called function has an error or a write error
     * occurs
     */
    public Writer write(Writer writer, int indentFactor, int indent)
            throws JSONException {
        try {
            boolean needsComma = false;
            final int length = this.length();
            writer.write('{');

            if (length == 1) {
            	final Entry<String,?> entry = this.entrySet().iterator().next();
                final String key = entry.getKey();
                writer.write(quote(key));
                writer.write(':');
                if (indentFactor > 0) {
                    writer.write(' ');
                }
                try{
                    writeValue(writer, entry.getValue(), indentFactor, indent);
                } catch (Exception e) {
                    throw new JSONException("Unable to write JSONObject value for key: " + key, e);
                }
            } else if (length != 0) {
                final int newIndent = indent + indentFactor;
                for (final Entry<String,?> entry : this.entrySet()) {
                    if (needsComma) {
                        writer.write(',');
                    }
                    if (indentFactor > 0) {
                        writer.write('\n');
                    }
                    indent(writer, newIndent);
                    final String key = entry.getKey();
                    writer.write(quote(key));
                    writer.write(':');
                    if (indentFactor > 0) {
                        writer.write(' ');
                    }
                    try {
                        writeValue(writer, entry.getValue(), indentFactor, newIndent);
                    } catch (Exception e) {
                        throw new JSONException("Unable to write JSONObject value for key: " + key, e);
                    }
                    needsComma = true;
                }
                if (indentFactor > 0) {
                    writer.write('\n');
                }
                indent(writer, indent);
            }
            writer.write('}');
            return writer;
        } catch (IOException exception) {
            throw new JSONException(exception);
        }
    }

    /**
     * Returns a java.util.Map containing all of the entries in this object.
     * If an entry in the object is a JSONArray or JSONObject it will also
     * be converted.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return a java.util.Map containing the entries of this object
     */
    public Map<String, Object> toMap() {
        Map<String, Object> results = new HashMap<String, Object>();
        for (Entry<String, Object> entry : this.entrySet()) {
            Object value;
            if (entry.getValue() == null || NULL.equals(entry.getValue())) {
                value = null;
            } else if (entry.getValue() instanceof JSONObject) {
                value = ((JSONObject) entry.getValue()).toMap();
            } else if (entry.getValue() instanceof JSONArray) {
                value = ((JSONArray) entry.getValue()).toList();
            } else {
                value = entry.getValue();
            }
            results.put(entry.getKey(), value);
        }
        return results;
    }

    /**
     * Create a new JSONException in a common format for incorrect conversions.
     * @param key name of the key
     * @param valueType the type of value being coerced to
     * @param cause optional cause of the coercion failure
     * @return JSONException that can be thrown.
     */
    private static JSONException wrongValueFormatException(
            String key,
            String valueType,
            Throwable cause) {
        return new JSONException(
                "JSONObject[" + quote(key) + "] is not a " + valueType + "."
                , cause);
    }

    /**
     * Create a new JSONException in a common format for incorrect conversions.
     * @param key name of the key
     * @param valueType the type of value being coerced to
     * @param cause optional cause of the coercion failure
     * @return JSONException that can be thrown.
     */
    private static JSONException wrongValueFormatException(
            String key,
            String valueType,
            Object value,
            Throwable cause) {
        return new JSONException(
                "JSONObject[" + quote(key) + "] is not a " + valueType + " (" + value + ")."
                , cause);
    }

    /**
     * Create a new JSONException in a common format for recursive object definition.
     * @param key name of the key
     * @return JSONException that can be thrown.
     */
    private static JSONException recursivelyDefinedObjectException(String key) {
        return new JSONException(
            "JavaBean object contains recursively defined member variable of key " + quote(key)
        );
    }
}

/*
Copyright (c) 2002 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

/**
 * A JSONTokener takes a source string and extracts characters and tokens from
 * it. It is used by the JSONObject and JSONArray constructors to parse
 * JSON source strings.
 * @author JSON.org
 * @version 2014-05-03
 */
 class JSONTokener {
    /** current read character position on the current line. */
    private long character;
    /** flag to indicate if the end of the input has been found. */
    private boolean eof;
    /** current read index of the input. */
    private long index;
    /** current line of the input. */
    private long line;
    /** previous character read from the input. */
    private char previous;
    /** Reader for the input. */
    private final Reader reader;
    /** flag to indicate that a previous character was requested. */
    private boolean usePrevious;
    /** the number of characters read in the previous line. */
    private long characterPreviousLine;


    /**
     * Construct a JSONTokener from a Reader. The caller must close the Reader.
     *
     * @param reader     A reader.
     */
    public JSONTokener(Reader reader) {
        this.reader = reader.markSupported()
                ? reader
                        : new BufferedReader(reader);
        this.eof = false;
        this.usePrevious = false;
        this.previous = 0;
        this.index = 0;
        this.character = 1;
        this.characterPreviousLine = 0;
        this.line = 1;
    }


    /**
     * Construct a JSONTokener from an InputStream. The caller must close the input stream.
     * @param inputStream The source.
     */
    public JSONTokener(InputStream inputStream) {
        this(new InputStreamReader(inputStream));
    }


    /**
     * Construct a JSONTokener from a string.
     *
     * @param s     A source string.
     */
    public JSONTokener(String s) {
        this(new StringReader(s));
    }


    /**
     * Back up one character. This provides a sort of lookahead capability,
     * so that you can test for a digit or letter before attempting to parse
     * the next number or identifier.
     * @throws JSONException Thrown if trying to step back more than 1 step
     *  or if already at the start of the string
     */
    public void back() throws JSONException {
        if (this.usePrevious || this.index <= 0) {
            throw new JSONException("Stepping back two steps is not supported");
        }
        this.decrementIndexes();
        this.usePrevious = true;
        this.eof = false;
    }

    /**
     * Decrements the indexes for the {@link #back()} method based on the previous character read.
     */
    private void decrementIndexes() {
        this.index--;
        if(this.previous=='\r' || this.previous == '\n') {
            this.line--;
            this.character=this.characterPreviousLine ;
        } else if(this.character > 0){
            this.character--;
        }
    }

    /**
     * Get the hex value of a character (base16).
     * @param c A character between '0' and '9' or between 'A' and 'F' or
     * between 'a' and 'f'.
     * @return  An int between 0 and 15, or -1 if c was not a hex digit.
     */
    public static int dehexchar(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - ('A' - 10);
        }
        if (c >= 'a' && c <= 'f') {
            return c - ('a' - 10);
        }
        return -1;
    }

    /**
     * Checks if the end of the input has been reached.
     *  
     * @return true if at the end of the file and we didn't step back
     */
    public boolean end() {
        return this.eof && !this.usePrevious;
    }


    /**
     * Determine if the source string still contains characters that next()
     * can consume.
     * @return true if not yet at the end of the source.
     * @throws JSONException thrown if there is an error stepping forward
     *  or backward while checking for more data.
     */
    public boolean more() throws JSONException {
        if(this.usePrevious) {
            return true;
        }
        try {
            this.reader.mark(1);
        } catch (IOException e) {
            throw new JSONException("Unable to preserve stream position", e);
        }
        try {
            // -1 is EOF, but next() can not consume the null character '\0'
            if(this.reader.read() <= 0) {
                this.eof = true;
                return false;
            }
            this.reader.reset();
        } catch (IOException e) {
            throw new JSONException("Unable to read the next character from the stream", e);
        }
        return true;
    }


    /**
     * Get the next character in the source string.
     *
     * @return The next character, or 0 if past the end of the source string.
     * @throws JSONException Thrown if there is an error reading the source string.
     */
    public char next() throws JSONException {
        int c;
        if (this.usePrevious) {
            this.usePrevious = false;
            c = this.previous;
        } else {
            try {
                c = this.reader.read();
            } catch (IOException exception) {
                throw new JSONException(exception);
            }
        }
        if (c <= 0) { // End of stream
            this.eof = true;
            return 0;
        }
        this.incrementIndexes(c);
        this.previous = (char) c;
        return this.previous;
    }
    
    /**
     * Get the last character read from the input or '\0' if nothing has been read yet.
     * @return the last character read from the input.
     */
    protected char getPrevious() { return this.previous;}

    /**
     * Increments the internal indexes according to the previous character
     * read and the character passed as the current character.
     * @param c the current character read.
     */
    private void incrementIndexes(int c) {
        if(c > 0) {
            this.index++;
            if(c=='\r') {
                this.line++;
                this.characterPreviousLine = this.character;
                this.character=0;
            }else if (c=='\n') {
                if(this.previous != '\r') {
                    this.line++;
                    this.characterPreviousLine = this.character;
                }
                this.character=0;
            } else {
                this.character++;
            }
        }
    }

    /**
     * Consume the next character, and check that it matches a specified
     * character.
     * @param c The character to match.
     * @return The character.
     * @throws JSONException if the character does not match.
     */
    public char next(char c) throws JSONException {
        char n = this.next();
        if (n != c) {
            if(n > 0) {
                throw this.syntaxError("Expected '" + c + "' and instead saw '" +
                        n + "'");
            }
            throw this.syntaxError("Expected '" + c + "' and instead saw ''");
        }
        return n;
    }


    /**
     * Get the next n characters.
     *
     * @param n     The number of characters to take.
     * @return      A string of n characters.
     * @throws JSONException
     *   Substring bounds error if there are not
     *   n characters remaining in the source string.
     */
    public String next(int n) throws JSONException {
        if (n == 0) {
            return "";
        }

        char[] chars = new char[n];
        int pos = 0;

        while (pos < n) {
            chars[pos] = this.next();
            if (this.end()) {
                throw this.syntaxError("Substring bounds error");
            }
            pos += 1;
        }
        return new String(chars);
    }


    /**
     * Get the next char in the string, skipping whitespace.
     * @throws JSONException Thrown if there is an error reading the source string.
     * @return  A character, or 0 if there are no more characters.
     */
    public char nextClean() throws JSONException {
        for (;;) {
            char c = this.next();
            if (c == 0 || c > ' ') {
                return c;
            }
        }
    }


    /**
     * Return the characters up to the next close quote character.
     * Backslash processing is done. The formal JSON format does not
     * allow strings in single quotes, but an implementation is allowed to
     * accept them.
     * @param quote The quoting character, either
     *      <code>"</code>&nbsp;<small>(double quote)</small> or
     *      <code>'</code>&nbsp;<small>(single quote)</small>.
     * @return      A String.
     * @throws JSONException Unterminated string.
     */
    public String nextString(char quote) throws JSONException {
        char c;
        StringBuilder sb = new StringBuilder();
        for (;;) {
            c = this.next();
            switch (c) {
            case 0:
            case '\n':
            case '\r':
                throw this.syntaxError("Unterminated string");
            case '\\':
                c = this.next();
                switch (c) {
                case 'b':
                    sb.append('\b');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 'u':
                    try {
                        sb.append((char)Integer.parseInt(this.next(4), 16));
                    } catch (NumberFormatException e) {
                        throw this.syntaxError("Illegal escape.", e);
                    }
                    break;
                case '"':
                case '\'':
                case '\\':
                case '/':
                    sb.append(c);
                    break;
                default:
                    throw this.syntaxError("Illegal escape.");
                }
                break;
            default:
                if (c == quote) {
                    return sb.toString();
                }
                sb.append(c);
            }
        }
    }


    /**
     * Get the text up but not including the specified character or the
     * end of line, whichever comes first.
     * @param  delimiter A delimiter character.
     * @return   A string.
     * @throws JSONException Thrown if there is an error while searching
     *  for the delimiter
     */
    public String nextTo(char delimiter) throws JSONException {
        StringBuilder sb = new StringBuilder();
        for (;;) {
            char c = this.next();
            if (c == delimiter || c == 0 || c == '\n' || c == '\r') {
                if (c != 0) {
                    this.back();
                }
                return sb.toString().trim();
            }
            sb.append(c);
        }
    }


    /**
     * Get the text up but not including one of the specified delimiter
     * characters or the end of line, whichever comes first.
     * @param delimiters A set of delimiter characters.
     * @return A string, trimmed.
     * @throws JSONException Thrown if there is an error while searching
     *  for the delimiter
     */
    public String nextTo(String delimiters) throws JSONException {
        char c;
        StringBuilder sb = new StringBuilder();
        for (;;) {
            c = this.next();
            if (delimiters.indexOf(c) >= 0 || c == 0 ||
                    c == '\n' || c == '\r') {
                if (c != 0) {
                    this.back();
                }
                return sb.toString().trim();
            }
            sb.append(c);
        }
    }


    /**
     * Get the next value. The value can be a Boolean, Double, Integer,
     * JSONArray, JSONObject, Long, or String, or the JSONObject.NULL object.
     * @throws JSONException If syntax error.
     *
     * @return An object.
     */
    public Object nextValue() throws JSONException {
        char c = this.nextClean();
        String string;

        switch (c) {
        case '"':
        case '\'':
            return this.nextString(c);
        case '{':
            this.back();
            try {
                return new JSONObject(this);
            } catch (StackOverflowError e) {
                throw new JSONException("JSON Array or Object depth too large to process.", e);
            }
        case '[':
            this.back();
            try {
                return new JSONArray(this);
            } catch (StackOverflowError e) {
                throw new JSONException("JSON Array or Object depth too large to process.", e);
            }
        }

        /*
         * Handle unquoted text. This could be the values true, false, or
         * null, or it can be a number. An implementation (such as this one)
         * is allowed to also accept non-standard forms.
         *
         * Accumulate characters until we reach the end of the text or a
         * formatting character.
         */

        StringBuilder sb = new StringBuilder();
        while (c >= ' ' && ",:]}/\\\"[{;=#".indexOf(c) < 0) {
            sb.append(c);
            c = this.next();
        }
        if (!this.eof) {
            this.back();
        }

        string = sb.toString().trim();
        if ("".equals(string)) {
            throw this.syntaxError("Missing value");
        }
        return JSONObject.stringToValue(string);
    }


    /**
     * Skip characters until the next character is the requested character.
     * If the requested character is not found, no characters are skipped.
     * @param to A character to skip to.
     * @return The requested character, or zero if the requested character
     * is not found.
     * @throws JSONException Thrown if there is an error while searching
     *  for the to character
     */
    public char skipTo(char to) throws JSONException {
        char c;
        try {
            long startIndex = this.index;
            long startCharacter = this.character;
            long startLine = this.line;
            this.reader.mark(1000000);
            do {
                c = this.next();
                if (c == 0) {
                    // in some readers, reset() may throw an exception if
                    // the remaining portion of the input is greater than
                    // the mark size (1,000,000 above).
                    this.reader.reset();
                    this.index = startIndex;
                    this.character = startCharacter;
                    this.line = startLine;
                    return 0;
                }
            } while (c != to);
            this.reader.mark(1);
        } catch (IOException exception) {
            throw new JSONException(exception);
        }
        this.back();
        return c;
    }

    /**
     * Make a JSONException to signal a syntax error.
     *
     * @param message The error message.
     * @return  A JSONException object, suitable for throwing
     */
    public JSONException syntaxError(String message) {
        return new JSONException(message + this.toString());
    }

    /**
     * Make a JSONException to signal a syntax error.
     *
     * @param message The error message.
     * @param causedBy The throwable that caused the error.
     * @return  A JSONException object, suitable for throwing
     */
    public JSONException syntaxError(String message, Throwable causedBy) {
        return new JSONException(message + this.toString(), causedBy);
    }

    /**
     * Make a printable string of this JSONTokener.
     *
     * @return " at {index} [character {character} line {line}]"
     */
    @Override
    public String toString() {
        return " at " + this.index + " [character " + this.character + " line " +
                this.line + "]";
    }
}

/*
Copyright (c) 2002 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

/**
 * The JSONException is thrown by the JSON.org classes when things are amiss.
 *
 * @author JSON.org
 * @version 2015-12-09
 */
class JSONException extends RuntimeException {
    /** Serialization ID */
    private static final long serialVersionUID = 0;

    /**
     * Constructs a JSONException with an explanatory message.
     *
     * @param message
     *            Detail about the reason for the exception.
     */
    public JSONException(final String message) {
        super(message);
    }

    /**
     * Constructs a JSONException with an explanatory message and cause.
     * 
     * @param message
     *            Detail about the reason for the exception.
     * @param cause
     *            The cause.
     */
    public JSONException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new JSONException with the specified cause.
     * 
     * @param cause
     *            The cause.
     */
    public JSONException(final Throwable cause) {
        super(cause.getMessage(), cause);
    }

}

/**
 * A JSONArray is an ordered sequence of values. Its external text form is a
 * string wrapped in square brackets with commas separating the values. The
 * internal form is an object having <code>get</code> and <code>opt</code>
 * methods for accessing the values by index, and <code>put</code> methods for
 * adding or replacing values. The values can be any of these types:
 * <code>Boolean</code>, <code>JSONArray</code>, <code>JSONObject</code>,
 * <code>Number</code>, <code>String</code>, or the
 * <code>JSONObject.NULL object</code>.
 * <p>
 * The constructor can convert a JSON text into a Java object. The
 * <code>toString</code> method converts to JSON text.
 * <p>
 * A <code>get</code> method returns a value if one can be found, and throws an
 * exception if one cannot be found. An <code>opt</code> method returns a
 * default value instead of throwing an exception, and so is useful for
 * obtaining optional values.
 * <p>
 * The generic <code>get()</code> and <code>opt()</code> methods return an
 * object which you can cast or query for type. There are also typed
 * <code>get</code> and <code>opt</code> methods that do type checking and type
 * coercion for you.
 * <p>
 * The texts produced by the <code>toString</code> methods strictly conform to
 * JSON syntax rules. The constructors are more forgiving in the texts they will
 * accept:
 * <ul>
 * <li>An extra <code>,</code>&nbsp;<small>(comma)</small> may appear just
 * before the closing bracket.</li>
 * <li>The <code>null</code> value will be inserted when there is <code>,</code>
 * &nbsp;<small>(comma)</small> elision.</li>
 * <li>Strings may be quoted with <code>'</code>&nbsp;<small>(single
 * quote)</small>.</li>
 * <li>Strings do not need to be quoted at all if they do not begin with a quote
 * or single quote, and if they do not contain leading or trailing spaces, and
 * if they do not contain any of these characters:
 * <code>{ } [ ] / \ : , #</code> and if they do not look like numbers and
 * if they are not the reserved words <code>true</code>, <code>false</code>, or
 * <code>null</code>.</li>
 * </ul>
 *
 * @author JSON.org
 * @version 2016-08/15
 */
 class JSONArray implements Iterable<Object> {

    /**
     * The arrayList where the JSONArray's properties are kept.
     */
    private final ArrayList<Object> myArrayList;

    /**
     * Construct an empty JSONArray.
     */
    public JSONArray() {
        this.myArrayList = new ArrayList<Object>();
    }

    /**
     * Construct a JSONArray from a JSONTokener.
     *
     * @param x
     *            A JSONTokener
     * @throws JSONException
     *             If there is a syntax error.
     */
    public JSONArray(JSONTokener x) throws JSONException {
        this();
        if (x.nextClean() != '[') {
            throw x.syntaxError("A JSONArray text must start with '['");
        }
        
        char nextChar = x.nextClean();
        if (nextChar == 0) {
            // array is unclosed. No ']' found, instead EOF
            throw x.syntaxError("Expected a ',' or ']'");
        }
        if (nextChar != ']') {
            x.back();
            for (;;) {
                if (x.nextClean() == ',') {
                    x.back();
                    this.myArrayList.add(JSONObject.NULL);
                } else {
                    x.back();
                    this.myArrayList.add(x.nextValue());
                }
                switch (x.nextClean()) {
                case 0:
                    // array is unclosed. No ']' found, instead EOF
                    throw x.syntaxError("Expected a ',' or ']'");
                case ',':
                    nextChar = x.nextClean();
                    if (nextChar == 0) {
                        // array is unclosed. No ']' found, instead EOF
                        throw x.syntaxError("Expected a ',' or ']'");
                    }
                    if (nextChar == ']') {
                        return;
                    }
                    x.back();
                    break;
                case ']':
                    return;
                default:
                    throw x.syntaxError("Expected a ',' or ']'");
                }
            }
        }
    }

    /**
     * Construct a JSONArray from a source JSON text.
     *
     * @param source
     *            A string that begins with <code>[</code>&nbsp;<small>(left
     *            bracket)</small> and ends with <code>]</code>
     *            &nbsp;<small>(right bracket)</small>.
     * @throws JSONException
     *             If there is a syntax error.
     */
    public JSONArray(String source) throws JSONException {
        this(new JSONTokener(source));
    }

    /**
     * Construct a JSONArray from a Collection.
     *
     * @param collection
     *            A Collection.
     */
    public JSONArray(Collection<?> collection) {
        if (collection == null) {
            this.myArrayList = new ArrayList<Object>();
        } else {
            this.myArrayList = new ArrayList<Object>(collection.size());
            this.addAll(collection, true);
        }
    }

    /**
     * Construct a JSONArray from an Iterable. This is a shallow copy.
     *
     * @param iter
     *            A Iterable collection.
     */
    public JSONArray(Iterable<?> iter) {
        this();
        if (iter == null) {
            return;
        }
        this.addAll(iter, true);
    }

    /**
     * Construct a JSONArray from another JSONArray. This is a shallow copy.
     *
     * @param array
     *            A array.
     */
    public JSONArray(JSONArray array) {
        if (array == null) {
            this.myArrayList = new ArrayList<Object>();
        } else {
            // shallow copy directly the internal array lists as any wrapping
            // should have been done already in the original JSONArray
            this.myArrayList = new ArrayList<Object>(array.myArrayList);
        }
    }

    /**
     * Construct a JSONArray from an array.
     *
     * @param array
     *            Array. If the parameter passed is null, or not an array, an
     *            exception will be thrown.
     *
     * @throws JSONException
     *            If not an array or if an array value is non-finite number.
     * @throws NullPointerException
     *            Thrown if the array parameter is null.
     */
    public JSONArray(Object array) throws JSONException {
        this();
        if (!array.getClass().isArray()) {
            throw new JSONException(
                    "JSONArray initial value should be a string or collection or array.");
        }
        this.addAll(array, true);
    }

    /**
     * Construct a JSONArray with the specified initial capacity.
     *
     * @param initialCapacity
     *            the initial capacity of the JSONArray.
     * @throws JSONException
     *             If the initial capacity is negative.
     */
    public JSONArray(int initialCapacity) throws JSONException {
    	if (initialCapacity < 0) {
            throw new JSONException(
                    "JSONArray initial capacity cannot be negative.");
    	}
    	this.myArrayList = new ArrayList<Object>(initialCapacity);
    }

    @Override
    public Iterator<Object> iterator() {
        return this.myArrayList.iterator();
    }

    /**
     * Get the object value associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return An object value.
     * @throws JSONException
     *             If there is no value for the index.
     */
    public Object get(int index) throws JSONException {
        Object object = this.opt(index);
        if (object == null) {
            throw new JSONException("JSONArray[" + index + "] not found.");
        }
        return object;
    }

    /**
     * Get the boolean value associated with an index. The string values "true"
     * and "false" are converted to boolean.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The truth.
     * @throws JSONException
     *             If there is no value for the index or if the value is not
     *             convertible to boolean.
     */
    public boolean getBoolean(int index) throws JSONException {
        Object object = this.get(index);
        if (object.equals(Boolean.FALSE)
                || (object instanceof String && ((String) object)
                        .equalsIgnoreCase("false"))) {
            return false;
        } else if (object.equals(Boolean.TRUE)
                || (object instanceof String && ((String) object)
                        .equalsIgnoreCase("true"))) {
            return true;
        }
        throw wrongValueFormatException(index, "boolean", null);
    }

    /**
     * Get the double value associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     * @throws JSONException
     *             If the key is not found or if the value cannot be converted
     *             to a number.
     */
    public double getDouble(int index) throws JSONException {
        final Object object = this.get(index);
        if(object instanceof Number) {
            return ((Number)object).doubleValue();
        }
        try {
            return Double.parseDouble(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(index, "double", e);
        }
    }

    /**
     * Get the float value associated with a key.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The numeric value.
     * @throws JSONException
     *             if the key is not found or if the value is not a Number
     *             object and cannot be converted to a number.
     */
    public float getFloat(int index) throws JSONException {
        final Object object = this.get(index);
        if(object instanceof Number) {
            return ((Number)object).floatValue();
        }
        try {
            return Float.parseFloat(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(index, "float", e);
        }
    }

    /**
     * Get the Number value associated with a key.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The numeric value.
     * @throws JSONException
     *             if the key is not found or if the value is not a Number
     *             object and cannot be converted to a number.
     */
    public Number getNumber(int index) throws JSONException {
        Object object = this.get(index);
        try {
            if (object instanceof Number) {
                return (Number)object;
            }
            return JSONObject.stringToNumber(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(index, "number", e);
        }
    }

    /**
     * Get the enum value associated with an index.
     * 
     * @param <E>
     *            Enum Type
     * @param clazz
     *            The type of enum to retrieve.
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The enum value at the index location
     * @throws JSONException
     *            if the key is not found or if the value cannot be converted
     *            to an enum.
     */
    public <E extends Enum<E>> E getEnum(Class<E> clazz, int index) throws JSONException {
        E val = optEnum(clazz, index);
        if(val==null) {
            // JSONException should really take a throwable argument.
            // If it did, I would re-implement this with the Enum.valueOf
            // method and place any thrown exception in the JSONException
            throw wrongValueFormatException(index, "enum of type "
                    + JSONObject.quote(clazz.getSimpleName()), null);
        }
        return val;
    }

    /**
     * Get the BigDecimal value associated with an index. If the value is float
     * or double, the {@link BigDecimal#BigDecimal(double)} constructor
     * will be used. See notes on the constructor for conversion issues that
     * may arise.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     * @throws JSONException
     *             If the key is not found or if the value cannot be converted
     *             to a BigDecimal.
     */
    public BigDecimal getBigDecimal (int index) throws JSONException {
        Object object = this.get(index);
        BigDecimal val = JSONObject.objectToBigDecimal(object, null);
        if(val == null) {
            throw wrongValueFormatException(index, "BigDecimal", object, null);
        }
        return val;
    }

    /**
     * Get the BigInteger value associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     * @throws JSONException
     *             If the key is not found or if the value cannot be converted
     *             to a BigInteger.
     */
    public BigInteger getBigInteger (int index) throws JSONException {
        Object object = this.get(index);
        BigInteger val = JSONObject.objectToBigInteger(object, null);
        if(val == null) {
            throw wrongValueFormatException(index, "BigInteger", object, null);
        }
        return val;
    }

    /**
     * Get the int value associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     * @throws JSONException
     *             If the key is not found or if the value is not a number.
     */
    public int getInt(int index) throws JSONException {
        final Object object = this.get(index);
        if(object instanceof Number) {
            return ((Number)object).intValue();
        }
        try {
            return Integer.parseInt(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(index, "int", e);
        }
    }

    /**
     * Get the JSONArray associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return A JSONArray value.
     * @throws JSONException
     *             If there is no value for the index. or if the value is not a
     *             JSONArray
     */
    public JSONArray getJSONArray(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof JSONArray) {
            return (JSONArray) object;
        }
        throw wrongValueFormatException(index, "JSONArray", null);
    }

    /**
     * Get the JSONObject associated with an index.
     *
     * @param index
     *            subscript
     * @return A JSONObject value.
     * @throws JSONException
     *             If there is no value for the index or if the value is not a
     *             JSONObject
     */
    public JSONObject getJSONObject(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof JSONObject) {
            return (JSONObject) object;
        }
        throw wrongValueFormatException(index, "JSONObject", null);
    }

    /**
     * Get the long value associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     * @throws JSONException
     *             If the key is not found or if the value cannot be converted
     *             to a number.
     */
    public long getLong(int index) throws JSONException {
        final Object object = this.get(index);
        if(object instanceof Number) {
            return ((Number)object).longValue();
        }
        try {
            return Long.parseLong(object.toString());
        } catch (Exception e) {
            throw wrongValueFormatException(index, "long", e);
        }
    }

    /**
     * Get the string associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return A string value.
     * @throws JSONException
     *             If there is no string value for the index.
     */
    public String getString(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof String) {
            return (String) object;
        }
        throw wrongValueFormatException(index, "String", null);
    }

    /**
     * Determine if the value is <code>null</code>.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return true if the value at the index is <code>null</code>, or if there is no value.
     */
    public boolean isNull(int index) {
        return JSONObject.NULL.equals(this.opt(index));
    }

    /**
     * Make a string from the contents of this JSONArray. The
     * <code>separator</code> string is inserted between each element. Warning:
     * This method assumes that the data structure is acyclical.
     *
     * @param separator
     *            A string that will be inserted between the elements.
     * @return a string.
     * @throws JSONException
     *             If the array contains an invalid number.
     */
    public String join(String separator) throws JSONException {
        int len = this.length();
        if (len == 0) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder(
                   JSONObject.valueToString(this.myArrayList.get(0)));

        for (int i = 1; i < len; i++) {
            sb.append(separator)
              .append(JSONObject.valueToString(this.myArrayList.get(i)));
        }
        return sb.toString();
    }

    /**
     * Get the number of elements in the JSONArray, included nulls.
     *
     * @return The length (or size).
     */
    public int length() {
        return this.myArrayList.size();
    }

    /**
     * Removes all of the elements from this JSONArray.
     * The JSONArray will be empty after this call returns.
     */
    public void clear() {
        this.myArrayList.clear();
    }

    /**
     * Get the optional object value associated with an index.
     *
     * @param index
     *            The index must be between 0 and length() - 1. If not, null is returned.
     * @return An object value, or null if there is no object at that index.
     */
    public Object opt(int index) {
        return (index < 0 || index >= this.length()) ? null : this.myArrayList
                .get(index);
    }

    /**
     * Get the optional boolean value associated with an index. It returns false
     * if there is no value at that index, or if the value is not Boolean.TRUE
     * or the String "true".
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The truth.
     */
    public boolean optBoolean(int index) {
        return this.optBoolean(index, false);
    }

    /**
     * Get the optional boolean value associated with an index. It returns the
     * defaultValue if there is no value at that index or if it is not a Boolean
     * or the String "true" or "false" (case insensitive).
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            A boolean default.
     * @return The truth.
     */
    public boolean optBoolean(int index, boolean defaultValue) {
        try {
            return this.getBoolean(index);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get the optional double value associated with an index. NaN is returned
     * if there is no value for the index, or if the value is not a number and
     * cannot be converted to a number.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     */
    public double optDouble(int index) {
        return this.optDouble(index, Double.NaN);
    }

    /**
     * Get the optional double value associated with an index. The defaultValue
     * is returned if there is no value for the index, or if the value is not a
     * number and cannot be converted to a number.
     *
     * @param index
     *            subscript
     * @param defaultValue
     *            The default value.
     * @return The value.
     */
    public double optDouble(int index, double defaultValue) {
        final Number val = this.optNumber(index, null);
        if (val == null) {
            return defaultValue;
        }
        final double doubleValue = val.doubleValue();
        // if (Double.isNaN(doubleValue) || Double.isInfinite(doubleValue)) {
        // return defaultValue;
        // }
        return doubleValue;
    }

    /**
     * Get the optional float value associated with an index. NaN is returned
     * if there is no value for the index, or if the value is not a number and
     * cannot be converted to a number.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     */
    public float optFloat(int index) {
        return this.optFloat(index, Float.NaN);
    }

    /**
     * Get the optional float value associated with an index. The defaultValue
     * is returned if there is no value for the index, or if the value is not a
     * number and cannot be converted to a number.
     *
     * @param index
     *            subscript
     * @param defaultValue
     *            The default value.
     * @return The value.
     */
    public float optFloat(int index, float defaultValue) {
        final Number val = this.optNumber(index, null);
        if (val == null) {
            return defaultValue;
        }
        final float floatValue = val.floatValue();
        // if (Float.isNaN(floatValue) || Float.isInfinite(floatValue)) {
        // return floatValue;
        // }
        return floatValue;
    }

    /**
     * Get the optional int value associated with an index. Zero is returned if
     * there is no value for the index, or if the value is not a number and
     * cannot be converted to a number.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     */
    public int optInt(int index) {
        return this.optInt(index, 0);
    }

    /**
     * Get the optional int value associated with an index. The defaultValue is
     * returned if there is no value for the index, or if the value is not a
     * number and cannot be converted to a number.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            The default value.
     * @return The value.
     */
    public int optInt(int index, int defaultValue) {
        final Number val = this.optNumber(index, null);
        if (val == null) {
            return defaultValue;
        }
        return val.intValue();
    }

    /**
     * Get the enum value associated with a key.
     * 
     * @param <E>
     *            Enum Type
     * @param clazz
     *            The type of enum to retrieve.
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The enum value at the index location or null if not found
     */
    public <E extends Enum<E>> E optEnum(Class<E> clazz, int index) {
        return this.optEnum(clazz, index, null);
    }

    /**
     * Get the enum value associated with a key.
     * 
     * @param <E>
     *            Enum Type
     * @param clazz
     *            The type of enum to retrieve.
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            The default in case the value is not found
     * @return The enum value at the index location or defaultValue if
     *            the value is not found or cannot be assigned to clazz
     */
    public <E extends Enum<E>> E optEnum(Class<E> clazz, int index, E defaultValue) {
        try {
            Object val = this.opt(index);
            if (JSONObject.NULL.equals(val)) {
                return defaultValue;
            }
            if (clazz.isAssignableFrom(val.getClass())) {
                // we just checked it!
                @SuppressWarnings("unchecked")
                E myE = (E) val;
                return myE;
            }
            return Enum.valueOf(clazz, val.toString());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        } catch (NullPointerException e) {
            return defaultValue;
        }
    }

    /**
     * Get the optional BigInteger value associated with an index. The 
     * defaultValue is returned if there is no value for the index, or if the 
     * value is not a number and cannot be converted to a number.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            The default value.
     * @return The value.
     */
    public BigInteger optBigInteger(int index, BigInteger defaultValue) {
        Object val = this.opt(index);
        return JSONObject.objectToBigInteger(val, defaultValue);
    }

    /**
     * Get the optional BigDecimal value associated with an index. The 
     * defaultValue is returned if there is no value for the index, or if the 
     * value is not a number and cannot be converted to a number. If the value
     * is float or double, the {@link BigDecimal#BigDecimal(double)}
     * constructor will be used. See notes on the constructor for conversion
     * issues that may arise.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            The default value.
     * @return The value.
     */
    public BigDecimal optBigDecimal(int index, BigDecimal defaultValue) {
        Object val = this.opt(index);
        return JSONObject.objectToBigDecimal(val, defaultValue);
    }

    /**
     * Get the optional JSONArray associated with an index.
     *
     * @param index
     *            subscript
     * @return A JSONArray value, or null if the index has no value, or if the
     *         value is not a JSONArray.
     */
    public JSONArray optJSONArray(int index) {
        Object o = this.opt(index);
        return o instanceof JSONArray ? (JSONArray) o : null;
    }

    /**
     * Get the optional JSONObject associated with an index. Null is returned if
     * the key is not found, or null if the index has no value, or if the value
     * is not a JSONObject.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return A JSONObject value.
     */
    public JSONObject optJSONObject(int index) {
        Object o = this.opt(index);
        return o instanceof JSONObject ? (JSONObject) o : null;
    }

    /**
     * Get the optional long value associated with an index. Zero is returned if
     * there is no value for the index, or if the value is not a number and
     * cannot be converted to a number.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return The value.
     */
    public long optLong(int index) {
        return this.optLong(index, 0);
    }

    /**
     * Get the optional long value associated with an index. The defaultValue is
     * returned if there is no value for the index, or if the value is not a
     * number and cannot be converted to a number.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            The default value.
     * @return The value.
     */
    public long optLong(int index, long defaultValue) {
        final Number val = this.optNumber(index, null);
        if (val == null) {
            return defaultValue;
        }
        return val.longValue();
    }

    /**
     * Get an optional {@link Number} value associated with a key, or <code>null</code>
     * if there is no such key or if the value is not a number. If the value is a string,
     * an attempt will be made to evaluate it as a number ({@link BigDecimal}). This method
     * would be used in cases where type coercion of the number value is unwanted.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return An object which is the value.
     */
    public Number optNumber(int index) {
        return this.optNumber(index, null);
    }

    /**
     * Get an optional {@link Number} value associated with a key, or the default if there
     * is no such key or if the value is not a number. If the value is a string,
     * an attempt will be made to evaluate it as a number ({@link BigDecimal}). This method
     * would be used in cases where type coercion of the number value is unwanted.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            The default.
     * @return An object which is the value.
     */
    public Number optNumber(int index, Number defaultValue) {
        Object val = this.opt(index);
        if (JSONObject.NULL.equals(val)) {
            return defaultValue;
        }
        if (val instanceof Number){
            return (Number) val;
        }
        
        if (val instanceof String) {
            try {
                return JSONObject.stringToNumber((String) val);
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Get the optional string value associated with an index. It returns an
     * empty string if there is no value at that index. If the value is not a
     * string and is not null, then it is converted to a string.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @return A String value.
     */
    public String optString(int index) {
        return this.optString(index, "");
    }

    /**
     * Get the optional string associated with an index. The defaultValue is
     * returned if the key is not found.
     *
     * @param index
     *            The index must be between 0 and length() - 1.
     * @param defaultValue
     *            The default value.
     * @return A String value.
     */
    public String optString(int index, String defaultValue) {
        Object object = this.opt(index);
        return JSONObject.NULL.equals(object) ? defaultValue : object
                .toString();
    }

    /**
     * Append a boolean value. This increases the array's length by one.
     *
     * @param value
     *            A boolean value.
     * @return this.
     */
    public JSONArray put(boolean value) {
        return this.put(value ? Boolean.TRUE : Boolean.FALSE);
    }

    /**
     * Put a value in the JSONArray, where the value will be a JSONArray which
     * is produced from a Collection.
     *
     * @param value
     *            A Collection value.
     * @return this.
     * @throws JSONException
     *            If the value is non-finite number.
     */
    public JSONArray put(Collection<?> value) {
        return this.put(new JSONArray(value));
    }

    /**
     * Append a double value. This increases the array's length by one.
     *
     * @param value
     *            A double value.
     * @return this.
     * @throws JSONException
     *             if the value is not finite.
     */
    public JSONArray put(double value) throws JSONException {
        return this.put(Double.valueOf(value));
    }
    
    /**
     * Append a float value. This increases the array's length by one.
     *
     * @param value
     *            A float value.
     * @return this.
     * @throws JSONException
     *             if the value is not finite.
     */
    public JSONArray put(float value) throws JSONException {
        return this.put(Float.valueOf(value));
    }

    /**
     * Append an int value. This increases the array's length by one.
     *
     * @param value
     *            An int value.
     * @return this.
     */
    public JSONArray put(int value) {
        return this.put(Integer.valueOf(value));
    }

    /**
     * Append an long value. This increases the array's length by one.
     *
     * @param value
     *            A long value.
     * @return this.
     */
    public JSONArray put(long value) {
        return this.put(Long.valueOf(value));
    }

    /**
     * Put a value in the JSONArray, where the value will be a JSONObject which
     * is produced from a Map.
     *
     * @param value
     *            A Map value.
     * @return this.
     * @throws JSONException
     *            If a value in the map is non-finite number.
     * @throws NullPointerException
     *            If a key in the map is <code>null</code>
     */
    public JSONArray put(Map<?, ?> value) {
        return this.put(new JSONObject(value));
    }

    /**
     * Append an object value. This increases the array's length by one.
     *
     * @param value
     *            An object value. The value should be a Boolean, Double,
     *            Integer, JSONArray, JSONObject, Long, or String, or the
     *            JSONObject.NULL object.
     * @return this.
     * @throws JSONException
     *            If the value is non-finite number.
     */
    public JSONArray put(Object value) {
        JSONObject.testValidity(value);
        this.myArrayList.add(value);
        return this;
    }

    /**
     * Put or replace a boolean value in the JSONArray. If the index is greater
     * than the length of the JSONArray, then null elements will be added as
     * necessary to pad it out.
     *
     * @param index
     *            The subscript.
     * @param value
     *            A boolean value.
     * @return this.
     * @throws JSONException
     *             If the index is negative.
     */
    public JSONArray put(int index, boolean value) throws JSONException {
        return this.put(index, value ? Boolean.TRUE : Boolean.FALSE);
    }

    /**
     * Put a value in the JSONArray, where the value will be a JSONArray which
     * is produced from a Collection.
     *
     * @param index
     *            The subscript.
     * @param value
     *            A Collection value.
     * @return this.
     * @throws JSONException
     *             If the index is negative or if the value is non-finite.
     */
    public JSONArray put(int index, Collection<?> value) throws JSONException {
        return this.put(index, new JSONArray(value));
    }

    /**
     * Put or replace a double value. If the index is greater than the length of
     * the JSONArray, then null elements will be added as necessary to pad it
     * out.
     *
     * @param index
     *            The subscript.
     * @param value
     *            A double value.
     * @return this.
     * @throws JSONException
     *             If the index is negative or if the value is non-finite.
     */
    public JSONArray put(int index, double value) throws JSONException {
        return this.put(index, Double.valueOf(value));
    }

    /**
     * Put or replace a float value. If the index is greater than the length of
     * the JSONArray, then null elements will be added as necessary to pad it
     * out.
     *
     * @param index
     *            The subscript.
     * @param value
     *            A float value.
     * @return this.
     * @throws JSONException
     *             If the index is negative or if the value is non-finite.
     */
    public JSONArray put(int index, float value) throws JSONException {
        return this.put(index, Float.valueOf(value));
    }

    /**
     * Put or replace an int value. If the index is greater than the length of
     * the JSONArray, then null elements will be added as necessary to pad it
     * out.
     *
     * @param index
     *            The subscript.
     * @param value
     *            An int value.
     * @return this.
     * @throws JSONException
     *             If the index is negative.
     */
    public JSONArray put(int index, int value) throws JSONException {
        return this.put(index, Integer.valueOf(value));
    }

    /**
     * Put or replace a long value. If the index is greater than the length of
     * the JSONArray, then null elements will be added as necessary to pad it
     * out.
     *
     * @param index
     *            The subscript.
     * @param value
     *            A long value.
     * @return this.
     * @throws JSONException
     *             If the index is negative.
     */
    public JSONArray put(int index, long value) throws JSONException {
        return this.put(index, Long.valueOf(value));
    }

    /**
     * Put a value in the JSONArray, where the value will be a JSONObject that
     * is produced from a Map.
     *
     * @param index
     *            The subscript.
     * @param value
     *            The Map value.
     * @return this.
     * @throws JSONException
     *             If the index is negative or if the value is an invalid
     *             number.
     * @throws NullPointerException
     *             If a key in the map is <code>null</code>
     */
    public JSONArray put(int index, Map<?, ?> value) throws JSONException {
        this.put(index, new JSONObject(value));
        return this;
    }

    /**
     * Put or replace an object value in the JSONArray. If the index is greater
     * than the length of the JSONArray, then null elements will be added as
     * necessary to pad it out.
     *
     * @param index
     *            The subscript.
     * @param value
     *            The value to put into the array. The value should be a
     *            Boolean, Double, Integer, JSONArray, JSONObject, Long, or
     *            String, or the JSONObject.NULL object.
     * @return this.
     * @throws JSONException
     *             If the index is negative or if the value is an invalid
     *             number.
     */
    public JSONArray put(int index, Object value) throws JSONException {
        if (index < 0) {
            throw new JSONException("JSONArray[" + index + "] not found.");
        }
        if (index < this.length()) {
            JSONObject.testValidity(value);
            this.myArrayList.set(index, value);
            return this;
        }
        if(index == this.length()){
            // simple append
            return this.put(value);
        }
        // if we are inserting past the length, we want to grow the array all at once
        // instead of incrementally.
        this.myArrayList.ensureCapacity(index + 1);
        while (index != this.length()) {
            // we don't need to test validity of NULL objects
            this.myArrayList.add(JSONObject.NULL);
        }
        return this.put(value);
    }

    /**
     * Put a collection's elements in to the JSONArray.
     *
     * @param collection
     *            A Collection.
     * @return this. 
     */
    public JSONArray putAll(Collection<?> collection) {
        this.addAll(collection, false);
        return this;
    }
    
    /**
     * Put an Iterable's elements in to the JSONArray.
     *
     * @param iter
     *            An Iterable.
     * @return this. 
     */
    public JSONArray putAll(Iterable<?> iter) {
        this.addAll(iter, false);
        return this;
    }

    /**
     * Put a JSONArray's elements in to the JSONArray.
     *
     * @param array
     *            A JSONArray.
     * @return this. 
     */
    public JSONArray putAll(JSONArray array) {
        // directly copy the elements from the source array to this one
        // as all wrapping should have been done already in the source.
        this.myArrayList.addAll(array.myArrayList);
        return this;
    }

    /**
     * Put an array's elements in to the JSONArray.
     *
     * @param array
     *            Array. If the parameter passed is null, or not an array or Iterable, an
     *            exception will be thrown.
     * @return this. 
     *
     * @throws JSONException
     *            If not an array, JSONArray, Iterable or if an value is non-finite number.
     * @throws NullPointerException
     *            Thrown if the array parameter is null.
     */
    public JSONArray putAll(Object array) throws JSONException {
        this.addAll(array, false);
        return this;
    }
    
    /**
     * Creates a JSONPointer using an initialization string and tries to 
     * match it to an item within this JSONArray. For example, given a
     * JSONArray initialized with this document:
     * <pre>
     * [
     *     {"b":"c"}
     * ]
     * </pre>
     * and this JSONPointer string: 
     * <pre>
     * "/0/b"
     * </pre>
     * Then this method will return the String "c"
     * A JSONPointerException may be thrown from code called by this method.
     *
     * @param jsonPointer string that can be used to create a JSONPointer
     * @return the item matched by the JSONPointer, otherwise null
     */
    public Object query(String jsonPointer) {
        return query(new JSONPointer(jsonPointer));
    }
    
    /**
     * Uses a user initialized JSONPointer  and tries to 
     * match it to an item within this JSONArray. For example, given a
     * JSONArray initialized with this document:
     * <pre>
     * [
     *     {"b":"c"}
     * ]
     * </pre>
     * and this JSONPointer: 
     * <pre>
     * "/0/b"
     * </pre>
     * Then this method will return the String "c"
     * A JSONPointerException may be thrown from code called by this method.
     *
     * @param jsonPointer string that can be used to create a JSONPointer
     * @return the item matched by the JSONPointer, otherwise null
     */
    public Object query(JSONPointer jsonPointer) {
        return jsonPointer.queryFrom(this);
    }
    
    /**
     * Queries and returns a value from this object using {@code jsonPointer}, or
     * returns null if the query fails due to a missing key.
     * 
     * @param jsonPointer the string representation of the JSON pointer
     * @return the queried value or {@code null}
     * @throws IllegalArgumentException if {@code jsonPointer} has invalid syntax
     */
    public Object optQuery(String jsonPointer) {
    	return optQuery(new JSONPointer(jsonPointer));
    }
    
    /**
     * Queries and returns a value from this object using {@code jsonPointer}, or
     * returns null if the query fails due to a missing key.
     * 
     * @param jsonPointer The JSON pointer
     * @return the queried value or {@code null}
     * @throws IllegalArgumentException if {@code jsonPointer} has invalid syntax
     */
    public Object optQuery(JSONPointer jsonPointer) {
        try {
            return jsonPointer.queryFrom(this);
        } catch (JSONPointerException e) {
            return null;
        }
    }

    /**
     * Remove an index and close the hole.
     *
     * @param index
     *            The index of the element to be removed.
     * @return The value that was associated with the index, or null if there
     *         was no value.
     */
    public Object remove(int index) {
        return index >= 0 && index < this.length()
            ? this.myArrayList.remove(index)
            : null;
    }

    /**
     * Determine if two JSONArrays are similar.
     * They must contain similar sequences.
     *
     * @param other The other JSONArray
     * @return true if they are equal
     */
    public boolean similar(Object other) {
        if (!(other instanceof JSONArray)) {
            return false;
        }
        int len = this.length();
        if (len != ((JSONArray)other).length()) {
            return false;
        }
        for (int i = 0; i < len; i += 1) {
            Object valueThis = this.myArrayList.get(i);
            Object valueOther = ((JSONArray)other).myArrayList.get(i);
            if(valueThis == valueOther) {
            	continue;
            }
            if(valueThis == null) {
            	return false;
            }
            if (valueThis instanceof JSONObject) {
                if (!((JSONObject)valueThis).similar(valueOther)) {
                    return false;
                }
            } else if (valueThis instanceof JSONArray) {
                if (!((JSONArray)valueThis).similar(valueOther)) {
                    return false;
                }
            } else if (valueThis instanceof Number && valueOther instanceof Number) {
                if (!JSONObject.isNumberSimilar((Number)valueThis, (Number)valueOther)) {
                	return false;
                }
            } else if (!valueThis.equals(valueOther)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Produce a JSONObject by combining a JSONArray of names with the values of
     * this JSONArray.
     *
     * @param names
     *            A JSONArray containing a list of key strings. These will be
     *            paired with the values.
     * @return A JSONObject, or null if there are no names or if this JSONArray
     *         has no values.
     * @throws JSONException
     *             If any of the names are null.
     */
    public JSONObject toJSONObject(JSONArray names) throws JSONException {
        if (names == null || names.isEmpty() || this.isEmpty()) {
            return null;
        }
        JSONObject jo = new JSONObject(names.length());
        for (int i = 0; i < names.length(); i += 1) {
            jo.put(names.getString(i), this.opt(i));
        }
        return jo;
    }

    /**
     * Make a JSON text of this JSONArray. For compactness, no unnecessary
     * whitespace is added. If it is not possible to produce a syntactically
     * correct JSON text then null will be returned instead. This could occur if
     * the array contains an invalid number.
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     * </b>
     *
     * @return a printable, displayable, transmittable representation of the
     *         array.
     */
    @Override
    public String toString() {
        try {
            return this.toString(0);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Make a pretty-printed JSON text of this JSONArray.
     * 
     * <p>If <pre> {@code indentFactor > 0}</pre> and the {@link JSONArray} has only
     * one element, then the array will be output on a single line:
     * <pre>{@code [1]}</pre>
     * 
     * <p>If an array has 2 or more elements, then it will be output across
     * multiple lines: <pre>{@code
     * [
     * 1,
     * "value 2",
     * 3
     * ]
     * }</pre>
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     * </b>
     * 
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @return a printable, displayable, transmittable representation of the
     *         object, beginning with <code>[</code>&nbsp;<small>(left
     *         bracket)</small> and ending with <code>]</code>
     *         &nbsp;<small>(right bracket)</small>.
     * @throws JSONException if a called function fails
     */
    public String toString(int indentFactor) throws JSONException {
        StringWriter sw = new StringWriter();
        synchronized (sw.getBuffer()) {
            return this.write(sw, indentFactor, 0).toString();
        }
    }

    /**
     * Write the contents of the JSONArray as JSON text to a writer. For
     * compactness, no whitespace is added.
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     *</b>
     * @param writer the writer object
     * @return The writer.
     * @throws JSONException if a called function fails
     */
    public Writer write(Writer writer) throws JSONException {
        return this.write(writer, 0, 0);
    }

    /**
     * Write the contents of the JSONArray as JSON text to a writer.
     * 
     * <p>If <pre>{@code indentFactor > 0}</pre> and the {@link JSONArray} has only
     * one element, then the array will be output on a single line:
     * <pre>{@code [1]}</pre>
     * 
     * <p>If an array has 2 or more elements, then it will be output across
     * multiple lines: <pre>{@code
     * [
     * 1,
     * "value 2",
     * 3
     * ]
     * }</pre>
     * <p><b>
     * Warning: This method assumes that the data structure is acyclical.
     * </b>
     *
     * @param writer
     *            Writes the serialized JSON
     * @param indentFactor
     *            The number of spaces to add to each level of indentation.
     * @param indent
     *            The indentation of the top level.
     * @return The writer.
     * @throws JSONException if a called function fails or unable to write
     */
    public Writer write(Writer writer, int indentFactor, int indent)
            throws JSONException {
        try {
            boolean needsComma = false;
            int length = this.length();
            writer.write('[');

            if (length == 1) {
                try {
                    JSONObject.writeValue(writer, this.myArrayList.get(0),
                            indentFactor, indent);
                } catch (Exception e) {
                    throw new JSONException("Unable to write JSONArray value at index: 0", e);
                }
            } else if (length != 0) {
                final int newIndent = indent + indentFactor;

                for (int i = 0; i < length; i += 1) {
                    if (needsComma) {
                        writer.write(',');
                    }
                    if (indentFactor > 0) {
                        writer.write('\n');
                    }
                    JSONObject.indent(writer, newIndent);
                    try {
                        JSONObject.writeValue(writer, this.myArrayList.get(i),
                                indentFactor, newIndent);
                    } catch (Exception e) {
                        throw new JSONException("Unable to write JSONArray value at index: " + i, e);
                    }
                    needsComma = true;
                }
                if (indentFactor > 0) {
                    writer.write('\n');
                }
                JSONObject.indent(writer, indent);
            }
            writer.write(']');
            return writer;
        } catch (IOException e) {
            throw new JSONException(e);
        }
    }

    /**
     * Returns a java.util.List containing all of the elements in this array.
     * If an element in the array is a JSONArray or JSONObject it will also
     * be converted to a List and a Map respectively.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return a java.util.List containing the elements of this array
     */
    public List<Object> toList() {
        List<Object> results = new ArrayList<Object>(this.myArrayList.size());
        for (Object element : this.myArrayList) {
            if (element == null || JSONObject.NULL.equals(element)) {
                results.add(null);
            } else if (element instanceof JSONArray) {
                results.add(((JSONArray) element).toList());
            } else if (element instanceof JSONObject) {
                results.add(((JSONObject) element).toMap());
            } else {
                results.add(element);
            }
        }
        return results;
    }

    /**
     * Check if JSONArray is empty.
     *
     * @return true if JSONArray is empty, otherwise false.
     */
    public boolean isEmpty() {
        return this.myArrayList.isEmpty();
    }

    /**
     * Add a collection's elements to the JSONArray.
     *
     * @param collection
     *            A Collection.
     * @param wrap
     *            {@code true} to call {@link JSONObject#wrap(Object)} for each item,
     *            {@code false} to add the items directly
     *            
     */
    private void addAll(Collection<?> collection, boolean wrap) {
        this.myArrayList.ensureCapacity(this.myArrayList.size() + collection.size());
        if (wrap) {
            for (Object o: collection){
                this.put(JSONObject.wrap(o));
            }
        } else {
            for (Object o: collection){
                this.put(o);
            }
        }
    }

    /**
     * Add an Iterable's elements to the JSONArray.
     *
     * @param iter
     *            An Iterable.
     * @param wrap
     *            {@code true} to call {@link JSONObject#wrap(Object)} for each item,
     *            {@code false} to add the items directly
     */
    private void addAll(Iterable<?> iter, boolean wrap) {
        if (wrap) {
            for (Object o: iter){
                this.put(JSONObject.wrap(o));
            }
        } else {
            for (Object o: iter){
                this.put(o);
            }
        }
    }
    
    /**
     * Add an array's elements to the JSONArray.
     *
     * @param array
     *            Array. If the parameter passed is null, or not an array,
     *            JSONArray, Collection, or Iterable, an exception will be
     *            thrown.
     * @param wrap
     *            {@code true} to call {@link JSONObject#wrap(Object)} for each item,
     *            {@code false} to add the items directly
     *
     * @throws JSONException
     *            If not an array or if an array value is non-finite number.
     * @throws NullPointerException
     *            Thrown if the array parameter is null.
     */
    private void addAll(Object array, boolean wrap) throws JSONException {
        if (array.getClass().isArray()) {
            int length = Array.getLength(array);
            this.myArrayList.ensureCapacity(this.myArrayList.size() + length);
            if (wrap) {
                for (int i = 0; i < length; i += 1) {
                    this.put(JSONObject.wrap(Array.get(array, i)));
                }
            } else {
                for (int i = 0; i < length; i += 1) {
                    this.put(Array.get(array, i));
                }
            }
        } else if (array instanceof JSONArray) {
            // use the built in array list `addAll` as all object
            // wrapping should have been completed in the original
            // JSONArray
            this.myArrayList.addAll(((JSONArray)array).myArrayList);
        } else if (array instanceof Collection) {
            this.addAll((Collection<?>)array, wrap);
        } else if (array instanceof Iterable) {
            this.addAll((Iterable<?>)array, wrap);
        } else {
            throw new JSONException(
                    "JSONArray initial value should be a string or collection or array.");
        }
    }
    
    /**
     * Create a new JSONException in a common format for incorrect conversions.
     * @param idx index of the item
     * @param valueType the type of value being coerced to
     * @param cause optional cause of the coercion failure
     * @return JSONException that can be thrown.
     */
    private static JSONException wrongValueFormatException(
            int idx,
            String valueType,
            Throwable cause) {
        return new JSONException(
                "JSONArray[" + idx + "] is not a " + valueType + "."
                , cause);
    }
    
    /**
     * Create a new JSONException in a common format for incorrect conversions.
     * @param idx index of the item
     * @param valueType the type of value being coerced to
     * @param cause optional cause of the coercion failure
     * @return JSONException that can be thrown.
     */
    private static JSONException wrongValueFormatException(
            int idx,
            String valueType,
            Object value,
            Throwable cause) {
        return new JSONException(
                "JSONArray[" + idx + "] is not a " + valueType + " (" + value + ")."
                , cause);
    }

}

@Documented
@Retention(RUNTIME)
@Target({METHOD})
/**
 * Use this annotation on a getter method to override the Bean name
 * parser for Bean -&gt; JSONObject mapping. A value set to empty string <code>""</code>
 * will have the Bean parser fall back to the default field name processing.
 */
 @interface JSONPropertyName {
    /**
     * @return The name of the property as to be used in the JSON Object.
     */
    String value();
}

/**
 * Annotation retention policy.  The constants of this enumerated class
 * describe the various policies for retaining annotations.  They are used
 * in conjunction with the {@link Retention} meta-annotation interface to
 * specify how long annotations are to be retained.
 *
 * @author  Joshua Bloch
 * @since 1.5
 */
 enum RetentionPolicy {
    /**
     * Annotations are to be discarded by the compiler.
     */
    SOURCE,

    /**
     * Annotations are to be recorded in the class file by the compiler
     * but need not be retained by the VM at run time.  This is the default
     * behavior.
     */
    CLASS,

    /**
     * Annotations are to be recorded in the class file by the compiler and
     * retained by the VM at run time, so they may be read reflectively.
     *
     * @see java.lang.reflect.AnnotatedElement
     */
    RUNTIME
}

/**
 * The constants of this enumerated class provide a simple classification of the
 * syntactic locations where annotations may appear in a Java program. These
 * constants are used in {@link java.lang.annotation.Target Target}
 * meta-annotations to specify where it is legal to write annotations of a
 * given type.
 *
 * <p>The syntactic locations where annotations may appear are split into
 * <em>declaration contexts</em>, where annotations apply to declarations, and
 * <em>type contexts</em>, where annotations apply to types used in
 * declarations and expressions.
 *
 * <p>The constants {@link #ANNOTATION_TYPE}, {@link #CONSTRUCTOR}, {@link
 * #FIELD}, {@link #LOCAL_VARIABLE}, {@link #METHOD}, {@link #PACKAGE}, {@link
 * #MODULE}, {@link #PARAMETER}, {@link #TYPE}, and {@link #TYPE_PARAMETER}
 * correspond to the declaration contexts in JLS 9.6.4.1.
 *
 * <p>For example, an annotation whose interface is meta-annotated with
 * {@code @Target(ElementType.FIELD)} may only be written as a modifier for a
 * field declaration.
 *
 * <p>The constant {@link #TYPE_USE} corresponds to the type contexts in JLS
 * 4.11, as well as to two declaration contexts: class and interface
 * declarations (including annotation declarations) and type parameter
 * declarations.
 *
 * <p>For example, an annotation whose interface is meta-annotated with
 * {@code @Target(ElementType.TYPE_USE)} may be written on the class or
 * interface of a field (or within the class or interface of the field, if it
 * is a nested or parameterized class or interface, or array class), and may
 * also appear as a modifier for, say, a class declaration.
 *
 * <p>The {@code TYPE_USE} constant includes class and interface declarations
 * and type parameter declarations as a convenience for designers of
 * type checkers which give semantics to annotation interfaces. For example,
 * if the annotation interface {@code NonNull} is meta-annotated with
 * {@code @Target(ElementType.TYPE_USE)}, then {@code @NonNull}
 * {@code class C {...}} could be treated by a type checker as indicating that
 * all variables of class {@code C} are non-null, while still allowing
 * variables of other classes to be non-null or not non-null based on whether
 * {@code @NonNull} appears at the variable's declaration.
 *
 * @author  Joshua Bloch
 * @since 1.5
 * @jls 9.6.4.1 @Target
 * @jls 4.1 The Kinds of Types and Values
 */
 enum ElementType {
    /** Class, interface (including annotation interface), enum, or record
     * declaration */
    TYPE,

    /** Field declaration (includes enum constants) */
    FIELD,

    /** Method declaration */
    METHOD,

    /** Formal parameter declaration */
    PARAMETER,

    /** Constructor declaration */
    CONSTRUCTOR,

    /** Local variable declaration */
    LOCAL_VARIABLE,

    /** Annotation interface declaration (Formerly known as an annotation type.) */
    ANNOTATION_TYPE,

    /** Package declaration */
    PACKAGE,

    /**
     * Type parameter declaration
     *
     * @since 1.8
     */
    TYPE_PARAMETER,

    /**
     * Use of a type
     *
     * @since 1.8
     */
    TYPE_USE,

    /**
     * Module declaration.
     *
     * @since 9
     */
    MODULE,

    /**
     * Record component
     *
     * @jls 8.10.3 Record Members
     * @jls 9.7.4 Where Annotations May Appear
     *
     * @since 16
     */
    RECORD_COMPONENT;
}

@Documented
@Retention(RUNTIME)
@Target({METHOD})
/**
 * Use this annotation on a getter method to override the Bean name
 * parser for Bean -&gt; JSONObject mapping. If this annotation is
 * present at any level in the class hierarchy, then the method will
 * not be serialized from the bean into the JSONObject.
 */
 @interface JSONPropertyIgnore { }

/**
 * A JSON Pointer is a simple query language defined for JSON documents by
 * <a href="https://tools.ietf.org/html/rfc6901">RFC 6901</a>.
 * 
 * In a nutshell, JSONPointer allows the user to navigate into a JSON document
 * using strings, and retrieve targeted objects, like a simple form of XPATH.
 * Path segments are separated by the '/' char, which signifies the root of
 * the document when it appears as the first char of the string. Array 
 * elements are navigated using ordinals, counting from 0. JSONPointer strings
 * may be extended to any arbitrary number of segments. If the navigation
 * is successful, the matched item is returned. A matched item may be a
 * JSONObject, a JSONArray, or a JSON value. If the JSONPointer string building 
 * fails, an appropriate exception is thrown. If the navigation fails to find
 * a match, a JSONPointerException is thrown. 
 * 
 * @author JSON.org
 * @version 2016-05-14
 */
 class JSONPointer {

    // used for URL encoding and decoding
    private static final String ENCODING = "utf-8";

    /**
     * This class allows the user to build a JSONPointer in steps, using
     * exactly one segment in each step.
     */
    public static class Builder {

        // Segments for the eventual JSONPointer string
        private final List<String> refTokens = new ArrayList<String>();

        /**
         * Creates a {@code JSONPointer} instance using the tokens previously set using the
         * {@link #append(String)} method calls.
         * @return a JSONPointer object
         */
        public JSONPointer build() {
            return new JSONPointer(this.refTokens);
        }

        /**
         * Adds an arbitrary token to the list of reference tokens. It can be any non-null value.
         * 
         * Unlike in the case of JSON string or URI fragment representation of JSON pointers, the
         * argument of this method MUST NOT be escaped. If you want to query the property called
         * {@code "a~b"} then you should simply pass the {@code "a~b"} string as-is, there is no
         * need to escape it as {@code "a~0b"}.
         * 
         * @param token the new token to be appended to the list
         * @return {@code this}
         * @throws NullPointerException if {@code token} is null
         */
        public Builder append(String token) {
            if (token == null) {
                throw new NullPointerException("token cannot be null");
            }
            this.refTokens.add(token);
            return this;
        }

        /**
         * Adds an integer to the reference token list. Although not necessarily, mostly this token will
         * denote an array index. 
         * 
         * @param arrayIndex the array index to be added to the token list
         * @return {@code this}
         */
        public Builder append(int arrayIndex) {
            this.refTokens.add(String.valueOf(arrayIndex));
            return this;
        }
    }

    /**
     * Static factory method for {@link Builder}. Example usage:
     * 
     * <pre><code>
     * JSONPointer pointer = JSONPointer.builder()
     *       .append("obj")
     *       .append("other~key").append("another/key")
     *       .append("\"")
     *       .append(0)
     *       .build();
     * </code></pre>
     * 
     *  @return a builder instance which can be used to construct a {@code JSONPointer} instance by chained
     *  {@link Builder#append(String)} calls.
     */
    public static Builder builder() {
        return new Builder();
    }

    // Segments for the JSONPointer string
    private final List<String> refTokens;

    /**
     * Pre-parses and initializes a new {@code JSONPointer} instance. If you want to
     * evaluate the same JSON Pointer on different JSON documents then it is recommended
     * to keep the {@code JSONPointer} instances due to performance considerations.
     * 
     * @param pointer the JSON String or URI Fragment representation of the JSON pointer.
     * @throws IllegalArgumentException if {@code pointer} is not a valid JSON pointer
     */
    public JSONPointer(final String pointer) {
        if (pointer == null) {
            throw new NullPointerException("pointer cannot be null");
        }
        if (pointer.isEmpty() || pointer.equals("#")) {
            this.refTokens = Collections.emptyList();
            return;
        }
        String refs;
        if (pointer.startsWith("#/")) {
            refs = pointer.substring(2);
            try {
                refs = URLDecoder.decode(refs, ENCODING);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        } else if (pointer.startsWith("/")) {
            refs = pointer.substring(1);
        } else {
            throw new IllegalArgumentException("a JSON pointer should start with '/' or '#/'");
        }
        this.refTokens = new ArrayList<String>();
        int slashIdx = -1;
        int prevSlashIdx = 0;
        do {
            prevSlashIdx = slashIdx + 1;
            slashIdx = refs.indexOf('/', prevSlashIdx);
            if(prevSlashIdx == slashIdx || prevSlashIdx == refs.length()) {
                // found 2 slashes in a row ( obj//next )
                // or single slash at the end of a string ( obj/test/ )
                this.refTokens.add("");
            } else if (slashIdx >= 0) {
                final String token = refs.substring(prevSlashIdx, slashIdx);
                this.refTokens.add(unescape(token));
            } else {
                // last item after separator, or no separator at all.
                final String token = refs.substring(prevSlashIdx);
                this.refTokens.add(unescape(token));
            }
        } while (slashIdx >= 0);
        // using split does not take into account consecutive separators or "ending nulls"
        //for (String token : refs.split("/")) {
        //    this.refTokens.add(unescape(token));
        //}
    }

    public JSONPointer(List<String> refTokens) {
        this.refTokens = new ArrayList<String>(refTokens);
    }

    /**
     * @see <a href="https://tools.ietf.org/html/rfc6901#section-3">rfc6901 section 3</a>
     */
    private static String unescape(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    /**
     * Evaluates this JSON Pointer on the given {@code document}. The {@code document}
     * is usually a {@link JSONObject} or a {@link JSONArray} instance, but the empty
     * JSON Pointer ({@code ""}) can be evaluated on any JSON values and in such case the
     * returned value will be {@code document} itself. 
     * 
     * @param document the JSON document which should be the subject of querying.
     * @return the result of the evaluation
     * @throws JSONPointerException if an error occurs during evaluation
     */
    public Object queryFrom(Object document) throws JSONPointerException {
        if (this.refTokens.isEmpty()) {
            return document;
        }
        Object current = document;
        for (String token : this.refTokens) {
            if (current instanceof JSONObject) {
                current = ((JSONObject) current).opt(unescape(token));
            } else if (current instanceof JSONArray) {
                current = readByIndexToken(current, token);
            } else {
                throw new JSONPointerException(String.format(
                        "value [%s] is not an array or object therefore its key %s cannot be resolved", current,
                        token));
            }
        }
        return current;
    }


    /**
     * Matches a JSONArray element by ordinal position
     * @param current the JSONArray to be evaluated
     * @param indexToken the array index in string form
     * @return the matched object. If no matching item is found a
     * @throws JSONPointerException is thrown if the index is out of bounds
     */
    private static Object readByIndexToken(Object current, String indexToken) throws JSONPointerException {
        try {
            int index = Integer.parseInt(indexToken);
            JSONArray currentArr = (JSONArray) current;
            if (index >= currentArr.length()) {
                throw new JSONPointerException(String.format("index %s is out of bounds - the array has %d elements", indexToken,
                        Integer.valueOf(currentArr.length())));
            }
            try {
				return currentArr.get(index);
			} catch (JSONException e) {
				throw new JSONPointerException("Error reading value at index position " + index, e);
			}
        } catch (NumberFormatException e) {
            throw new JSONPointerException(String.format("%s is not an array index", indexToken, null), e);
        }
    }

    /**
     * Returns a string representing the JSONPointer path value using string
     * representation
     */
    @Override
    public String toString() {
        StringBuilder rval = new StringBuilder("");
        for (String token: this.refTokens) {
            rval.append('/').append(escape(token));
        }
        return rval.toString();
    }

    /**
     * Escapes path segment values to an unambiguous form.
     * The escape char to be inserted is '~'. The chars to be escaped 
     * are ~, which maps to ~0, and /, which maps to ~1.
     * @param token the JSONPointer segment value to be escaped
     * @return the escaped value for the token
     * 
     * @see <a href="https://tools.ietf.org/html/rfc6901#section-3">rfc6901 section 3</a>
     */
    private static String escape(String token) {
        return token.replace("~", "~0")
                .replace("/", "~1");
    }

    /**
     * Returns a string representing the JSONPointer path value using URI
     * fragment identifier representation
     * @return a uri fragment string
     */
    public String toURIFragment() {
        try {
            StringBuilder rval = new StringBuilder("#");
            for (String token : this.refTokens) {
                rval.append('/').append(URLEncoder.encode(token, ENCODING));
            }
            return rval.toString();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    
}

/**
 * The JSONPointerException is thrown by {@link JSONPointer} if an error occurs
 * during evaluating a pointer.
 * 
 * @author JSON.org
 * @version 2016-05-13
 */
 class JSONPointerException extends JSONException {
    private static final long serialVersionUID = 8872944667561856751L;

    public JSONPointerException(String message) {
        super(message);
    }

    public JSONPointerException(String message, Throwable cause) {
        super(message, cause);
    }

}

/**
 * The <code>JSONString</code> interface allows a <code>toJSONString()</code>
 * method so that a class can change the behavior of
 * <code>JSONObject.toString()</code>, <code>JSONArray.toString()</code>,
 * and <code>JSONWriter.value(</code>Object<code>)</code>. The
 * <code>toJSONString</code> method will be used instead of the default behavior
 * of using the Object's <code>toString()</code> method and quoting the result.
 */
 interface JSONString {
    /**
     * The <code>toJSONString</code> method allows a class to produce its own JSON
     * serialization.
     *
     * @return A strictly syntactically correct JSON text.
     */
    public String toJSONString();
}

/**
 * JSONWriter provides a quick and convenient way of producing JSON text.
 * The texts produced strictly conform to JSON syntax rules. No whitespace is
 * added, so the results are ready for transmission or storage. Each instance of
 * JSONWriter can produce one JSON text.
 * <p>
 * A JSONWriter instance provides a <code>value</code> method for appending
 * values to the
 * text, and a <code>key</code>
 * method for adding keys before values in objects. There are <code>array</code>
 * and <code>endArray</code> methods that make and bound array values, and
 * <code>object</code> and <code>endObject</code> methods which make and bound
 * object values. All of these methods return the JSONWriter instance,
 * permitting a cascade style. For example, <pre>
 * new JSONWriter(myWriter)
 *     .object()
 *         .key("JSON")
 *         .value("Hello, World!")
 *     .endObject();</pre> which writes <pre>
 * {"JSON":"Hello, World!"}</pre>
 * <p>
 * The first method called must be <code>array</code> or <code>object</code>.
 * There are no methods for adding commas or colons. JSONWriter adds them for
 * you. Objects and arrays can be nested up to 200 levels deep.
 * <p>
 * This can sometimes be easier than using a JSONObject to build a string.
 * @author JSON.org
 * @version 2016-08-08
 */
 class JSONWriter {
    private static final int maxdepth = 200;

    /**
     * The comma flag determines if a comma should be output before the next
     * value.
     */
    private boolean comma;

    /**
     * The current mode. Values:
     * 'a' (array),
     * 'd' (done),
     * 'i' (initial),
     * 'k' (key),
     * 'o' (object).
     */
    protected char mode;

    /**
     * The object/array stack.
     */
    private final JSONObject stack[];

    /**
     * The stack top index. A value of 0 indicates that the stack is empty.
     */
    private int top;

    /**
     * The writer that will receive the output.
     */
    protected Appendable writer;

    /**
     * Make a fresh JSONWriter. It can be used to build one JSON text.
     * @param w an appendable object
     */
    public JSONWriter(Appendable w) {
        this.comma = false;
        this.mode = 'i';
        this.stack = new JSONObject[maxdepth];
        this.top = 0;
        this.writer = w;
    }

    /**
     * Append a value.
     * @param string A string value.
     * @return this
     * @throws JSONException If the value is out of sequence.
     */
    private JSONWriter append(String string) throws JSONException {
        if (string == null) {
            throw new JSONException("Null pointer");
        }
        if (this.mode == 'o' || this.mode == 'a') {
            try {
                if (this.comma && this.mode == 'a') {
                    this.writer.append(',');
                }
                this.writer.append(string);
            } catch (IOException e) {
            	// Android as of API 25 does not support this exception constructor
            	// however we won't worry about it. If an exception is happening here
            	// it will just throw a "Method not found" exception instead.
                throw new JSONException(e);
            }
            if (this.mode == 'o') {
                this.mode = 'k';
            }
            this.comma = true;
            return this;
        }
        throw new JSONException("Value out of sequence.");
    }

    /**
     * Begin appending a new array. All values until the balancing
     * <code>endArray</code> will be appended to this array. The
     * <code>endArray</code> method must be called to mark the array's end.
     * @return this
     * @throws JSONException If the nesting is too deep, or if the object is
     * started in the wrong place (for example as a key or after the end of the
     * outermost array or object).
     */
    public JSONWriter array() throws JSONException {
        if (this.mode == 'i' || this.mode == 'o' || this.mode == 'a') {
            this.push(null);
            this.append("[");
            this.comma = false;
            return this;
        }
        throw new JSONException("Misplaced array.");
    }

    /**
     * End something.
     * @param m Mode
     * @param c Closing character
     * @return this
     * @throws JSONException If unbalanced.
     */
    private JSONWriter end(char m, char c) throws JSONException {
        if (this.mode != m) {
            throw new JSONException(m == 'a'
                ? "Misplaced endArray."
                : "Misplaced endObject.");
        }
        this.pop(m);
        try {
            this.writer.append(c);
        } catch (IOException e) {
        	// Android as of API 25 does not support this exception constructor
        	// however we won't worry about it. If an exception is happening here
        	// it will just throw a "Method not found" exception instead.
            throw new JSONException(e);
        }
        this.comma = true;
        return this;
    }

    /**
     * End an array. This method most be called to balance calls to
     * <code>array</code>.
     * @return this
     * @throws JSONException If incorrectly nested.
     */
    public JSONWriter endArray() throws JSONException {
        return this.end('a', ']');
    }

    /**
     * End an object. This method most be called to balance calls to
     * <code>object</code>.
     * @return this
     * @throws JSONException If incorrectly nested.
     */
    public JSONWriter endObject() throws JSONException {
        return this.end('k', '}');
    }

    /**
     * Append a key. The key will be associated with the next value. In an
     * object, every value must be preceded by a key.
     * @param string A key string.
     * @return this
     * @throws JSONException If the key is out of place. For example, keys
     *  do not belong in arrays or if the key is null.
     */
    public JSONWriter key(String string) throws JSONException {
        if (string == null) {
            throw new JSONException("Null key.");
        }
        if (this.mode == 'k') {
            try {
                JSONObject topObject = this.stack[this.top - 1];
                // don't use the built in putOnce method to maintain Android support
				if(topObject.has(string)) {
					throw new JSONException("Duplicate key \"" + string + "\"");
				}
                topObject.put(string, true);
                if (this.comma) {
                    this.writer.append(',');
                }
                this.writer.append(JSONObject.quote(string));
                this.writer.append(':');
                this.comma = false;
                this.mode = 'o';
                return this;
            } catch (IOException e) {
            	// Android as of API 25 does not support this exception constructor
            	// however we won't worry about it. If an exception is happening here
            	// it will just throw a "Method not found" exception instead.
                throw new JSONException(e);
            }
        }
        throw new JSONException("Misplaced key.");
    }


    /**
     * Begin appending a new object. All keys and values until the balancing
     * <code>endObject</code> will be appended to this object. The
     * <code>endObject</code> method must be called to mark the object's end.
     * @return this
     * @throws JSONException If the nesting is too deep, or if the object is
     * started in the wrong place (for example as a key or after the end of the
     * outermost array or object).
     */
    public JSONWriter object() throws JSONException {
        if (this.mode == 'i') {
            this.mode = 'o';
        }
        if (this.mode == 'o' || this.mode == 'a') {
            this.append("{");
            this.push(new JSONObject());
            this.comma = false;
            return this;
        }
        throw new JSONException("Misplaced object.");

    }


    /**
     * Pop an array or object scope.
     * @param c The scope to close.
     * @throws JSONException If nesting is wrong.
     */
    private void pop(char c) throws JSONException {
        if (this.top <= 0) {
            throw new JSONException("Nesting error.");
        }
        char m = this.stack[this.top - 1] == null ? 'a' : 'k';
        if (m != c) {
            throw new JSONException("Nesting error.");
        }
        this.top -= 1;
        this.mode = this.top == 0
            ? 'd'
            : this.stack[this.top - 1] == null
            ? 'a'
            : 'k';
    }

    /**
     * Push an array or object scope.
     * @param jo The scope to open.
     * @throws JSONException If nesting is too deep.
     */
    private void push(JSONObject jo) throws JSONException {
        if (this.top >= maxdepth) {
            throw new JSONException("Nesting too deep.");
        }
        this.stack[this.top] = jo;
        this.mode = jo == null ? 'a' : 'k';
        this.top += 1;
    }

    /**
     * Make a JSON text of an Object value. If the object has an
     * value.toJSONString() method, then that method will be used to produce the
     * JSON text. The method is required to produce a strictly conforming text.
     * If the object does not contain a toJSONString method (which is the most
     * common case), then a text will be produced by other means. If the value
     * is an array or Collection, then a JSONArray will be made from it and its
     * toJSONString method will be called. If the value is a MAP, then a
     * JSONObject will be made from it and its toJSONString method will be
     * called. Otherwise, the value's toString method will be called, and the
     * result will be quoted.
     *
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param value
     *            The value to be serialized.
     * @return a printable, displayable, transmittable representation of the
     *         object, beginning with <code>{</code>&nbsp;<small>(left
     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     *         brace)</small>.
     * @throws JSONException
     *             If the value is or contains an invalid number.
     */
    public static String valueToString(Object value) throws JSONException {
        if (value == null || value.equals(null)) {
            return "null";
        }
        if (value instanceof JSONString) {
            String object;
            try {
                object = ((JSONString) value).toJSONString();
            } catch (Exception e) {
                throw new JSONException(e);
            }
            if (object != null) {
                return object;
            }
            throw new JSONException("Bad value from toJSONString: " + object);
        }
        if (value instanceof Number) {
            // not all Numbers may match actual JSON Numbers. i.e. Fractions or Complex
            final String numberAsString = JSONObject.numberToString((Number) value);
            if(JSONObject.NUMBER_PATTERN.matcher(numberAsString).matches()) {
                // Close enough to a JSON number that we will return it unquoted
                return numberAsString;
            }
            // The Number value is not a valid JSON number.
            // Instead we will quote it as a string
            return JSONObject.quote(numberAsString);
        }
        if (value instanceof Boolean || value instanceof JSONObject
                || value instanceof JSONArray) {
            return value.toString();
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            return new JSONObject(map).toString();
        }
        if (value instanceof Collection) {
            Collection<?> coll = (Collection<?>) value;
            return new JSONArray(coll).toString();
        }
        if (value.getClass().isArray()) {
            return new JSONArray(value).toString();
        }
        if(value instanceof Enum<?>){
            return JSONObject.quote(((Enum<?>)value).name());
        }
        return JSONObject.quote(value.toString());
    }

    /**
     * Append either the value <code>true</code> or the value
     * <code>false</code>.
     * @param b A boolean.
     * @return this
     * @throws JSONException if a called function has an error
     */
    public JSONWriter value(boolean b) throws JSONException {
        return this.append(b ? "true" : "false");
    }

    /**
     * Append a double value.
     * @param d A double.
     * @return this
     * @throws JSONException If the number is not finite.
     */
    public JSONWriter value(double d) throws JSONException {
        return this.value(Double.valueOf(d));
    }

    /**
     * Append a long value.
     * @param l A long.
     * @return this
     * @throws JSONException if a called function has an error
     */
    public JSONWriter value(long l) throws JSONException {
        return this.append(Long.toString(l));
    }


    /**
     * Append an object value.
     * @param object The object to append. It can be null, or a Boolean, Number,
     *   String, JSONObject, or JSONArray, or an object that implements JSONString.
     * @return this
     * @throws JSONException If the value is out of sequence.
     */
    public JSONWriter value(Object object) throws JSONException {
        return this.append(valueToString(object));
    }
}
