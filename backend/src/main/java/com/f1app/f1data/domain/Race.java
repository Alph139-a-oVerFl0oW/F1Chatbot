package com.f1app.f1data.domain;

import java.time.LocalDate;
import java.time.LocalTime;

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
@Table(name = "races")
public class Race {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "season", nullable = false)
    private Integer season;

    @Column(name = "round", nullable = false)
    private Integer round;

    @Column(name = "name", nullable = false)
    private String name;

    // LAZY everywhere: open-in-view is off, so anything the response needs is
    // pulled explicitly with a JOIN FETCH in the repository query.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circuit_id", nullable = false)
    private Circuit circuit;

    @Column(name = "race_date")
    private LocalDate raceDate;

    @Column(name = "race_time")
    private LocalTime raceTime;

    @Column(name = "url")
    private String url;

    @Column(name = "sprint_date")
    private LocalDate sprintDate;

    public Integer getId() { return id; }
    public Integer getSeason() { return season; }
    public Integer getRound() { return round; }
    public String getName() { return name; }
    public Circuit getCircuit() { return circuit; }
    public LocalDate getRaceDate() { return raceDate; }
    public LocalTime getRaceTime() { return raceTime; }
    public String getUrl() { return url; }
    public LocalDate getSprintDate() { return sprintDate; }
}
