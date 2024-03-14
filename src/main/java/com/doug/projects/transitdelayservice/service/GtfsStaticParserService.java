package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.entity.gtfs.csv.RoutesAttributes;
import com.doug.projects.transitdelayservice.entity.gtfs.csv.StopAttributes;
import com.doug.projects.transitdelayservice.entity.gtfs.csv.StopTimeAttributes;
import com.doug.projects.transitdelayservice.entity.gtfs.csv.TripAttributes;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
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

import static com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData.TYPE.ROUTE;
import static com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData.TYPE.STOPTIME;
import static com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData.getType;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsStaticParserService {
    private final GtfsStaticRepository gtfsStaticRepository;

    private static GtfsStaticData convert(RoutesAttributes routesAttributes, String agencyId) {
        String routeName = routesAttributes.getRouteShortName();
        if (StringUtils.isBlank(routeName)) {
            routeName = routesAttributes.getRouteServiceName();
        }
        GtfsStaticData staticData = new GtfsStaticData();
        staticData.setAgencyType(agencyId, ROUTE);
        staticData.setId(routesAttributes.getRouteId());
        staticData.setRouteName(routeName);
        staticData.setRouteColor('#' + routesAttributes.getRouteColor());
        staticData.setRouteSortOrder(routesAttributes.getRouteSortOrder());
        return staticData;
    }

    private static GtfsStaticData convert(StopAttributes stopAttributes, String agencyId) {
        GtfsStaticData staticData = new GtfsStaticData();
        staticData.setAgencyType(agencyId, ROUTE);
        staticData.setId(String.valueOf(stopAttributes.getStopId()));
        staticData.setStopName(stopAttributes.getStopName());
        staticData.setStopLat(stopAttributes.getStopLat());
        staticData.setStopLon(stopAttributes.getStopLon());
        return staticData;
    }

    private static GtfsStaticData convert(TripAttributes tripAttributes, String agencyId) {
        GtfsStaticData staticData = new GtfsStaticData();
        staticData.setAgencyType(agencyId, STOPTIME);
        staticData.setId(tripAttributes.getTripId());
        staticData.setRouteId(tripAttributes.get());
        return staticData;
    }

    private static GtfsStaticData convert(StopTimeAttributes stopTimeAttributes, String agencyId) {
        GtfsStaticData staticData = new GtfsStaticData();
        staticData.setAgencyType(agencyId, STOPTIME);
        staticData.setId(String.valueOf(stopTimeAttributes.getStopId()), stopTimeAttributes.getStopSequence());
        return staticData;
    }
    private static boolean writeGtfsRoutesToDisk(String staticUrl, String id) {
        log.info("Checking out routes data from {}, id {}", staticUrl, id);
        try (BufferedInputStream GTFS = new BufferedInputStream(new URL(staticUrl).openStream())) {
            ZipInputStream zis = new ZipInputStream(GTFS);
            ZipEntry ze = zis.getNextEntry();
            byte[] buffer = new byte[1024];
            while (ze != null) {
                String fileName = ze.getName().replace(".txt", ".csv");
                GtfsStaticData.TYPE type = getType(fileName);
                if (type == null) {
                    zis.closeEntry();
                    ze = zis.getNextEntry();
                    continue;
                }
                File newFile = new File("files" + File.separator + id + File.separator + type.getFileName());
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
            return false;
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

        for (GtfsStaticData.TYPE value : GtfsStaticData.TYPE.values()) {
            File file = new File("files" + File.separator + agencyId + File.separator + value.getFileName());
            try {
                switch (value) {
                    case ROUTE -> readGtfsAndSaveToDb(agencyId, file, RoutesAttributes.class);
                    case TRIP -> readGtfsAndSaveToDb(agencyId, file, TripAttributes.class);
                    case STOPTIME -> readGtfsAndSaveToDb(agencyId, file, StopTimeAttributes.class);
                    case STOP -> readGtfsAndSaveToDb(agencyId, file, StopAttributes.class);
                    //TODO consider shapes.txt, maybe add it in the future?
                }
                file.delete();
                log.debug("Write finished for id: {}, url: {}", agencyId, staticUrl);
                return null;
            } catch (IOException e) {
                file.delete();
                log.error("Read file failed for id: {}, url: {}", agencyId, staticUrl);
                return null;
            }
        }
        return null;
    }

    /**
     * Generic converter to read from file and write to dynamo.
     *
     * @param agencyId the agencyId to write to dynamo from
     * @param file     the file to read from
     * @param clazz    instance of T
     * @param <T>      an Attributes class used to map against .csv file passed in. Should be
     * @throws IOException
     */
    private <T> void readGtfsAndSaveToDb(String agencyId, File file, Class<T> clazz) throws IOException {
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        MappingIterator<T> attributesIterator = csvMapper
                .readerWithSchemaFor(clazz)
                .with(schema)
                .readValues(file);
        List<GtfsStaticData> gtfsList = new ArrayList<>(50);
        while (attributesIterator.hasNext()) {
            T attributes = attributesIterator.next();
            if (attributes instanceof RoutesAttributes)
                gtfsList.add(convert((RoutesAttributes) attributes, agencyId));
            else if (attributes instanceof StopAttributes)
                gtfsList.add(convert((StopAttributes) attributes, agencyId));
            else if (attributes instanceof TripAttributes)
                gtfsList.add(convert((TripAttributes) attributes, agencyId));
            else if (attributes instanceof StopTimeAttributes)
                gtfsList.add(convert((StopTimeAttributes) attributes, agencyId));
            else
                attributesIterator.close();
            if (gtfsList.size() >= 25) {
                gtfsStaticRepository.saveAll(gtfsList);
                gtfsList.clear();
            }
        }
        attributesIterator.close();
    }
}
