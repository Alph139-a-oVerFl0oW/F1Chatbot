package com.f1app.f1data.repo;

import java.util.List;

import com.f1app.f1data.domain.Result;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResultRepository extends JpaRepository<Result, Integer> {

    @Query("""
            select res from Result res
            join fetch res.driver d
            join fetch res.constructor c
            left join fetch res.status s
            where res.race.id = :raceId
            order by res.positionOrder
            """)
    List<Result> findByRaceId(@Param("raceId") int raceId);

    @Query("select count(res) from Result res where res.driver.driverRef = :ref")
    long countStarts(@Param("ref") String driverRef);

    @Query("select count(res) from Result res where res.driver.driverRef = :ref and res.position = 1")
    long countWins(@Param("ref") String driverRef);

    @Query("select count(res) from Result res where res.driver.driverRef = :ref and res.position <= 3")
    long countPodiums(@Param("ref") String driverRef);

    @Query("select coalesce(sum(res.points), 0) from Result res where res.driver.driverRef = :ref")
    java.math.BigDecimal sumPoints(@Param("ref") String driverRef);

    @Query("select min(res.race.season) from Result res where res.driver.driverRef = :ref")
    Integer firstSeason(@Param("ref") String driverRef);

    @Query("select max(res.race.season) from Result res where res.driver.driverRef = :ref")
    Integer lastSeason(@Param("ref") String driverRef);

    @Query("""
            select res from Result res
            join fetch res.race r
            join fetch r.circuit
            join fetch res.constructor c
            left join fetch res.status s
            where res.driver.driverRef = :ref and r.season = :season
            order by r.round
            """)
    List<Result> findByDriverAndSeason(@Param("ref") String driverRef, @Param("season") int season);
}
