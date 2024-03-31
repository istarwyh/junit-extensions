package io.github.glytching.junit.extension.json;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.fastjson2.TypeReference;
import io.github.glytching.junit.extension.json.annotation.JsonFileSource;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class JsonFileArgumentsProviderTest {

    @ParameterizedTest
    @CsvSource(value = {"1,2", "3,4"})
    void should_show_how_to_parse_multi_args_with_csv(Integer in, Integer out) {
        assertEquals(out, in + 1);
    }

    @JsonFileSource(resources = {"string_test_case.json"})
    void should_generate_test_case_json_lack_of_it(TestCase<String, String> testCase) {
        assertEquals("eOMtThyhVNLWUZNRcBaQKxI", testCase.getInput());
        assertEquals("yedUsFwdkelQbxeTeQOvaScfqIOOmaa", testCase.getOutput());
    }

    @JsonFileSource(resources = {"string_test_case.json"})
    void should_generate_test_case_json_given_ownClass(TestCase<String, String> testCase) {
        assertEquals("eOMtThyhVNLWUZNRcBaQKxI", testCase.getInput());
        assertEquals("yedUsFwdkelQbxeTeQOvaScfqIOOmaa", testCase.getOutput());
    }

    @JsonFileSource(resources = {"list_testCase.json", "list_testCase.json"})
    void should_parse_multi_List_with_Integer_type_test_case(
            TestCase<List<Integer>, List<Integer>> testCase) {
        assertEquals(0, testCase.getInput().size());
        assertEquals(0,  testCase.getOutput(new TypeReference<List<Integer>>() {}).size());
    }

    @JsonFileSource(resources = {"map_testCase.json"})
    void should_parse_Map_type_test_case(
            TestCase<Map<String, Integer>, Map<String, Integer>> testCase) {
        assertNull(testCase.getInput().get("key1"));
        assertNull(testCase.getOutput().get("key2"));
    }

    @JsonFileSource(resources = {"people_input.json"})
    void should_parse_People_input(People people) {
        assertEquals("lele", people.name);
    }

    @JsonFileSource(resources = {"RecursionClass_input.json"})
    void should_parse_recursionClass_input(RecursionClass recursionClass) {
        assertNotNull(recursionClass);
        assertNull(recursionClass.getRecursionClasses());
        assertNotNull(recursionClass.getPeople());
    }

    @Test
    void setNullIfRecursive() {
        RecursionClass recursionClass = new EasyRandom().nextObject(RecursionClass.class);
        JsonFileArgumentsProvider.setNullIfRecursive(recursionClass);
        assertNull(recursionClass.getRecursionClass());
        assertNull(recursionClass.getRecursionClasses());
        assertNotNull(recursionClass.getPeople());
    }

    @Data
    public static class RecursionClass {
        private People people;
        private RecursionClass recursionClass;
        private List<RecursionClass> recursionClasses;
    }

    public static class People {
        public final String id = "02";
        public final String name = "lele";
    }


}