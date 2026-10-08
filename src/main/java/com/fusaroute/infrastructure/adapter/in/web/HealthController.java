package com.fusaroute.infrastructure.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {

    @Operation(summary = "Estado del sistema", description = "Verifica si la API está arriba y funcionando correctamente")
    @ApiResponse(responseCode = "200", description = "El sistema está operativo")
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
