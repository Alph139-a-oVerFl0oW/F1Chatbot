package com.f1app.f1data.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "circuits")
public class Circuit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "circuit_ref", nullable = false)
    private String circuitRef;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "locality")
    private String locality;

    @Column(name = "country")
    private String country;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "url")
    private String url;

    public Integer getId() { return id; }
    public String getCircuitRef() { return circuitRef; }
    public String getName() { return name; }
    public String getLocality() { return locality; }
    public String getCountry() { return country; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getUrl() { return url; }
}
