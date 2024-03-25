package com.doug.projects.transitdelayservice.util;

import java.util.ArrayList;
import java.util.List;

public class DynamoUtils {
    /**
     * Creates a List<List<T>> with each sublist having a max size of the size parameter.
     */
    public static <T> List<List<T>> chunkList(List<T> list, int size) {
        if (list.size() <= size) {
            return List.of(list);
        }
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(new ArrayList<>(list.subList(i, Math.min(i + size, list.size()))));
        }
        return result;
    }
}
