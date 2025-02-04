/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.broker.stats;

import io.prometheus.client.Counter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.apache.pulsar.common.stats.Metrics;

/**
 */
public class BrokerOperabilityMetrics {
    private static final Counter TOPIC_LOAD_FAILED = Counter.build("topic_load_failed", "-").register();
    private final List<Metrics> metricsList;
    private final String localCluster;
    private final DimensionStats oldTopicLoadStats;
    private final DimensionStats topicLoadStats;
    private final String brokerName;
    private final LongAdder connectionTotalCreatedCount;
    private final LongAdder connectionCreateSuccessCount;
    private final LongAdder connectionCreateFailCount;
    private final LongAdder connectionTotalClosedCount;
    private final LongAdder connectionActive;

    public BrokerOperabilityMetrics(String localCluster, String brokerName) {
        this.metricsList = new ArrayList<>();
        this.localCluster = localCluster;
        this.oldTopicLoadStats = new DimensionStats("topic_load_times", 60);
        this.topicLoadStats = new DimensionStats("pulsar_topic_load_times", 60);
        this.brokerName = brokerName;
        this.connectionTotalCreatedCount = new LongAdder();
        this.connectionCreateSuccessCount = new LongAdder();
        this.connectionCreateFailCount = new LongAdder();
        this.connectionTotalClosedCount = new LongAdder();
        this.connectionActive = new LongAdder();
    }

    public List<Metrics> getMetrics() {
        generate();
        return metricsList;
    }

    private void generate() {
        metricsList.add(getOldTopicLoadMetrics());
        metricsList.add(getTopicLoadMetrics());
        metricsList.add(getConnectionMetrics());
    }

    public Metrics generateConnectionMetrics() {
        return getConnectionMetrics();
    }

    Metrics getConnectionMetrics() {
        Metrics rMetrics = Metrics.create(getDimensionMap("broker_connection"));
        rMetrics.put("brk_connection_created_total_count", connectionTotalCreatedCount.longValue());
        rMetrics.put("brk_connection_create_success_count", connectionCreateSuccessCount.longValue());
        rMetrics.put("brk_connection_create_fail_count", connectionCreateFailCount.longValue());
        rMetrics.put("brk_connection_closed_total_count", connectionTotalClosedCount.longValue());
        rMetrics.put("brk_active_connections", connectionActive.longValue());
        return rMetrics;
    }

    Map<String, String> getDimensionMap(String metricsName) {
        Map<String, String> dimensionMap = new HashMap<>();
        dimensionMap.put("broker", brokerName);
        dimensionMap.put("cluster", localCluster);
        dimensionMap.put("metric", metricsName);
        return dimensionMap;
    }

    Metrics getOldTopicLoadMetrics() {
        Metrics metrics = getDimensionMetrics("topic_load_times", "topic_load", oldTopicLoadStats);
        return metrics;
    }

    Metrics getTopicLoadMetrics() {
        Metrics metrics = getDimensionMetrics("pulsar_topic_load_times", "topic_load", topicLoadStats);
        metrics.put("brk_topic_load_failed_count", TOPIC_LOAD_FAILED.get());
        return metrics;
    }

    Metrics getDimensionMetrics(String metricsName, String dimensionName, DimensionStats stats) {
        Metrics dMetrics = Metrics.create(getDimensionMap(metricsName));

        DimensionStats.DimensionStatsSnapshot statsSnapshot = stats.getSnapshot();
        dMetrics.put("brk_" + dimensionName + "_time_mean_ms", statsSnapshot.getMeanDimension());
        dMetrics.put("brk_" + dimensionName + "_time_median_ms", statsSnapshot.getMedianDimension());
        dMetrics.put("brk_" + dimensionName + "_time_75percentile_ms", statsSnapshot.getDimension75());
        dMetrics.put("brk_" + dimensionName + "_time_95percentile_ms", statsSnapshot.getDimension95());
        dMetrics.put("brk_" + dimensionName + "_time_99_percentile_ms", statsSnapshot.getDimension99());
        dMetrics.put("brk_" + dimensionName + "_time_99_9_percentile_ms", statsSnapshot.getDimension999());
        dMetrics.put("brk_" + dimensionName + "_time_99_99_percentile_ms", statsSnapshot.getDimension9999());
        dMetrics.put("brk_" + dimensionName + "_rate_s", statsSnapshot.getDimensionCount());

        return dMetrics;
    }

    public void reset() {
        metricsList.clear();
        oldTopicLoadStats.reset();
        topicLoadStats.reset();
    }

    public void recordTopicLoadTimeValue(long topicLoadLatencyMs) {
        oldTopicLoadStats.recordDimensionTimeValue(topicLoadLatencyMs, TimeUnit.MILLISECONDS);
        topicLoadStats.recordDimensionTimeValue(topicLoadLatencyMs, TimeUnit.MILLISECONDS);
    }

    public void recordTopicLoadFailed() {
        this.TOPIC_LOAD_FAILED.inc();
    }

    public void recordConnectionCreate() {
        this.connectionTotalCreatedCount.increment();
        this.connectionActive.increment();
    }

    public void recordConnectionClose() {
        this.connectionTotalClosedCount.increment();
        this.connectionActive.decrement();
    }

    public void recordConnectionCreateSuccess() {
        this.connectionCreateSuccessCount.increment();
    }

    public void recordConnectionCreateFail() {
        this.connectionCreateFailCount.increment();
    }
}
