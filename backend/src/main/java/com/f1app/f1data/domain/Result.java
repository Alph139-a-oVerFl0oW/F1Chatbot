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
@Table(name = "results")
public class Result {

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

    @Column(name = "car_number")
    private Integer carNumber;

    @Column(name = "grid")
    private Integer grid;

    @Column(name = "position")
    private Integer position;

    @Column(name = "position_text")
    private String positionText;

    @Column(name = "position_order", nullable = false)
    private Integer positionOrder;

    @Column(name = "points", nullable = false)
    private BigDecimal points;

    @Column(name = "laps")
    private Integer laps;

    @Column(name = "finish_time")
    private String finishTime;

    @Column(name = "fastest_lap_time")
    private String fastestLapTime;

    @Column(name = "fastest_lap_rank")
    private Integer fastestLapRank;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id")
    private Status status;

    public Integer getId() { return id; }
    public Race getRace() { return race; }
    public Driver getDriver() { return driver; }
    public Constructor getConstructor() { return constructor; }
    public Integer getCarNumber() { return carNumber; }
    public Integer getGrid() { return grid; }
    public Integer getPosition() { return position; }
    public String getPositionText() { return positionText; }
    public Integer getPositionOrder() { return positionOrder; }
    public BigDecimal getPoints() { return points; }
    public Integer getLaps() { return laps; }
    public String getFinishTime() { return finishTime; }
    public String getFastestLapTime() { return fastestLapTime; }
    public Integer getFastestLapRank() { return fastestLapRank; }
    public Status getStatus() { return status; }
}
