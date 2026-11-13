package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.SuspensionFilter;
import com.fusaroute.domain.port.in.CreateRouteSuspensionUseCase;
import com.fusaroute.domain.port.in.LiftRouteSuspensionUseCase;
import com.fusaroute.domain.port.in.ListRouteSuspensionsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Suspension temporal de rutas del administrador (RF-12, SCRUM-25). Como {@link AdminRouteController}, no
 * comprueba el rol: {@code SecurityConfig} exige ROLE_ADMIN sobre todo {@code /api/admin/**}.
 *
 * <p>Es distinta del soft delete de la ruta: la ruta sigue existiendo y vuelve sola al cerrarse la ventana
 * (sin scheduler; ver {@code docs/contratos/rf12-suspension.md}).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminSuspensionController {

    private final CreateRouteSuspensionUseCase createSuspension;
    private final ListRouteSuspensionsUseCase listSuspensions;
    private final LiftRouteSuspensionUseCase liftSuspension;

    public AdminSuspensionController(CreateRouteSuspensionUseCase createSuspension,
                                     ListRouteSuspensionsUseCase listSuspensions,
                                     LiftRouteSuspensionUseCase liftSuspension) {
        this.createSuspension = createSuspension;
        this.listSuspensions = listSuspensions;
        this.liftSuspension = liftSuspension;
    }

    @Operation(summary = "Suspender una ruta",
            description = "Saca la ruta de lo público (catálogo, búsqueda, tarifa) entre startsAt y endsAt; vuelve sola al "
                    + "terminar la ventana. Fechas ISO-8601 con offset; endsAt en el futuro; máximo 90 días; "
                    + "reason opcional (≤ 200 caracteres, sin enlaces).")
    @ApiResponse(responseCode = "201", description = "Suspensión creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (lista por campo en `errors`)")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @ApiResponse(responseCode = "404", description = "La ruta no existe")
    @ApiResponse(responseCode = "409", description = "La ruta está INACTIVA o la ventana se solapa con otra no levantada")
    @PostMapping("/routes/{id}/suspensions")
    @ResponseStatus(HttpStatus.CREATED)
    public SuspensionResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                     @RequestBody CreateSuspensionRequest request) {
        return SuspensionResponse.from(
                createSuspension.create(request.toCommand(id, Long.parseLong(jwt.getSubject()))));
    }

    @Operation(summary = "Listado de suspensiones",
            description = "state = active | upcoming | past | all (por defecto all). Orden: vigentes, próximas y pasadas "
                    + "(las terminadas y las levantadas).")
    @ApiResponse(responseCode = "200", description = "Suspensiones recuperadas")
    @ApiResponse(responseCode = "400", description = "state desconocido")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @GetMapping("/suspensions")
    public List<SuspensionResponse> list(@RequestParam(required = false) String state) {
        return listSuspensions.list(SuspensionFilter.parse(state)).stream().map(SuspensionResponse::from).toList();
    }

    @Operation(summary = "Reactivar una ruta a mano",
            description = "Levanta la suspensión antes de su fin (o antes de que empiece): fija liftedAt = ahora. "
                    + "La fila se conserva como historial.")
    @ApiResponse(responseCode = "200", description = "Suspensión levantada")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Autenticado pero sin rol de administrador")
    @ApiResponse(responseCode = "404", description = "La suspensión no existe")
    @ApiResponse(responseCode = "409", description = "La suspensión ya terminó o ya estaba levantada")
    @DeleteMapping("/suspensions/{id}")
    public SuspensionResponse lift(@PathVariable Long id) {
        return SuspensionResponse.from(liftSuspension.lift(id));
    }
}
