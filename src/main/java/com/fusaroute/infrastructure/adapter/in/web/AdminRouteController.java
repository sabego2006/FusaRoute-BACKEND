package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.CreateRouteUseCase;
import com.fusaroute.domain.port.in.DeactivateRouteUseCase;
import com.fusaroute.domain.port.in.GetAdminRouteUseCase;
import com.fusaroute.domain.port.in.ListAllRoutesUseCase;
import com.fusaroute.domain.port.in.RestoreRouteUseCase;
import com.fusaroute.domain.port.in.UpdateRouteUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD de rutas del administrador (RF-11, SCRUM-24). Como {@link AdminCommentController},
 * no comprueba el rol: {@code SecurityConfig} exige ROLE_ADMIN sobre todo
 * {@code /api/admin/**}, asi que estos endpoints quedan protegidos por el prefijo.
 *
 * <p>DELETE es un soft delete (la ruta pasa a INACTIVA, no se borra ninguna fila).
 */
@RestController
@RequestMapping("/api/admin/routes")
public class AdminRouteController {

    private final ListAllRoutesUseCase listAll;
    private final GetAdminRouteUseCase getRoute;
    private final CreateRouteUseCase createRoute;
    private final UpdateRouteUseCase updateRoute;
    private final DeactivateRouteUseCase deactivateRoute;
    private final RestoreRouteUseCase restoreRoute;

    public AdminRouteController(ListAllRoutesUseCase listAll, GetAdminRouteUseCase getRoute,
                                CreateRouteUseCase createRoute, UpdateRouteUseCase updateRoute,
                                DeactivateRouteUseCase deactivateRoute, RestoreRouteUseCase restoreRoute) {
        this.listAll = listAll;
        this.getRoute = getRoute;
        this.createRoute = createRoute;
        this.updateRoute = updateRoute;
        this.deactivateRoute = deactivateRoute;
        this.restoreRoute = restoreRoute;
    }

    @Operation(summary = "Listado de rutas (administrador)",
            description = "Todas las rutas, en cualquier estado (incluidas INACTIVA y SUSPENDIDA), ordenadas por nombre.")
    @ApiResponse(responseCode = "200", description = "Rutas recuperadas")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @GetMapping
    public List<AdminRouteSummaryResponse> list() {
        return listAll.listAll().stream().map(AdminRouteSummaryResponse::from).toList();
    }

    @Operation(summary = "Detalle de una ruta (administrador)",
            description = "La ruta con su trazado, barrios en orden y tarifas vigentes, en cualquier estado.")
    @ApiResponse(responseCode = "200", description = "Ruta recuperada")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @ApiResponse(responseCode = "404", description = "La ruta no existe")
    @GetMapping("/{id}")
    public AdminRouteDetailResponse detail(@PathVariable Long id) {
        return AdminRouteDetailResponse.from(getRoute.getById(id));
    }

    @Operation(summary = "Crear una ruta",
            description = "Crea la ruta en estado ACTIVA. Errores de validación por campo en `errors`.")
    @ApiResponse(responseCode = "201", description = "Ruta creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (lista por campo en `errors`)")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @ApiResponse(responseCode = "409", description = "Ya existe una ruta con ese nombre (sin distinguir mayúsculas) o letrero")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminRouteDetailResponse create(@RequestBody AdminRouteRequest request) {
        return AdminRouteDetailResponse.from(createRoute.create(request.toDraft()));
    }

    @Operation(summary = "Modificar una ruta",
            description = "Reemplaza los datos de la ruta; el estado no cambia. Una tarifa distinta de la vigente "
                    + "se guarda como fila nueva con vigencia desde hoy; si no cambió, se conserva su fecha.")
    @ApiResponse(responseCode = "200", description = "Ruta modificada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (lista por campo en `errors`)")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @ApiResponse(responseCode = "404", description = "La ruta no existe")
    @ApiResponse(responseCode = "409", description = "Otro registro ya usa ese nombre o letrero")
    @PutMapping("/{id}")
    public AdminRouteDetailResponse update(@PathVariable Long id, @RequestBody AdminRouteRequest request) {
        return AdminRouteDetailResponse.from(updateRoute.update(id, request.toDraft()));
    }

    @Operation(summary = "Eliminar una ruta (soft delete)",
            description = "Pasa la ruta a INACTIVA: no se borra ninguna fila. Deja de verse en el catálogo y en las "
                    + "búsquedas; el historial de viajes que la referencia se conserva marcado como no disponible.")
    @ApiResponse(responseCode = "204", description = "Ruta inactivada")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @ApiResponse(responseCode = "404", description = "La ruta no existe")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deactivateRoute.deactivate(id);
    }

    @Operation(summary = "Restaurar una ruta",
            description = "Devuelve a ACTIVA una ruta INACTIVA.")
    @ApiResponse(responseCode = "200", description = "Ruta restaurada")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @ApiResponse(responseCode = "404", description = "La ruta no existe")
    @ApiResponse(responseCode = "409", description = "La ruta no estaba INACTIVA")
    @PostMapping("/{id}/restore")
    public AdminRouteDetailResponse restore(@PathVariable Long id) {
        return AdminRouteDetailResponse.from(restoreRoute.restore(id));
    }
}
