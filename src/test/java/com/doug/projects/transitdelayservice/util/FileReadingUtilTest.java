package com.doug.projects.transitdelayservice.util;

import org.junit.jupiter.api.Test;

import java.io.File;

import static com.doug.projects.transitdelayservice.util.FileReadingUtil.fileSorted;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileReadingUtilTest {
    @Test
    void fileSorted_testFail() {
        File failedFile = new File("src/test/resources/testFileReading_fail.csv");
        var actual = fileSorted(failedFile, IdTest.class, id -> id.getId().toString());
        assertFalse(actual);
    }

    @Test
    void fileSorted_testSuccess() {
        File failedFile = new File("src/test/resources/testFileReading_true.csv");
        var actual = fileSorted(failedFile, IdTest.class, id -> id.getId().toString());
        assertTrue(actual);
    }
}