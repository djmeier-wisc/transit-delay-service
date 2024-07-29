package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.AgencyStaticStatus;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.entity.gtfs.csv.*;
import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.zeroturnaround.zip.ZipUtil;
import reactor.core.publisher.Flux;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
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

    /**
     * Create a staticData object, getting routeName from routeIdToServiceNameMap. Populates tripIdToServiceNameMap based on contents of routeIdToServiceNameMap.
     *
     * @param tripAttributes   the trip.txt row to populate staticData based on
     * @param agencyId         the agencyId we are pulling data from, used to generate id
     * @param routeIdToNameMap unmodified, used to get route name based on route id
     * @param tripIdToNameMap  modified, put tripId and respective routeName gathered from routeIdToNameMap
     * @return
     */
    private static GtfsStaticData convert(TripAttributes tripAttributes, String agencyId,
                                          Map<String, String> routeIdToNameMap,
                                          Map<String, String> tripIdToNameMap) {
        GtfsStaticData staticData = new GtfsStaticData();
        staticData.setAgencyType(agencyId, TRIP);
        staticData.setId(tripAttributes.getTripId());
        staticData.setRouteName(routeIdToNameMap.get(tripAttributes.getRouteId()));
        staticData.setShapeId(tripAttributes.getShapeId());
        tripIdToNameMap.put(tripAttributes.getTripId(), routeIdToNameMap.get(tripAttributes.getRouteId()));
        return staticData;
    }

    private static GtfsStaticData convert(StopTimeAttributes stopTimeAttributes,
                                          String agencyId,
                                          Map<String, String> tripIdToNameMap,
                                          Map<String, String> stopIdToNameMap) {
        GtfsStaticData staticData = new GtfsStaticData();
        staticData.setAgencyType(agencyId, STOPTIME);
        staticData.setId(String.valueOf(stopTimeAttributes.getTripId()), stopTimeAttributes.getStopSequence());
        staticData.setDepartureTime(stopTimeAttributes.getDepartureTime());
        staticData.setArrivalTime(stopTimeAttributes.getArrivalTime());
        staticData.setStopId(stopTimeAttributes.getStopId());
        staticData.setStopName(stopIdToNameMap.get(stopTimeAttributes.getStopId()));
        staticData.setRouteName(tripIdToNameMap.get(stopTimeAttributes.getTripId()));
        return staticData;
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
    public static void interpolateDelay(List<GtfsStaticData> gtfsList) {
        gtfsList.sort(Comparator.comparing(GtfsStaticData::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(GtfsStaticData::getSequence, Comparator.nullsLast(Comparator.naturalOrder())));
        int startDepartureIndex = 0;
        int startArrivalIndex = 0;
        for (int i = 1; i < gtfsList.size(); i++) {
            GtfsStaticData sameTripStop = gtfsList.get(i);
            if (isNotEmpty(sameTripStop.getDepartureTime())) {
                try {
                    LocalTime startTime = LocalTime.parse(replaceGreaterThan24Hr(gtfsList.get(startDepartureIndex)
                            .getDepartureTime()));
                    LocalTime endTime = LocalTime.parse(replaceGreaterThan24Hr(sameTripStop.getDepartureTime()));
                    Duration difference = Duration.between(startTime, endTime).dividedBy(i - startDepartureIndex);
                    for (int j = startDepartureIndex + 1; j < i; j++) {
                        GtfsStaticData gtfsStaticData = gtfsList.get(j);
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
                        GtfsStaticData gtfsStaticData = gtfsList.get(j);
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

    private void readAgencyTimezoneAndSaveToDb(String agencyId, File file) {
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (MappingIterator<AgencyAttributes> attributesIterator = csvMapper
                .readerWithSchemaFor(AgencyAttributes.class)
                .with(schema)
                .with(CsvParser.Feature.TRIM_SPACES)
                .readValues(file)) {
            while (attributesIterator.hasNext()) { //we presume all stopTimes in the files parsed are
                String timezone = attributesIterator.next().getAgencyTimezone();
                if (timezone == null) {
                    log.error("UNABLE TO FIND TZ FOR ID: {}", agencyId);
                    return;
                }
                agencyFeedRepository.getAgencyFeedById(agencyId)
                        .subscribe(f -> {
                            f.setTimezone(timezone);
                            agencyFeedRepository.writeAgencyFeed(f);
                            log.info("Completed TZ write for id: {}", agencyId);
                        });
                break;
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
        for (GtfsStaticData.TYPE value : GtfsStaticData.TYPE.values()) {
            File file = new File("files" + File.separator + agencyId + File.separator + value.getFileName());
            try {
                switch (value) {
                    case AGENCY -> readAgencyTimezoneAndSaveToDb(agencyId, file);
                    case ROUTE ->
                            readGtfsAndSaveToDb(agencyId, file, RoutesAttributes.class, routeIdToNameMap, tripIdToNameMap, stopIdToNameMap);
                    case TRIP ->
                            readGtfsAndSaveToDb(agencyId, file, TripAttributes.class, routeIdToNameMap, tripIdToNameMap, stopIdToNameMap);
                    case STOPTIME ->
                            readGtfsAndSaveToDb(agencyId, file, StopTimeAttributes.class, routeIdToNameMap, tripIdToNameMap, stopIdToNameMap);
                    case STOP ->
                            readGtfsAndSaveToDb(agencyId, file, StopAttributes.class, routeIdToNameMap, tripIdToNameMap, stopIdToNameMap);
                    case SHAPE ->
                            readGtfsAndSaveToDb(agencyId, file, ShapeAttributes.class, routeIdToNameMap, tripIdToNameMap, stopIdToNameMap);
                    default -> log.error("No case for type: {}", value);
                    //TODO consider shapes.txt, maybe add it in the future?
                }
                file.delete();
                log.info("{} read finished for id: {}", value.getName(), agencyId);
            } finally {
                file.deleteOnExit();
            }
        }
        new File("files" + File.separator + agencyId).delete();
        log.info("All file read finished for id: {}", agencyId);
    }

    /**
     * Generic converter to read data from a single file (routes.txt, trips.txt, etc.) from file and write to dynamo.
     *
     * @param <T>      an Attributes class used to map against .csv file passed in. Should be
     * @param agencyId the agencyId to write to dynamo from
     * @param file     the file to read from
     * @param clazz    instance of T
     */
    private <T> void readGtfsAndSaveToDb(String agencyId, File file, Class<T> clazz,
                                         Map<String, String> routeIdToNameMap,
                                         Map<String, String> tripIdToNameMap,
                                         Map<String, String> stopIdToNameMap) {
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (MappingIterator<T> attributesIterator = csvMapper
                .readerWithSchemaFor(clazz)
                .with(schema)
                .with(CsvParser.Feature.TRIM_SPACES)
                .readValues(file)) {
            List<GtfsStaticData> gtfsList = new ArrayList<>();
            boolean isStopTimes = false;
            while (attributesIterator.hasNext()) {
                T attributes = attributesIterator.next();
                if (attributes instanceof RoutesAttributes)
                    gtfsList.add(convert((RoutesAttributes) attributes, agencyId, routeIdToNameMap));
                else if (attributes instanceof StopAttributes)
                    gtfsList.add(convert((StopAttributes) attributes, agencyId, stopIdToNameMap));
                else if (attributes instanceof TripAttributes)
                    gtfsList.add(convert((TripAttributes) attributes, agencyId, routeIdToNameMap, tripIdToNameMap));
                else if (attributes instanceof ShapeAttributes)
                    gtfsList.add(convert((ShapeAttributes) attributes, agencyId));
                else if (attributes instanceof StopTimeAttributes) {
                    gtfsList.add(convert((StopTimeAttributes) attributes, agencyId, tripIdToNameMap, stopIdToNameMap));
                    isStopTimes = true;
                } else {
                    //this shouldn't be possible if you code it right... famous last words
                    log.error("UNRECOGNIZED TYPE OF ATTRIBUTE, FAST FAIL.");
                    attributesIterator.close();
                }
            }
            if (isStopTimes && isMissingArrivalsOrDepartures(gtfsList)) {
                interpolateDelay(gtfsList);
            }
            gtfsStaticRepository.saveAll(Flux.fromIterable(gtfsList));
        } catch (IOException | DateTimeParseException e) {
            log.error("Failed to read file: {}", file.getName(), e);
        }
    }

    private GtfsStaticData convert(ShapeAttributes attributes, String agencyId) {
        GtfsStaticData gtfsStaticData = new GtfsStaticData();
        gtfsStaticData.setId(attributes.getShapeId() + ":" + attributes.getShapePtSequence());
        gtfsStaticData.setStopLat(attributes.getShapePtLat());
        gtfsStaticData.setStopLon(attributes.getShapePtLon());
        gtfsStaticData.setAgencyType(agencyId, SHAPE);
        return gtfsStaticData;
    }

    private boolean isMissingArrivalsOrDepartures(List<GtfsStaticData> gtfsList) {
        for (GtfsStaticData gtfsStaticData : gtfsList) {
            if (gtfsStaticData.getDepartureTime() == null || gtfsStaticData.getArrivalTime() == null) {
                return true;
            }
        }
        return false;
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
