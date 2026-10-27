package com.f1app.f1data.domain;

import java.math.BigDecimal;

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
@Table(name = "driver_standings")
public class DriverStanding {

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

    @Column(name = "points", nullable = false)
    private BigDecimal points;

    @Column(name = "position")
    private Integer position;

    @Column(name = "position_text")
    private String positionText;

    @Column(name = "wins", nullable = false)
    private Integer wins;

    public Integer getId() { return id; }
    public Race getRace() { return race; }
    public Driver getDriver() { return driver; }
    public BigDecimal getPoints() { return points; }
    public Integer getPosition() { return position; }
    public String getPositionText() { return positionText; }
    public Integer getWins() { return wins; }
}
