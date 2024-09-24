package com.doug.projects.transitdelayservice.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.doug.projects.transitdelayservice.util.DynamoUtils.chunkList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamoUtilsTest {

    @Test
    void chunkListTest() {
        var testList = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<List<Integer>> expected = new ArrayList<>(20);
        List<Integer> subList = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            if (i % 2 == 1 && i != 1) {
                expected.add(subList);
                subList = new ArrayList<>();
            }
            subList.add(i);
        }
        expected.add(subList);
        var actual = chunkList(testList, 2);
        assertEquals(expected, actual);
    }
}