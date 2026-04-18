/**
 * Mongo document
 * 
 * @Document(collection = 'metrics) tells Spring Data Mongodb
 * this class is stored in the metrics collection.
 * 
 * @Id marks the Mongo document identifier.
 * Spring Data MondoDb uses this for document identity and mapping.
 */
package com.minte9.monitor.metrics.domain;

import com.minte9.monitor.common.api.MetricType;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "metrics")
public record MetricRecord(
    @Id String id,
    @Indexed String nodeId,
    MetricType metricType,
    Instant timestamp,
    Map<String, Object> payload,
    Instant receivedAt
) {}
