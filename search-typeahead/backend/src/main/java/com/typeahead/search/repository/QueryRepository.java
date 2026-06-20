package com.typeahead.search.repository;

import com.typeahead.search.entity.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QueryRepository extends JpaRepository<Query, Long> {

    interface SuggestProjection {
        String getQuery();
        Long getCount();
    }

    List<SuggestProjection> findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(String prefix);
}
