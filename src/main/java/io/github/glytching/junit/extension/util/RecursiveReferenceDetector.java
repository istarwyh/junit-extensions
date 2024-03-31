package io.github.glytching.junit.extension.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * @author mac
 */
public class RecursiveReferenceDetector {

    /**
     * Checks for recursive references in an object using a breadth-first search approach.
     *
     * @param obj The object to check.
     * @return true if a recursive reference is found, false otherwise.
     */
    public static boolean hasRecursiveReference(Object obj) {
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        return Stream.of(obj).anyMatch(newObj -> checkRecursiveAndAdd(visited, newObj));
    }

    private static boolean checkRecursiveAndAdd(IdentityHashMap<Object, Boolean> visited, Object obj) {
        return Optional.ofNullable(obj)
                .map(Object::getClass)
                .filter(it -> !TypeUtils.isBuiltInType(it))
                .map(
                        it ->
                                canIterate(it)
                                        ? Arrays.stream(toInstanceArray(obj))
                                        : Stream.of(it.getDeclaredFields()).map(field -> getFieldValue(obj, field)))
                .map(
                        it ->
                                it.anyMatch(
                                        childObj -> {
                                            if (isNotRecursion(visited, childObj)) {
                                                return checkRecursiveAndAdd(visited, childObj);
                                            }
                                            return true;
                                        }))
                .orElse(false);
    }

    private static boolean canIterate(Class<?> it) {
        return it.isArray() || Collection.class.isAssignableFrom(it);
    }

    private static Object getFieldValue(Object obj, Field cur) {
        Predicate<Field> fieldPredicate = getFieldTypePredicate(TypeUtils::isBuiltInType)
                .and(combineModifierPredicates(
                        Modifier::isFinal,
                        Modifier::isAbstract,
                        Modifier::isNative,
                        Modifier::isTransient,
                        Modifier::isInterface
                ));
        return Optional.of(cur)
                .map(field -> ReflectionUtils.getFieldWithFilter(obj, field.getName(), fieldPredicate))
                .orElse(null);
    }

    private static Predicate<Field> getFieldTypePredicate(Predicate<Class<?>> classPredicate) {
        return field -> !classPredicate.test(field.getType());
    }

    @SafeVarargs
    private static Predicate<Field> combineModifierPredicates(Predicate<Integer>... predicates) {
        Predicate<Field> combinedPredicate = field -> true;
        for (Predicate<Integer> predicate : predicates) {
            combinedPredicate = combinedPredicate.and(field -> !predicate.test(field.getModifiers()));
        }
        return combinedPredicate;
    }

    /**
     * The `putIfAbsent` method returns the value previously associated with the key, or `null` if
     * there was no previous association. If it's the first access, the condition is true,which means
     * no recursive reference is found; if it's not the first access, the condition is false,
     * indicating that a recursive reference has been detected.
     */
    private static boolean isNotRecursion(IdentityHashMap<Object, Boolean> visited, Object childObj) {
        if(childObj == null){
            return true;
        }
        return visited.putIfAbsent(childObj, Boolean.TRUE) == null;
    }

    private static Object[] toInstanceArray(Object array) {
        array = array.getClass().isArray() ? array : ((Collection<?>) array).toArray();
        int length = Array.getLength(array);
        List<Object> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(Array.get(array, i));
        }
        return list.toArray();
    }
}

