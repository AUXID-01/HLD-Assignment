package com.typeahead.search.entity;

import jakarta.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "queries", indexes = {
    @Index(name = "idx_query", columnList = "query")
})
public class Query {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String query;

    @Column(nullable = false)
    private Long count;

    @Column(name = "last_searched_at")
    private Timestamp lastSearchedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public Timestamp getLastSearchedAt() {
        return lastSearchedAt;
    }

    public void setLastSearchedAt(Timestamp lastSearchedAt) {
        this.lastSearchedAt = lastSearchedAt;
    }
}
