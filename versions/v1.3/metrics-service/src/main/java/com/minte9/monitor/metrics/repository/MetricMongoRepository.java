/**
 * Mongo Repository
 * ----------------
 * 
 * Already gives the methods by default:
 *  - save
 *  - findAll
 *  - findById
 *  - deleteById
 * 
 * And findByNodeId is derived automatically from the method name
 * using Spring Data repository conventions.
 */
package com.minte9.monitor.metrics.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.minte9.monitor.common.api.MetricType;
import com.minte9.monitor.metrics.domain.MetricRecord;

public interface MetricMongoRepository extends MongoRepository<MetricRecord, String> {
    List<MetricRecord> findByNodeId(String nodeId);
    List<MetricRecord> findByNodeIdAndMetricType(String nodeId, MetricType metricType);
    List<MetricRecord> findByTimestampAfter(Instant timestamp);
}