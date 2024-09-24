package com.doug.projects.transitdelayservice.exceptions;

import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class UnprocessedItemsGtfsStaticDataException extends Throwable {
    String message;
    List<GtfsStaticData> unprocessedItems;
}
