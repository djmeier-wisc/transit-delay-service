package com.doug.projects.transitdelayservice.entity.openmobilityfeed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMobilitySource {

    @JsonProperty("mdb_source_id")
    private String mdbSourceId;

    @JsonProperty("data_type")
    private String dataType;

    @JsonProperty("entity_type")
    private String entityType;

    @JsonProperty("location.country_code")
    private String countryCode;

    @JsonProperty("location.subdivision_name")
    private String subdivisionName;

    @JsonProperty("location.municipality")
    private String municipality;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("name")
    private String name;

    @JsonProperty("note")
    private String note;

    @JsonProperty("feed_contact_email")
    private String feedContactEmail;

    @JsonProperty("static_reference")
    private String staticReference;

    @JsonProperty("urls.direct_download")
    private String directDownloadUrl;

    @JsonProperty("urls.authentication_type")
    private String authenticationTypeUrl;

    @JsonProperty("urls.authentication_info")
    private String authenticationInfoUrl;

    @JsonProperty("urls.api_key_parameter_name")
    private String apiKeyParameterNameUrl;

    @JsonProperty("urls.latest")
    private String latestUrl;

    @JsonProperty("urls.license")
    private String licenseUrl;

    @JsonProperty("location.bounding_box.minimum_latitude")
    private double minLatitude;

    @JsonProperty("location.bounding_box.maximum_latitude")
    private double maxLatitude;

    @JsonProperty("location.bounding_box.minimum_longitude")
    private double minLongitude;

    @JsonProperty("location.bounding_box.maximum_longitude")
    private double maxLongitude;

    @JsonProperty("location.bounding_box.extracted_on")
    private String extractedOn;

    @JsonProperty("status")
    private String status;

    @JsonProperty("features")
    private String features;

    @JsonProperty("redirect.id")
    private String redirectId;

    @JsonProperty("redirect.comment")
    private String redirectComment;
}

