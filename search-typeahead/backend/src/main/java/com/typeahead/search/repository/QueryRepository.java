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

    List<SuggestProjection> findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(String prefix);

    @Modifying
    @Transactional
    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value = 
        "INSERT INTO queries (query, count, last_searched_at) VALUES (:query, 1, :lastSearchedAt) " +
        "ON CONFLICT (query) DO UPDATE SET count = queries.count + 1, last_searched_at = EXCLUDED.last_searched_at")
    void upsertSearchQuery(@Param("query") String query, @Param("lastSearchedAt") Timestamp lastSearchedAt);
}
