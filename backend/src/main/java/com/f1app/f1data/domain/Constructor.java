package com.f1app.f1data.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "constructors")
public class Constructor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "constructor_ref", nullable = false)
    private String constructorRef;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "nationality")
    private String nationality;

    @Column(name = "url")
    private String url;

    public Integer getId() { return id; }
    public String getConstructorRef() { return constructorRef; }
    public String getName() { return name; }
    public String getNationality() { return nationality; }
    public String getUrl() { return url; }
}
