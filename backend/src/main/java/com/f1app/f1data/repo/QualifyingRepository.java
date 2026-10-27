package com.f1app.f1data.repo;

import java.util.List;

import com.f1app.f1data.domain.QualifyingResult;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QualifyingRepository extends JpaRepository<QualifyingResult, Integer> {

    @Query("""
            select q from QualifyingResult q
            join fetch q.driver d
            join fetch q.constructor c
            where q.race.id = :raceId
            order by q.position
            """)
    List<QualifyingResult> findByRaceId(@Param("raceId") int raceId);

    @Query("select count(q) from QualifyingResult q where q.driver.driverRef = :ref and q.position = 1")
    long countPoles(@Param("ref") String driverRef);
}
