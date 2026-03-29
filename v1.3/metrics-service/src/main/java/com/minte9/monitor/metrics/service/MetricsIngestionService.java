/**
 * Metrics Ingestion Service
 * -------------------------
 * 
 * Set id to null when creating a new record. 
 * Mongo db will generate the identifier when saving the document. 
 * This is the usual pattern with Spring Data MongoDB. 
 */

package com.minte9.monitor.metrics.service;

import com.minte9.monitor.common.api.MetricIngestRequest;
import com.minte9.monitor.metrics.domain.MetricRecord;
import com.minte9.monitor.metrics.repository.MetricMongoRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

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
}
