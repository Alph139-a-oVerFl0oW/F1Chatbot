package com.f1app.f1data.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Ergast status codes. Ids come from upstream and are never generated locally,
 * so there is no @GeneratedValue here.
 */
@Entity
@Table(name = "statuses")
public class Status {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "status", nullable = false)
    private String status;

    public Integer getId() { return id; }
    public String getStatus() { return status; }
}
