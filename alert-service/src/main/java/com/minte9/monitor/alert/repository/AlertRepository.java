/**
 * This repository abstraction keeps storage details 
 * out of the service layer.
 * 
 * For now: only in memory
 * Later: MongoDB, PostgreSQL, or Elasticsearch 
 */

package com.minte9.monitor.alert.repository;

import com.minte9.monitor.alert.domain.AlertRecord;
import java.util.List;
import java.util.Optional;

public interface AlertRepository {
    
    AlertRecord save(AlertRecord alertRecord);

    List<AlertRecord> findAll();

    List<AlertRecord> findByNodeId(String nodeId);

    List<AlertRecord> findByStatus(String status);

    Optional<AlertRecord> findById(String id);
}
