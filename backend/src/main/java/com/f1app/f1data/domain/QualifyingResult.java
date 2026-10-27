package com.f1app.f1data.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "qualifying")
public class QualifyingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "constructor_id", nullable = false)
    private Constructor constructor;

    @Column(name = "position")
    private Integer position;

    @Column(name = "q1")
    private String q1;

    @Column(name = "q2")
    private String q2;

    @Column(name = "q3")
    private String q3;

    public Integer getId() { return id; }
    public Race getRace() { return race; }
    public Driver getDriver() { return driver; }
    public Constructor getConstructor() { return constructor; }
    public Integer getPosition() { return position; }
    public String getQ1() { return q1; }
    public String getQ2() { return q2; }
    public String getQ3() { return q3; }
}
