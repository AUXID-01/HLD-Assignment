package com.typeahead.search.repository;

import com.typeahead.search.entity.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Repository
public interface QueryRepository extends JpaRepository<Query, Long> {

    interface SuggestProjection {
        String getQuery();
        Long getCount();
    }

    interface TrendingProjection {
        String getQuery();
        Long getCount();
        Double getScore();
    }

    List<SuggestProjection> findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(String prefix);

    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value = 
        "SELECT query, count, " +
        "(count * EXP(-0.0289 * (EXTRACT(EPOCH FROM (NOW() - last_searched_at)) / 3600.0))) AS score " +
        "FROM queries " +
        "WHERE UPPER(query) LIKE UPPER(CONCAT(:prefix, '%')) " +
        "ORDER BY score DESC " +
        "LIMIT 10")
    List<TrendingProjection> findTrendingSuggestions(@Param("prefix") String prefix);

    @Modifying
    @Transactional
    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value = 
        "INSERT INTO queries (query, count, last_searched_at) VALUES (:query, 1, :lastSearchedAt) " +
        "ON CONFLICT (query) DO UPDATE SET count = queries.count + 1, last_searched_at = EXCLUDED.last_searched_at")
    void upsertSearchQuery(@Param("query") String query, @Param("lastSearchedAt") Timestamp lastSearchedAt);

    /**
     * Batch-aware UPSERT: applies an aggregated count delta (N searches buffered since last flush)
     * and the latest observed timestamp in a single DB round-trip.
     * GREATEST() ensures last_searched_at is never rolled backward if two flushes overlap.
     */
    @Modifying
    @Transactional
    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value =
        "INSERT INTO queries (query, count, last_searched_at) VALUES (:query, :incrementCount, :lastSearchedAt) " +
        "ON CONFLICT (query) DO UPDATE SET " +
        "count = queries.count + :incrementCount, " +
        "last_searched_at = GREATEST(queries.last_searched_at, EXCLUDED.last_searched_at)")
    void batchUpsertSearchQuery(
        @Param("query") String query,
        @Param("incrementCount") long incrementCount,
        @Param("lastSearchedAt") Timestamp lastSearchedAt);
}
