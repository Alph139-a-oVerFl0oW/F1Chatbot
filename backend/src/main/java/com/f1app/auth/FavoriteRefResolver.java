package com.f1app.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves favorite refs to display names, and checks they exist.
 *
 * entity_ref in user_favorites cannot carry a foreign key, because which table
 * it points at depends on entity_type. This class is where that integrity is
 * enforced instead.
 *
 * Plain JDBC rather than reaching into the f1data read layer: these are three
 * single-column lookups by an indexed unique key, and going through JPA would
 * mean loading whole entities to read one field. It also keeps the auth package
 * from depending on f1data's service API.
 */
@Component
public class FavoriteRefResolver {

    private final JdbcTemplate jdbc;

    public FavoriteRefResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean exists(FavoriteType type, String ref) {
        return lookup(type, ref).isPresent();
    }

    /** Human-readable label, falling back to the ref itself if it has vanished. */
    public String displayName(FavoriteType type, String ref) {
        return lookup(type, ref).orElse(ref);
    }

    private Optional<String> lookup(FavoriteType type, String ref) {
        String sql = switch (type) {
            case DRIVER -> "SELECT given_name || ' ' || family_name FROM drivers WHERE driver_ref = ?";
            case CONSTRUCTOR -> "SELECT name FROM constructors WHERE constructor_ref = ?";
            case CIRCUIT -> "SELECT name FROM circuits WHERE circuit_ref = ?";
        };
        List<String> found = jdbc.queryForList(sql, String.class, ref);
        return found.isEmpty() ? Optional.empty() : Optional.ofNullable(found.get(0));
    }
}
