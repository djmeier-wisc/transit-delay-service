package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRoute;
import com.doug.projects.transitdelayservice.entity.gtfs.csv.RoutesAttributes;
import com.doug.projects.transitdelayservice.repository.RoutesRepository;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsStaticParserService {
    private final RoutesRepository routesRepository;

    private static AgencyRoute convert(RoutesAttributes routesAttributes, String agencyId) {
        String routeName = routesAttributes.getRoute_short_name();
        if (StringUtils.isBlank(routeName)) {
            routeName = routesAttributes.getRoute_service_name();
        }
        return AgencyRoute.builder()
                .routeId(String.valueOf(routesAttributes.getRoute_id()))
                .routeName(routeName)
                .agencyId(agencyId)
                .color(routesAttributes.getRoute_color())
                .sortOrder(routesAttributes.getRoute_sort_order())
                .build();
    }

    private static boolean writeGtfsRoutesToDisk(String staticUrl, String id) {
        log.info("Checking out routes data from {}, id {}", staticUrl, id);
        try (BufferedInputStream GTFS = new BufferedInputStream(new URL(staticUrl).openStream())) {
            ZipInputStream zis = new ZipInputStream(GTFS);
            ZipEntry ze = zis.getNextEntry();
            byte[] buffer = new byte[1024];
            while (ze != null) {
                String fileName = ze.getName();
                if (!fileName.contains("routes.txt")) {
                    zis.closeEntry();
                    ze = zis.getNextEntry();
                    continue;
                }
                File newFile = new File("files" + File.separator + id + "-routes.csv");
                //create directories for sub directories in zip
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                    fos.flush();
                }
                fos.close();
                //close this ZipEntry
                zis.closeEntry();
                return true;
            }
        } catch (IOException e) {
            log.error("Failed to write data from metro!", e);
        }
        return false;
    }

    public CompletableFuture<Void> writeStaticDataAsync(String staticUrl, String agencyId) {
        return CompletableFuture.supplyAsync(() -> writeStaticDataSync(staticUrl, agencyId));


    }

    @Nullable
    private Void writeStaticDataSync(String staticUrl, String agencyId) {
        if (!writeGtfsRoutesToDisk(staticUrl, agencyId)) {
            log.debug("Write failed for id: {}, url: {}", agencyId, staticUrl);
            return null;
        }
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        File file = new File("files" + File.separator + agencyId + "-routes.csv");
        try {
            MappingIterator<RoutesAttributes> routesAttributesIterator = csvMapper
                    .readerWithSchemaFor(RoutesAttributes.class)
                    .with(schema)
                    .readValues(file);
            List<AgencyRoute> agencyRouteList = new ArrayList<>(50);
            while (routesAttributesIterator.hasNext()) {
                RoutesAttributes routesAttributes = routesAttributesIterator.next();
                agencyRouteList.add(convert(routesAttributes, agencyId));
                if (agencyRouteList.size() >= 25) {
                    routesRepository.saveAll(agencyRouteList);
                    agencyRouteList.clear();
                }
            }
            routesAttributesIterator.close();
            file.delete();
            log.debug("Write finished for id: {}, url: {}", agencyId, staticUrl);
            return null;
        } catch (IOException e) {
            file.delete();
            log.error("Write failed for id: {}, url: {}", agencyId, staticUrl);
            return null;
        }
    }
}
