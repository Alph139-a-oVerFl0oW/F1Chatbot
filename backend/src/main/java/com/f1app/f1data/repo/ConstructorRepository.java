package com.f1app.f1data.repo;

import java.util.List;
import java.util.Optional;

import com.f1app.f1data.domain.Constructor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConstructorRepository extends JpaRepository<Constructor, Integer> {

    Optional<Constructor> findByConstructorRef(String constructorRef);

    @Query("""
            select distinct res.constructor from Result res
            where res.race.season = :season
            order by res.constructor.name
            """)
    List<Constructor> findBySeason(@Param("season") int season);
}
