package io.github.glytching.junit.extension.util;

import static io.github.glytching.junit.extension.util.UnsafeUtils.unsafe;

import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

/**
 * This class is changed from WhileBox in PowerMock. The class can set instance field,including
 * final field, not null or static field
 *
 * @author xiaohui
 */
public class ReflectionUtils {

  /**
   * If you want to set static field, you should call {@link ReflectionUtils#setField(Object, Field,
   * Object)}
   *
   * @param modifiedObj modifiedObj
   * @param fieldName including final field, not null or static field
   * @param value value
   */
  @SneakyThrows(NoSuchFieldException.class)
  public static void setField(Object modifiedObj, String fieldName, Object value) {
    Predicate<Field> fieldPredicate = field -> hasFieldProperModifier(modifiedObj, field);
    Field foundField =
        findFieldInHierarchy(modifiedObj, fieldName, fieldPredicate)
            .orElseThrow(
                () ->
                    new NoSuchFieldException(
                        String.format(
                            "No %s field named \"%s\" could be found in the \"%s\" class hierarchy",
                            isClass(modifiedObj) ? "static" : "instance",
                            fieldName,
                            getClassOf(modifiedObj).getName())));
    setField(modifiedObj, foundField, value);
  }

  @Nullable
  public static <T> T getField(Object object, String fieldName) {
    return getFieldWithFilter(object, fieldName, anyField -> true);
  }

  @Nullable
  @SneakyThrows({IllegalAccessException.class})
  public static <T> T getFieldWithFilter(
      Object object, String fieldName, Predicate<Field> fieldPredicate) {
    Field foundField = findFieldInHierarchy(object, fieldName, fieldPredicate).orElse(null);
    if (foundField == null) {
      return null;
    }
    return (T) foundField.get(object);
  }

  public static Optional<Field> findFieldInHierarchy(
      Object modifiedObj, String fieldName, @NotNull Predicate<Field> fieldPredicate) {
    if (modifiedObj == null) {
      throw new IllegalArgumentException("The modifiedObj containing the field cannot be null!");
    }
    Class<?> startClass = getClassOf(modifiedObj);

    Optional<Field> optionalField =
        findFieldByUniqueName(fieldName, startClass).filter(fieldPredicate);
    optionalField.ifPresent(it -> it.setAccessible(true));
    return optionalField;
  }

  public static Optional<Field> findFieldByUniqueName(String fieldName, Class<?> startClass) {
    FieldSearchCriteria criteria =
        new FieldSearchCriteria(startClass, field -> field.getName().equals(fieldName), fieldName);
    return findField(criteria);
  }

  private static Optional<Field> findField(FieldSearchCriteria criteria) {
    Field foundField = null;
    Class<?> currentClass = criteria.getStartClass();
    while (currentClass != null) {
      Field[] declaredFields = currentClass.getDeclaredFields();
      for (val field : declaredFields) {
        if (criteria.getMatcher().apply(field)) {
          if (foundField != null) {
            throw new IllegalStateException(
                "Two or more fields matching " + criteria.getErrorMessage() + ".");
          }
          foundField = field;
        }
      }
      if (foundField != null) {
        break;
      }
      currentClass = currentClass.getSuperclass();
    }
    return Optional.ofNullable(foundField);
  }

  @Getter
  @RequiredArgsConstructor
  public static class FieldSearchCriteria {
    private final Class<?> startClass;

    private final Function<Field, Boolean> matcher;

    private final String errorMessage;
  }

  private static boolean hasFieldProperModifier(Object object, Field field) {
    if (isClass(object)) {
      return Modifier.isStatic(field.getModifiers());
    } else {
      return !Modifier.isStatic(field.getModifiers());
    }
  }

  private static Class<?> getClassOf(@NotNull Object object) {
    Class<?> type;
    if (isClass(object)) {
      type = (Class<?>) object;
    } else {
      type = object.getClass();
    }
    return type;
  }

  private static boolean isClass(Object object) {
    return object instanceof Class<?>;
  }

  public static void setField(Object object, Field foundField, Object value) {
    boolean isStatic = isModifier(foundField, Modifier.STATIC);
    Unsafe unsafe = unsafe();
    if (isStatic) {
      setStaticFieldUsingUnsafe(foundField, value);
    } else {
      setFieldUsingUnsafe(object, foundField, unsafe.objectFieldOffset(foundField), value);
    }
  }

  private static void setStaticFieldUsingUnsafe(Field field, Object value) {
    Object base = unsafe().staticFieldBase(field);
    long offset = unsafe().staticFieldOffset(field);
    setFieldUsingUnsafe(base, field, offset, value);
  }

  /**
   * judge whether modifier the field belongs to
   *
   * @param field field
   * @param modifier {@link Modifier#STATIC }.etc
   * @return if modifier the field belongs to
   */
  private static boolean isModifier(Field field, int modifier) {
    return (field.getModifiers() & modifier) == modifier;
  }

  @SneakyThrows
  @SuppressWarnings("all")
  private static void setFieldUsingUnsafe(Object base, Field field, long offset, Object newValue) {
    field.setAccessible(true);
    boolean isFinal = isModifier(field, Modifier.FINAL);
    if (isFinal) {
      AccessController.doPrivileged(
          (PrivilegedAction<Object>)
              () -> {
                setFieldUsingUnsafe(base, field.getType(), offset, newValue);
                return null;
              });
    } else {
      field.set(base, newValue);
    }
  }

  private static void setFieldUsingUnsafe(
      Object base, Class<?> type, long offset, Object newValue) {
    if (type == Integer.TYPE) {
      unsafe().putInt(base, offset, (Integer) newValue);
    } else if (type == Short.TYPE) {
      unsafe().putShort(base, offset, (Short) newValue);
    } else if (type == Long.TYPE) {
      unsafe().putLong(base, offset, (Long) newValue);
    } else if (type == Byte.TYPE) {
      unsafe().putByte(base, offset, (Byte) newValue);
    } else if (type == Boolean.TYPE) {
      unsafe().putBoolean(base, offset, (Boolean) newValue);
    } else if (type == Float.TYPE) {
      unsafe().putFloat(base, offset, (Float) newValue);
    } else if (type == Double.TYPE) {
      unsafe().putDouble(base, offset, (Double) newValue);
    } else if (type == Character.TYPE) {
      unsafe().putChar(base, offset, (Character) newValue);
    } else {
      unsafe().putObject(base, offset, newValue);
    }
  }
}
