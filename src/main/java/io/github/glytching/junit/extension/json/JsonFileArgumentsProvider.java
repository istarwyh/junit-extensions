package io.github.glytching.junit.extension.json;

import static java.util.Arrays.copyOf;
import static java.util.Arrays.stream;
import static java.util.concurrent.CompletableFuture.*;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import io.github.glytching.junit.extension.json.annotation.JsonFileSource;
import io.github.glytching.junit.extension.util.RecursiveReferenceDetector;
import io.github.glytching.junit.extension.util.ReflectionUtils;
import io.github.glytching.junit.extension.util.TypeUtils;
import lombok.SneakyThrows;
import org.jeasy.random.EasyRandom;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;
import org.junit.platform.commons.util.Preconditions;

/**
 * @author xiaohui
 */
public class JsonFileArgumentsProvider
        implements AnnotationConsumer<JsonFileSource>, ArgumentsProvider {

    public static final String ADDRESS_DASH = "/";
    private final BiFunction<Class<?>, String, InputStream> inputStreamProvider;
    private static final String RESOURCES_PATH_PREFIX = "src/test/resources";

    private static final EasyRandom RANDOM = new EasyRandom();

    private String[] resourceNames;

    private Method requiredTestMethod;

    private Class<?> requiredTestClass;

    private Class<?> testMethodParameterClazz;

    @SuppressWarnings("unused")
    JsonFileArgumentsProvider() {
        this(Class::getResourceAsStream);
    }

    JsonFileArgumentsProvider(BiFunction<Class<?>, String, InputStream> inputStreamProvider) {
        this.inputStreamProvider = inputStreamProvider;
    }

    private Object valueOfType(InputStream inputStream) {
        try (JSONReader reader = JSONReader.of(inputStream, Charset.defaultCharset())) {
            return reader.read(testMethodParameterClazz);
        }
    }

    @Override
    public void accept(JsonFileSource jsonFileSource) {
        resourceNames = jsonFileSource.resources();
    }

    private String[] getResourcePaths(String[] partResourceNames) {
        final String[] resourcePaths = copyOf(partResourceNames, partResourceNames.length);
        String packageName = requiredTestClass.getPackage().getName();
        for (int i = 0; i < resourcePaths.length; i++) {
            resourcePaths[i] =
                    packageName.replaceAll("\\.", ADDRESS_DASH) + ADDRESS_DASH + partResourceNames[i];
        }
        for (int i = 0; i < resourcePaths.length; i++) {
            if (!partResourceNames[i].startsWith(ADDRESS_DASH)) {
                resourcePaths[i] = ADDRESS_DASH + resourcePaths[i];
            }
        }
        return resourcePaths;
    }

    /** Only support one genericParameterType */
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        requiredTestMethod = context.getRequiredTestMethod();
        requiredTestClass = context.getRequiredTestClass();
        testMethodParameterClazz = initTestMethodParameterClazz();
        String[] resourcePaths = getResourcePaths(resourceNames);
        return stream(resourcePaths)
                .map(resource -> openInputStream(requiredTestClass, resource))
                .map(this::valueOfType)
                .map(Arguments::arguments);
    }

    /** Only support one genericParameterType */
    @SneakyThrows
    private Class<?> initTestMethodParameterClazz() {
        Type genericParameterType = requiredTestMethod.getGenericParameterTypes()[0];
        String testMethodParameterTypeName =
                genericParameterType instanceof ParameterizedType
                        ? ((ParameterizedType) genericParameterType).getRawType().getTypeName()
                        : genericParameterType.getTypeName();
        return Class.forName(testMethodParameterTypeName);
    }

    @SneakyThrows(Exception.class)
    private InputStream openInputStream(Class<?> testClass, String resource) {
        InputStream inputStream;
        inputStream = inputStreamProvider.apply(testClass, resource);
        if (inputStream == null) {
            CompletableFuture<String> resourceFuture = supplyAsync(() -> createTestResource(resource));
            // avoid too long to end this process by io error
            inputStream = inputStreamProvider.apply(testClass, resourceFuture.get(5, TimeUnit.SECONDS));
        }
        return Preconditions.notNull(
                inputStream,
                () ->
                        "*** Classpath resource does not exist: " + resource + ", and we have created it ***");
    }

    private String createTestResource(String resource) {
        createDirectoryForResource(resource);
        return createFileAndWriteTestResource(resource);
    }

    private String createFileAndWriteTestResource(String resource) {
        try {
            String moduleAbsoluteResource = RESOURCES_PATH_PREFIX + resource;
            File file = createFile(moduleAbsoluteResource);
            writeTestResource2File(moduleAbsoluteResource, file);
            return resource;
        } catch (IOException e) {
            System.out.println("Error creating file: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static File createFile(String moduleAbsoluteResource) throws IOException {
        File file = new File(moduleAbsoluteResource);
        if (!file.createNewFile()) {
            System.out.println("File already exists or could not be created: " + moduleAbsoluteResource);
        }
        return file;
    }

    private void writeTestResource2File(String moduleAbsoluteResource, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(moduleAbsoluteResource))) {
            Type[] genericParameterTypes = requiredTestMethod.getGenericParameterTypes();
            Type genericParameterType = genericParameterTypes[0];
            Object parameterInstance = getParameterInstance(genericParameterType);
            writer.write(JSON.toJSONString(parameterInstance));
            writer.flush();
            System.out.println(
                    moduleAbsoluteResource + (file.exists() ? "\ncreated successfully" : "on way..."));
        }
    }

    @SneakyThrows
    private Object getParameterInstance(Type genericParameterType) {
        Object object;
        if (genericParameterType instanceof ParameterizedType) {
            object = newParameterTypeInstance((ParameterizedType) genericParameterType);
        } else {
            object = newConcreteTypeInstance(Class.forName(genericParameterType.getTypeName()));
        }
        return object;
    }

    private static Object newConcreteTypeInstance(Class<?> testMethodParameterClazz1) {
        Object object = RANDOM.nextObject(testMethodParameterClazz1);
        setNullIfRecursive(object);
        return object;
    }

    /** 将对象中所有有递归引用的字段设置为null */
    public static void setNullIfRecursive(Object object) {
        if (object == null || TypeUtils.isBuiltInType(object.getClass())) {
            return;
        }
        boolean hasRecursiveReference = RecursiveReferenceDetector.hasRecursiveReference(object);
        if (hasRecursiveReference) {
            Stream.of(object.getClass().getDeclaredFields())
                    .forEach(field -> setNullIfRecursive(object, field));
        }
    }

    private static void setNullIfRecursive(Object object, Field it) {
        Object filedObj = ReflectionUtils.getField(object, it.getName());
        if (RecursiveReferenceDetector.hasRecursiveReference(filedObj)) {
            ReflectionUtils.setField(object, it, null);
        } else {
            setNullIfRecursive(filedObj);
        }
    }

    /**
     * Here it is assumed that {@code testMethodParameterClass} must have corresponding constructor
     * arguments.
     *
     * <p>Because for parameterized types of classes (such as generic classes), we cannot actually
     * find that field to assign a value to it, so as a compromise, it is agreed to use the
     * constructor instead.
     */
    @SneakyThrows
    @NotNull
    private Object newParameterTypeInstance(ParameterizedType genericParameterType) {
        Class<?> methodParameterClazz = Class.forName(genericParameterType.getRawType().getTypeName());
        Type[] genericParameterTypes = genericParameterType.getActualTypeArguments();
        Object newInstance;
        try {
            newInstance = newInstanceBySuitableConstructor(methodParameterClazz, genericParameterTypes);
        } catch (UnsupportedOperationException exception) {
            System.out.println(exception.toString());
            newInstance = RANDOM.nextObject(methodParameterClazz);
        }
        return newInstance;
    }

    @NotNull
    private Object newInstanceBySuitableConstructor(
            Class<?> methodParameterClazz, Type[] genericParameterTypes)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Constructor<?> suitableConstructor =
                findSuitableConstructor(methodParameterClazz, genericParameterTypes);
        Object[] args = stream(genericParameterTypes).map(this::getParameterInstance).toArray();
        return suitableConstructor.newInstance(args);
    }

    @SneakyThrows
    public static Constructor<?> findSuitableConstructor(Class<?> clazz, Type[] typeArguments) {
        return stream(clazz.getConstructors())
                .filter(it -> it.getParameterCount() == typeArguments.length)
                .findFirst()
                .orElseThrow(
                        () ->
                                new UnsupportedOperationException(
                                        "Lack of the first matched constructors for type argument: "
                                                + Arrays.toString(typeArguments)));
    }

    private static void createDirectoryForResource(String resource) {
        String fileDirPath = RESOURCES_PATH_PREFIX + resource.substring(0, resource.lastIndexOf("/"));
        if (!new File(fileDirPath).mkdirs()) {
            System.out.println("Directory already exists or could not be created: " + fileDirPath);
        }
    }
}
