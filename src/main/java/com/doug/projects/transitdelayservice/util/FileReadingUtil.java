package com.doug.projects.transitdelayservice.util;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;

@Slf4j
public class FileReadingUtil {
    /**
     * For every item in the CSV file provided, extract with idMappingFunction.
     * If every id in that file is sequentially grouped, return true. Otherwise, return false.
     * <br /> This is particularly useful for checking whether a file is grouped by tripId / shapeId for reading optimizations
     *
     * @param file              the CSV file to read
     * @param clazz             the class, used by the mapperFunction and the CSV attr reader
     * @param idMappingFunction used to generate the id, which we check for grouping.
     * @param <T>
     * @return
     */
    public static <T> boolean fileSorted(File file, Class<T> clazz, Function<T, String> idMappingFunction) {
        Set<String> seenTripIds = new HashSet<>();
        try (var itr = getAttrItr(file, clazz)) {
            String lastSeenTripId = null;
            while (itr.hasNext()) {
                T nextValue = itr.nextValue();
                String currId = idMappingFunction.apply(nextValue);
                if (lastSeenTripId == null) {
                    lastSeenTripId = currId;
                }
                if (!lastSeenTripId.equals(currId)) {
                    if (!seenTripIds.contains(lastSeenTripId)) {
                        //if we haven't seen this before
                        //note that we have now seen it, and we expect no more duplicates
                        seenTripIds.add(lastSeenTripId);
                        lastSeenTripId = currId;
                    } else {
                        return false;
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to read data from file", e);
            return false;
        }
        return true;
    }

    /**
     * Gets a GTFS related attribute iterator over file, for class clazz
     *
     * @param file  the file to read from
     * @param clazz the class to read the schema with
     * @param <T>   the type of clazz
     * @return a mappingIterator over type clazz/T
     * @throws IOException if an error reading a file occurs
     */
    public static <T> MappingIterator<T> getAttrItr(File file, Class<T> clazz) throws IOException {
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        return csvMapper
                .readerWithSchemaFor(clazz)
                .with(schema)
                .with(CsvParser.Feature.TRIM_SPACES)
                .readValues(file);
    }

    public static <T> @NotNull Flux<T> getFluxFromIterator(Iterator<T> attr) {
        Iterable<T> iterable = () -> attr;
        return Flux.fromIterable(iterable);
    }
}
