package com.doug.projects.transitdelayservice.entity.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SequencedDataListConverter implements AttributeConverter<List<SequencedData>> {

    private static AttributeValue nullSafeNumberAttrVal(Double d) {
        return d == null ? AttributeValue.builder().nul(true).build() : AttributeValue.builder().n(String.valueOf(d)).build();
    }

    private static AttributeValue nullSafeNumberAttrVal(Integer d) {
        return d == null ? AttributeValue.builder().nul(true).build() : AttributeValue.builder().n(String.valueOf(d)).build();
    }

    private static AttributeValue nullSafeStrAttrVal(String d) {
        return d == null ? AttributeValue.builder().nul(true).build() : AttributeValue.builder().s(d).build();
    }

    @Override
    public AttributeValue transformFrom(List<SequencedData> input) {
        List<AttributeValue> attributeValues = input.stream()
                .map(seqData -> AttributeValue.builder()
                        .m(Map.of(
                                "sequenceNo", nullSafeNumberAttrVal(seqData.getSequenceNo()),
                                "departureTime", nullSafeStrAttrVal(seqData.getDepartureTime()),
                                "arrivalTime", nullSafeStrAttrVal(seqData.getArrivalTime()),
                                "stopId", nullSafeStrAttrVal(seqData.getStopId()),
                                "stopName", nullSafeStrAttrVal(seqData.getStopName()),
                                "shapeLat", nullSafeNumberAttrVal(seqData.getShapeLat()),
                                "shapeLon", nullSafeNumberAttrVal(seqData.getShapeLon())
                        ))
                        .build())
                .collect(Collectors.toList());

        return AttributeValue.builder().l(attributeValues).build();
    }

    @Override
    public List<SequencedData> transformTo(AttributeValue attributeValue) {
        return attributeValue.l().stream()
                .map(attr -> {
                    Map<String, AttributeValue> m = attr.m();
                    return new SequencedData(
                            Integer.parseInt(m.get("sequenceNo").n()),
                            m.get("departureTime").s(),
                            m.get("arrivalTime").s(),
                            m.get("stopId").s(),
                            m.get("stopName").s(),
                            m.get("shapeLat").nul() ? null : Double.parseDouble(m.get("shapeLat").n()),
                            m.get("shapeLat").nul() ? null : Double.parseDouble(m.get("shapeLon").n())
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public EnhancedType<List<SequencedData>> type() {
        return EnhancedType.listOf(SequencedData.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.L;
    }
}

