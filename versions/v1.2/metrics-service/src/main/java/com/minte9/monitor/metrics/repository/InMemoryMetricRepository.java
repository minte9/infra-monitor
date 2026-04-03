/**
 * In memory repository
 * 
 * Why CopyOnWriteArrayList?
 *  - simple thread-safe option
 *  - good enought for low write volume
 */
package com.minte9.monitor.metrics.repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Repository;

import com.minte9.monitor.metrics.domain.MetricRecord;

@Repository
public class InMemoryMetricRepository implements MetricRepository {
 
    private final CopyOnWriteArrayList<MetricRecord> storage = 
        new CopyOnWriteArrayList<>();

    @Override
    public MetricRecord save(MetricRecord metricRecord) {
        storage.add(metricRecord);
        return metricRecord;
    }

    @Override
    public List<MetricRecord> findAll() {
        return List.copyOf(storage);
    }

    @Override
    public List<MetricRecord> findByNodeId(String nodeId) {
        return storage.stream()
                .filter(record -> record.nodeId().equals(nodeId))
                .toList();
    }
}
