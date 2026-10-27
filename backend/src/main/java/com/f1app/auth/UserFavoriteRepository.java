package com.f1app.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Integer> {

    @Query("select f from UserFavorite f where f.user.id = :userId order by f.createdAt")
    List<UserFavorite> findByUserId(@Param("userId") int userId);

    @Query("""
            select f from UserFavorite f
            where f.user.id = :userId and f.entityType = :type and f.entityRef = :ref
            """)
    Optional<UserFavorite> find(@Param("userId") int userId,
                                @Param("type") String entityType,
                                @Param("ref") String entityRef);
}
