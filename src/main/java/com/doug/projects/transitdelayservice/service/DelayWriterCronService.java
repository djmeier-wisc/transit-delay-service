package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.RealtimeTransitResponse;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class DelayWriterCronService {
    private final RouteTimestampRepository routeTimestampRepository;
    private final RealtimeMetroService realtimeMetroService;
    private final RouteMapperService routeMapperService;
    private final RealtimeResponseAdaptor adaptor;
    @Value("${doesCronRun}")
    private Boolean doesCronRun;

    private static List<RouteTimestamp> removeDuplicates(List<RouteTimestamp> routeTimestamps) {
        Set<String> seen = new HashSet<>();
        return routeTimestamps.stream().filter(rt -> seen.add(rt.getRoute())).collect(Collectors.toList());
    }

    private static boolean nullTransitFields(RealtimeTransitResponse transitResponse) {
        return transitResponse == null || transitResponse.getHeader() == null || transitResponse.getHeader().getTimestamp() == null || transitResponse.getEntity() == null;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void getGtfsData() {
        log.info("Checking out routes data from metro...");
        try (BufferedInputStream GTFS = realtimeMetroService.getGTFSStatic()) {
            ZipInputStream zis = new ZipInputStream(GTFS);
            ZipEntry ze = zis.getNextEntry();
            byte[] buffer = new byte[1024];
            while (ze != null) {
                String fileName = ze.getName().replace(".txt", ".csv");
                if (!fileName.contains("routes.csv")) {
                    ze = zis.getNextEntry();
                    continue;
                }
                File newFile = new File("files" + File.separator + fileName);
                log.info("Unzipping to " + newFile.getAbsolutePath());
                //create directories for sub directories in zip
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                //close this ZipEntry
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            log.info("Completed local write of metro data. Refreshing map...");
            if (routeMapperService.refreshMaps())
                log.info("Refreshed map");
            else
                log.info("Failed to refresh map");
        } catch (IOException e) {
            log.error("Failed to write data from metro!", e);
            throw new RuntimeException(e);
        }
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void getDelayAndWriteToDb() {
        try {
            if (doesCronRun != null && !doesCronRun)
                return;
            //get realtime delays from metro
            RealtimeTransitResponse transitResponse = realtimeMetroService.getCurrentRunData();
            //remove basic fields for timestamp checking / iteration
            if (nullTransitFields(transitResponse)) {
                log.info("transitResponse:{} data invalid, will not write anything", transitResponse);
                return;
            }
            log.info("Completed realtimeRequest successfully, building model...");
            List<RouteTimestamp> routeTimestamps = adaptor.convertFrom(transitResponse);
            List<RouteTimestamp> uniqueRouteTimestamps = DelayWriterCronService.removeDuplicates(routeTimestamps);
            log.info("Built model successfully from {} objects, yielding {} values for database write.",
                    transitResponse.getEntity()
                            .size(), uniqueRouteTimestamps.size());
            if (!routeTimestampRepository.writeRouteTimestamps(uniqueRouteTimestamps)) {
                throw new RuntimeException("Failed to write route timestamps");
            }
        } catch (Exception e) {
            log.error("Failed to write route timestamps", e);
        }
    }

}
