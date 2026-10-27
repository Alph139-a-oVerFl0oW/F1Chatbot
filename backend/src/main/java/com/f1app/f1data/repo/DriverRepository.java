package com.f1app.f1data.repo;

import java.util.List;
import java.util.Optional;

import com.f1app.f1data.domain.Driver;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverRepository extends JpaRepository<Driver, Integer> {

    Optional<Driver> findByDriverRef(String driverRef);

//     @Query("""
//             select d from Driver d
//             where lower(d.givenName) like lower(concat('%', :q, '%'))
//                or lower(d.familyName) like lower(concat('%', :q, '%'))
//             """)
    @Query("""
        select d from Driver d
        where lower(concat(d.givenName, ' ', d.familyName)) like lower(concat('%', :q, '%'))
           or lower(d.familyName) like lower(concat('%', :q, '%'))
        """)

    Page<Driver> search(@Param("q") String query, Pageable pageable);

    @Query("""
            select distinct res.driver from Result res
            where res.race.season = :season
            order by res.driver.familyName
            """)
    List<Driver> findBySeason(@Param("season") int season);
}
