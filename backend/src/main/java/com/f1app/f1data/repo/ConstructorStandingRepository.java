package com.f1app.f1data.repo;

import java.util.List;

import com.f1app.f1data.domain.ConstructorStanding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConstructorStandingRepository extends JpaRepository<ConstructorStanding, Integer> {

    @Query("select max(s.race.round) from ConstructorStanding s where s.race.season = :season")
    Integer findLatestRound(@Param("season") int season);

    @Query("""
            select s from ConstructorStanding s
            join fetch s.constructor c
            join fetch s.race r
            where r.season = :season and r.round = :round
            order by s.position
            """)
    List<ConstructorStanding> findBySeasonAndRound(@Param("season") int season, @Param("round") int round);
}
