package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.GetRouteDetailUseCase;
import com.fusaroute.domain.port.in.ListActiveRoutesUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catalogo publico de rutas (RF-15, SCRUM-19). Solo lectura y sin sesion. */
@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final ListActiveRoutesUseCase listActiveRoutesUseCase;
    private final GetRouteDetailUseCase getRouteDetailUseCase;

    public RouteController(ListActiveRoutesUseCase listActiveRoutesUseCase,
                           GetRouteDetailUseCase getRouteDetailUseCase) {
        this.listActiveRoutesUseCase = listActiveRoutesUseCase;
        this.getRouteDetailUseCase = getRouteDetailUseCase;
    }

    @GetMapping
    public List<RouteResponse> list() {
        return listActiveRoutesUseCase.listActive().stream().map(RouteResponse::from).toList();
    }

    @GetMapping("/{id}")
    public RouteResponse detail(@PathVariable Long id) {
        return RouteResponse.from(getRouteDetailUseCase.getById(id));
    }
}
