package com.f1app.f1data.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "drivers")
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "driver_ref", nullable = false)
    private String driverRef;

    @Column(name = "permanent_number")
    private Integer permanentNumber;

    @Column(name = "code")
    private String code;

    @Column(name = "given_name", nullable = false)
    private String givenName;

    @Column(name = "family_name", nullable = false)
    private String familyName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "nationality")
    private String nationality;

    @Column(name = "url")
    private String url;

    public Integer getId() { return id; }
    public String getDriverRef() { return driverRef; }
    public Integer getPermanentNumber() { return permanentNumber; }
    public String getCode() { return code; }
    public String getGivenName() { return givenName; }
    public String getFamilyName() { return familyName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getNationality() { return nationality; }
    public String getUrl() { return url; }

    public String getFullName() { return givenName + " " + familyName; }
}
