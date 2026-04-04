package com.minte9.monitor.metrics.service;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import org.springframework.stereotype.Service;

import com.minte9.monitor.common.api.MetricIngestRequest;
import com.minte9.monitor.metrics.domain.MetricRecord;
import com.minte9.monitor.metrics.repository.MetricRepository;

@Service
public class MetricsIngestionService {
    
    private final MetricRepository metricRepository;

    public MetricsIngestionService(MetricRepository metricRepository) {
        this.metricRepository = metricRepository;
    }

    public MetricRecord ingest(MetricIngestRequest request) {
        MetricRecord metricRecord = new MetricRecord(
            UUID.randomUUID().toString(),
            request.nodeId(),
            request.metricType(),
            request.timestamp(),
            request.payload(),
            Instant.now()
        );

        return metricRepository.save(metricRecord);
    }

    public List<MetricRecord> findAll() {
        return metricRepository.findAll();
    }

    public List<MetricRecord> findByNodeId(String nodeId) {
        return metricRepository.findByNodeId(nodeId);
    }
}
