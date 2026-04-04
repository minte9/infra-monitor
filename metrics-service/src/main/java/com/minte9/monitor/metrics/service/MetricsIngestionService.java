/**
 * Metrics Ingestion Service
 * -------------------------
 * 
 * Set id to null when creating a new record. 
 * Mongo db will generate the identifier when saving the document. 
 * This is the usual pattern with Spring Data MongoDB. 
 */

package com.minte9.monitor.metrics.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.minte9.monitor.common.api.MetricIngestRequest;
import com.minte9.monitor.common.api.MetricType;
import com.minte9.monitor.metrics.domain.MetricRecord;
import com.minte9.monitor.metrics.repository.MetricMongoRepository;

@Service
public class MetricsIngestionService {
    
    private final MetricMongoRepository metricMongoRepository;

    public MetricsIngestionService(MetricMongoRepository metricMongoRepository) {
        this.metricMongoRepository = metricMongoRepository;
    }

    public MetricRecord ingest(MetricIngestRequest request) {
        MetricRecord metricRecord = new MetricRecord(
            null,  // Look Here
            request.nodeId(),
            request.metricType(),
            request.timestamp(),
            request.payload(),
            Instant.now()
        );

        return metricMongoRepository.save(metricRecord);
    }

    public List<MetricRecord> findAll() {
        return metricMongoRepository.findAll();
    }

    public List<MetricRecord> findByNodeId(String nodeId) {
        return metricMongoRepository.findByNodeId(nodeId);
    }

    public Optional<MetricRecord> findLatestByNodeId(String nodeId) {
        return metricMongoRepository.findFirstByNodeIdOrderByTimestampDesc(nodeId);
    }

    public Optional<MetricRecord> findLatestByNodeIdAndMetricType(String nodeId, MetricType metricType) {
        return metricMongoRepository.findFirstByNodeIdAndMetricTypeOrderByTimestampDesc(nodeId, metricType);
    }
}
