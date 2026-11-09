package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.ListPlacesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Catalogo publico de lugares canonicos (P1): lo que el selector de origen y destino muestra. Cacheable:
 * los lugares cambian solo por migracion, y {@code CatalogEtagFilter} agrega el ETag para que el
 * navegador revalide con {@code If-None-Match} y reciba un 304.
 */
@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(10))
            .staleWhileRevalidate(Duration.ofMinutes(5)).cachePublic();

    private final ListPlacesUseCase listPlacesUseCase;

    public PlaceController(ListPlacesUseCase listPlacesUseCase) {
        this.listPlacesUseCase = listPlacesUseCase;
    }

    @Operation(summary = "Listar lugares",
            description = "Lugares canonicos con coordenada, ordenados por nombre. 'id' es el que se manda como "
                    + "originPlaceId / destinationPlaceId en la busqueda de ruta.")
    @ApiResponse(responseCode = "200", description = "Lugares recuperados exitosamente")
    @GetMapping
    public ResponseEntity<List<PlaceResponse>> list() {
        return ResponseEntity.ok().cacheControl(CACHE)
                .body(listPlacesUseCase.listPlaces().stream().map(PlaceResponse::from).toList());
    }
}
