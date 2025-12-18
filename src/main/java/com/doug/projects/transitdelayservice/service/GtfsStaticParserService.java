package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.AgencyStaticStatus;
import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticDataType;
import com.doug.projects.transitdelayservice.entity.gtfs.csv.*;
import com.doug.projects.transitdelayservice.entity.jpa.*;
import com.doug.projects.transitdelayservice.repository.jpa.*;
import com.doug.projects.transitdelayservice.util.TransitDateUtil;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.zeroturnaround.zip.ZipUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticDataType.*;
import static com.doug.projects.transitdelayservice.util.UrlRedirectUtil.handleRedirect;
import static io.micrometer.common.util.StringUtils.isBlank;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsStaticParserService {

    private final AgencyFeedRepository agencyFeedRepository;
    private final AgencyRouteRepository agencyRouteRepository;
    private final AgencyTripRepository agencyTripRepository;
    private final AgencyShapeRepository agencyShapeRepository;
    private final AgencyStopRepository agencyStopRepository;
    private final AgencyStopTimeRepository agencyStopTimeRepository;

    /**
     * Gets the associated type with this filename by checking whether the filename ENDS with type.getFileName.
     */
    public static GtfsStaticDataType getTypeEndsWith(String fileName) {
        for (GtfsStaticDataType gtfsStaticDataType : GtfsStaticDataType.values()) {
            if (StringUtils.endsWith(fileName, gtfsStaticDataType.getFileName())) {
                return gtfsStaticDataType;
            }
        }
        return null;
    }

    /**
     * In some GTFS feeds, the schedule is missing departureTimes.
     * <br /><br />
     * For example: 5:00,null,null,5:03,null,null,5:06 would be replaced with 5:00,5:01,5:02,5:03,5:04,5:05,5:06
     *
     * @param stopTimes the gtfsList to be modified by this method
     */
    public static void interpolateDelay(List<AgencyStopTime> stopTimes) {
        stopTimes.sort(Comparator.comparing(AgencyStopTime::getTripId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AgencyStopTime::getStopSeq, Comparator.nullsLast(Comparator.naturalOrder())));
        int startDepartureIndex = 0;
        int startArrivalIndex = 0;
        for (int i = 1; i < stopTimes.size(); i++) {
            AgencyStopTime sameTripStop = stopTimes.get(i);
            if (sameTripStop.getDepartureTimeSecs() != null) {
                var startTime = stopTimes.get(startDepartureIndex).getDepartureTimeSecs();
                var endTime = sameTripStop.getDepartureTimeSecs();
                var difference = (endTime - startTime) / (i - startDepartureIndex);
                for (int j = startDepartureIndex + 1; j < i; j++) {
                    AgencyStopTime agencyStopTime = stopTimes.get(j);
                    var newDepartureTime = startTime + (difference * (j - startDepartureIndex));
                    agencyStopTime.setDepartureTimeSecs(newDepartureTime);
                }
                startDepartureIndex = i;
            }
            if (sameTripStop.getArrivalTimeSecs() != null) {
                var startTime = stopTimes.get(startArrivalIndex).getArrivalTimeSecs();
                var endTime = sameTripStop.getArrivalTimeSecs();
                var difference = (endTime - startTime) / (i - startArrivalIndex);
                for (int j = startArrivalIndex + 1; j < i; j++) {
                    AgencyStopTime agencyStopTime = stopTimes.get(j);
                    var newArrivalTime = startTime + (difference * (j - startArrivalIndex));
                    agencyStopTime.setArrivalTimeSecs(newArrivalTime);
                }
                startArrivalIndex = i;
            }
        }
    }

    private void readAgencyTimezoneAndSaveToDb(String agencyId) {
        File file = new File("files" + File.separator + agencyId + File.separator + AGENCY.getFileName());
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
                agencyFeedRepository.updateTimezoneById(timezone, agencyId);
            }
        } catch (IOException e) {
            log.error("Failed to read agency.csv file: {}", file.getName(), e);
        }
    }

    public void writeGtfsStaticDataToDynamoFromDiskSync(AgencyFeedDto feed) {
        var agencyId = feed.getId();
        var agency = agencyFeedRepository.findById(agencyId);
        if (agency.isEmpty()) {
            log.error("Agency not found!");
            return;
        }
        readAgencyTimezoneAndSaveToDb(agencyId);
        log.info("Saving routes...");
        saveRoutes(agency, agencyId);
        log.info("Saving trips...");
        var maps = saveTrips(agencyId);
        var agencyTripMap = maps.get(0);
        log.info("Saving shapes...");
        saveShapes(agencyId);
        log.info("Saving stops...");
        var agencyStopMap = saveStops(agencyId);
        log.info("Saving stopTimes...");
        saveStopTimes(agencyTripMap,agencyStopMap,agencyId, agency.get().getTimezone());
        new File("files" + File.separator + agencyId).delete();
        log.info("All file read finished for id: {}", agencyId);
    }

    private void saveStopTimes(Map<String, AgencyTrip> agencyTripMap, Map<String, AgencyStop> agencyStopMap, String agencyId, String timezone) {
        File file = new File("files" + File.separator + agencyId + File.separator + STOPTIME.getFileName());
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (MappingIterator<StopTimeAttributes> attributesIterator = csvMapper
                .readerWithSchemaFor(StopTimeAttributes.class)
                .with(schema)
                .with(CsvParser.Feature.TRIM_SPACES)
                .readValues(file)) {

            List<AgencyStopTime> stopTimesToSave = new ArrayList<>();
            String tripId = null;
            while (attributesIterator.hasNext()) {
                var stopTime = attributesIterator.next();

                int arrivalSecs = TransitDateUtil.convertGtfsTimeToSeconds(stopTime.getArrivalTime());
                int departureSecs = TransitDateUtil.convertGtfsTimeToSeconds(stopTime.getDepartureTime());

                AgencyTrip trip = agencyTripMap.get(stopTime.getTripId());
                AgencyStop stop = agencyStopMap.get(stopTime.getStopId());

                if (trip == null || stop == null) {
                    log.warn("Skipping StopTime for tripId: {} or stopId: {} - Missing reference data.",
                            stopTime.getTripId(), stopTime.getStopId());
                    continue;
                }

                var entity = AgencyStopTime.builder()
                        .id(AgencyStopTimeId.builder()
                                .stopSequence(stopTime.getStopSequence())
                                .agencyId(agencyId)
                                .tripId(stopTime.getTripId())
                                .build()
                        )
                        .trip(trip)
                        .stop(stop)
                        .stopId(stop.getStopId())
                        .arrivalTimeSecs(arrivalSecs)
                        .departureTimeSecs(departureSecs)
                        .build();
                if (tripId != null && !Objects.equals(stopTime.getTripId(), tripId)) {
                    interpolateDelay(stopTimesToSave);
                    agencyStopTimeRepository.saveAllAndFlush(stopTimesToSave);
                    stopTimesToSave.clear();
                }
                stopTimesToSave.add(entity);
                tripId = stopTime.getTripId();
            }
            interpolateDelay(stopTimesToSave);
            agencyStopTimeRepository.saveAllAndFlush(stopTimesToSave);
        } catch (IOException e) {
            log.error("Failed to read {} stop times", agencyId, e);
        }

        // Note: You must correct the original method's save call.
        // It was trying to save stopMap (AgencyStop) instead of the StopTime entity.
        // Also, usually you keep stop files for debugging, but if deletion is policy, keep it.
        // file.delete();
    }

    private Map<String, AgencyStop> saveStops(String agencyId) {
        File file = new File("files" + File.separator + agencyId + File.separator + STOP.getFileName());
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        Map<String, AgencyStop> stopMap = new HashMap<>();
        try (MappingIterator<StopAttributes> attributesIterator = csvMapper
                .readerWithSchemaFor(StopAttributes.class)
                .with(schema)
                .with(CsvParser.Feature.TRIM_SPACES)
                .readValues(file)) {
            while (attributesIterator.hasNext()) {
                var stop = attributesIterator.next();
                var entity = AgencyStop.builder()
                        .id(new AgencyStopId(stop.getStopId(), agencyId))
                        .stopName(stop.getStopName())
                        .stopLat(stop.getStopLat())
                        .stopLon(stop.getStopLon())
                        .build();
                stopMap.put(stop.getStopId(), entity);
            }
        } catch (IOException e) {
            log.error("Failed to read {} stops", agencyId);
        }
        agencyStopRepository.saveAll(stopMap.values());
        file.delete();
        return stopMap;
    }

    private void saveShapes(String agencyId) {
        File file = new File("files" + File.separator + agencyId + File.separator + SHAPE.getFileName());
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (MappingIterator<ShapeAttributes> attributesIterator = csvMapper
                .readerWithSchemaFor(ShapeAttributes.class)
                .with(schema)
                .with(CsvParser.Feature.TRIM_SPACES)
                .readValues(file)) {
            List<AgencyShape> shapes = new ArrayList<>();
            while (attributesIterator.hasNext()) {
                ShapeAttributes shapeAttrs = attributesIterator.next();
                var entity = AgencyShape.builder()
                        .id(AgencyShapeId.builder()
                                .shapeId(shapeAttrs.getShapeId())
                                .sequence(shapeAttrs.getShapePtSequence())
                                .agencyId(agencyId)
                                .build())
                        .shapePtLat(shapeAttrs.getShapePtLat())
                        .shapePtLon(shapeAttrs.getShapePtLon())
                        .build();
                shapes.add(entity);
                if(shapes.size() >= 50) {
                    agencyShapeRepository.saveAll(shapes);
                    shapes.clear();
                }
            }
            agencyShapeRepository.saveAll(shapes);
        } catch (IOException e) {
            log.error("Failed to read {} shapes", agencyId);
        }
        agencyShapeRepository.flush();
        file.delete();
    }

    private List<Map<String, AgencyTrip>> saveTrips(String agencyId) {
        File file = new File("files" + File.separator + agencyId + File.separator + TRIP.getFileName());
        Map<String, AgencyTrip> tripMap = new HashMap<>();
        Map<String, AgencyTrip> shapeMap = new HashMap<>();
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (MappingIterator<TripAttributes> attributesIterator = csvMapper
                .readerWithSchemaFor(TripAttributes.class)
                .with(schema)
                .with(CsvParser.Feature.TRIM_SPACES)
                .readValues(file)) {
            while (attributesIterator.hasNext()) {
                TripAttributes tripsAttributes = attributesIterator.next();
                var entity = AgencyTrip.builder()
                        .id(new AgencyTripId(tripsAttributes.getTripId(), agencyId))
                        .routeId(tripsAttributes.getRouteId())
                        .shapeId(tripsAttributes.getShapeId())
                        .build();
                tripMap.putIfAbsent(entity.getTripId(), entity);
                shapeMap.putIfAbsent(tripsAttributes.getShapeId(), entity);
            }
            agencyTripRepository.saveAll(tripMap.values());
        } catch (IOException e) {
            log.error("Failed to read {} trips", agencyId);
        }
        file.delete();
        agencyTripRepository.flush();
        return List.of(tripMap, shapeMap);
    }

    private void saveRoutes(Optional<AgencyFeed> agency, String agencyId) {
        File file = new File("files" + File.separator + agencyId + File.separator + ROUTE.getFileName());
        Map<String, AgencyRoute> routeMap = new HashMap<>();
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        try (MappingIterator<RoutesAttributes> attributesIterator = csvMapper
                .readerWithSchemaFor(RoutesAttributes.class)
                .with(schema)
                .with(CsvParser.Feature.TRIM_SPACES)
                .readValues(file)) {
            while (attributesIterator.hasNext()) {
                RoutesAttributes routesAttributes = attributesIterator.next();
                var routesName = routesAttributes.getRouteShortName();
                if (isBlank(routesName)) routesName = routesAttributes.getRouteLongName();
                var entity = AgencyRoute.builder()
                        .id(new AgencyRouteId(routesAttributes.getRouteId(), agencyId))
                        .agency(agency.get())
                        .routeName(routesName)
                        .routeColor('#' + routesAttributes.getRouteColor())
                        .routeSortOrder(routesAttributes.getRouteSortOrder())
                        .build();
                routeMap.putIfAbsent(entity.getRouteId(), entity);
            }
            agencyRouteRepository.saveAll(routeMap.values());
        } catch (IOException e) {
            log.error("Failed to read {} routes", agencyId);
        }
        file.delete();
        agencyRouteRepository.flush();
    }

    /**
     * Writes gtfs static data as .csv files to disk under /files.
     *
     * @param feed           the feed to download gtfs static data from
     * @param timeoutSeconds the number of seconds until timeout. In the event of failure,
     * @return AgencyStaticStatus w/ success false if timeout or IO exception, success of true otherwise.
     * @implNote this may need to be forcibly timed out. some providers never return data, leading to thread starvation
     */
    public CompletableFuture<AgencyStaticStatus> writeGtfsRoutesToDiskAsync(AgencyFeedDto feed, int timeoutSeconds) {
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
                //only write files that we have a valid GtfsStaticDataType to parse for.
                GtfsStaticDataType gtfsStaticDataType = getTypeEndsWith(fileName.replace(".txt", ".csv"));
                if (gtfsStaticDataType == null) return null;
                return gtfsStaticDataType.getFileName();
            });
            return true;
        }
    }
}
