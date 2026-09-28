package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final GetProfileUseCase getProfileUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;

    public UserController(GetProfileUseCase getProfileUseCase, UpdateProfileUseCase updateProfileUseCase, ChangePasswordUseCase changePasswordUseCase) {
        this.getProfileUseCase = getProfileUseCase;
        this.updateProfileUseCase = updateProfileUseCase;
        this.changePasswordUseCase = changePasswordUseCase;
    }

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        User user = getProfileUseCase.execute(userId);
        return ResponseEntity.ok(UserProfileResponse.fromDomain(user));
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateProfileRequest request) {

        Long userId = Long.parseLong(jwt.getSubject());
        UpdateProfileCommand command = new UpdateProfileCommand(
                userId,
                request.name(),
                request.email(),
                request.phone()
        );

        User updatedUser = updateProfileUseCase.execute(command);
        return ResponseEntity.ok(UserProfileResponse.fromDomain(updatedUser));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ChangePasswordRequest request) {

        Long userId = Long.parseLong(jwt.getSubject());
        ChangePasswordCommand command = new ChangePasswordCommand(
                userId,
                request.currentPassword(),
                request.newPassword()
        );

        changePasswordUseCase.execute(command);
        return ResponseEntity.noContent().build();
    }
}
