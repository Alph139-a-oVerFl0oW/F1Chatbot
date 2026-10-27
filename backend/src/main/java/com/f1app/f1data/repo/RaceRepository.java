package com.f1app.f1data.repo;

import java.util.List;
import java.util.Optional;

import com.f1app.f1data.domain.Race;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RaceRepository extends JpaRepository<Race, Integer> {

    @Query("select distinct r.season from Race r order by r.season desc")
    List<Integer> findSeasons();

    @Query("select r from Race r join fetch r.circuit c where r.season = :season order by r.round")
    List<Race> findBySeason(@Param("season") int season);

    @Query("select r from Race r join fetch r.circuit c where r.season = :season and r.round = :round")
    Optional<Race> findBySeasonAndRound(@Param("season") int season, @Param("round") int round);

    /**
     * Most recent race that already has results. Pass PageRequest.of(0, 1);
     * JPQL has no LIMIT, so the bound comes from the Pageable.
     */
    @Query("""
            select r from Race r join fetch r.circuit c
            where exists (select 1 from Result res where res.race = r)
            order by r.raceDate desc
            """)
    List<Race> findMostRecentCompleted(Pageable pageable);

    @Query("""
            select r from Race r join fetch r.circuit c
            where r.raceDate > current_date
            order by r.raceDate asc
            """)
    List<Race> findUpcoming(Pageable pageable);
}
