/**
 * Simple in-memory repository for early development.
 * 
 * This is useful:
 *  - zero external database setup for alert-service
 *  - easy to inspect behavior
 *  - easy to replace later 
 */

package com.minte9.monitor.alert.repository;

import org.springframework.stereotype.Repository;
import com.minte9.monitor.alert.domain.AlertRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InMemoryAlertRepository implements AlertRepository {
    
    private final List<AlertRecord> storage = new CopyOnWriteArrayList<>();

    @Override
    public AlertRecord save(AlertRecord alertRecord) {
        storage.add(alertRecord);
        return alertRecord;
    }

    @Override
    public List<AlertRecord> findAll() {
        return new ArrayList<>(storage);
    }

    @Override
    public List<AlertRecord> findByNodeId(String nodeId) {
        return storage.stream()
                .filter(alert -> alert.nodeId().equals(nodeId))
                .toList();
    }

    @Override
    public List<AlertRecord> findByStatus(String status) {
        return storage.stream()
                .filter(alert -> alert.status().name().equalsIgnoreCase(status))
                .toList();
    }

    @Override
    public Optional<AlertRecord> findById(String id) {
        return storage.stream()
                .filter(alert -> alert.id().equals(id))
                .findFirst();
    }
}
