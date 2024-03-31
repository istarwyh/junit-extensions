package io.github.glytching.junit.extension.util;


import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author mac
 */
public class TypeUtils {

    private static final Set<Class<?>> BUILT_IN_TYPES = new HashSet<>(
            Arrays.asList(
                    Boolean.class, Byte.class, Character.class, Short.class,
                    Integer.class, Long.class, Float.class, Double.class,
                    boolean.class, byte.class, char.class, short.class,
                    int.class, long.class, float.class, double.class
            )
    );

    public static boolean isBuiltInType(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        return BUILT_IN_TYPES.contains(clazz);
    }

    public static  <T> boolean isJsonType(T o){
        return o instanceof JSONObject || o instanceof JSONArray;
    }
}
