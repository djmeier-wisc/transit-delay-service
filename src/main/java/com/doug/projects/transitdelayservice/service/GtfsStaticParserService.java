package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.AgencyStaticStatus;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.entity.dynamodb.SequencedData;
import com.doug.projects.transitdelayservice.entity.gtfs.csv.*;
import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import com.doug.projects.transitdelayservice.util.FileReadingUtil;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.zeroturnaround.zip.ZipUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData.TYPE.*;
import static com.doug.projects.transitdelayservice.util.DynamoUtils.chunkList;
import static com.doug.projects.transitdelayservice.util.FileReadingUtil.fileSorted;
import static com.doug.projects.transitdelayservice.util.FileReadingUtil.getAttrItr;
import static com.doug.projects.transitdelayservice.util.TransitDateUtil.replaceGreaterThan24Hr;
import static com.doug.projects.transitdelayservice.util.UrlRedirectUtil.handleRedirect;
import static io.micrometer.common.util.StringUtils.isNotEmpty;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsStaticParserService {
    private static final DateTimeFormatter staticScheduleTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final GtfsStaticRepository gtfsStaticRepository;
    private final AgencyFeedRepository agencyFeedRepository;

    private static GtfsStaticData convert(RoutesAttributes routesAttributes, String agencyId, Map<String, String> routeIdToNameMap) {
        String routeName = routesAttributes.getRouteShortName();
        if (StringUtils.isBlank(routeName)) {
            routeName = routesAttributes.getRouteLongName();
        }
        routeIdToNameMap.put(routesAttributes.getRouteId(), routeName);
        GtfsStaticData staticData = new GtfsStaticData();
        staticData.setAgencyType(agencyId, ROUTE);
        staticData.setId(routesAttributes.getRouteId());
        staticData.setRouteName(routeName);
        staticData.setRouteColor('#' + routesAttributes.getRouteColor());
        staticData.setRouteSortOrder(routesAttributes.getRouteSortOrder());
        return staticData;
    }

    private static GtfsStaticData convert(StopAttributes stopAttributes, String agencyId, Map<String, String> stopIdToNameMap) {
        stopIdToNameMap.put(stopAttributes.getStopId(), stopAttributes.getStopName());
        GtfsStaticData staticData = new GtfsStaticData();
        staticData.setAgencyType(agencyId, STOP);
        staticData.setId(String.valueOf(stopAttributes.getStopId()));
        staticData.setStopName(stopAttributes.getStopName());
        staticData.setStopLat(stopAttributes.getStopLat());
        staticData.setStopLon(stopAttributes.getStopLon());
        return staticData;
    }

    private static GtfsStaticData convert(StopTimeAttributes stopTimeAttributes,
                                          String agencyId,
                                          Map<String, String> tripIdToNameMap,
                                          Map<String, String> stopIdToNameMap,
                                          Map<String, String> tripIdToShapeIdMap) {
        GtfsStaticData staticData = new GtfsStaticData();
        staticData.setAgencyType(agencyId, STOPTIME);
        staticData.setId(stopTimeAttributes.getTripId());
        staticData.setStopName(stopIdToNameMap.get(stopTimeAttributes.getStopId()));
        staticData.setRouteName(tripIdToNameMap.get(stopTimeAttributes.getTripId()));
        staticData.setShapeId(tripIdToShapeIdMap.get(stopTimeAttributes.getTripId()));
        return staticData;
    }

    /**
     * Gets the associated type with this filename by checking whether the filename ENDS with type.getFileName.
     */
    public static GtfsStaticData.TYPE getTypeEndsWith(String fileName) {
        for (GtfsStaticData.TYPE type : GtfsStaticData.TYPE.values()) {
            if (StringUtils.endsWith(fileName, type.getFileName())) {
                return type;
            }
        }
        return null;
    }

    /**
     * In some GTFS feeds, the schedule is missing departureTimes.
     * <br /><br />
     * For example: 5:00,null,null,5:03,null,null,5:06 would be replaced with 5:00,5:01,5:02,5:03,5:04,5:05,5:06
     *
     * @param gtfsList the gtfsList to be modified by this method
     */
    public static void interpolateDelay(List<SequencedData> gtfsList) {
        gtfsList.sort(Comparator.comparing(SequencedData::getSequenceNo, Comparator.nullsLast(Comparator.naturalOrder())));
        int startDepartureIndex = 0;
        int startArrivalIndex = 0;
        for (int i = 1; i < gtfsList.size(); i++) {
            SequencedData sameTripStop = gtfsList.get(i);
            if (isNotEmpty(sameTripStop.getDepartureTime())) {
                try {
                    LocalTime startTime = LocalTime.parse(replaceGreaterThan24Hr(gtfsList.get(startDepartureIndex)
                            .getDepartureTime()));
                    LocalTime endTime = LocalTime.parse(replaceGreaterThan24Hr(sameTripStop.getDepartureTime()));
                    Duration difference = Duration.between(startTime, endTime).dividedBy(i - startDepartureIndex);
                    for (int j = startDepartureIndex + 1; j < i; j++) {
                        SequencedData gtfsStaticData = gtfsList.get(j);
                        gtfsStaticData.setDepartureTime(startTime.plus(difference.multipliedBy(j - startDepartureIndex))
                                .format(staticScheduleTimeFormatter));
                    }
                } catch (DateTimeParseException ignored) {
                } finally {
                    startDepartureIndex = i;
                }
            }
            if (isNotEmpty(sameTripStop.getArrivalTime())) {
                try {
                    LocalTime startTime = LocalTime.parse(replaceGreaterThan24Hr(gtfsList.get(startArrivalIndex)
                            .getArrivalTime()));
                    LocalTime endTime = LocalTime.parse(replaceGreaterThan24Hr(sameTripStop.getArrivalTime()));
                    Duration difference = Duration.between(startTime, endTime)
                            .dividedBy(i - startArrivalIndex);
                    for (int j = startArrivalIndex + 1; j < i; j++) {
                        SequencedData gtfsStaticData = gtfsList.get(j);
                        gtfsStaticData.setArrivalTime(startTime.plus(difference.multipliedBy(j - startArrivalIndex))
                                .format(staticScheduleTimeFormatter));
                    }
                } catch (DateTimeParseException ignored) {
                } finally {
                    startArrivalIndex = i;
                }
            }
        }
    }

    private static void waitForRequest(FluxSink<List<Integer>> fluxSink) {
        while (fluxSink.requestedFromDownstream() == 0) {
            try {
                Thread.sleep(100); // Sleep for 100ms until more requests arrive
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean isMissingArrivalsOrDepartures(SequencedData sequencedData) {
        return sequencedData.getDepartureTime() == null || sequencedData.getArrivalTime() == null;
    }


    private static @NotNull Map<String, GtfsStaticData> mapTripIdToGtfsData(String agencyId,
                                                                            File file,
                                                                            Map<String, String> tripIdToNameMap,
                                                                            Map<String, String> stopIdToNameMap,
                                                                            Map<String, String> tripIdToShapeIdMap) {
        Map<String, GtfsStaticData> gtfsList = new HashMap<>();
        try (MappingIterator<StopTimeAttributes> attributesIterator = getAttrItr(file, StopTimeAttributes.class)) {
            while (attributesIterator.hasNext()) {
                var a = attributesIterator.next();
                gtfsList.putIfAbsent(a.getTripId(), convert(a, agencyId, tripIdToNameMap, stopIdToNameMap, tripIdToShapeIdMap));
                var gtfsData = gtfsList.get(a.getTripId());
                var stopTimes = gtfsData.getSequencedData();
                if (stopTimes == null) {
                    stopTimes = new ArrayList<>();
                    gtfsData.setSequencedData(stopTimes);
                }
            }
        } catch (IOException | DateTimeParseException e) {
            log.error("Failed to read file: {}", file.getName(), e);
        }
        return gtfsList;
    }

    private void readAgencyTimezoneAndSaveToDb(String agencyId, File file) {
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (MappingIterator<AgencyAttributes> attributesIterator = csvMapper
                .readerWithSchemaFor(AgencyAttributes.class)
                .with(schema)
                .with(CsvParser.Feature.TRIM_SPACES)
                .readValues(new BufferedReader(new FileReader(file)))) {
            if (attributesIterator.hasNext()) { //we presume all sequencedData in the files parsed are
                String timezone = attributesIterator.next().getAgencyTimezone();
                if (timezone == null) {
                    log.error("UNABLE TO FIND TZ FOR ID: {}", agencyId);
                    return;
                }
                agencyFeedRepository.getAgencyFeedById(agencyId, false)
                        .subscribe(f -> {
                            f.setTimezone(timezone);
                            agencyFeedRepository.writeAgencyFeed(f);
                            log.info("Completed TZ write for id: {}", agencyId);
                        });
            }
        } catch (IOException e) {
            log.error("Failed to read agency.csv file: {}", file.getName(), e);
        }
    }

    public void writeGtfsStaticDataToDynamoFromDiskSync(AgencyFeed feed) {
        var agencyId = feed.getId();
        if (agencyId == null) {
            log.error("Agency id null!");
        }
        Map<String, String> routeIdToNameMap = new HashMap<>();
        Map<String, String> tripIdToNameMap = new HashMap<>();
        Map<String, String> stopIdToNameMap = new HashMap<>();
        Map<String, String> tripIdToShapeIdMap = new HashMap<>();
        for (GtfsStaticData.TYPE value : GtfsStaticData.TYPE.values()) {
            File file = new File("files" + File.separator + agencyId + File.separator + value.getFileName());
            try {
                switch (value) {
                    case AGENCY -> readAgencyTimezoneAndSaveToDb(agencyId, file);
                    case ROUTE ->
                            readGtfsAndSaveToDb(agencyId, file, RoutesAttributes.class, routeIdToNameMap, tripIdToNameMap, stopIdToNameMap, tripIdToShapeIdMap);
                    case TRIP ->
                            readGtfsAndSaveToDb(agencyId, file, TripAttributes.class, routeIdToNameMap, tripIdToNameMap, stopIdToNameMap, tripIdToShapeIdMap);
                    case STOPTIME ->
                            readStopTimeAndSaveToDb(agencyId, file, tripIdToNameMap, stopIdToNameMap, tripIdToShapeIdMap);
                    case STOP ->
                            readGtfsAndSaveToDb(agencyId, file, StopAttributes.class, routeIdToNameMap, tripIdToNameMap, stopIdToNameMap, tripIdToShapeIdMap);
                    case SHAPE -> readShapeAndSaveToDb(agencyId, file);
                    default -> log.error("No case for type: {}", value);
                }
                file.delete();
                log.info("{} read finished for id: {}", value.getName(), agencyId);
            } catch (Exception e) {
                log.error("Exception while reading static files from disk for id: {}", feed.getId(), e);
            } finally {
                file.deleteOnExit();
            }
        }
        new File("files" + File.separator + agencyId).delete();
        log.info("All file read finished for id: {}", agencyId);
    }

    private void readShapeAndSaveToDb(String agencyId, File file) {
        Map<String, GtfsStaticData> gtfsList = new HashMap<>();
        try (MappingIterator<ShapeAttributes> attributesIterator = getAttrItr(file, ShapeAttributes.class)) {
            while (attributesIterator.hasNext()) {
                var a = attributesIterator.next();
                gtfsList.putIfAbsent(a.getShapeId(), convert(a, agencyId));
                var gtfsData = gtfsList.get(a.getShapeId());
                var shapes = gtfsData.getSequencedData();
                if (shapes == null) {
                    shapes = new ArrayList<>();
                    gtfsData.setSequencedData(shapes);
                }
            }
        } catch (IOException | DateTimeParseException e) {
            log.error("Failed to read file: {}", file.getName(), e);
        }
        for (List<GtfsStaticData> chunkedList : chunkList(new ArrayList<>(gtfsList.values()), 500)) {
            chunkedList.parallelStream().forEach(gtfsData -> {
                try (MappingIterator<ShapeAttributes> attributesIterator = getAttrItr(file, ShapeAttributes.class)) {
                    while (attributesIterator.hasNext()) {
                        var a = attributesIterator.next();
                        if (!Objects.equals(a.getShapeId(), gtfsData.getId())) {
                            continue;
                        }
                        var shapes = gtfsData.getSequencedData();
                        var shape = SequencedData.builder()
                                .sequenceNo(a.getShapePtSequence())
                                .shapeLat(a.getShapePtLat())
                                .shapeLon(a.getShapePtLon())
                                .build();
                        shapes.add(shape);
                    }
                } catch (IOException | DateTimeParseException e) {
                    log.error("Failed to read file: {}", file.getName(), e);
                }
                interpolateListIfNeeded(gtfsData.getSequencedData());
            });
            gtfsStaticRepository.saveAll(chunkedList);
        }
    }

    /**
     * Stop times have a special format in the DB that reduces the number of records.
     * Instead of one record per stopTime entry (sometimes 300 million lines), we have one record per trip.
     * Inside that, we have data per stopTime. This also means that we don't need to store any TRIP data.
     *
     * @param agencyId           the agencyId to save to
     * @param file               the file to read from, with MappingItr
     * @param tripIdToNameMap    used to populate the routeName field in stopTimes
     * @param stopIdToNameMap    used to populate the stopName field in stopTimes
     * @param tripIdToShapeIdMap used to populate the shapeId field in stopTimes
     */
    private void readStopTimeAndSaveToDb(String agencyId,
                                         File file,
                                         Map<String, String> tripIdToNameMap,
                                         Map<String, String> stopIdToNameMap,
                                         Map<String, String> tripIdToShapeIdMap) {

        if (fileSorted(file, StopTimeAttributes.class, StopTimeAttributes::getTripId)) {
            readSortedFileAndPerformIncrementalDbWrite(agencyId, file, tripIdToNameMap, stopIdToNameMap, tripIdToShapeIdMap);
        } else {
            readUnsortedFileAndPerformWholeDbWrite(agencyId, file, tripIdToNameMap, stopIdToNameMap, tripIdToShapeIdMap);
        }
    }

    /**
     * Reads the entire file's contents into memory, and then write them all to the db.
     *
     * @param agencyId
     * @param file
     * @param tripIdToNameMap
     * @param stopIdToNameMap
     * @param tripIdToShapeIdMap
     */
    private void readUnsortedFileAndPerformWholeDbWrite(String agencyId, File file, Map<String, String> tripIdToNameMap, Map<String, String> stopIdToNameMap, Map<String, String> tripIdToShapeIdMap) {
        Map<String, GtfsStaticData> gtfsList = new HashMap<>();
        try (MappingIterator<StopTimeAttributes> attributesIterator = getAttrItr(file, StopTimeAttributes.class)) {
            while (attributesIterator.hasNext()) {
                var a = attributesIterator.next();
                gtfsList.putIfAbsent(a.getTripId(), convert(a, agencyId, tripIdToNameMap, stopIdToNameMap, tripIdToShapeIdMap));
                var gtfsData = gtfsList.get(a.getTripId());
                var stopTimes = gtfsData.getSequencedData();
                if (stopTimes == null) {
                    stopTimes = new ArrayList<>();
                    gtfsData.setSequencedData(stopTimes);
                }
                var stopTime = SequencedData.builder()
                        .arrivalTime(a.getArrivalTime())
                        .departureTime(a.getDepartureTime())
                        .stopId(a.getStopId())
                        .stopName(stopIdToNameMap.get(a.getStopId()))
                        .sequenceNo(a.getStopSequence())
                        .build();
                stopTimes.add(stopTime);
            }
        } catch (IOException | DateTimeParseException e) {
            log.error("Failed to read file: {}", file.getName(), e);
        }
        gtfsList.values().stream().map(GtfsStaticData::getSequencedData).forEach(this::interpolateListIfNeeded);
        gtfsStaticRepository.saveAll(new ArrayList<>(gtfsList.values()));
    }

    /**
     * Read 500 tripIds at once, then write to db, continue to EOF. Assumes that the file is grouped by tripId
     *
     * @param agencyId
     * @param file
     * @param tripIdToNameMap
     * @param stopIdToNameMap
     * @param tripIdToShapeIdMap
     */
    private void readSortedFileAndPerformIncrementalDbWrite(String agencyId, File file, Map<String, String> tripIdToNameMap, Map<String, String> stopIdToNameMap, Map<String, String> tripIdToShapeIdMap) {
        Map<String, GtfsStaticData> gtfsList = new HashMap<>();
        try (MappingIterator<StopTimeAttributes> itr = getAttrItr(file, StopTimeAttributes.class)) {
            while (itr.hasNext()) {
                var a = itr.next();
                boolean isNewTripId = !gtfsList.containsKey(a.getTripId());
                if (isNewTripId && gtfsList.size() > 25) {
                    //because we know that this data is sorted / grouped by tripId
                    //we can incrementally write this data, without needing to put the entire thing into memory
                    gtfsList.values().forEach(g -> interpolateListIfNeeded(g.getSequencedData()));
                    gtfsStaticRepository.saveAll(new ArrayList<>(gtfsList.values()));
                    gtfsList.clear();
                }
                gtfsList.putIfAbsent(a.getTripId(), convert(a, agencyId, tripIdToNameMap, stopIdToNameMap, tripIdToShapeIdMap));
                var gtfsData = gtfsList.get(a.getTripId());
                var stopTimes = gtfsData.getSequencedData();
                if (stopTimes == null) {
                    stopTimes = new ArrayList<>();
                    gtfsData.setSequencedData(stopTimes);
                }
                var stopTime = SequencedData.builder()
                        .arrivalTime(a.getArrivalTime())
                        .departureTime(a.getDepartureTime())
                        .stopId(a.getStopId())
                        .stopName(stopIdToNameMap.get(a.getStopId()))
                        .sequenceNo(a.getStopSequence())
                        .build();
                stopTimes.add(stopTime);
            }
        } catch (IOException | DateTimeParseException e) {
            log.error("Failed to read file: {}", file.getName(), e);
        }
    }


    /**
     * Writes gtfs static data as .csv files to disk under /files.
     *
     * @param feed           the feed to download gtfs static data from
     * @param timeoutSeconds the number of seconds until timeout. In the event of failure,
     * @return AgencyStaticStatus w/ success false if timeout or IO exception, success of true otherwise.
     * @implNote this may need to be forcibly timed out. some providers never return data, leading to thread starvation
     */
    public CompletableFuture<AgencyStaticStatus> writeGtfsRoutesToDiskAsync(AgencyFeed feed, int timeoutSeconds) {
        var timeOutErr = AgencyStaticStatus.builder().success(false).message("Timeout").feed(feed).build();
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!writeGtfsRoutesToDiskSync(feed.getStaticUrl(), feed.getId())) {
                    return AgencyStaticStatus.builder().message("Failed to write to disk").success(false).feed(feed).build();
                }
            } catch (IOException e) {
                return AgencyStaticStatus.builder().message(e.getMessage()).success(false).feed(feed).build();
            }
            return AgencyStaticStatus.builder().message("Success-ish :)").success(true).feed(feed).build();
        }).completeOnTimeout(timeOutErr, timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * Generic converter to read data from a single file (routes.txt, trips.txt, etc.) from file and write to dynamo.
     *
     * @param <T>                an Attributes class used to map against .csv file passed in. Should be
     * @param agencyId           the agencyId to write to dynamo from
     * @param file               the file to read from
     * @param clazz              instance of T
     * @param tripIdToShapeIdMap
     */
    private <T> void readGtfsAndSaveToDb(String agencyId, File file, Class<T> clazz,
                                         Map<String, String> routeIdToNameMap,
                                         Map<String, String> tripIdToNameMap,
                                         Map<String, String> stopIdToNameMap,
                                         Map<String, String> tripIdToShapeIdMap) {
        var gtfsDataFlux = Flux.using(
                        () -> getAttrItr(file, clazz),
                        FileReadingUtil::getFluxFromIterator
                ).mapNotNull(attr -> getGtfsData(agencyId, routeIdToNameMap, tripIdToNameMap, stopIdToNameMap, tripIdToShapeIdMap, attr))
                .collectList()
                .block();
        assert gtfsDataFlux != null;
        gtfsStaticRepository.saveAll(gtfsDataFlux);
    }

    private <T> GtfsStaticData getGtfsData(String agencyId, Map<String, String> routeIdToNameMap, Map<String, String> tripIdToNameMap, Map<String, String> stopIdToNameMap, Map<String, String> tripIdToShapeIdMap, T attributes) {
        if (attributes instanceof RoutesAttributes routesAttributes)
            return convert(routesAttributes, agencyId, routeIdToNameMap);
        else if (attributes instanceof StopAttributes stopAttributes)
            return convert(stopAttributes, agencyId, stopIdToNameMap);
        else if (attributes instanceof TripAttributes tripAttributes) {
            //tripAttributes does not need to be written
            tripIdToNameMap.put(tripAttributes.getTripId(), routeIdToNameMap.get(tripAttributes.getRouteId()));
            tripIdToShapeIdMap.put(tripAttributes.getTripId(), tripAttributes.getShapeId());
            return null;
        } else {
            //this shouldn't be possible if you code it right... famous last words
            log.error("UNRECOGNIZED TYPE OF ATTRIBUTE, FAST FAIL.");
            return null;
        }
    }

    private GtfsStaticData convert(ShapeAttributes attributes, String agencyId) {
        GtfsStaticData gtfsStaticData = new GtfsStaticData();
        gtfsStaticData.setId(attributes.getShapeId());
        gtfsStaticData.setAgencyType(agencyId, SHAPE);
        return gtfsStaticData;
    }

    private void interpolateListIfNeeded(List<SequencedData> sequencedData) {
        if (isMissingArrivalsOrDepartures(sequencedData)) {
            interpolateDelay(sequencedData);
        }
    }

    private boolean isMissingArrivalsOrDepartures(List<SequencedData> sequencedData) {
        return sequencedData.stream()
                .anyMatch(GtfsStaticParserService::isMissingArrivalsOrDepartures);
    }

    /**
     * Writes gtfs static data as .csv files to disk under /files/{id}/{type}.csv.
     * It is up to the callee to determine successful completion time & handle errors.
     * <p>
     * This method may never complete. The callee _must_ set a timeout.
     * Files will only be written if they are in TYPES.
     * This does not mean that all TYPES will be written, since sometimes people put up bad GTFS feeds.
     *
     * @param staticUrl the url to download the gtfs zip from
     * @param feedId    the id to use when writing data to disk
     */
    private boolean writeGtfsRoutesToDiskSync(String staticUrl, String feedId) throws IOException {
        var conn = ((HttpURLConnection) new URL(staticUrl).openConnection());
        if (conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP || conn.getResponseCode() == 308) {
            log.info("Redirected id \"{}\" to \"{}\"", feedId, staticUrl);
            //call with new url, don't write the old one.
            return writeGtfsRoutesToDiskSync(handleRedirect(conn), feedId);
        } else if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            log.error("Failed to download static data from \"{}\", resCode \"{}\"", staticUrl, conn.getResponseCode());
            return false;
        }
        try (BufferedInputStream agencyGtfsZipStream = new BufferedInputStream(new URL(staticUrl).openStream())) {
            //all hail zt-zip. The code before this was hard to read and difficult to maintain
            var outputDir = new File("files" + File.separator + feedId);
            ZipUtil.unpack(agencyGtfsZipStream, outputDir, fileName -> {
                //only write files that we have a valid TYPE to parse for.
                GtfsStaticData.TYPE type = getTypeEndsWith(fileName.replace(".txt", ".csv"));
                if (type == null) return null;
                return type.getFileName();
            });
            return true;
        }
    }
}
