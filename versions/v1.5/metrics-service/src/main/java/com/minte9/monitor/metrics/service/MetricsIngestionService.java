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
import com.minte9.monitor.common.events.MetricReceivedEvent;
import com.minte9.monitor.metrics.domain.MetricRecord;
import com.minte9.monitor.metrics.messaging.MetricEventPublisher;
import com.minte9.monitor.metrics.repository.MetricMongoRepository;

@Service
public class MetricsIngestionService {
    
    private final MetricMongoRepository metricMongoRepository;
    private final MetricEventPublisher metricEventPublisher; 

    public MetricsIngestionService(MetricMongoRepository metricMongoRepository,
            MetricEventPublisher metricEventPublisher) {
        this.metricMongoRepository = metricMongoRepository;
        this.metricEventPublisher = metricEventPublisher;
    }

    public MetricRecord ingest(MetricIngestRequest request) {
        MetricRecord metricRecord = new MetricRecord(
            null,
            request.nodeId(),
            request.metricType(),
            request.timestamp(),
            request.payload(),
            Instant.now()
        );

        MetricRecord saved = metricMongoRepository.save(metricRecord);

        MetricReceivedEvent event = new MetricReceivedEvent(
            saved.id(),
            saved.nodeId(),
            saved.metricType().name(),
            saved.timestamp(),
            saved.receivedAt(),
            saved.payload()
        );

        metricEventPublisher.publish(event);

        return saved;
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
