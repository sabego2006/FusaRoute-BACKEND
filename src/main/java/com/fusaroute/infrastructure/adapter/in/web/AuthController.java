package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.RegisterUserCommand;
import com.fusaroute.domain.port.in.RegisterUserUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticacion. Hoy solo el registro (RF-01, SCRUM-31); el login y
 * el JWT llegan con SCRUM-13.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> register(@RequestBody RegisterUserRequest request) {
        User user = registerUserUseCase.register(
                new RegisterUserCommand(request.name(), request.email(), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterUserResponse.from(user));
    }
}
