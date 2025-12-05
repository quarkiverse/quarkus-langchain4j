package io.quarkiverse.langchain4j.evaluation.junit5;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;

/**
 * Static registry for evaluation reports.
 */
public class ReportRegistry {

    private static final Map<String, EvaluationReport<?>> REPORTS = new ConcurrentHashMap<>();

    /**
     * Registers a report for a specific test class and field.
     *
     * @param testClassName the name of the test class
     * @param fieldName the name of the field
     * @param report the report to register
     */
    public static void registerReport(String testClassName, String fieldName, EvaluationReport<?> report) {
        String key = testClassName + "." + fieldName;
        REPORTS.put(key, report);
    }

    /**
     * Gets all registered reports for a specific test class.
     *
     * @param testClassName the name of the test class
     * @return map of field names to reports
     */
    public static Map<String, EvaluationReport<?>> getReportsForClass(String testClassName) {
        Map<String, EvaluationReport<?>> classReports = new ConcurrentHashMap<>();
        String prefix = testClassName + ".";

        for (Map.Entry<String, EvaluationReport<?>> entry : REPORTS.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String fieldName = entry.getKey().substring(prefix.length());
                classReports.put(fieldName, entry.getValue());
            }
        }

        return classReports;
    }

    /**
     * Clears all registered reports.
     * Useful for testing.
     */
    public static void clear() {
        REPORTS.clear();
    }

    /**
     * Gets all registered reports across all test classes.
     *
     * @return map of "ClassName.fieldName" to reports
     */
    public static Map<String, EvaluationReport<?>> getAllReports() {
        return new ConcurrentHashMap<>(REPORTS);
    }
}
