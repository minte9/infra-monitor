/**
 * This collector uses OSHI to read host system metrics.
 * 
 * We produce MetricIngestRequest objects directly because this is the
 * format expected by metrics-service.
 */

package com.minte9.monitor.agent.collector;

import com.minte9.monitor.common.api.MetricIngestRequest;
import com.minte9.monitor.common.api.MetricType;
import org.springframework.stereotype.Component;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SystemMetricsCollector {
    
    private final SystemInfo systemInfo;
    private long[] previousCpuTicks;

    // Constructor
    public SystemMetricsCollector() {
        this.systemInfo = new SystemInfo();

        // CPU usage is not read from a single static number.  
        // OSHI calculates it beween two snapshots of CPU ticks.  
        this.previousCpuTicks = systemInfo.getHardware().getProcessor().getSystemCpuLoadTicks();
    }

    // Metrics collection (system)
    public List<MetricIngestRequest> collect(String nodeId) {
        List<MetricIngestRequest> metrics = new ArrayList<>();
        Instant now = Instant.now();

        metrics.add(buildCpuMetric(nodeId, now));
        metrics.add(buildRamMetric(nodeId, now));
        metrics.add(buildDiskMetric(nodeId, now));

        return metrics;
    }

    // CPU
    private MetricIngestRequest buildCpuMetric(String nodeId, Instant timestamp) {
            CentralProcessor processor = systemInfo.getHardware().getProcessor();
    
            double cpuLoad = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100.0;
            previousCpuTicks = processor.getSystemCpuLoadTicks();
    
            double loadAverage = processor.getSystemLoadAverage(1)[0];
    
            Map<String, Object> payload = new HashMap<>();
            payload.put("usagePercent", round(cpuLoad));
            payload.put("systemLoadAverage", round(loadAverage));
    
            return new MetricIngestRequest(
                    nodeId,
                    MetricType.CPU,
                    timestamp,
                    payload
            );
        }
        
        // RAM
        private MetricIngestRequest buildRamMetric(String nodeId, Instant timestamp) {
            GlobalMemory memory = systemInfo.getHardware().getMemory();
    
            long totalBytes = memory.getTotal();
            long availableBytes = memory.getAvailable();
            long usedBytes = totalBytes - availableBytes;
    
            double totalMb = bytesToMb(totalBytes);
            double usedMb = bytesToMb(usedBytes);
            double usagePercent = totalBytes == 0 ? 0.0 : ((double) usedBytes / totalBytes) * 100.0;
    
            Map<String, Object> payload = new HashMap<>();
            payload.put("totalMb", round(totalMb));
            payload.put("usedMb", round(usedMb));
            payload.put("usagePercent", round(usagePercent));
    
            return new MetricIngestRequest(
                    nodeId,
                    MetricType.RAM,
                    timestamp,
                    payload
            );
        }
        
        // Disk
        private MetricIngestRequest buildDiskMetric(String nodeId, Instant timestamp) {
            FileSystem fileSystem = systemInfo.getOperatingSystem().getFileSystem();
    
            long totalBytes = 0L;
            long usableBytes = 0L;
    
            for (OSFileStore fileStore : fileSystem.getFileStores()) {
                totalBytes += fileStore.getTotalSpace();
                usableBytes += fileStore.getUsableSpace();
            }
    
            long usedBytes = totalBytes - usableBytes;
            double totalGb = bytesToGb(totalBytes);
            double usedGb = bytesToGb(usedBytes);
            double usagePercent = totalBytes == 0 ? 0.0 : ((double) usedBytes / totalBytes) * 100.0;
    
            Map<String, Object> payload = new HashMap<>();
            payload.put("totalGb", round(totalGb));
            payload.put("usedGb", round(usedGb));
            payload.put("usagePercent", round(usagePercent));
    
            return new MetricIngestRequest(
                    nodeId,
                    MetricType.DISK,
                    timestamp,
                    payload
            );
        }

        // Utills
        private double bytesToMb(long bytes) {
            return bytes / 1024.0 / 1024.0;
        }

        private double bytesToGb(long bytes) {
            return bytes / 1024.0 / 1024.0 / 1024.0;
        }

        private double round(double value) {
            return Math.round(value * 100.0) / 100.0;
        }
}
