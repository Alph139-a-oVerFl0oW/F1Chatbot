package com.f1app.auth;

import java.util.List;
import java.util.Locale;

import com.f1app.auth.AuthDtos.AuthResponse;
import com.f1app.auth.AuthDtos.FavoriteItem;
import com.f1app.auth.AuthDtos.LoginRequest;
import com.f1app.auth.AuthDtos.RegisterRequest;
import com.f1app.auth.AuthDtos.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final UserFavoriteRepository favoriteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final FavoriteRefResolver refResolver;

    public AuthService(UserRepository userRepository,
                       UserFavoriteRepository favoriteRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       FavoriteRefResolver refResolver) {
        this.userRepository = userRepository;
        this.favoriteRepository = favoriteRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refResolver = refResolver;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim();
        if (userRepository.existsByEmail(email)) {
            // 409 rather than 400: the request was well-formed, the state conflicts.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with that email already exists");
        }

        User user = new User(email,
                request.displayName().trim(),
                passwordEncoder.encode(request.password()));
        user = userRepository.save(user);
        log.info("Registered user {}", user.getId());

        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim()).orElse(null);

        // Deliberately identical response for "no such user" and "wrong password".
        // Distinguishing them turns the login endpoint into an account enumerator.
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is disabled");
        }

        return tokenFor(user);
    }

    public UserProfile profile(User user) {
        return toProfile(user);
    }

    // ------------------------------------------------------------------
    // Favorites
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<FavoriteItem> favorites(User user) {
        return favoriteRepository.findByUserId(user.getId()).stream()
                .map(this::toItem)
                .toList();
    }

    @Transactional
    public FavoriteItem addFavorite(User user, String rawType, String entityRef) {
        FavoriteType type = parseType(rawType);
        String ref = entityRef.trim();

        // entity_ref carries no foreign key (it points at a different table per
        // type), so integrity is enforced here instead.
        if (!refResolver.exists(type, ref)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No " + type.name().toLowerCase(Locale.ROOT) + " with ref '" + ref + "'");
        }

        // Idempotent: favouriting twice returns the existing row rather than 409.
        // The UI toggles a star; a double-click should not be an error.
        return favoriteRepository.find(user.getId(), type.name(), ref)
                .map(this::toItem)
                .orElseGet(() -> toItem(favoriteRepository.save(new UserFavorite(user, type, ref))));
    }

    @Transactional
    public void removeFavorite(User user, String rawType, String entityRef) {
        FavoriteType type = parseType(rawType);
        favoriteRepository.find(user.getId(), type.name(), entityRef.trim())
                .ifPresent(favoriteRepository::delete);
        // Deleting something that isn't there is a no-op, not a 404 - the caller
        // wanted it gone and it is gone.
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private FavoriteType parseType(String raw) {
        try {
            return FavoriteType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "entityType must be one of DRIVER, CONSTRUCTOR, CIRCUIT");
        }
    }

    private FavoriteItem toItem(UserFavorite favorite) {
        return new FavoriteItem(
                favorite.getId(),
                favorite.getEntityType(),
                favorite.getEntityRef(),
                displayNameFor(favorite));
    }

    /** Resolve a human-readable label so the UI doesn't have to fetch each ref. */
    private String displayNameFor(UserFavorite favorite) {
        try {
            return refResolver.displayName(
                    FavoriteType.valueOf(favorite.getEntityType()), favorite.getEntityRef());
        } catch (RuntimeException e) {
            return favorite.getEntityRef();
        }
    }

    private AuthResponse tokenFor(User user) {
        return new AuthResponse(jwtService.issue(user), jwtService.expirySeconds(), toProfile(user));
    }

    private UserProfile toProfile(User user) {
        return new UserProfile(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
    }
}
