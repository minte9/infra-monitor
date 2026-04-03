/**
 * Repository abstraction
 */
package com.minte9.monitor.metrics.repository;

import com.minte9.monitor.metrics.domain.MetricRecord;
import java.util.List;

public interface MetricRepository {
    MetricRecord save(MetricRecord metricRecord);    
    List<MetricRecord> findAll();
    List<MetricRecord> findByNodeId(String nodeId);
}
