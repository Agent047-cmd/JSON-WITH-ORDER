package org.testjson.connected;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

class JSONML {
   private static Object parse(XMLTokener x, boolean arrayForm, JSONArray ja, boolean keepStrings) throws JSONException {
      String closeTag = null;
      JSONArray newja = null;
      JSONObject newjo = null;
      String tagName = null;

      while(true) {
         while(x.more()) {
            Object token = x.nextContent();
            if (token == XML.LT) {
               token = x.nextToken();
               if (token instanceof Character) {
                  if (token == XML.SLASH) {
                     token = x.nextToken();
                     if (!(token instanceof String)) {
                        throw new JSONException("Expected a closing name instead of '" + token + "'.");
                     }

                     if (x.nextToken() != XML.GT) {
                        throw x.syntaxError("Misshaped close tag");
                     }

                     return token;
                  }

                  if (token != XML.BANG) {
                     if (token != XML.QUEST) {
                        throw x.syntaxError("Misshaped tag");
                     }

                     x.skipPast("?>");
                  } else {
                     char c = x.next();
                     if (c == '-') {
                        if (x.next() == '-') {
                           x.skipPast("-->");
                        } else {
                           x.back();
                        }
                     } else if (c == '[') {
                        token = x.nextToken();
                        if (!token.equals("CDATA") || x.next() != '[') {
                           throw x.syntaxError("Expected 'CDATA['");
                        }

                        if (ja != null) {
                           ja.put((Object)x.nextCDATA());
                        }
                     } else {
                        int i = 1;

                        while(true) {
                           token = x.nextMeta();
                           if (token == null) {
                              throw x.syntaxError("Missing '>' after '<!'.");
                           }

                           if (token == XML.LT) {
                              ++i;
                           } else if (token == XML.GT) {
                              --i;
                           }

                           if (i <= 0) {
                              break;
                           }
                        }
                     }
                  }
               } else {
                  if (!(token instanceof String)) {
                     throw x.syntaxError("Bad tagName '" + token + "'.");
                  }

                  tagName = (String)token;
                  newja = new JSONArray();
                  newjo = new JSONObject();
                  if (arrayForm) {
                     newja.put((Object)tagName);
                     if (ja != null) {
                        ja.put((Object)newja);
                     }
                  } else {
                     newjo.put("tagName", (Object)tagName);
                     if (ja != null) {
                        ja.put((Object)newjo);
                     }
                  }

                  token = null;

                  while(true) {
                     if (token == null) {
                        token = x.nextToken();
                     }

                     if (token == null) {
                        throw x.syntaxError("Misshaped tag");
                     }

                     if (!(token instanceof String)) {
                        if (arrayForm && newjo.length() > 0) {
                           newja.put((Object)newjo);
                        }

                        if (token == XML.SLASH) {
                           if (x.nextToken() != XML.GT) {
                              throw x.syntaxError("Misshaped tag");
                           }

                           if (ja == null) {
                              if (arrayForm) {
                                 return newja;
                              }

                              return newjo;
                           }
                        } else {
                           if (token != XML.GT) {
                              throw x.syntaxError("Misshaped tag");
                           }

                           closeTag = (String)parse(x, arrayForm, newja, keepStrings);
                           if (closeTag != null) {
                              if (!closeTag.equals(tagName)) {
                                 throw x.syntaxError("Mismatched '" + tagName + "' and '" + closeTag + "'");
                              }

                              tagName = null;
                              if (!arrayForm && newja.length() > 0) {
                                 newjo.put("childNodes", (Object)newja);
                              }

                              if (ja == null) {
                                 if (arrayForm) {
                                    return newja;
                                 }

                                 return newjo;
                              }
                           }
                        }
                        break;
                     }

                     String attribute = (String)token;
                     if (!arrayForm && ("tagName".equals(attribute) || "childNode".equals(attribute))) {
                        throw x.syntaxError("Reserved attribute.");
                     }

                     token = x.nextToken();
                     if (token == XML.EQ) {
                        token = x.nextToken();
                        if (!(token instanceof String)) {
                           throw x.syntaxError("Missing value");
                        }

                        newjo.accumulate(attribute, keepStrings ? (String)token : XML.stringToValue((String)token));
                        token = null;
                     } else {
                        newjo.accumulate(attribute, "");
                     }
                  }
               }
            } else if (ja != null) {
               ja.put(token instanceof String ? (keepStrings ? XML.unescape((String)token) : XML.stringToValue((String)token)) : token);
            }
         }

         throw x.syntaxError("Bad XML");
      }
   }

   public static JSONArray toJSONArray(String string) throws JSONException {
      return (JSONArray)parse(new XMLTokener(string), true, (JSONArray)null, false);
   }

   public static JSONArray toJSONArray(String string, boolean keepStrings) throws JSONException {
      return (JSONArray)parse(new XMLTokener(string), true, (JSONArray)null, keepStrings);
   }

   public static JSONArray toJSONArray(XMLTokener x, boolean keepStrings) throws JSONException {
      return (JSONArray)parse(x, true, (JSONArray)null, keepStrings);
   }

   public static JSONArray toJSONArray(XMLTokener x) throws JSONException {
      return (JSONArray)parse(x, true, (JSONArray)null, false);
   }

   public static JSONObject toJSONObject(String string) throws JSONException {
      return (JSONObject)parse(new XMLTokener(string), false, (JSONArray)null, false);
   }

   public static JSONObject toJSONObject(String string, boolean keepStrings) throws JSONException {
      return (JSONObject)parse(new XMLTokener(string), false, (JSONArray)null, keepStrings);
   }

   public static JSONObject toJSONObject(XMLTokener x) throws JSONException {
      return (JSONObject)parse(x, false, (JSONArray)null, false);
   }

   public static JSONObject toJSONObject(XMLTokener x, boolean keepStrings) throws JSONException {
      return (JSONObject)parse(x, false, (JSONArray)null, keepStrings);
   }

   public static String toString(JSONArray ja) throws JSONException {
      StringBuilder sb = new StringBuilder();
      String tagName = ja.getString(0);
      XML.noSpace(tagName);
      tagName = XML.escape(tagName);
      sb.append('<');
      sb.append(tagName);
      Object object = ja.opt(1);
      int i;
      if (object instanceof JSONObject) {
         i = 2;
         JSONObject jo = (JSONObject)object;
         Iterator var7 = jo.keySet().iterator();

         while(var7.hasNext()) {
            String key = (String)var7.next();
            Object value = jo.opt(key);
            XML.noSpace(key);
            if (value != null) {
               sb.append(' ');
               sb.append(XML.escape(key));
               sb.append('=');
               sb.append('"');
               sb.append(XML.escape(value.toString()));
               sb.append('"');
            }
         }
      } else {
         i = 1;
      }

      int length = ja.length();
      if (i >= length) {
         sb.append('/');
         sb.append('>');
      } else {
         sb.append('>');

         do {
            object = ja.get(i);
            ++i;
            if (object != null) {
               if (object instanceof String) {
                  sb.append(XML.escape(object.toString()));
               } else if (object instanceof JSONObject) {
                  sb.append(toString((JSONObject)object));
               } else if (object instanceof JSONArray) {
                  sb.append(toString((JSONArray)object));
               } else {
                  sb.append(object.toString());
               }
            }
         } while(i < length);

         sb.append('<');
         sb.append('/');
         sb.append(tagName);
         sb.append('>');
      }

      return sb.toString();
   }

   public static String toString(JSONObject jo) throws JSONException {
      StringBuilder sb = new StringBuilder();
      String tagName = jo.optString("tagName");
      if (tagName == null) {
         return XML.escape(jo.toString());
      } else {
         XML.noSpace(tagName);
         tagName = XML.escape(tagName);
         sb.append('<');
         sb.append(tagName);
         Iterator var8 = jo.keySet().iterator();

         while(var8.hasNext()) {
            String key = (String)var8.next();
            if (!"tagName".equals(key) && !"childNodes".equals(key)) {
               XML.noSpace(key);
               Object value = jo.opt(key);
               if (value != null) {
                  sb.append(' ');
                  sb.append(XML.escape(key));
                  sb.append('=');
                  sb.append('"');
                  sb.append(XML.escape(value.toString()));
                  sb.append('"');
               }
            }
         }

         JSONArray ja = jo.optJSONArray("childNodes");
         if (ja == null) {
            sb.append('/');
            sb.append('>');
         } else {
            sb.append('>');
            int length = ja.length();

            for(int i = 0; i < length; ++i) {
               Object object = ja.get(i);
               if (object != null) {
                  if (object instanceof String) {
                     sb.append(XML.escape(object.toString()));
                  } else if (object instanceof JSONObject) {
                     sb.append(toString((JSONObject)object));
                  } else if (object instanceof JSONArray) {
                     sb.append(toString((JSONArray)object));
                  } else {
                     sb.append(object.toString());
                  }
               }
            }

            sb.append('<');
            sb.append('/');
            sb.append(tagName);
            sb.append('>');
         }

         return sb.toString();
      }
   }
}
class JSONPointer {
   private static final String ENCODING = "utf-8";
   private final List<String> refTokens;

   public static JSONPointer.Builder builder() {
      return new JSONPointer.Builder();
   }

   public JSONPointer(String pointer) {
      if (pointer == null) {
         throw new NullPointerException("pointer cannot be null");
      } else if (!pointer.isEmpty() && !pointer.equals("#")) {
         String refs;
         if (pointer.startsWith("#/")) {
            refs = pointer.substring(2);

            try {
               refs = URLDecoder.decode(refs, "utf-8");
            } catch (UnsupportedEncodingException var6) {
               throw new RuntimeException(var6);
            }
         } else {
            if (!pointer.startsWith("/")) {
               throw new IllegalArgumentException("a JSON pointer should start with '/' or '#/'");
            }

            refs = pointer.substring(1);
         }

         this.refTokens = new ArrayList();
         int slashIdx = -1;
         boolean var4 = false;

         do {
            int prevSlashIdx = slashIdx + 1;
            slashIdx = refs.indexOf(47, prevSlashIdx);
            if (prevSlashIdx != slashIdx && prevSlashIdx != refs.length()) {
               String token;
               if (slashIdx >= 0) {
                  token = refs.substring(prevSlashIdx, slashIdx);
                  this.refTokens.add(unescape(token));
               } else {
                  token = refs.substring(prevSlashIdx);
                  this.refTokens.add(unescape(token));
               }
            } else {
               this.refTokens.add("");
            }
         } while(slashIdx >= 0);

      } else {
         this.refTokens = Collections.emptyList();
      }
   }

   public JSONPointer(List<String> refTokens) {
      this.refTokens = new ArrayList(refTokens);
   }

   private static String unescape(String token) {
      return token.replace("~1", "/").replace("~0", "~");
   }

   public Object queryFrom(Object document) throws JSONPointerException {
      if (this.refTokens.isEmpty()) {
         return document;
      } else {
         Object current = document;
         Iterator var3 = this.refTokens.iterator();

         while(var3.hasNext()) {
            String token = (String)var3.next();
            if (current instanceof JSONObject) {
               current = ((JSONObject)current).opt(unescape(token));
            } else {
               if (!(current instanceof JSONArray)) {
                  throw new JSONPointerException(String.format("value [%s] is not an array or object therefore its key %s cannot be resolved", current, token));
               }

               current = readByIndexToken(current, token);
            }
         }

         return current;
      }
   }

   private static Object readByIndexToken(Object current, String indexToken) throws JSONPointerException {
      try {
         int index = Integer.parseInt(indexToken);
         JSONArray currentArr = (JSONArray)current;
         if (index >= currentArr.length()) {
            throw new JSONPointerException(String.format("index %s is out of bounds - the array has %d elements", indexToken, currentArr.length()));
         } else {
            try {
               return currentArr.get(index);
            } catch (JSONException var5) {
               throw new JSONPointerException("Error reading value at index position " + index, var5);
            }
         }
      } catch (NumberFormatException var6) {
         throw new JSONPointerException(String.format("%s is not an array index", indexToken), var6);
      }
   }

   public String toString() {
      StringBuilder rval = new StringBuilder("");
      Iterator var2 = this.refTokens.iterator();

      while(var2.hasNext()) {
         String token = (String)var2.next();
         rval.append('/').append(escape(token));
      }

      return rval.toString();
   }

   private static String escape(String token) {
      return token.replace("~", "~0").replace("/", "~1");
   }

   public String toURIFragment() {
      try {
         StringBuilder rval = new StringBuilder("#");
         Iterator var2 = this.refTokens.iterator();

         while(var2.hasNext()) {
            String token = (String)var2.next();
            rval.append('/').append(URLEncoder.encode(token, "utf-8"));
         }

         return rval.toString();
      } catch (UnsupportedEncodingException var4) {
         throw new RuntimeException(var4);
      }
   }

   public static class Builder {
      private final List<String> refTokens = new ArrayList();

      public JSONPointer build() {
         return new JSONPointer(this.refTokens);
      }

      public JSONPointer.Builder append(String token) {
         if (token == null) {
            throw new NullPointerException("token cannot be null");
         } else {
            this.refTokens.add(token);
            return this;
         }
      }

      public JSONPointer.Builder append(int arrayIndex) {
         this.refTokens.add(String.valueOf(arrayIndex));
         return this;
      }
   }
}
class JSONPointerException extends JSONException {
   private static final long serialVersionUID = 8872944667561856751L;

   public JSONPointerException(String message) {
      super(message);
   }

   public JSONPointerException(String message, Throwable cause) {
      super(message, cause);
   }
}
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
 @interface JSONPropertyIgnore {
}
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
 @interface JSONPropertyName {
   String value();
}
 interface JSONString {
   String toJSONString();
}
class JSONStringer extends JSONWriter {
   public JSONStringer() {
      super(new StringWriter());
   }

   public String toString() {
      return this.mode == 'd' ? this.writer.toString() : null;
   }
}
class JSONWriter {
   private static final int maxdepth = 200;
   private boolean comma = false;
   protected char mode = 'i';
   private final JSONObject[] stack = new JSONObject[200];
   private int top = 0;
   protected Appendable writer;

   public JSONWriter(Appendable w) {
      this.writer = w;
   }

   private JSONWriter append(String string) throws JSONException {
      if (string == null) {
         throw new JSONException("Null pointer");
      } else if (this.mode != 'o' && this.mode != 'a') {
         throw new JSONException("Value out of sequence.");
      } else {
         try {
            if (this.comma && this.mode == 'a') {
               this.writer.append(',');
            }

            this.writer.append(string);
         } catch (IOException var3) {
            throw new JSONException(var3);
         }

         if (this.mode == 'o') {
            this.mode = 'k';
         }

         this.comma = true;
         return this;
      }
   }

   public JSONWriter array() throws JSONException {
      if (this.mode != 'i' && this.mode != 'o' && this.mode != 'a') {
         throw new JSONException("Misplaced array.");
      } else {
         this.push((JSONObject)null);
         this.append("[");
         this.comma = false;
         return this;
      }
   }

   private JSONWriter end(char m, char c) throws JSONException {
      if (this.mode != m) {
         throw new JSONException(m == 'a' ? "Misplaced endArray." : "Misplaced endObject.");
      } else {
         this.pop(m);

         try {
            this.writer.append(c);
         } catch (IOException var4) {
            throw new JSONException(var4);
         }

         this.comma = true;
         return this;
      }
   }

   public JSONWriter endArray() throws JSONException {
      return this.end('a', ']');
   }

   public JSONWriter endObject() throws JSONException {
      return this.end('k', '}');
   }

   public JSONWriter key(String string) throws JSONException {
      if (string == null) {
         throw new JSONException("Null key.");
      } else if (this.mode == 'k') {
         try {
            JSONObject topObject = this.stack[this.top - 1];
            if (topObject.has(string)) {
               throw new JSONException("Duplicate key \"" + string + "\"");
            } else {
               topObject.put(string, true);
               if (this.comma) {
                  this.writer.append(',');
               }

               this.writer.append(JSONObject.quote(string));
               this.writer.append(':');
               this.comma = false;
               this.mode = 'o';
               return this;
            }
         } catch (IOException var3) {
            throw new JSONException(var3);
         }
      } else {
         throw new JSONException("Misplaced key.");
      }
   }

   public JSONWriter object() throws JSONException {
      if (this.mode == 'i') {
         this.mode = 'o';
      }

      if (this.mode != 'o' && this.mode != 'a') {
         throw new JSONException("Misplaced object.");
      } else {
         this.append("{");
         this.push(new JSONObject());
         this.comma = false;
         return this;
      }
   }

   private void pop(char c) throws JSONException {
      if (this.top <= 0) {
         throw new JSONException("Nesting error.");
      } else {
         char m = (char) (this.stack[this.top - 1] == null ? 97 : 107);
         if (m != c) {
            throw new JSONException("Nesting error.");
         } else {
            --this.top;
            this.mode = (char)(this.top == 0 ? 100 : (this.stack[this.top - 1] == null ? 97 : 107));
         }
      }
   }

   private void push(JSONObject jo) throws JSONException {
      if (this.top >= 200) {
         throw new JSONException("Nesting too deep.");
      } else {
         this.stack[this.top] = jo;
         this.mode = (char)(jo == null ? 97 : 107);
         ++this.top;
      }
   }

   public static String valueToString(Object value) throws JSONException {
      if (value != null && !value.equals((Object)null)) {
         String object;
         if (value instanceof JSONString) {
            try {
               object = ((JSONString)value).toJSONString();
            } catch (Exception var3) {
               throw new JSONException(var3);
            }

            if (object != null) {
               return object;
            } else {
               throw new JSONException("Bad value from toJSONString: " + object);
            }
         } else if (value instanceof Number) {
            object = JSONObject.numberToString((Number)value);
            return JSONObject.NUMBER_PATTERN.matcher(object).matches() ? object : JSONObject.quote(object);
         } else if (!(value instanceof Boolean) && !(value instanceof JSONObject) && !(value instanceof JSONArray)) {
            if (value instanceof Map) {
               Map<?, ?> map = (Map)value;
               return (new JSONObject(map)).toString();
            } else if (value instanceof Collection) {
               Collection<?> coll = (Collection)value;
               return (new JSONArray(coll)).toString();
            } else if (value.getClass().isArray()) {
               return (new JSONArray(value)).toString();
            } else {
               return value instanceof Enum ? JSONObject.quote(((Enum)value).name()) : JSONObject.quote(value.toString());
            }
         } else {
            return value.toString();
         }
      } else {
         return "null";
      }
   }

   public JSONWriter value(boolean b) throws JSONException {
      return this.append(b ? "true" : "false");
   }

   public JSONWriter value(double d) throws JSONException {
      return this.value(d);
   }

   public JSONWriter value(long l) throws JSONException {
      return this.append(Long.toString(l));
   }

   public JSONWriter value(Object object) throws JSONException {
      return this.append(valueToString(object));
   }
}
class Property {
   public static JSONObject toJSONObject(Properties properties) throws JSONException {
      JSONObject jo = new JSONObject();
      if (properties != null && !properties.isEmpty()) {
         Enumeration enumProperties = properties.propertyNames();

         while(enumProperties.hasMoreElements()) {
            String name = (String)enumProperties.nextElement();
            jo.put(name, (Object)properties.getProperty(name));
         }
      }

      return jo;
   }

   public static Properties toProperties(JSONObject jo) throws JSONException {
      Properties properties = new Properties();
      if (jo != null) {
         Iterator var2 = jo.keySet().iterator();

         while(var2.hasNext()) {
            String key = (String)var2.next();
            Object value = jo.opt(key);
            if (!JSONObject.NULL.equals(value)) {
               properties.put(key, value.toString());
            }
         }
      }

      return properties;
   }
}
class XML {
   public static final Character AMP = '&';
   public static final Character APOS = '\'';
   public static final Character BANG = '!';
   public static final Character EQ = '=';
   public static final Character GT = '>';
   public static final Character LT = '<';
   public static final Character QUEST = '?';
   public static final Character QUOT = '"';
   public static final Character SLASH = '/';
   public static final String NULL_ATTR = "xsi:nil";
   public static final String TYPE_ATTR = "xsi:type";

   private static Iterable<Integer> codePointIterator(final String string) {
      return new Iterable<Integer>() {
         public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {
               private int nextIndex = 0;
               private int length = string.length();

               public boolean hasNext() {
                  return this.nextIndex < this.length;
               }

               public Integer next() {
                  int result = string.codePointAt(this.nextIndex);
                  this.nextIndex += Character.charCount(result);
                  return result;
               }

               public void remove() {
                  throw new UnsupportedOperationException();
               }
            };
         }
      };
   }

   public static String escape(String string) {
      StringBuilder sb = new StringBuilder(string.length());
      Iterator var2 = codePointIterator(string).iterator();

      while(var2.hasNext()) {
         int cp = (Integer)var2.next();
         switch(cp) {
            case 34:
               sb.append("&quot;");
               break;
            case 38:
               sb.append("&amp;");
               break;
            case 39:
               sb.append("&apos;");
               break;
            case 60:
               sb.append("&lt;");
               break;
            case 62:
               sb.append("&gt;");
               break;
            default:
               if (mustEscape(cp)) {
                  sb.append("&#x");
                  sb.append(Integer.toHexString(cp));
                  sb.append(';');
               } else {
                  sb.appendCodePoint(cp);
               }
         }
      }

      return sb.toString();
   }

   private static boolean mustEscape(int cp) {
      return Character.isISOControl(cp) && cp != 9 && cp != 10 && cp != 13 || (cp < 32 || cp > 55295) && (cp < 57344 || cp > 65533) && (cp < 65536 || cp > 1114111);
   }

   public static String unescape(String string) {
      StringBuilder sb = new StringBuilder(string.length());
      int i = 0;

      for(int length = string.length(); i < length; ++i) {
         char c = string.charAt(i);
         if (c == '&') {
            int semic = string.indexOf(59, i);
            if (semic > i) {
               String entity = string.substring(i + 1, semic);
               sb.append(XMLTokener.unescapeEntity(entity));
               i += entity.length() + 1;
            } else {
               sb.append(c);
            }
         } else {
            sb.append(c);
         }
      }

      return sb.toString();
   }

   public static void noSpace(String string) throws JSONException {
      int length = string.length();
      if (length == 0) {
         throw new JSONException("Empty string.");
      } else {
         for(int i = 0; i < length; ++i) {
            if (Character.isWhitespace(string.charAt(i))) {
               throw new JSONException("'" + string + "' contains a space character.");
            }
         }

      }
   }

   private static boolean parse(XMLTokener x, JSONObject context, String name, XMLParserConfiguration config) throws JSONException {
      JSONObject jsonObject = null;
      Object token = x.nextToken();
      String string;
      if (token == BANG) {
         char c = x.next();
         if (c == '-') {
            if (x.next() == '-') {
               x.skipPast("-->");
               return false;
            }

            x.back();
         } else if (c == '[') {
            token = x.nextToken();
            if ("CDATA".equals(token) && x.next() == '[') {
               string = x.nextCDATA();
               if (string.length() > 0) {
                  context.accumulate(config.getcDataTagName(), string);
               }

               return false;
            }

            throw x.syntaxError("Expected 'CDATA['");
         }

         int i = 1;

         do {
            token = x.nextMeta();
            if (token == null) {
               throw x.syntaxError("Missing '>' after '<!'.");
            }

            if (token == LT) {
               ++i;
            } else if (token == GT) {
               --i;
            }
         } while(i > 0);

         return false;
      } else if (token == QUEST) {
         x.skipPast("?>");
         return false;
      } else if (token == SLASH) {
         token = x.nextToken();
         if (name == null) {
            throw x.syntaxError("Mismatched close tag " + token);
         } else if (!token.equals(name)) {
            throw x.syntaxError("Mismatched " + name + " and " + token);
         } else if (x.nextToken() != GT) {
            throw x.syntaxError("Misshaped close tag");
         } else {
            return true;
         }
      } else if (token instanceof Character) {
         throw x.syntaxError("Misshaped tag");
      } else {
         String tagName = (String)token;
         token = null;
         jsonObject = new JSONObject();
         boolean nilAttributeFound = false;
         XMLXsiTypeConverter xmlXsiTypeConverter = null;

         while(true) {
            while(true) {
               if (token == null) {
                  token = x.nextToken();
               }

               if (!(token instanceof String)) {
                  if (token == SLASH) {
                     if (x.nextToken() != GT) {
                        throw x.syntaxError("Misshaped tag");
                     }

                     if (config.getForceList().contains(tagName)) {
                        if (nilAttributeFound) {
                           context.append(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                           context.append(tagName, jsonObject);
                        } else {
                           context.put(tagName, (Object)(new JSONArray()));
                        }
                     } else if (nilAttributeFound) {
                        context.accumulate(tagName, JSONObject.NULL);
                     } else if (jsonObject.length() > 0) {
                        context.accumulate(tagName, jsonObject);
                     } else {
                        context.accumulate(tagName, "");
                     }

                     return false;
                  }

                  if (token != GT) {
                     throw x.syntaxError("Misshaped tag");
                  }

                  while(true) {
                     token = x.nextContent();
                     if (token == null) {
                        if (tagName != null) {
                           throw x.syntaxError("Unclosed tag " + tagName);
                        }

                        return false;
                     }

                     if (token instanceof String) {
                        string = (String)token;
                        if (string.length() > 0) {
                           if (xmlXsiTypeConverter != null) {
                              jsonObject.accumulate(config.getcDataTagName(), stringToValue(string, xmlXsiTypeConverter));
                           } else {
                              jsonObject.accumulate(config.getcDataTagName(), config.isKeepStrings() ? string : stringToValue(string));
                           }
                        }
                     } else if (token == LT && parse(x, jsonObject, tagName, config)) {
                        if (config.getForceList().contains(tagName)) {
                           if (jsonObject.length() == 0) {
                              context.put(tagName, (Object)(new JSONArray()));
                           } else if (jsonObject.length() == 1 && jsonObject.opt(config.getcDataTagName()) != null) {
                              context.append(tagName, jsonObject.opt(config.getcDataTagName()));
                           } else {
                              context.append(tagName, jsonObject);
                           }
                        } else if (jsonObject.length() == 0) {
                           context.accumulate(tagName, "");
                        } else if (jsonObject.length() == 1 && jsonObject.opt(config.getcDataTagName()) != null) {
                           context.accumulate(tagName, jsonObject.opt(config.getcDataTagName()));
                        } else {
                           context.accumulate(tagName, jsonObject);
                        }

                        return false;
                     }
                  }
               }

               string = (String)token;
               token = x.nextToken();
               if (token == EQ) {
                  token = x.nextToken();
                  if (!(token instanceof String)) {
                     throw x.syntaxError("Missing value");
                  }

                  if (config.isConvertNilAttributeToNull() && "xsi:nil".equals(string) && Boolean.parseBoolean((String)token)) {
                     nilAttributeFound = true;
                  } else if (config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty() && "xsi:type".equals(string)) {
                     xmlXsiTypeConverter = (XMLXsiTypeConverter)config.getXsiTypeMap().get(token);
                  } else if (!nilAttributeFound) {
                     jsonObject.accumulate(string, config.isKeepStrings() ? (String)token : stringToValue((String)token));
                  }

                  token = null;
               } else {
                  jsonObject.accumulate(string, "");
               }
            }
         }
      }
   }

   public static Object stringToValue(String string, XMLXsiTypeConverter<?> typeConverter) {
      return typeConverter != null ? typeConverter.convert(string) : stringToValue(string);
   }

   public static Object stringToValue(String string) {
      if ("".equals(string)) {
         return string;
      } else if ("true".equalsIgnoreCase(string)) {
         return Boolean.TRUE;
      } else if ("false".equalsIgnoreCase(string)) {
         return Boolean.FALSE;
      } else if ("null".equalsIgnoreCase(string)) {
         return JSONObject.NULL;
      } else {
         char initial = string.charAt(0);
         if (initial >= '0' && initial <= '9' || initial == '-') {
            try {
               return stringToNumber(string);
            } catch (Exception var3) {
            }
         }

         return string;
      }
   }

   private static Number stringToNumber(String val) throws NumberFormatException {
      char initial = val.charAt(0);
      if ((initial < '0' || initial > '9') && initial != '-') {
         throw new NumberFormatException("val [" + val + "] is not a valid number.");
      } else if (isDecimalNotation(val)) {
         try {
            BigDecimal bd = new BigDecimal(val);
            return (Number)(initial == '-' && BigDecimal.ZERO.compareTo(bd) == 0 ? -0.0D : bd);
         } catch (NumberFormatException var5) {
            try {
               Double d = Double.valueOf(val);
               if (!d.isNaN() && !d.isInfinite()) {
                  return d;
               } else {
                  throw new NumberFormatException("val [" + val + "] is not a valid number.");
               }
            } catch (NumberFormatException var4) {
               throw new NumberFormatException("val [" + val + "] is not a valid number.");
            }
         }
      } else {
         char at1;
         if (initial == '0' && val.length() > 1) {
            at1 = val.charAt(1);
            if (at1 >= '0' && at1 <= '9') {
               throw new NumberFormatException("val [" + val + "] is not a valid number.");
            }
         } else if (initial == '-' && val.length() > 2) {
            at1 = val.charAt(1);
            char at2 = val.charAt(2);
            if (at1 == '0' && at2 >= '0' && at2 <= '9') {
               throw new NumberFormatException("val [" + val + "] is not a valid number.");
            }
         }

         BigInteger bi = new BigInteger(val);
         if (bi.bitLength() <= 31) {
            return bi.intValue();
         } else {
            return (Number)(bi.bitLength() <= 63 ? bi.longValue() : bi);
         }
      }
   }

   private static boolean isDecimalNotation(String val) {
      return val.indexOf(46) > -1 || val.indexOf(101) > -1 || val.indexOf(69) > -1 || "-0".equals(val);
   }

   public static JSONObject toJSONObject(String string) throws JSONException {
      return toJSONObject(string, XMLParserConfiguration.ORIGINAL);
   }

   public static JSONObject toJSONObject(Reader reader) throws JSONException {
      return toJSONObject(reader, XMLParserConfiguration.ORIGINAL);
   }

   public static JSONObject toJSONObject(Reader reader, boolean keepStrings) throws JSONException {
      return keepStrings ? toJSONObject(reader, XMLParserConfiguration.KEEP_STRINGS) : toJSONObject(reader, XMLParserConfiguration.ORIGINAL);
   }

   public static JSONObject toJSONObject(Reader reader, XMLParserConfiguration config) throws JSONException {
      JSONObject jo = new JSONObject();
      XMLTokener x = new XMLTokener(reader);

      while(x.more()) {
         x.skipPast("<");
         if (x.more()) {
            parse(x, jo, (String)null, config);
         }
      }

      return jo;
   }

   public static JSONObject toJSONObject(String string, boolean keepStrings) throws JSONException {
      return toJSONObject((Reader)(new StringReader(string)), keepStrings);
   }

   public static JSONObject toJSONObject(String string, XMLParserConfiguration config) throws JSONException {
      return toJSONObject((Reader)(new StringReader(string)), config);
   }

   public static String toString(Object object) throws JSONException {
      return toString(object, (String)null, XMLParserConfiguration.ORIGINAL);
   }

   public static String toString(Object object, String tagName) {
      return toString(object, tagName, XMLParserConfiguration.ORIGINAL);
   }

   public static String toString(Object object, String tagName, XMLParserConfiguration config) throws JSONException {
      StringBuilder sb = new StringBuilder();
      JSONArray ja;
      Object value;
      if (object instanceof JSONObject) {
         if (tagName != null) {
            sb.append('<');
            sb.append(tagName);
            sb.append('>');
         }

         JSONObject jo = (JSONObject)object;
         Iterator var13 = jo.keySet().iterator();

         while(true) {
            while(true) {
               while(var13.hasNext()) {
                  String key = (String)var13.next();
                  value = jo.opt(key);
                  if (value == null) {
                     value = "";
                  } else if (value.getClass().isArray()) {
                     value = new JSONArray(value);
                  }

                  int jaLength;
                  int i;
                  Object val;
                  if (key.equals(config.getcDataTagName())) {
                     if (value instanceof JSONArray) {
                        ja = (JSONArray)value;
                        jaLength = ja.length();

                        for(i = 0; i < jaLength; ++i) {
                           if (i > 0) {
                              sb.append('\n');
                           }

                           val = ja.opt(i);
                           sb.append(escape(val.toString()));
                        }
                     } else {
                        sb.append(escape(value.toString()));
                     }
                  } else if (value instanceof JSONArray) {
                     ja = (JSONArray)value;
                     jaLength = ja.length();

                     for(i = 0; i < jaLength; ++i) {
                        val = ja.opt(i);
                        if (val instanceof JSONArray) {
                           sb.append('<');
                           sb.append(key);
                           sb.append('>');
                           sb.append(toString(val, (String)null, config));
                           sb.append("</");
                           sb.append(key);
                           sb.append('>');
                        } else {
                           sb.append(toString(val, key, config));
                        }
                     }
                  } else if ("".equals(value)) {
                     sb.append('<');
                     sb.append(key);
                     sb.append("/>");
                  } else {
                     sb.append(toString(value, key, config));
                  }
               }

               if (tagName != null) {
                  sb.append("</");
                  sb.append(tagName);
                  sb.append('>');
               }

               return sb.toString();
            }
         }
      } else if (object != null && (object instanceof JSONArray || object.getClass().isArray())) {
         if (object.getClass().isArray()) {
            ja = new JSONArray(object);
         } else {
            ja = (JSONArray)object;
         }

         int jaLength = ja.length();

         for(int i = 0; i < jaLength; ++i) {
            value = ja.opt(i);
            sb.append(toString(value, tagName == null ? "array" : tagName, config));
         }

         return sb.toString();
      } else {
         String string = object == null ? "null" : escape(object.toString());
         return tagName == null ? "\"" + string + "\"" : (string.length() == 0 ? "<" + tagName + "/>" : "<" + tagName + ">" + string + "</" + tagName + ">");
      }
   }
}
class XMLParserConfiguration {
   public static final XMLParserConfiguration ORIGINAL = new XMLParserConfiguration();
   public static final XMLParserConfiguration KEEP_STRINGS = (new XMLParserConfiguration()).withKeepStrings(true);
   private boolean keepStrings;
   private String cDataTagName;
   private boolean convertNilAttributeToNull;
   private Map<String, XMLXsiTypeConverter<?>> xsiTypeMap;
   private Set<String> forceList;

   public XMLParserConfiguration() {
      this.keepStrings = false;
      this.cDataTagName = "content";
      this.convertNilAttributeToNull = false;
      this.xsiTypeMap = Collections.emptyMap();
      this.forceList = Collections.emptySet();
   }

   /** @deprecated */
   @Deprecated
   public XMLParserConfiguration(boolean keepStrings) {
      this(keepStrings, "content", false);
   }

   /** @deprecated */
   @Deprecated
   public XMLParserConfiguration(String cDataTagName) {
      this(false, cDataTagName, false);
   }

   /** @deprecated */
   @Deprecated
   public XMLParserConfiguration(boolean keepStrings, String cDataTagName) {
      this.keepStrings = keepStrings;
      this.cDataTagName = cDataTagName;
      this.convertNilAttributeToNull = false;
   }

   /** @deprecated */
   @Deprecated
   public XMLParserConfiguration(boolean keepStrings, String cDataTagName, boolean convertNilAttributeToNull) {
      this.keepStrings = keepStrings;
      this.cDataTagName = cDataTagName;
      this.convertNilAttributeToNull = convertNilAttributeToNull;
   }

   private XMLParserConfiguration(boolean keepStrings, String cDataTagName, boolean convertNilAttributeToNull, Map<String, XMLXsiTypeConverter<?>> xsiTypeMap, Set<String> forceList) {
      this.keepStrings = keepStrings;
      this.cDataTagName = cDataTagName;
      this.convertNilAttributeToNull = convertNilAttributeToNull;
      this.xsiTypeMap = Collections.unmodifiableMap(xsiTypeMap);
      this.forceList = Collections.unmodifiableSet(forceList);
   }

   protected XMLParserConfiguration clone() {
      return new XMLParserConfiguration(this.keepStrings, this.cDataTagName, this.convertNilAttributeToNull, this.xsiTypeMap, this.forceList);
   }

   public boolean isKeepStrings() {
      return this.keepStrings;
   }

   public XMLParserConfiguration withKeepStrings(boolean newVal) {
      XMLParserConfiguration newConfig = this.clone();
      newConfig.keepStrings = newVal;
      return newConfig;
   }

   public String getcDataTagName() {
      return this.cDataTagName;
   }

   public XMLParserConfiguration withcDataTagName(String newVal) {
      XMLParserConfiguration newConfig = this.clone();
      newConfig.cDataTagName = newVal;
      return newConfig;
   }

   public boolean isConvertNilAttributeToNull() {
      return this.convertNilAttributeToNull;
   }

   public XMLParserConfiguration withConvertNilAttributeToNull(boolean newVal) {
      XMLParserConfiguration newConfig = this.clone();
      newConfig.convertNilAttributeToNull = newVal;
      return newConfig;
   }

   public Map<String, XMLXsiTypeConverter<?>> getXsiTypeMap() {
      return this.xsiTypeMap;
   }

   public XMLParserConfiguration withXsiTypeMap(Map<String, XMLXsiTypeConverter<?>> xsiTypeMap) {
      XMLParserConfiguration newConfig = this.clone();
      Map<String, XMLXsiTypeConverter<?>> cloneXsiTypeMap = new HashMap(xsiTypeMap);
      newConfig.xsiTypeMap = Collections.unmodifiableMap(cloneXsiTypeMap);
      return newConfig;
   }

   public Set<String> getForceList() {
      return this.forceList;
   }

   public XMLParserConfiguration withForceList(Set<String> forceList) {
      XMLParserConfiguration newConfig = this.clone();
      Set<String> cloneForceList = new HashSet(forceList);
      newConfig.forceList = Collections.unmodifiableSet(cloneForceList);
      return newConfig;
   }
}
class XMLTokener extends JSONTokener {
   public static final HashMap<String, Character> entity = new HashMap(8);

   public XMLTokener(Reader r) {
      super(r);
   }

   public XMLTokener(String s) {
      super(s);
   }

   public String nextCDATA() throws JSONException {
      StringBuilder sb = new StringBuilder();

      int i;
      do {
         if (!this.more()) {
            throw this.syntaxError("Unclosed CDATA");
         }

         char c = this.next();
         sb.append(c);
         i = sb.length() - 3;
      } while(i < 0 || sb.charAt(i) != ']' || sb.charAt(i + 1) != ']' || sb.charAt(i + 2) != '>');

      sb.setLength(i);
      return sb.toString();
   }

   public Object nextContent() throws JSONException {
      char c;
      do {
         c = this.next();
      } while(Character.isWhitespace(c));

      if (c == 0) {
         return null;
      } else if (c == '<') {
         return XML.LT;
      } else {
         StringBuilder sb;
         for(sb = new StringBuilder(); c != 0; c = this.next()) {
            if (c == '<') {
               this.back();
               return sb.toString().trim();
            }

            if (c == '&') {
               sb.append(this.nextEntity(c));
            } else {
               sb.append(c);
            }
         }

         return sb.toString().trim();
      }
   }

   public Object nextEntity(char ampersand) throws JSONException {
      StringBuilder sb = new StringBuilder();

      while(true) {
         char c = this.next();
         if (!Character.isLetterOrDigit(c) && c != '#') {
            if (c == ';') {
               String string = sb.toString();
               return unescapeEntity(string);
            }

            throw this.syntaxError("Missing ';' in XML entity: &" + sb);
         }

         sb.append(Character.toLowerCase(c));
      }
   }

   static String unescapeEntity(String e) {
      if (e != null && !e.isEmpty()) {
         if (e.charAt(0) != '#') {
            Character knownEntity = (Character)entity.get(e);
            return knownEntity == null ? '&' + e + ';' : knownEntity.toString();
         } else {
            int cp;
            if (e.charAt(1) != 'x' && e.charAt(1) != 'X') {
               cp = Integer.parseInt(e.substring(1));
            } else {
               cp = Integer.parseInt(e.substring(2), 16);
            }

            return new String(new int[]{cp}, 0, 1);
         }
      } else {
         return "";
      }
   }

   public Object nextMeta() throws JSONException {
      char c;
      do {
         c = this.next();
      } while(Character.isWhitespace(c));

      switch(c) {
         case '\u0000':
            throw this.syntaxError("Misshaped meta tag");
         case '!':
            return XML.BANG;
         case '"':
         case '\'':
            char q = c;

            do {
               c = this.next();
               if (c == 0) {
                  throw this.syntaxError("Unterminated string");
               }
            } while(c != q);

            return Boolean.TRUE;
         case '/':
            return XML.SLASH;
         case '<':
            return XML.LT;
         case '=':
            return XML.EQ;
         case '>':
            return XML.GT;
         case '?':
            return XML.QUEST;
         default:
            while(true) {
               c = this.next();
               if (Character.isWhitespace(c)) {
                  return Boolean.TRUE;
               }

               switch(c) {
                  case '\u0000':
                     throw this.syntaxError("Unterminated string");
                  case '!':
                  case '"':
                  case '\'':
                  case '/':
                  case '<':
                  case '=':
                  case '>':
                  case '?':
                     this.back();
                     return Boolean.TRUE;
               }
            }
      }
   }

   public Object nextToken() throws JSONException {
      char c;
      do {
         c = this.next();
      } while(Character.isWhitespace(c));

      StringBuilder sb;
      switch(c) {
         case '\u0000':
            throw this.syntaxError("Misshaped element");
         case '!':
            return XML.BANG;
         case '"':
         case '\'':
            char q = c;
            sb = new StringBuilder();

            while(true) {
               c = this.next();
               if (c == 0) {
                  throw this.syntaxError("Unterminated string");
               }

               if (c == q) {
                  return sb.toString();
               }

               if (c == '&') {
                  sb.append(this.nextEntity(c));
               } else {
                  sb.append(c);
               }
            }
         case '/':
            return XML.SLASH;
         case '<':
            throw this.syntaxError("Misplaced '<'");
         case '=':
            return XML.EQ;
         case '>':
            return XML.GT;
         case '?':
            return XML.QUEST;
         default:
            sb = new StringBuilder();

            while(true) {
               sb.append(c);
               c = this.next();
               if (Character.isWhitespace(c)) {
                  return sb.toString();
               }

               switch(c) {
                  case '\u0000':
                     return sb.toString();
                  case '!':
                  case '/':
                  case '=':
                  case '>':
                  case '?':
                  case '[':
                  case ']':
                     this.back();
                     return sb.toString();
                  case '"':
                  case '\'':
                  case '<':
                     throw this.syntaxError("Bad character in a name");
               }
            }
      }
   }

   public void skipPast(String to) {
      int offset = 0;
      int length = to.length();
      char[] circle = new char[length];

      char c;
      int i;
      for(i = 0; i < length; ++i) {
         c = this.next();
         if (c == 0) {
            return;
         }

         circle[i] = c;
      }

      while(true) {
         int j = offset;
         boolean b = true;

         for(i = 0; i < length; ++i) {
            if (circle[j] != to.charAt(i)) {
               b = false;
               break;
            }

            ++j;
            if (j >= length) {
               j -= length;
            }
         }

         if (b) {
            return;
         }

         c = this.next();
         if (c == 0) {
            return;
         }

         circle[offset] = c;
         ++offset;
         if (offset >= length) {
            offset -= length;
         }
      }
   }

   static {
      entity.put("amp", XML.AMP);
      entity.put("apos", XML.APOS);
      entity.put("gt", XML.GT);
      entity.put("lt", XML.LT);
      entity.put("quot", XML.QUOT);
   }
}
interface XMLXsiTypeConverter<T> {
   T convert(String var1);
}

class JSONTokener {
   private long character;
   private boolean eof;
   private long index;
   private long line;
   private char previous;
   private final Reader reader;
   private boolean usePrevious;
   private long characterPreviousLine;

   public JSONTokener(Reader reader) {
      this.reader = (Reader)(reader.markSupported() ? reader : new BufferedReader(reader));
      this.eof = false;
      this.usePrevious = false;
      this.previous = 0;
      this.index = 0L;
      this.character = 1L;
      this.characterPreviousLine = 0L;
      this.line = 1L;
   }

   public JSONTokener(InputStream inputStream) {
      this((Reader)(new InputStreamReader(inputStream)));
   }

   public JSONTokener(String s) {
      this((Reader)(new StringReader(s)));
   }

   public void back() throws JSONException {
      if (!this.usePrevious && this.index > 0L) {
         this.decrementIndexes();
         this.usePrevious = true;
         this.eof = false;
      } else {
         throw new JSONException("Stepping back two steps is not supported");
      }
   }

   private void decrementIndexes() {
      --this.index;
      if (this.previous != '\r' && this.previous != '\n') {
         if (this.character > 0L) {
            --this.character;
         }
      } else {
         --this.line;
         this.character = this.characterPreviousLine;
      }

   }

   public static int dehexchar(char c) {
      if (c >= '0' && c <= '9') {
         return c - 48;
      } else if (c >= 'A' && c <= 'F') {
         return c - 55;
      } else {
         return c >= 'a' && c <= 'f' ? c - 87 : -1;
      }
   }

   public boolean end() {
      return this.eof && !this.usePrevious;
   }

   public boolean more() throws JSONException {
      if (this.usePrevious) {
         return true;
      } else {
         try {
            this.reader.mark(1);
         } catch (IOException var3) {
            throw new JSONException("Unable to preserve stream position", var3);
         }

         try {
            if (this.reader.read() <= 0) {
               this.eof = true;
               return false;
            } else {
               this.reader.reset();
               return true;
            }
         } catch (IOException var2) {
            throw new JSONException("Unable to read the next character from the stream", var2);
         }
      }
   }

   public char next() throws JSONException {
      int c;
      if (this.usePrevious) {
         this.usePrevious = false;
         c = this.previous;
      } else {
         try {
            c = this.reader.read();
         } catch (IOException var3) {
            throw new JSONException(var3);
         }
      }

      if (c <= 0) {
         this.eof = true;
         return '\u0000';
      } else {
         this.incrementIndexes(c);
         this.previous = (char)c;
         return this.previous;
      }
   }

   protected char getPrevious() {
      return this.previous;
   }

   private void incrementIndexes(int c) {
      if (c > 0) {
         ++this.index;
         if (c == 13) {
            ++this.line;
            this.characterPreviousLine = this.character;
            this.character = 0L;
         } else if (c == 10) {
            if (this.previous != '\r') {
               ++this.line;
               this.characterPreviousLine = this.character;
            }

            this.character = 0L;
         } else {
            ++this.character;
         }
      }

   }

   public char next(char c) throws JSONException {
      char n = this.next();
      if (n != c) {
         if (n > 0) {
            throw this.syntaxError("Expected '" + c + "' and instead saw '" + n + "'");
         } else {
            throw this.syntaxError("Expected '" + c + "' and instead saw ''");
         }
      } else {
         return n;
      }
   }

   public String next(int n) throws JSONException {
      if (n == 0) {
         return "";
      } else {
         char[] chars = new char[n];

         for(int pos = 0; pos < n; ++pos) {
            chars[pos] = this.next();
            if (this.end()) {
               throw this.syntaxError("Substring bounds error");
            }
         }

         return new String(chars);
      }
   }

   public char nextClean() throws JSONException {
      char c;
      do {
         c = this.next();
      } while(c != 0 && c <= ' ');

      return c;
   }

   public String nextString(char quote) throws JSONException {
      StringBuilder sb = new StringBuilder();

      while(true) {
         char c = this.next();
         switch(c) {
            case '\u0000':
            case '\n':
            case '\r':
               throw this.syntaxError("Unterminated string");
            case '\\':
               c = this.next();
               switch(c) {
                  case '"':
                  case '\'':
                  case '/':
                  case '\\':
                     sb.append(c);
                     continue;
                  case 'b':
                     sb.append('\b');
                     continue;
                  case 'f':
                     sb.append('\f');
                     continue;
                  case 'n':
                     sb.append('\n');
                     continue;
                  case 'r':
                     sb.append('\r');
                     continue;
                  case 't':
                     sb.append('\t');
                     continue;
                  case 'u':
                     try {
                        sb.append((char)Integer.parseInt(this.next((int)4), 16));
                        continue;
                     } catch (NumberFormatException var5) {
                        throw this.syntaxError("Illegal escape.", var5);
                     }
                  default:
                     throw this.syntaxError("Illegal escape.");
               }
            default:
               if (c == quote) {
                  return sb.toString();
               }

               sb.append(c);
         }
      }
   }

   public String nextTo(char delimiter) throws JSONException {
      StringBuilder sb = new StringBuilder();

      while(true) {
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

   public String nextTo(String delimiters) throws JSONException {
      StringBuilder sb = new StringBuilder();

      while(true) {
         char c = this.next();
         if (delimiters.indexOf(c) >= 0 || c == 0 || c == '\n' || c == '\r') {
            if (c != 0) {
               this.back();
            }

            return sb.toString().trim();
         }

         sb.append(c);
      }
   }

   public Object nextValue() throws JSONException {
      char c = this.nextClean();
      switch(c) {
         case '"':
         case '\'':
            return this.nextString(c);
         case '[':
            this.back();

            try {
               return new JSONArray(this);
            } catch (StackOverflowError var5) {
               throw new JSONException("JSON Array or Object depth too large to process.", var5);
            }
         case '{':
            this.back();

            try {
               return new JSONObject(this);
            } catch (StackOverflowError var4) {
               throw new JSONException("JSON Array or Object depth too large to process.", var4);
            }
         default:
            StringBuilder sb;
            for(sb = new StringBuilder(); c >= ' ' && ",:]}/\\\"[{;=#".indexOf(c) < 0; c = this.next()) {
               sb.append(c);
            }

            if (!this.eof) {
               this.back();
            }

            String string = sb.toString().trim();
            if ("".equals(string)) {
               throw this.syntaxError("Missing value");
            } else {
               return JSONObject.stringToValue(string);
            }
      }
   }

   public char skipTo(char to) throws JSONException {
      char c;
      try {
         long startIndex = this.index;
         long startCharacter = this.character;
         long startLine = this.line;
         this.reader.mark(1000000);

         while(true) {
            c = this.next();
            if (c == 0) {
               this.reader.reset();
               this.index = startIndex;
               this.character = startCharacter;
               this.line = startLine;
               return '\u0000';
            }

            if (c == to) {
               this.reader.mark(1);
               break;
            }
         }
      } catch (IOException var9) {
         throw new JSONException(var9);
      }

      this.back();
      return c;
   }

   public JSONException syntaxError(String message) {
      return new JSONException(message + this.toString());
   }

   public JSONException syntaxError(String message, Throwable causedBy) {
      return new JSONException(message + this.toString(), causedBy);
   }

   public String toString() {
      return " at " + this.index + " [character " + this.character + " line " + this.line + "]";
   }
}
class JSONException extends RuntimeException {
   private static final long serialVersionUID = 0L;

   public JSONException(String message) {
      super(message);
   }

   public JSONException(String message, Throwable cause) {
      super(message, cause);
   }

   public JSONException(Throwable cause) {
      super(cause.getMessage(), cause);
   }
}
class HTTPTokener extends JSONTokener {
   public HTTPTokener(String string) {
      super(string);
   }

   public String nextToken() throws JSONException {
      StringBuilder sb = new StringBuilder();

      char c;
      do {
         c = this.next();
      } while(Character.isWhitespace(c));

      if (c != '"' && c != '\'') {
         while(c != 0 && !Character.isWhitespace(c)) {
            sb.append(c);
            c = this.next();
         }

         return sb.toString();
      } else {
         char q = c;

         while(true) {
            c = this.next();
            if (c < ' ') {
               throw this.syntaxError("Unterminated string.");
            }

            if (c == q) {
               return sb.toString();
            }

            sb.append(c);
         }
      }
   }
}
class HTTP {
   public static final String CRLF = "\r\n";

   public static JSONObject toJSONObject(String string) throws JSONException {
      JSONObject jo = new JSONObject();
      HTTPTokener x = new HTTPTokener(string);
      String token = x.nextToken();
      if (token.toUpperCase(Locale.ROOT).startsWith("HTTP")) {
         jo.put("HTTP-Version", (Object)token);
         jo.put("Status-Code", (Object)x.nextToken());
         jo.put("Reason-Phrase", (Object)x.nextTo('\u0000'));
         x.next();
      } else {
         jo.put("Method", (Object)token);
         jo.put("Request-URI", (Object)x.nextToken());
         jo.put("HTTP-Version", (Object)x.nextToken());
      }

      while(x.more()) {
         String name = x.nextTo(':');
         x.next(':');
         jo.put(name, (Object)x.nextTo('\u0000'));
         x.next();
      }

      return jo;
   }

   public static String toString(JSONObject jo) throws JSONException {
      StringBuilder sb = new StringBuilder();
      if (jo.has("Status-Code") && jo.has("Reason-Phrase")) {
         sb.append(jo.getString("HTTP-Version"));
         sb.append(' ');
         sb.append(jo.getString("Status-Code"));
         sb.append(' ');
         sb.append(jo.getString("Reason-Phrase"));
      } else {
         if (!jo.has("Method") || !jo.has("Request-URI")) {
            throw new JSONException("Not enough material for an HTTP header.");
         }

         sb.append(jo.getString("Method"));
         sb.append(' ');
         sb.append('"');
         sb.append(jo.getString("Request-URI"));
         sb.append('"');
         sb.append(' ');
         sb.append(jo.getString("HTTP-Version"));
      }

      sb.append("\r\n");
      Iterator var2 = jo.keySet().iterator();

      while(var2.hasNext()) {
         String key = (String)var2.next();
         String value = jo.optString(key);
         if (!"HTTP-Version".equals(key) && !"Status-Code".equals(key) && !"Reason-Phrase".equals(key) && !"Method".equals(key) && !"Request-URI".equals(key) && !JSONObject.NULL.equals(value)) {
            sb.append(key);
            sb.append(": ");
            sb.append(jo.optString(key));
            sb.append("\r\n");
         }
      }

      sb.append("\r\n");
      return sb.toString();
   }
}

class CookieList {
   public static JSONObject toJSONObject(String string) throws JSONException {
      JSONObject jo = new JSONObject();
      JSONTokener x = new JSONTokener(string);

      while(x.more()) {
         String name = Cookie.unescape(x.nextTo('='));
         x.next('=');
         jo.put(name, (Object) Cookie.unescape(x.nextTo(';')));
         x.next();
      }

      return jo;
   }

   public static String toString(JSONObject jo) throws JSONException {
      boolean b = false;
      StringBuilder sb = new StringBuilder();
      Iterator var3 = jo.keySet().iterator();

      while(var3.hasNext()) {
         String key = (String)var3.next();
         Object value = jo.opt(key);
         if (!JSONObject.NULL.equals(value)) {
            if (b) {
               sb.append(';');
            }

            sb.append(Cookie.escape(key));
            sb.append("=");
            sb.append(Cookie.escape(value.toString()));
            b = true;
         }
      }

      return sb.toString();
   }
}
class Cookie {
   public static String escape(String string) {
      String s = string.trim();
      int length = s.length();
      StringBuilder sb = new StringBuilder(length);

      for(int i = 0; i < length; ++i) {
         char c = s.charAt(i);
         if (c >= ' ' && c != '+' && c != '%' && c != '=' && c != ';') {
            sb.append(c);
         } else {
            sb.append('%');
            sb.append(Character.forDigit((char)(c >>> 4 & 15), 16));
            sb.append(Character.forDigit((char)(c & 15), 16));
         }
      }

      return sb.toString();
   }

   public static JSONObject toJSONObject(String string) {
      JSONObject jo = new JSONObject();
      JSONTokener x = new JSONTokener(string);
      String name = unescape(x.nextTo('=').trim());
      if ("".equals(name)) {
         throw new JSONException("Cookies must have a 'name'");
      } else {
         jo.put("name", (Object)name);
         x.next('=');
         jo.put("value", (Object)unescape(x.nextTo(';')).trim());
         x.next();

         while(x.more()) {
            name = unescape(x.nextTo("=;")).trim().toLowerCase(Locale.ROOT);
            if ("name".equalsIgnoreCase(name)) {
               throw new JSONException("Illegal attribute name: 'name'");
            }

            if ("value".equalsIgnoreCase(name)) {
               throw new JSONException("Illegal attribute name: 'value'");
            }

            Object value;
            if (x.next() != '=') {
               value = Boolean.TRUE;
            } else {
               value = unescape(x.nextTo(';')).trim();
               x.next();
            }

            if (!"".equals(name) && !"".equals(value)) {
               jo.put(name, value);
            }
         }

         return jo;
      }
   }

   public static String toString(JSONObject jo) throws JSONException {
      StringBuilder sb = new StringBuilder();
      String name = null;
      Object value = null;
      Iterator var4 = jo.keySet().iterator();

      String key;
      while(var4.hasNext()) {
         key = (String)var4.next();
         if ("name".equalsIgnoreCase(key)) {
            name = jo.getString(key).trim();
         }

         if ("value".equalsIgnoreCase(key)) {
            value = jo.getString(key).trim();
         }

         if (name != null && value != null) {
            break;
         }
      }

      if (name != null && !"".equals(name.trim())) {
         if (value == null) {
            value = "";
         }

         sb.append(escape(name));
         sb.append("=");
         sb.append(escape((String)value));
         var4 = jo.keySet().iterator();

         while(var4.hasNext()) {
            key = (String)var4.next();
            if (!"name".equalsIgnoreCase(key) && !"value".equalsIgnoreCase(key)) {
               value = jo.opt(key);
               if (value instanceof Boolean) {
                  if (Boolean.TRUE.equals(value)) {
                     sb.append(';').append(escape(key));
                  }
               } else {
                  sb.append(';').append(escape(key)).append('=').append(escape(value.toString()));
               }
            }
         }

         return sb.toString();
      } else {
         throw new JSONException("Cookie does not have a name");
      }
   }

   public static String unescape(String string) {
      int length = string.length();
      StringBuilder sb = new StringBuilder(length);

      for(int i = 0; i < length; ++i) {
         char c = string.charAt(i);
         if (c == '+') {
            c = ' ';
         } else if (c == '%' && i + 2 < length) {
            int d = JSONTokener.dehexchar(string.charAt(i + 1));
            int e = JSONTokener.dehexchar(string.charAt(i + 2));
            if (d >= 0 && e >= 0) {
               c = (char)(d * 16 + e);
               i += 2;
            }
         }

         sb.append(c);
      }

      return sb.toString();
   }
}
class CDL {
   private static String getValue(JSONTokener x) throws JSONException {
      char c;
      do {
         c = x.next();
      } while(c == ' ' || c == '\t');

      switch(c) {
         case '\u0000':
            return null;
         case '"':
         case '\'':
            char q = c;
            StringBuilder sb = new StringBuilder();

            while(true) {
               c = x.next();
               if (c == q) {
                  char nextC = x.next();
                  if (nextC != '"') {
                     if (nextC > 0) {
                        x.back();
                     }

                     return sb.toString();
                  }
               }

               if (c == 0 || c == '\n' || c == '\r') {
                  throw x.syntaxError("Missing close quote '" + q + "'.");
               }

               sb.append(c);
            }
         case ',':
            x.back();
            return "";
         default:
            x.back();
            return x.nextTo(',');
      }
   }

   public static JSONArray rowToJSONArray(JSONTokener x) throws JSONException {
      JSONArray ja = new JSONArray();

      while(true) {
         String value = getValue(x);
         char c = x.next();
         if (value == null || ja.length() == 0 && value.length() == 0 && c != ',') {
            return null;
         }

         ja.put((Object)value);

         while(c != ',') {
            if (c != ' ') {
               if (c != '\n' && c != '\r' && c != 0) {
                  throw x.syntaxError("Bad character '" + c + "' (" + c + ").");
               }

               return ja;
            }

            c = x.next();
         }
      }
   }

   public static JSONObject rowToJSONObject(JSONArray names, JSONTokener x) throws JSONException {
      JSONArray ja = rowToJSONArray(x);
      return ja != null ? ja.toJSONObject(names) : null;
   }

   public static String rowToString(JSONArray ja) {
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < ja.length(); ++i) {
         if (i > 0) {
            sb.append(',');
         }

         Object object = ja.opt(i);
         if (object != null) {
            String string = object.toString();
            if (string.length() > 0 && (string.indexOf(44) >= 0 || string.indexOf(10) >= 0 || string.indexOf(13) >= 0 || string.indexOf(0) >= 0 || string.charAt(0) == '"')) {
               sb.append('"');
               int length = string.length();

               for(int j = 0; j < length; ++j) {
                  char c = string.charAt(j);
                  if (c >= ' ' && c != '"') {
                     sb.append(c);
                  }
               }

               sb.append('"');
            } else {
               sb.append(string);
            }
         }
      }

      sb.append('\n');
      return sb.toString();
   }

   public static JSONArray toJSONArray(String string) throws JSONException {
      return toJSONArray(new JSONTokener(string));
   }

   public static JSONArray toJSONArray(JSONTokener x) throws JSONException {
      return toJSONArray(rowToJSONArray(x), x);
   }

   public static JSONArray toJSONArray(JSONArray names, String string) throws JSONException {
      return toJSONArray(names, new JSONTokener(string));
   }

   public static JSONArray toJSONArray(JSONArray names, JSONTokener x) throws JSONException {
      if (names != null && names.length() != 0) {
         JSONArray ja = new JSONArray();

         while(true) {
            JSONObject jo = rowToJSONObject(names, x);
            if (jo == null) {
               return ja.length() == 0 ? null : ja;
            }

            ja.put((Object)jo);
         }
      } else {
         return null;
      }
   }

   public static String toString(JSONArray ja) throws JSONException {
      JSONObject jo = ja.optJSONObject(0);
      if (jo != null) {
         JSONArray names = jo.names();
         if (names != null) {
            return rowToString(names) + toString(names, ja);
         }
      }

      return null;
   }

   public static String toString(JSONArray names, JSONArray ja) throws JSONException {
      if (names != null && names.length() != 0) {
         StringBuilder sb = new StringBuilder();

         for(int i = 0; i < ja.length(); ++i) {
            JSONObject jo = ja.optJSONObject(i);
            if (jo != null) {
               sb.append(rowToString(jo.toJSONArray(names)));
            }
         }

         return sb.toString();
      } else {
         return null;
      }
   }
}
public class JSONObject {
   static final Pattern NUMBER_PATTERN = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");
   private final LinkedHashMap map;
   public static final Object NULL = new Null();

   public JSONObject() {
      this.map = new LinkedHashMap();

   }

   public JSONObject(JSONObject jo, String... names) {
      this(names.length);

      for(int i = 0; i < names.length; ++i) {
         try {
            this.putOnce(names[i], jo.opt(names[i]));
         } catch (Exception var5) {
         }
      }

   }

   public JSONObject(JSONTokener x) throws JSONException {
      this();
      if (x.nextClean() != '{') {
         throw x.syntaxError("A JSONObject text must begin with '{'");
      } else {
         while(true) {
            char prev = x.getPrevious();
            char c = x.nextClean();
            switch(c) {
            case '\u0000':
               throw x.syntaxError("A JSONObject text must end with '}'");
            case '[':
            case '{':
               if (prev == '{') {
                  throw x.syntaxError("A JSON Object can not directly nest another JSON Object or JSON Array.");
               }
            default:
               x.back();
               String key = x.nextValue().toString();
               c = x.nextClean();
               if (c != ':') {
                  throw x.syntaxError("Expected a ':' after a key");
               }

               if (key != null) {
                  if (this.opt(key) != null) {
                     throw x.syntaxError("Duplicate key \"" + key + "\"");
                  }

                  Object value = x.nextValue();
                  if (value != null) {
                     this.put(key, value);
                  }
               }

               switch(x.nextClean()) {
               case ',':
               case ';':
                  if (x.nextClean() == '}') {
                     return;
                  }

                  x.back();
                  continue;
               case '}':
                  return;
               default:
                  throw x.syntaxError("Expected a ',' or '}'");
               }
            case '}':
               return;
            }
         }
      }
   }

   public JSONObject(Map<?, ?> m) {
      if (m == null) {
         this.map = new LinkedHashMap();
      } else {
         this.map = new LinkedHashMap(m.size());
         Iterator var2 = m.entrySet().iterator();

         while(var2.hasNext()) {
            Entry<?, ?> e = (Entry)var2.next();
            if (e.getKey() == null) {
               throw new NullPointerException("Null key.");
            }

            Object value = e.getValue();
            if (value != null) {
               this.map.put(String.valueOf(e.getKey()), wrap(value));
            }
         }
      }

   }

   public JSONObject(Object bean) {
      this();
      this.populateMap(bean);
   }

   private JSONObject(Object bean, Set<Object> objectsRecord) {
      this();
      this.populateMap(bean, objectsRecord);
   }

   public JSONObject(Object object, String... names) {
      this(names.length);
      Class<?> c = object.getClass();

      for(int i = 0; i < names.length; ++i) {
         String name = names[i];

         try {
            this.putOpt(name, c.getField(name).get(object));
         } catch (Exception var7) {
         }
      }

   }

   public JSONObject(String source) throws JSONException {
      this(new JSONTokener(source));
   }

   public JSONObject(String baseName, Locale locale) throws JSONException {
      this();
      ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale, Thread.currentThread().getContextClassLoader());
      Enumeration keys = bundle.getKeys();

      while(true) {
         Object key;
         do {
            if (!keys.hasMoreElements()) {
               return;
            }

            key = keys.nextElement();
         } while(key == null);

         String[] path = ((String)key).split("\\.");
         int last = path.length - 1;
         JSONObject target = this;

         for(int i = 0; i < last; ++i) {
            String segment = path[i];
            JSONObject nextTarget = target.optJSONObject(segment);
            if (nextTarget == null) {
               nextTarget = new JSONObject();
               target.put(segment, (Object)nextTarget);
            }

            target = nextTarget;
         }

         target.put(path[last], (Object)bundle.getString((String)key));
      }
   }

   protected JSONObject(int initialCapacity) {
      this.map = new LinkedHashMap(initialCapacity);
   }

   public JSONObject accumulate(String key, Object value) throws JSONException {
      testValidity(value);
      Object object = this.opt(key);
      if (object == null) {
         this.put(key, value instanceof JSONArray ? (new JSONArray()).put(value) : value);
      } else if (object instanceof JSONArray) {
         ((JSONArray)object).put(value);
      } else {
         this.put(key, (Object)(new JSONArray()).put(object).put(value));
      }

      return this;
   }

   public JSONObject append(String key, Object value) throws JSONException {
      testValidity(value);
      Object object = this.opt(key);
      if (object == null) {
         this.put(key, (Object)(new JSONArray()).put(value));
      } else {
         if (!(object instanceof JSONArray)) {
            throw wrongValueFormatException(key, "JSONArray", (Object)null, (Throwable)null);
         }

         this.put(key, (Object)((JSONArray)object).put(value));
      }

      return this;
   }

   public static String doubleToString(double d) {
      if (!Double.isInfinite(d) && !Double.isNaN(d)) {
         String string = Double.toString(d);
         if (string.indexOf(46) > 0 && string.indexOf(101) < 0 && string.indexOf(69) < 0) {
            while(string.endsWith("0")) {
               string = string.substring(0, string.length() - 1);
            }

            if (string.endsWith(".")) {
               string = string.substring(0, string.length() - 1);
            }
         }

         return string;
      } else {
         return "null";
      }
   }

   public Object get(String key) throws JSONException {
      if (key == null) {
         throw new JSONException("Null key.");
      } else {
         Object object = this.opt(key);
         if (object == null) {
            throw new JSONException("JSONObject[" + quote(key) + "] not found.");
         } else {
            return object;
         }
      }
   }

   public <E extends Enum<E>> E getEnum(Class<E> clazz, String key) throws JSONException {
      E val = this.optEnum(clazz, key);
      if (val == null) {
         throw wrongValueFormatException(key, "enum of type " + quote(clazz.getSimpleName()), (Throwable)null);
      } else {
         return val;
      }
   }

   public boolean getBoolean(String key) throws JSONException {
      Object object = this.get(key);
      if (!object.equals(Boolean.FALSE) && (!(object instanceof String) || !((String)object).equalsIgnoreCase("false"))) {
         if (!object.equals(Boolean.TRUE) && (!(object instanceof String) || !((String)object).equalsIgnoreCase("true"))) {
            throw wrongValueFormatException(key, "Boolean", (Throwable)null);
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public BigInteger getBigInteger(String key) throws JSONException {
      Object object = this.get(key);
      BigInteger ret = objectToBigInteger(object, (BigInteger)null);
      if (ret != null) {
         return ret;
      } else {
         throw wrongValueFormatException(key, "BigInteger", object, (Throwable)null);
      }
   }

   public BigDecimal getBigDecimal(String key) throws JSONException {
      Object object = this.get(key);
      BigDecimal ret = objectToBigDecimal(object, (BigDecimal)null);
      if (ret != null) {
         return ret;
      } else {
         throw wrongValueFormatException(key, "BigDecimal", object, (Throwable)null);
      }
   }

   public double getDouble(String key) throws JSONException {
      Object object = this.get(key);
      if (object instanceof Number) {
         return ((Number)object).doubleValue();
      } else {
         try {
            return Double.parseDouble(object.toString());
         } catch (Exception var4) {
            throw wrongValueFormatException(key, "double", var4);
         }
      }
   }

   public float getFloat(String key) throws JSONException {
      Object object = this.get(key);
      if (object instanceof Number) {
         return ((Number)object).floatValue();
      } else {
         try {
            return Float.parseFloat(object.toString());
         } catch (Exception var4) {
            throw wrongValueFormatException(key, "float", var4);
         }
      }
   }

   public Number getNumber(String key) throws JSONException {
      Object object = this.get(key);

      try {
         return object instanceof Number ? (Number)object : stringToNumber(object.toString());
      } catch (Exception var4) {
         throw wrongValueFormatException(key, "number", var4);
      }
   }

   public int getInt(String key) throws JSONException {
      Object object = this.get(key);
      if (object instanceof Number) {
         return ((Number)object).intValue();
      } else {
         try {
            return Integer.parseInt(object.toString());
         } catch (Exception var4) {
            throw wrongValueFormatException(key, "int", var4);
         }
      }
   }

   public JSONArray getJSONArray(String key) throws JSONException {
      Object object = this.get(key);
      if (object instanceof JSONArray) {
         return (JSONArray)object;
      } else {
         throw wrongValueFormatException(key, "JSONArray", (Throwable)null);
      }
   }

   public JSONObject getJSONObject(String key) throws JSONException {
      Object object = this.get(key);
      if (object instanceof JSONObject) {
         return (JSONObject)object;
      } else {
         throw wrongValueFormatException(key, "JSONObject", (Throwable)null);
      }
   }

   public long getLong(String key) throws JSONException {
      Object object = this.get(key);
      if (object instanceof Number) {
         return ((Number)object).longValue();
      } else {
         try {
            return Long.parseLong(object.toString());
         } catch (Exception var4) {
            throw wrongValueFormatException(key, "long", var4);
         }
      }
   }

   public static String[] getNames(JSONObject jo) {
      return jo.isEmpty() ? null : (String[])jo.keySet().toArray(new String[jo.length()]);
   }

   public static String[] getNames(Object object) {
      if (object == null) {
         return null;
      } else {
         Class<?> klass = object.getClass();
         Field[] fields = klass.getFields();
         int length = fields.length;
         if (length == 0) {
            return null;
         } else {
            String[] names = new String[length];

            for(int i = 0; i < length; ++i) {
               names[i] = fields[i].getName();
            }

            return names;
         }
      }
   }

   public String getString(String key) throws JSONException {
      Object object = this.get(key);
      if (object instanceof String) {
         return (String)object;
      } else {
         throw wrongValueFormatException(key, "string", (Throwable)null);
      }
   }

   public boolean has(String key) {
      return this.map.containsKey(key);
   }

   public JSONObject increment(String key) throws JSONException {
      Object value = this.opt(key);
      if (value == null) {
         this.put(key, 1);
      } else if (value instanceof Integer) {
         this.put(key, (Integer)value + 1);
      } else if (value instanceof Long) {
         this.put(key, (Long)value + 1L);
      } else if (value instanceof BigInteger) {
         this.put(key, (Object)((BigInteger)value).add(BigInteger.ONE));
      } else if (value instanceof Float) {
         this.put(key, (Float)value + 1.0F);
      } else if (value instanceof Double) {
         this.put(key, (Double)value + 1.0D);
      } else {
         if (!(value instanceof BigDecimal)) {
            throw new JSONException("Unable to increment [" + quote(key) + "].");
         }

         this.put(key, (Object)((BigDecimal)value).add(BigDecimal.ONE));
      }

      return this;
   }

   public boolean isNull(String key) {
      return NULL.equals(this.opt(key));
   }

   public Iterator<String> keys() {
      return this.keySet().iterator();
   }

   public Set<String> keySet() {
      return this.map.keySet();
   }

   protected Set<Entry> entrySet() {
      return this.map.entrySet();
   }

   public int length() {
      return this.map.size();
   }

   public void clear() {
      this.map.clear();
   }

   public boolean isEmpty() {
      return this.map.isEmpty();
   }

   public JSONArray names() {
      return this.map.isEmpty() ? null : new JSONArray(this.map.keySet());
   }

   public static String numberToString(Number number) throws JSONException {
      if (number == null) {
         throw new JSONException("Null pointer");
      } else {
         testValidity(number);
         String string = number.toString();
         if (string.indexOf(46) > 0 && string.indexOf(101) < 0 && string.indexOf(69) < 0) {
            while(string.endsWith("0")) {
               string = string.substring(0, string.length() - 1);
            }

            if (string.endsWith(".")) {
               string = string.substring(0, string.length() - 1);
            }
         }

         return string;
      }
   }

   public Object opt(String key) {
      return key == null ? null : this.map.get(key);
   }

   public <E extends Enum<E>> E optEnum(Class<E> clazz, String key) {
      return this.optEnum(clazz, key, (E) null);
   }

   public <E extends Enum<E>> E optEnum(Class<E> clazz, String key, E defaultValue) {
      try {
         Object val = this.opt(key);
         if (NULL.equals(val)) {
            return defaultValue;
         } else if (clazz.isAssignableFrom(val.getClass())) {
            E myE = (E) val;
            return myE;
         } else {
            return Enum.valueOf(clazz, val.toString());
         }
      } catch (IllegalArgumentException var6) {
         return defaultValue;
      } catch (NullPointerException var7) {
         return defaultValue;
      }
   }

   public boolean optBoolean(String key) {
      return this.optBoolean(key, false);
   }

   public boolean optBoolean(String key, boolean defaultValue) {
      Object val = this.opt(key);
      if (NULL.equals(val)) {
         return defaultValue;
      } else if (val instanceof Boolean) {
         return (Boolean)val;
      } else {
         try {
            return this.getBoolean(key);
         } catch (Exception var5) {
            return defaultValue;
         }
      }
   }

   public BigDecimal optBigDecimal(String key, BigDecimal defaultValue) {
      Object val = this.opt(key);
      return objectToBigDecimal(val, defaultValue);
   }

   static BigDecimal objectToBigDecimal(Object val, BigDecimal defaultValue) {
      return objectToBigDecimal(val, defaultValue, true);
   }

   static BigDecimal objectToBigDecimal(Object val, BigDecimal defaultValue, boolean exact) {
      if (NULL.equals(val)) {
         return defaultValue;
      } else if (val instanceof BigDecimal) {
         return (BigDecimal)val;
      } else if (val instanceof BigInteger) {
         return new BigDecimal((BigInteger)val);
      } else if (!(val instanceof Double) && !(val instanceof Float)) {
         if (!(val instanceof Long) && !(val instanceof Integer) && !(val instanceof Short) && !(val instanceof Byte)) {
            try {
               return new BigDecimal(val.toString());
            } catch (Exception var4) {
               return defaultValue;
            }
         } else {
            return new BigDecimal(((Number)val).longValue());
         }
      } else if (!numberIsFinite((Number)val)) {
         return defaultValue;
      } else {
         return exact ? new BigDecimal(((Number)val).doubleValue()) : new BigDecimal(val.toString());
      }
   }

   public BigInteger optBigInteger(String key, BigInteger defaultValue) {
      Object val = this.opt(key);
      return objectToBigInteger(val, defaultValue);
   }

   static BigInteger objectToBigInteger(Object val, BigInteger defaultValue) {
      if (NULL.equals(val)) {
         return defaultValue;
      } else if (val instanceof BigInteger) {
         return (BigInteger)val;
      } else if (val instanceof BigDecimal) {
         return ((BigDecimal)val).toBigInteger();
      } else if (!(val instanceof Double) && !(val instanceof Float)) {
         if (!(val instanceof Long) && !(val instanceof Integer) && !(val instanceof Short) && !(val instanceof Byte)) {
            try {
               String valStr = val.toString();
               return isDecimalNotation(valStr) ? (new BigDecimal(valStr)).toBigInteger() : new BigInteger(valStr);
            } catch (Exception var3) {
               return defaultValue;
            }
         } else {
            return BigInteger.valueOf(((Number)val).longValue());
         }
      } else {
         return !numberIsFinite((Number)val) ? defaultValue : (new BigDecimal(((Number)val).doubleValue())).toBigInteger();
      }
   }

   public double optDouble(String key) {
      return this.optDouble(key, Double.NaN);
   }

   public double optDouble(String key, double defaultValue) {
      Number val = this.optNumber(key);
      if (val == null) {
         return defaultValue;
      } else {
         double doubleValue = val.doubleValue();
         return doubleValue;
      }
   }

   public float optFloat(String key) {
      return this.optFloat(key, Float.NaN);
   }

   public float optFloat(String key, float defaultValue) {
      Number val = this.optNumber(key);
      if (val == null) {
         return defaultValue;
      } else {
         float floatValue = val.floatValue();
         return floatValue;
      }
   }

   public int optInt(String key) {
      return this.optInt(key, 0);
   }

   public int optInt(String key, int defaultValue) {
      Number val = this.optNumber(key, (Number)null);
      return val == null ? defaultValue : val.intValue();
   }

   public JSONArray optJSONArray(String key) {
      Object o = this.opt(key);
      return o instanceof JSONArray ? (JSONArray)o : null;
   }

   public JSONObject optJSONObject(String key) {
      return this.optJSONObject(key, (JSONObject)null);
   }

   public JSONObject optJSONObject(String key, JSONObject defaultValue) {
      Object object = this.opt(key);
      return object instanceof JSONObject ? (JSONObject)object : defaultValue;
   }

   public long optLong(String key) {
      return this.optLong(key, 0L);
   }

   public long optLong(String key, long defaultValue) {
      Number val = this.optNumber(key, (Number)null);
      return val == null ? defaultValue : val.longValue();
   }

   public Number optNumber(String key) {
      return this.optNumber(key, (Number)null);
   }

   public Number optNumber(String key, Number defaultValue) {
      Object val = this.opt(key);
      if (NULL.equals(val)) {
         return defaultValue;
      } else if (val instanceof Number) {
         return (Number)val;
      } else {
         try {
            return stringToNumber(val.toString());
         } catch (Exception var5) {
            return defaultValue;
         }
      }
   }

   public String optString(String key) {
      return this.optString(key, "");
   }

   public String optString(String key, String defaultValue) {
      Object object = this.opt(key);
      return NULL.equals(object) ? defaultValue : object.toString();
   }

   private void populateMap(Object bean) {
      this.populateMap(bean, Collections.newSetFromMap(new IdentityHashMap()));
   }

   private void populateMap(Object bean, Set<Object> objectsRecord) {
      Class<?> klass = bean.getClass();
      boolean includeSuperClass = klass.getClassLoader() != null;
      Method[] methods = includeSuperClass ? klass.getMethods() : klass.getDeclaredMethods();
      Method[] var6 = methods;
      int var7 = methods.length;

      for(int var8 = 0; var8 < var7; ++var8) {
         Method method = var6[var8];
         int modifiers = method.getModifiers();
         if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) && method.getParameterTypes().length == 0 && !method.isBridge() && method.getReturnType() != Void.TYPE && isValidMethodName(method.getName())) {
            String key = getKeyNameFromMethod(method);
            if (key != null && !key.isEmpty()) {
               try {
                  Object result = method.invoke(bean);
                  if (result != null) {
                     if (objectsRecord.contains(result)) {
                        throw recursivelyDefinedObjectException(key);
                     }

                     objectsRecord.add(result);
                     this.map.put(key, wrap(result, objectsRecord));
                     objectsRecord.remove(result);
                     if (result instanceof Closeable) {
                        try {
                           ((Closeable)result).close();
                        } catch (IOException var14) {
                        }
                     }
                  }
               } catch (IllegalAccessException var15) {
               } catch (IllegalArgumentException var16) {
               } catch (InvocationTargetException var17) {
               }
            }
         }
      }

   }

   private static boolean isValidMethodName(String name) {
      return !"getClass".equals(name) && !"getDeclaringClass".equals(name);
   }

   private static String getKeyNameFromMethod(Method method) {
      int ignoreDepth = getAnnotationDepth(method, JSONPropertyIgnore.class);
      if (ignoreDepth > 0) {
         int forcedNameDepth = getAnnotationDepth(method, JSONPropertyName.class);
         if (forcedNameDepth < 0 || ignoreDepth <= forcedNameDepth) {
            return null;
         }
      }

      JSONPropertyName annotation = (JSONPropertyName)getAnnotation(method, JSONPropertyName.class);
      if (annotation != null && annotation.value() != null && !annotation.value().isEmpty()) {
         return annotation.value();
      } else {
         String name = method.getName();
         String key;
         if (name.startsWith("get") && name.length() > 3) {
            key = name.substring(3);
         } else {
            if (!name.startsWith("is") || name.length() <= 2) {
               return null;
            }

            key = name.substring(2);
         }

         if (key.length() != 0 && !Character.isLowerCase(key.charAt(0))) {
            if (key.length() == 1) {
               key = key.toLowerCase(Locale.ROOT);
            } else if (!Character.isUpperCase(key.charAt(1))) {
               key = key.substring(0, 1).toLowerCase(Locale.ROOT) + key.substring(1);
            }

            return key;
         } else {
            return null;
         }
      }
   }

   private static <A extends Annotation> A getAnnotation(Method m, Class<A> annotationClass) {
      if (m != null && annotationClass != null) {
         if (m.isAnnotationPresent(annotationClass)) {
            return m.getAnnotation(annotationClass);
         } else {
            Class<?> c = m.getDeclaringClass();
            if (c.getSuperclass() == null) {
               return null;
            } else {
               Class[] var3 = c.getInterfaces();
               int var4 = var3.length;

               for(int var5 = 0; var5 < var4; ++var5) {
                  Class i = var3[var5];

                  try {
                     Method im = i.getMethod(m.getName(), m.getParameterTypes());
                     return getAnnotation(im, annotationClass);
                  } catch (SecurityException var10) {
                  } catch (NoSuchMethodException var11) {
                  }
               }

               try {
                  return getAnnotation(c.getSuperclass().getMethod(m.getName(), m.getParameterTypes()), annotationClass);
               } catch (SecurityException var8) {
                  return null;
               } catch (NoSuchMethodException var9) {
                  return null;
               }
            }
         }
      } else {
         return null;
      }
   }

   private static int getAnnotationDepth(Method m, Class<? extends Annotation> annotationClass) {
      if (m != null && annotationClass != null) {
         if (m.isAnnotationPresent(annotationClass)) {
            return 1;
         } else {
            Class<?> c = m.getDeclaringClass();
            if (c.getSuperclass() == null) {
               return -1;
            } else {
               Class[] var3 = c.getInterfaces();
               int var4 = var3.length;

               for(int var5 = 0; var5 < var4; ++var5) {
                  Class i = var3[var5];

                  try {
                     Method im = i.getMethod(m.getName(), m.getParameterTypes());
                     int d = getAnnotationDepth(im, annotationClass);
                     if (d > 0) {
                        return d + 1;
                     }
                  } catch (SecurityException var11) {
                  } catch (NoSuchMethodException var12) {
                  }
               }

               try {
                  int d = getAnnotationDepth(c.getSuperclass().getMethod(m.getName(), m.getParameterTypes()), annotationClass);
                  return d > 0 ? d + 1 : -1;
               } catch (SecurityException var9) {
                  return -1;
               } catch (NoSuchMethodException var10) {
                  return -1;
               }
            }
         }
      } else {
         return -1;
      }
   }

   public JSONObject put(String key, boolean value) throws JSONException {
      return this.put(key, (Object)(value ? Boolean.TRUE : Boolean.FALSE));
   }

   public JSONObject put(String key, Collection<?> value) throws JSONException {
      return this.put(key, (Object)(new JSONArray(value)));
   }

   public JSONObject put(String key, double value) throws JSONException {
      return this.put(key, (Object)value);
   }

   public JSONObject put(String key, float value) throws JSONException {
      return this.put(key, (Object)value);
   }

   public JSONObject put(String key, long value) throws JSONException {
      return this.put(key, (Object)value);
   }

   public JSONObject put(String key, int value) throws JSONException {
      return this.put(key, (Object)value);
   }

   public JSONObject put(String key, Map<?, ?> value) throws JSONException {
      return this.put(key, (Object)(new JSONObject(value)));
   }

   public JSONObject put(String key, Object value) throws JSONException {
      if (key == null) {
         throw new NullPointerException("Null key.");
      } else {
         if (value != null) {
            testValidity(value);
            this.map.put(key, value);
         } else {
            this.remove(key);
         }

         return this;
      }
   }

   public JSONObject putOnce(String key, Object value) throws JSONException {
      if (key != null && value != null) {
         if (this.opt(key) != null) {
            throw new JSONException("Duplicate key \"" + key + "\"");
         } else {
            return this.put(key, value);
         }
      } else {
         return this;
      }
   }

   public JSONObject putOpt(String key, Object value) throws JSONException {
      return key != null && value != null ? this.put(key, value) : this;
   }

   public Object query(String jsonPointer) {
      return this.query(new JSONPointer(jsonPointer));
   }

   public Object query(JSONPointer jsonPointer) {
      return jsonPointer.queryFrom(this);
   }

   public Object optQuery(String jsonPointer) {
      return this.optQuery(new JSONPointer(jsonPointer));
   }

   public Object optQuery(JSONPointer jsonPointer) {
      try {
         return jsonPointer.queryFrom(this);
      } catch (JSONPointerException var3) {
         return null;
      }
   }

   public static String quote(String string) {
      StringWriter sw = new StringWriter();
      synchronized(sw.getBuffer()) {
         String var10000;
         try {
            var10000 = quote(string, sw).toString();
         } catch (IOException var5) {
            return "";
         }

         return var10000;
      }
   }

   public static Writer quote(String string, Writer w) throws IOException {
      if (string != null && !string.isEmpty()) {
         char c = 0;
         int len = string.length();
         w.write(34);

         for(int i = 0; i < len; ++i) {
            char b = c;
            c = string.charAt(i);
            switch(c) {
            case '\b':
               w.write("\\b");
               continue;
            case '\t':
               w.write("\\t");
               continue;
            case '\n':
               w.write("\\n");
               continue;
            case '\f':
               w.write("\\f");
               continue;
            case '\r':
               w.write("\\r");
               continue;
            case '"':
            case '\\':
               w.write(92);
               w.write(c);
               continue;
            case '/':
               if (b == '<') {
                  w.write(92);
               }

               w.write(c);
               continue;
            }

            if (c >= ' ' && (c < 128 || c >= 160) && (c < 8192 || c >= 8448)) {
               w.write(c);
            } else {
               w.write("\\u");
               String hhhh = Integer.toHexString(c);
               w.write("0000", 0, 4 - hhhh.length());
               w.write(hhhh);
            }
         }

         w.write(34);
         return w;
      } else {
         w.write("\"\"");
         return w;
      }
   }

   public Object remove(String key) {
      return this.map.remove(key);
   }

   public boolean similar(Object other) {
      try {
         if (!(other instanceof JSONObject)) {
            return false;
         } else if (!this.keySet().equals(((JSONObject)other).keySet())) {
            return false;
         } else {
            Iterator var2 = this.entrySet().iterator();

            Object valueThis;
            Object valueOther;
            do {
               while(true) {
                  do {
                     if (!var2.hasNext()) {
                        return true;
                     }

                     Entry<String, ?> entry = (Entry)var2.next();
                     String name = (String)entry.getKey();
                     valueThis = entry.getValue();
                     valueOther = ((JSONObject)other).get(name);
                  } while(valueThis == valueOther);

                  if (valueThis == null) {
                     return false;
                  }

                  if (valueThis instanceof JSONObject) {
                     break;
                  }

                  if (valueThis instanceof JSONArray) {
                     if (!((JSONArray)valueThis).similar(valueOther)) {
                        return false;
                     }
                  } else if (valueThis instanceof Number && valueOther instanceof Number) {
                     if (!isNumberSimilar((Number)valueThis, (Number)valueOther)) {
                        return false;
                     }
                  } else if (!valueThis.equals(valueOther)) {
                     return false;
                  }
               }
            } while(((JSONObject)valueThis).similar(valueOther));

            return false;
         }
      } catch (Throwable var7) {
         return false;
      }
   }

   static boolean isNumberSimilar(Number l, Number r) {
      if (numberIsFinite(l) && numberIsFinite(r)) {
         if (l.getClass().equals(r.getClass()) && l instanceof Comparable) {
            int compareTo = ((Comparable)l).compareTo(r);
            return compareTo == 0;
         } else {
            BigDecimal lBigDecimal = objectToBigDecimal(l, (BigDecimal)null, false);
            BigDecimal rBigDecimal = objectToBigDecimal(r, (BigDecimal)null, false);
            if (lBigDecimal != null && rBigDecimal != null) {
               return lBigDecimal.compareTo(rBigDecimal) == 0;
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private static boolean numberIsFinite(Number n) {
      if (!(n instanceof Double) || !((Double)n).isInfinite() && !((Double)n).isNaN()) {
         return !(n instanceof Float) || !((Float)n).isInfinite() && !((Float)n).isNaN();
      } else {
         return false;
      }
   }

   protected static boolean isDecimalNotation(String val) {
      return val.indexOf(46) > -1 || val.indexOf(101) > -1 || val.indexOf(69) > -1 || "-0".equals(val);
   }

   protected static Number stringToNumber(String val) throws NumberFormatException {
      char initial = val.charAt(0);
      if ((initial < '0' || initial > '9') && initial != '-') {
         throw new NumberFormatException("val [" + val + "] is not a valid number.");
      } else if (isDecimalNotation(val)) {
         try {
            BigDecimal bd = new BigDecimal(val);
            return (Number)(initial == '-' && BigDecimal.ZERO.compareTo(bd) == 0 ? -0.0D : bd);
         } catch (NumberFormatException var5) {
            try {
               Double d = Double.valueOf(val);
               if (!d.isNaN() && !d.isInfinite()) {
                  return d;
               } else {
                  throw new NumberFormatException("val [" + val + "] is not a valid number.");
               }
            } catch (NumberFormatException var4) {
               throw new NumberFormatException("val [" + val + "] is not a valid number.");
            }
         }
      } else {
         char at1;
         if (initial == '0' && val.length() > 1) {
            at1 = val.charAt(1);
            if (at1 >= '0' && at1 <= '9') {
               throw new NumberFormatException("val [" + val + "] is not a valid number.");
            }
         } else if (initial == '-' && val.length() > 2) {
            at1 = val.charAt(1);
            char at2 = val.charAt(2);
            if (at1 == '0' && at2 >= '0' && at2 <= '9') {
               throw new NumberFormatException("val [" + val + "] is not a valid number.");
            }
         }

         BigInteger bi = new BigInteger(val);
         if (bi.bitLength() <= 31) {
            return bi.intValue();
         } else {
            return (Number)(bi.bitLength() <= 63 ? bi.longValue() : bi);
         }
      }
   }

   public static Object stringToValue(String string) {
      if ("".equals(string)) {
         return string;
      } else if ("true".equalsIgnoreCase(string)) {
         return Boolean.TRUE;
      } else if ("false".equalsIgnoreCase(string)) {
         return Boolean.FALSE;
      } else if ("null".equalsIgnoreCase(string)) {
         return NULL;
      } else {
         char initial = string.charAt(0);
         if (initial >= '0' && initial <= '9' || initial == '-') {
            try {
               return stringToNumber(string);
            } catch (Exception var3) {
            }
         }

         return string;
      }
   }

   public static void testValidity(Object o) throws JSONException {
      if (o instanceof Number && !numberIsFinite((Number)o)) {
         throw new JSONException("JSON does not allow non-finite numbers.");
      }
   }

   public JSONArray toJSONArray(JSONArray names) throws JSONException {
      if (names != null && !names.isEmpty()) {
         JSONArray ja = new JSONArray();

         for(int i = 0; i < names.length(); ++i) {
            ja.put(this.opt(names.getString(i)));
         }

         return ja;
      } else {
         return null;
      }
   }

   public String toString() {
      try {
         return this.toString(0);
      } catch (Exception var2) {
         return null;
      }
   }

   public String toString(int indentFactor) throws JSONException {
      StringWriter w = new StringWriter();
      synchronized(w.getBuffer()) {
         return this.write(w, indentFactor, 0).toString();
      }
   }

   public static String valueToString(Object value) throws JSONException {
      return JSONWriter.valueToString(value);
   }

   public static Object wrap(Object object) {
      return wrap(object, (Set)null);
   }

   private static Object wrap(Object object, Set<Object> objectsRecord) {
      try {
         if (NULL.equals(object)) {
            return NULL;
         } else if (!(object instanceof JSONObject) && !(object instanceof JSONArray) && !NULL.equals(object) && !(object instanceof JSONString) && !(object instanceof Byte) && !(object instanceof Character) && !(object instanceof Short) && !(object instanceof Integer) && !(object instanceof Long) && !(object instanceof Boolean) && !(object instanceof Float) && !(object instanceof Double) && !(object instanceof String) && !(object instanceof BigInteger) && !(object instanceof BigDecimal) && !(object instanceof Enum)) {
            if (object instanceof Collection) {
               Collection<?> coll = (Collection)object;
               return new JSONArray(coll);
            } else if (object.getClass().isArray()) {
               return new JSONArray(object);
            } else if (object instanceof Map) {
               Map<?, ?> map = (Map)object;
               return new JSONObject(map);
            } else {
               Package objectPackage = object.getClass().getPackage();
               String objectPackageName = objectPackage != null ? objectPackage.getName() : "";
               if (!objectPackageName.startsWith("java.") && !objectPackageName.startsWith("javax.") && object.getClass().getClassLoader() != null) {
                  return objectsRecord != null ? new JSONObject(object, objectsRecord) : new JSONObject(object);
               } else {
                  return object.toString();
               }
            }
         } else {
            return object;
         }
      } catch (JSONException var4) {
         throw var4;
      } catch (Exception var5) {
         return null;
      }
   }

   public Writer write(Writer writer) throws JSONException {
      return this.write(writer, 0, 0);
   }

   static final Writer writeValue(Writer writer, Object value, int indentFactor, int indent) throws JSONException, IOException {
      if (value != null && !value.equals((Object)null)) {
         String numberAsString;
         if (value instanceof JSONString) {
            try {
               numberAsString = ((JSONString)value).toJSONString();
            } catch (Exception var6) {
               throw new JSONException(var6);
            }

            writer.write(numberAsString != null ? numberAsString.toString() : quote(value.toString()));
         } else if (value instanceof Number) {
            numberAsString = numberToString((Number)value);
            if (NUMBER_PATTERN.matcher(numberAsString).matches()) {
               writer.write(numberAsString);
            } else {
               quote(numberAsString, writer);
            }
         } else if (value instanceof Boolean) {
            writer.write(value.toString());
         } else if (value instanceof Enum) {
            writer.write(quote(((Enum)value).name()));
         } else if (value instanceof JSONObject) {
            ((JSONObject)value).write(writer, indentFactor, indent);
         } else if (value instanceof JSONArray) {
            ((JSONArray)value).write(writer, indentFactor, indent);
         } else if (value instanceof Map) {
            Map<?, ?> map = (Map)value;
            (new JSONObject(map)).write(writer, indentFactor, indent);
         } else if (value instanceof Collection) {
            Collection<?> coll = (Collection)value;
            (new JSONArray(coll)).write(writer, indentFactor, indent);
         } else if (value.getClass().isArray()) {
            (new JSONArray(value)).write(writer, indentFactor, indent);
         } else {
            quote(value.toString(), writer);
         }
      } else {
         writer.write("null");
      }

      return writer;
   }

   static final void indent(Writer writer, int indent) throws IOException {
      for(int i = 0; i < indent; ++i) {
         writer.write(32);
      }

   }

   public Writer write(Writer writer, int indentFactor, int indent) throws JSONException {
      try {
         boolean needsComma = false;
         int length = this.length();
         writer.write(123);
         if (length == 1) {
            Entry<String, ?> entry = (Entry)this.entrySet().iterator().next();
            String key = (String)entry.getKey();
            writer.write(quote(key));
            writer.write(58);
            if (indentFactor > 0) {
               writer.write(32);
            }

            try {
               writeValue(writer, entry.getValue(), indentFactor, indent);
            } catch (Exception var12) {
               throw new JSONException("Unable to write JSONObject value for key: " + key, var12);
            }
         } else if (length != 0) {
            int newIndent = indent + indentFactor;

            for(Iterator var15 = this.entrySet().iterator(); var15.hasNext(); needsComma = true) {
               Entry<String, ?> entry = (Entry)var15.next();
               if (needsComma) {
                  writer.write(44);
               }

               if (indentFactor > 0) {
                  writer.write(10);
               }

               indent(writer, newIndent);
               String key = (String)entry.getKey();
               writer.write(quote(key));
               writer.write(58);
               if (indentFactor > 0) {
                  writer.write(32);
               }

               try {
                  writeValue(writer, entry.getValue(), indentFactor, newIndent);
               } catch (Exception var11) {
                  throw new JSONException("Unable to write JSONObject value for key: " + key, var11);
               }
            }

            if (indentFactor > 0) {
               writer.write(10);
            }

            indent(writer, indent);
         }

         writer.write(125);
         return writer;
      } catch (IOException var13) {
         throw new JSONException(var13);
      }
   }

   public Map<String, Object> toMap() {
      Map<String, Object> results = new LinkedHashMap();

      Entry entry;
      Object value;
      for(Iterator var2 = this.entrySet().iterator(); var2.hasNext(); results.put((String) entry.getKey(), value)) {
         entry = (Entry)var2.next();
         if (entry.getValue() != null && !NULL.equals(entry.getValue())) {
            if (entry.getValue() instanceof JSONObject) {
               value = ((JSONObject)entry.getValue()).toMap();
            } else if (entry.getValue() instanceof JSONArray) {
               value = ((JSONArray)entry.getValue()).toList();
            } else {
               value = entry.getValue();
            }
         } else {
            value = null;
         }
      }

      return results;
   }

   private static JSONException wrongValueFormatException(String key, String valueType, Throwable cause) {
      return new JSONException("JSONObject[" + quote(key) + "] is not a " + valueType + ".", cause);
   }

   private static JSONException wrongValueFormatException(String key, String valueType, Object value, Throwable cause) {
      return new JSONException("JSONObject[" + quote(key) + "] is not a " + valueType + " (" + value + ").", cause);
   }

   private static JSONException recursivelyDefinedObjectException(String key) {
      return new JSONException("JavaBean object contains recursively defined member variable of key " + quote(key));
   }

   private static final class Null {
      private Null() {
      }

      protected final Object clone() {
         return this;
      }

      public boolean equals(Object object) {
         return object == null || object == this;
      }

      public int hashCode() {
         return 0;
      }

      public String toString() {
         return "null";
      }

      // $FF: synthetic method
      Null(Object x0) {
         this();
      }
   }
}
class JSONArray implements Iterable<Object> {
   private final ArrayList<Object> myArrayList;

   public JSONArray() {
      this.myArrayList = new ArrayList<>();
   }

   public JSONArray(JSONTokener x) throws JSONException {
      this();
      if (x.nextClean() != '[') {
         throw x.syntaxError("A JSONArray text must start with '['");
      } else {
         char nextChar = x.nextClean();
         if (nextChar == 0) {
            throw x.syntaxError("Expected a ',' or ']'");
         } else if (nextChar != ']') {
            x.back();

            while(true) {
               if (x.nextClean() == ',') {
                  x.back();
                  this.myArrayList.add(JSONObject.NULL);
               } else {
                  x.back();
                  this.myArrayList.add(x.nextValue());
               }

               switch(x.nextClean()) {
                  case '\u0000':
                     throw x.syntaxError("Expected a ',' or ']'");
                  case ',':
                     nextChar = x.nextClean();
                     if (nextChar == 0) {
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
   }

   public JSONArray(String source) throws JSONException {
      this(new JSONTokener(source));
   }

   public JSONArray(Collection<?> collection) {
      if (collection == null) {
         this.myArrayList = new ArrayList();
      } else {
         this.myArrayList = new ArrayList(collection.size());
         this.addAll(collection, true);
      }

   }

   public JSONArray(Iterable<?> iter) {
      this();
      if (iter != null) {
         this.addAll(iter, true);
      }
   }

   public JSONArray(JSONArray array) {
      if (array == null) {
         this.myArrayList = new ArrayList();
      } else {
         this.myArrayList = new ArrayList(array.myArrayList);
      }

   }

   public JSONArray(Object array) throws JSONException {
      this();
      if (!array.getClass().isArray()) {
         throw new JSONException("JSONArray initial value should be a string or collection or array.");
      } else {
         this.addAll(array, true);
      }
   }

   public JSONArray(int initialCapacity) throws JSONException {
      if (initialCapacity < 0) {
         throw new JSONException("JSONArray initial capacity cannot be negative.");
      } else {
         this.myArrayList = new ArrayList(initialCapacity);
      }
   }

   public Iterator<Object> iterator() {
      return this.myArrayList.iterator();
   }

   public Object get(int index) throws JSONException {
      Object object = this.opt(index);
      if (object == null) {
         throw new JSONException("JSONArray[" + index + "] not found.");
      } else {
         return object;
      }
   }

   public boolean getBoolean(int index) throws JSONException {
      Object object = this.get(index);
      if (!object.equals(Boolean.FALSE) && (!(object instanceof String) || !((String)object).equalsIgnoreCase("false"))) {
         if (!object.equals(Boolean.TRUE) && (!(object instanceof String) || !((String)object).equalsIgnoreCase("true"))) {
            throw wrongValueFormatException(index, "boolean", (Throwable)null);
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public double getDouble(int index) throws JSONException {
      Object object = this.get(index);
      if (object instanceof Number) {
         return ((Number)object).doubleValue();
      } else {
         try {
            return Double.parseDouble(object.toString());
         } catch (Exception var4) {
            throw wrongValueFormatException(index, "double", var4);
         }
      }
   }

   public float getFloat(int index) throws JSONException {
      Object object = this.get(index);
      if (object instanceof Number) {
         return ((Number)object).floatValue();
      } else {
         try {
            return Float.parseFloat(object.toString());
         } catch (Exception var4) {
            throw wrongValueFormatException(index, "float", var4);
         }
      }
   }

   public Number getNumber(int index) throws JSONException {
      Object object = this.get(index);

      try {
         return object instanceof Number ? (Number)object : JSONObject.stringToNumber(object.toString());
      } catch (Exception var4) {
         throw wrongValueFormatException(index, "number", var4);
      }
   }

   public <E extends Enum<E>> E getEnum(Class<E> clazz, int index) throws JSONException {
      E val = this.optEnum(clazz, index);
      if (val == null) {
         throw wrongValueFormatException(index, "enum of type " + JSONObject.quote(clazz.getSimpleName()), (Throwable)null);
      } else {
         return val;
      }
   }

   public BigDecimal getBigDecimal(int index) throws JSONException {
      Object object = this.get(index);
      BigDecimal val = JSONObject.objectToBigDecimal(object, (BigDecimal)null);
      if (val == null) {
         throw wrongValueFormatException(index, "BigDecimal", object, (Throwable)null);
      } else {
         return val;
      }
   }

   public BigInteger getBigInteger(int index) throws JSONException {
      Object object = this.get(index);
      BigInteger val = JSONObject.objectToBigInteger(object, (BigInteger)null);
      if (val == null) {
         throw wrongValueFormatException(index, "BigInteger", object, (Throwable)null);
      } else {
         return val;
      }
   }

   public int getInt(int index) throws JSONException {
      Object object = this.get(index);
      if (object instanceof Number) {
         return ((Number)object).intValue();
      } else {
         try {
            return Integer.parseInt(object.toString());
         } catch (Exception var4) {
            throw wrongValueFormatException(index, "int", var4);
         }
      }
   }

   public JSONArray getJSONArray(int index) throws JSONException {
      Object object = this.get(index);
      if (object instanceof JSONArray) {
         return (JSONArray)object;
      } else {
         throw wrongValueFormatException(index, "JSONArray", (Throwable)null);
      }
   }

   public JSONObject getJSONObject(int index) throws JSONException {
      Object object = this.get(index);
      if (object instanceof JSONObject) {
         return (JSONObject)object;
      } else {
         throw wrongValueFormatException(index, "JSONObject", (Throwable)null);
      }
   }

   public long getLong(int index) throws JSONException {
      Object object = this.get(index);
      if (object instanceof Number) {
         return ((Number)object).longValue();
      } else {
         try {
            return Long.parseLong(object.toString());
         } catch (Exception var4) {
            throw wrongValueFormatException(index, "long", var4);
         }
      }
   }

   public String getString(int index) throws JSONException {
      Object object = this.get(index);
      if (object instanceof String) {
         return (String)object;
      } else {
         throw wrongValueFormatException(index, "String", (Throwable)null);
      }
   }

   public boolean isNull(int index) {
      return JSONObject.NULL.equals(this.opt(index));
   }

   public String join(String separator) throws JSONException {
      int len = this.length();
      if (len == 0) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder(JSONObject.valueToString(this.myArrayList.get(0)));

         for(int i = 1; i < len; ++i) {
            sb.append(separator).append(JSONObject.valueToString(this.myArrayList.get(i)));
         }

         return sb.toString();
      }
   }

   public int length() {
      return this.myArrayList.size();
   }

   public void clear() {
      this.myArrayList.clear();
   }

   public Object opt(int index) {
      return index >= 0 && index < this.length() ? this.myArrayList.get(index) : null;
   }

   public boolean optBoolean(int index) {
      return this.optBoolean(index, false);
   }

   public boolean optBoolean(int index, boolean defaultValue) {
      try {
         return this.getBoolean(index);
      } catch (Exception var4) {
         return defaultValue;
      }
   }

   public double optDouble(int index) {
      return this.optDouble(index, Double.NaN);
   }

   public double optDouble(int index, double defaultValue) {
      Number val = this.optNumber(index, (Number)null);
      if (val == null) {
         return defaultValue;
      } else {
         double doubleValue = val.doubleValue();
         return doubleValue;
      }
   }

   public float optFloat(int index) {
      return this.optFloat(index, Float.NaN);
   }

   public float optFloat(int index, float defaultValue) {
      Number val = this.optNumber(index, (Number)null);
      if (val == null) {
         return defaultValue;
      } else {
         float floatValue = val.floatValue();
         return floatValue;
      }
   }

   public int optInt(int index) {
      return this.optInt(index, 0);
   }

   public int optInt(int index, int defaultValue) {
      Number val = this.optNumber(index, (Number)null);
      return val == null ? defaultValue : val.intValue();
   }

   public <E extends Enum<E>> E optEnum(Class<E> clazz, int index) {
      return this.optEnum(clazz, index, null);
   }


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

   public BigInteger optBigInteger(int index, BigInteger defaultValue) {
      Object val = this.opt(index);
      return JSONObject.objectToBigInteger(val, defaultValue);
   }

   public BigDecimal optBigDecimal(int index, BigDecimal defaultValue) {
      Object val = this.opt(index);
      return JSONObject.objectToBigDecimal(val, defaultValue);
   }

   public JSONArray optJSONArray(int index) {
      Object o = this.opt(index);
      return o instanceof JSONArray ? (JSONArray)o : null;
   }

   public JSONObject optJSONObject(int index) {
      Object o = this.opt(index);
      return o instanceof JSONObject ? (JSONObject)o : null;
   }

   public long optLong(int index) {
      return this.optLong(index, 0L);
   }

   public long optLong(int index, long defaultValue) {
      Number val = this.optNumber(index, (Number)null);
      return val == null ? defaultValue : val.longValue();
   }

   public Number optNumber(int index) {
      return this.optNumber(index, (Number)null);
   }

   public Number optNumber(int index, Number defaultValue) {
      Object val = this.opt(index);
      if (JSONObject.NULL.equals(val)) {
         return defaultValue;
      } else if (val instanceof Number) {
         return (Number)val;
      } else if (val instanceof String) {
         try {
            return JSONObject.stringToNumber((String)val);
         } catch (Exception var5) {
            return defaultValue;
         }
      } else {
         return defaultValue;
      }
   }

   public String optString(int index) {
      return this.optString(index, "");
   }

   public String optString(int index, String defaultValue) {
      Object object = this.opt(index);
      return JSONObject.NULL.equals(object) ? defaultValue : object.toString();
   }

   public JSONArray put(boolean value) {
      return this.put((Object)(value ? Boolean.TRUE : Boolean.FALSE));
   }

   public JSONArray put(Collection<?> value) {
      return this.put((Object)(new JSONArray(value)));
   }

   public JSONArray put(double value) throws JSONException {
      return this.put((Object)value);
   }

   public JSONArray put(float value) throws JSONException {
      return this.put((Object)value);
   }

   public JSONArray put(int value) {
      return this.put((Object)value);
   }

   public JSONArray put(long value) {
      return this.put((Object)value);
   }

   public JSONArray put(Map<?, ?> value) {
      return this.put((Object)(new JSONObject(value)));
   }

   public JSONArray put(Object value) {
      JSONObject.testValidity(value);
      this.myArrayList.add(value);
      return this;
   }

   public JSONArray put(int index, boolean value) throws JSONException {
      return this.put(index, (Object)(value ? Boolean.TRUE : Boolean.FALSE));
   }

   public JSONArray put(int index, Collection<?> value) throws JSONException {
      return this.put(index, (Object)(new JSONArray(value)));
   }

   public JSONArray put(int index, double value) throws JSONException {
      return this.put(index, (Object)value);
   }

   public JSONArray put(int index, float value) throws JSONException {
      return this.put(index, (Object)value);
   }

   public JSONArray put(int index, int value) throws JSONException {
      return this.put(index, (Object)value);
   }

   public JSONArray put(int index, long value) throws JSONException {
      return this.put(index, (Object)value);
   }

   public JSONArray put(int index, Map<?, ?> value) throws JSONException {
      this.put(index, (Object)(new JSONObject(value)));
      return this;
   }

   public JSONArray put(int index, Object value) throws JSONException {
      if (index < 0) {
         throw new JSONException("JSONArray[" + index + "] not found.");
      } else if (index < this.length()) {
         JSONObject.testValidity(value);
         this.myArrayList.set(index, value);
         return this;
      } else if (index == this.length()) {
         return this.put(value);
      } else {
         this.myArrayList.ensureCapacity(index + 1);

         while(index != this.length()) {
            this.myArrayList.add(JSONObject.NULL);
         }

         return this.put(value);
      }
   }

   public JSONArray putAll(Collection<?> collection) {
      this.addAll(collection, false);
      return this;
   }

   public JSONArray putAll(Iterable<?> iter) {
      this.addAll(iter, false);
      return this;
   }

   public JSONArray putAll(JSONArray array) {
      this.myArrayList.addAll(array.myArrayList);
      return this;
   }

   public JSONArray putAll(Object array) throws JSONException {
      this.addAll(array, false);
      return this;
   }

   public Object query(String jsonPointer) {
      return this.query(new JSONPointer(jsonPointer));
   }

   public Object query(JSONPointer jsonPointer) {
      return jsonPointer.queryFrom(this);
   }

   public Object optQuery(String jsonPointer) {
      return this.optQuery(new JSONPointer(jsonPointer));
   }

   public Object optQuery(JSONPointer jsonPointer) {
      try {
         return jsonPointer.queryFrom(this);
      } catch (JSONPointerException var3) {
         return null;
      }
   }

   public Object remove(int index) {
      return index >= 0 && index < this.length() ? this.myArrayList.remove(index) : null;
   }

   public boolean similar(Object other) {
      if (!(other instanceof JSONArray)) {
         return false;
      } else {
         int len = this.length();
         if (len != ((JSONArray)other).length()) {
            return false;
         } else {
            for(int i = 0; i < len; ++i) {
               Object valueThis = this.myArrayList.get(i);
               Object valueOther = ((JSONArray)other).myArrayList.get(i);
               if (valueThis != valueOther) {
                  if (valueThis == null) {
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
            }

            return true;
         }
      }
   }

   public JSONObject toJSONObject(JSONArray names) throws JSONException {
      if (names != null && !names.isEmpty() && !this.isEmpty()) {
         JSONObject jo = new JSONObject(names.length());

         for(int i = 0; i < names.length(); ++i) {
            jo.put(names.getString(i), this.opt(i));
         }

         return jo;
      } else {
         return null;
      }
   }

   public String toString() {
      try {
         return this.toString(0);
      } catch (Exception var2) {
         return null;
      }
   }

   public String toString(int indentFactor) throws JSONException {
      StringWriter sw = new StringWriter();
      synchronized(sw.getBuffer()) {
         return this.write(sw, indentFactor, 0).toString();
      }
   }

   public Writer write(Writer writer) throws JSONException {
      return this.write(writer, 0, 0);
   }

   public Writer write(Writer writer, int indentFactor, int indent) throws JSONException {
      try {
         boolean needsComma = false;
         int length = this.length();
         writer.write(91);
         if (length == 1) {
            try {
               JSONObject.writeValue(writer, this.myArrayList.get(0), indentFactor, indent);
            } catch (Exception var10) {
               throw new JSONException("Unable to write JSONArray value at index: 0", var10);
            }
         } else if (length != 0) {
            int newIndent = indent + indentFactor;

            for(int i = 0; i < length; ++i) {
               if (needsComma) {
                  writer.write(44);
               }

               if (indentFactor > 0) {
                  writer.write(10);
               }

               JSONObject.indent(writer, newIndent);

               try {
                  JSONObject.writeValue(writer, this.myArrayList.get(i), indentFactor, newIndent);
               } catch (Exception var9) {
                  throw new JSONException("Unable to write JSONArray value at index: " + i, var9);
               }

               needsComma = true;
            }

            if (indentFactor > 0) {
               writer.write(10);
            }

            JSONObject.indent(writer, indent);
         }

         writer.write(93);
         return writer;
      } catch (IOException var11) {
         throw new JSONException(var11);
      }
   }

   public List<Object> toList() {
      List<Object> results = new ArrayList(this.myArrayList.size());
      Iterator var2 = this.myArrayList.iterator();

      while(true) {
         while(var2.hasNext()) {
            Object element = var2.next();
            if (element != null && !JSONObject.NULL.equals(element)) {
               if (element instanceof JSONArray) {
                  results.add(((JSONArray)element).toList());
               } else if (element instanceof JSONObject) {
                  results.add(((JSONObject)element).toMap());
               } else {
                  results.add(element);
               }
            } else {
               results.add((Object)null);
            }
         }

         return results;
      }
   }

   public boolean isEmpty() {
      return this.myArrayList.isEmpty();
   }

   private void addAll(Collection<?> collection, boolean wrap) {
      this.myArrayList.ensureCapacity(this.myArrayList.size() + collection.size());
      Iterator var3;
      Object o;
      if (wrap) {
         var3 = collection.iterator();

         while(var3.hasNext()) {
            o = var3.next();
            this.put(JSONObject.wrap(o));
         }
      } else {
         var3 = collection.iterator();

         while(var3.hasNext()) {
            o = var3.next();
            this.put(o);
         }
      }

   }

   private void addAll(Iterable<?> iter, boolean wrap) {
      Iterator var3;
      Object o;
      if (wrap) {
         var3 = iter.iterator();

         while(var3.hasNext()) {
            o = var3.next();
            this.put(JSONObject.wrap(o));
         }
      } else {
         var3 = iter.iterator();

         while(var3.hasNext()) {
            o = var3.next();
            this.put(o);
         }
      }

   }

   private void addAll(Object array, boolean wrap) throws JSONException {
      if (array.getClass().isArray()) {
         int length = Array.getLength(array);
         this.myArrayList.ensureCapacity(this.myArrayList.size() + length);
         int i;
         if (wrap) {
            for(i = 0; i < length; ++i) {
               this.put(JSONObject.wrap(Array.get(array, i)));
            }
         } else {
            for(i = 0; i < length; ++i) {
               this.put(Array.get(array, i));
            }
         }
      } else if (array instanceof JSONArray) {
         this.myArrayList.addAll(((JSONArray)array).myArrayList);
      } else if (array instanceof Collection) {
         this.addAll((Collection)array, wrap);
      } else {
         if (!(array instanceof Iterable)) {
            throw new JSONException("JSONArray initial value should be a string or collection or array.");
         }

         this.addAll((Iterable)array, wrap);
      }

   }

   private static JSONException wrongValueFormatException(int idx, String valueType, Throwable cause) {
      return new JSONException("JSONArray[" + idx + "] is not a " + valueType + ".", cause);
   }

   private static JSONException wrongValueFormatException(int idx, String valueType, Object value, Throwable cause) {
      return new JSONException("JSONArray[" + idx + "] is not a " + valueType + " (" + value + ").", cause);
   }
}

