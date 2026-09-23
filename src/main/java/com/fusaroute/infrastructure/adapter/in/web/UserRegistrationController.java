package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.CreateUserUseCasePort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST para la gestión de usuarios.
 * Adaptador de entrada que expone la funcionalidad de registro.
 */
@RestController
@RequestMapping("/api/auth")
public class UserRegistrationController {

    private final CreateUserUseCasePort createUserUseCase;

    public UserRegistrationController(CreateUserUseCasePort createUserUseCase) {
        this.createUserUseCase = createUserUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegistrationRequest request) {
        try {
            User user = createUserUseCase.createUser(
                    request.name(),
                    request.email(),
                    request.password()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Usuario creado exitosamente",
                    "userId", user.getId()
            ));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("ya se encuentra registrado")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", e.getMessage()
                ));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    // DTO interno para la petición de registro
    public record RegistrationRequest(String name, String email, String password) {}
}
