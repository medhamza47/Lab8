package com.example.util;

import java.util.logging.Logger;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

public class PerformanceReport {
    private static final Logger LOGGER = Logger.getLogger(PerformanceReport.class.getName());
    private final Statistics statistics;

    public PerformanceReport(SessionFactory sessionFactory) {
        if (sessionFactory == null) {
            throw new IllegalArgumentException("La SessionFactory est obligatoire");
        }
        statistics = sessionFactory.getStatistics();
    }

    public Report snapshot() {
        return new Report(
                statistics.getQueryExecutionCount(),
                statistics.getSecondLevelCacheHitCount(),
                statistics.getSecondLevelCacheMissCount(),
                statistics.getSecondLevelCachePutCount(),
                statistics.getTransactionCount(),
                queryExecutionTimeTotal(),
                statistics.getQueryExecutionMaxTime());
    }

    private long queryExecutionTimeTotal() {
        long total = 0;
        for (String query : statistics.getQueries()) {
            total += statistics.getQueryStatistics(query).getExecutionTotalTime();
        }
        return total;
    }

    public void printReport() {
        Report report = snapshot();
        LOGGER.info(() -> "Hibernate performance: " + report);
    }

    public static final class Report {
        private final long queryCount;
        private final long cacheHits;
        private final long cacheMisses;
        private final long cachePuts;
        private final long transactions;
        private final long queryTimeMillis;
        private final long maxQueryTimeMillis;

        private Report(long queryCount, long cacheHits, long cacheMisses, long cachePuts,
                       long transactions, long queryTimeMillis, long maxQueryTimeMillis) {
            this.queryCount = queryCount;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.cachePuts = cachePuts;
            this.transactions = transactions;
            this.queryTimeMillis = queryTimeMillis;
            this.maxQueryTimeMillis = maxQueryTimeMillis;
        }

        public long getQueryCount() { return queryCount; }
        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }
        public long getCachePuts() { return cachePuts; }
        public long getTransactions() { return transactions; }
        public long getQueryTimeMillis() { return queryTimeMillis; }
        public long getMaxQueryTimeMillis() { return maxQueryTimeMillis; }

        @Override
        public String toString() {
            return "Report{queries=" + queryCount + ", cacheHits=" + cacheHits +
                    ", cacheMisses=" + cacheMisses + ", cachePuts=" + cachePuts +
                    ", transactions=" + transactions + ", queryTimeMillis=" + queryTimeMillis +
                    ", maxQueryTimeMillis=" + maxQueryTimeMillis + "}";
        }
    }
}
