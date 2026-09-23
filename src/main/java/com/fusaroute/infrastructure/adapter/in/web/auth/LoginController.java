package com.fusaroute.infrastructure.adapter.in.web.auth;

import com.fusaroute.application.dto.LoginRequest;
import com.fusaroute.application.dto.LoginResponse;
import com.fusaroute.application.usecase.LoginUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST para la autenticación de usuarios.
 * Adaptador de entrada que expone el login.
 */
@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final LoginUseCase loginUseCase;

    public LoginController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = loginUseCase.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}
