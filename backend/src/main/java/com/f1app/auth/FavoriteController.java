package com.f1app.auth;

import com.f1app.auth.AuthDtos.FavoriteItem;
import com.f1app.auth.AuthDtos.FavoriteRequest;
import com.f1app.auth.AuthDtos.FavoritesResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every method is scoped to the authenticated principal - there is no userId in
 * any path or body. That is deliberate: an endpoint like
 * /api/users/{id}/favorites invites the classic broken-object-level-authorization
 * bug where someone edits the id and reads another user's data. If the id can
 * only come from the verified token, that bug cannot be written.
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final AuthService authService;

    public FavoriteController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public FavoritesResponse list(@AuthenticationPrincipal User user) {
        return new FavoritesResponse(authService.favorites(user));
    }

    @PostMapping
    public FavoriteItem add(@AuthenticationPrincipal User user,
                            @Valid @RequestBody FavoriteRequest request) {
        return authService.addFavorite(user, request.entityType(), request.entityRef());
    }

    @DeleteMapping("/{entityType}/{entityRef}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal User user,
                                       @PathVariable String entityType,
                                       @PathVariable String entityRef) {
        authService.removeFavorite(user, entityType, entityRef);
        return ResponseEntity.noContent().build();
    }
}
