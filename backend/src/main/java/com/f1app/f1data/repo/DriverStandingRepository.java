package com.f1app.f1data.repo;

import java.util.List;

import com.f1app.f1data.domain.DriverStanding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverStandingRepository extends JpaRepository<DriverStanding, Integer> {

    /** Latest round in a season that actually has standings recorded. */
    @Query("select max(s.race.round) from DriverStanding s where s.race.season = :season")
    Integer findLatestRound(@Param("season") int season);

    @Query("""
            select s from DriverStanding s
            join fetch s.driver d
            join fetch s.race r
            where r.season = :season and r.round = :round
            order by s.position
            """)
    List<DriverStanding> findBySeasonAndRound(@Param("season") int season, @Param("round") int round);

    @Query("""
            select s from DriverStanding s
            join fetch s.race r
            where s.driver.driverRef = :ref and s.position = 1
              and r.round = (select max(r2.round) from Race r2 where r2.season = r.season)
            """)
    List<DriverStanding> findTitles(@Param("ref") String driverRef);
}
