package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.Status;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeed;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeedDto;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyFeedRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgencyFeedService {
    private final AgencyFeedRepository agencyFeedRepository;

    public List<AgencyFeedDto> getAllAgencyFeeds() {
        return agencyFeedRepository.findAll().stream()
                .map(AgencyFeedService::mapAgencyFeedDto)
                .sorted(Comparator.comparing(AgencyFeedDto::getId))
                .toList();
    }

    public List<AgencyFeedDto> getAgencyFeedsByStatus(Status status) {
        return agencyFeedRepository.findAllByStatus(status).stream()
                .map(AgencyFeedService::mapAgencyFeedDto)
                .toList();
    }

    public Optional<AgencyFeedDto> getAgencyFeedById(String id) {
        return agencyFeedRepository.findById(id)
                .map(AgencyFeedService::mapAgencyFeedDto);
    }

    public Optional<String> getAgencyFeedStatusById(String id) {
        return agencyFeedRepository.findStatusById(id);
    }

    private static @NotNull AgencyFeedDto mapAgencyFeedDto(AgencyFeed entity) {
        return new AgencyFeedDto(
                entity.getId(),
                entity.getStatus(),
                entity.getName(),
                entity.getRealTimeUrl(),
                entity.getStaticUrl(),
                entity.getState(),
                entity.getTimezone());
    }

    private static @NotNull AgencyFeed mapAgencyFeedDto(AgencyFeedDto dto) {
        AgencyFeed feed = new AgencyFeed();
        feed.setId(dto.getId());
        feed.setStatus(dto.getStatus());
        feed.setName(dto.getName());
        feed.setRealTimeUrl(dto.getRealTimeUrl());
        feed.setStaticUrl(dto.getStaticUrl());
        feed.setState(dto.getState());
        feed.setTimezone(dto.getTimezone());
        return feed;
    }

    public void saveAll(List<AgencyFeedDto> newFeeds) {
        var entites = newFeeds.stream()
                .map(AgencyFeedService::mapAgencyFeedDto)
                .toList();
        agencyFeedRepository.saveAll(entites);
    }

    public void deleteAll() {
        agencyFeedRepository.deleteAll();
        agencyFeedRepository.flush();
    }
}
