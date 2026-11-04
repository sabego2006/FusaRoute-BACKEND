package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.exception.EmailAlreadyRegisteredException;
import com.fusaroute.domain.exception.FieldViolation;
import com.fusaroute.domain.exception.IncorrectCurrentPasswordException;
import com.fusaroute.domain.exception.InvalidFavoriteDestinationException;
import com.fusaroute.domain.exception.InvalidProfileException;
import com.fusaroute.domain.model.CongestionWarning;
import com.fusaroute.domain.model.Email;
import com.fusaroute.domain.model.FavoriteDestination;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.model.TrafficSector;
import com.fusaroute.domain.model.UserRole;
import com.fusaroute.domain.port.in.ChangePasswordUseCase;
import com.fusaroute.domain.port.in.ClearFavoriteDestinationUseCase;
import com.fusaroute.domain.port.in.GetCongestionWarningUseCase;
import com.fusaroute.domain.port.in.GetFavoriteDestinationUseCase;
import com.fusaroute.domain.port.in.GetProfileUseCase;
import com.fusaroute.domain.port.in.SetFavoriteDestinationUseCase;
import com.fusaroute.domain.port.in.UpdateProfileUseCase;
import com.fusaroute.infrastructure.config.JwtConfig;
import com.fusaroute.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:4200",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetProfileUseCase getProfile;

    @MockitoBean
    private UpdateProfileUseCase updateProfile;

    @MockitoBean
    private ChangePasswordUseCase changePassword;

    @MockitoBean
    private GetFavoriteDestinationUseCase getFavoriteDestination;

    @MockitoBean
    private SetFavoriteDestinationUseCase setFavoriteDestination;

    @MockitoBean
    private ClearFavoriteDestinationUseCase clearFavoriteDestination;

    @MockitoBean
    private GetCongestionWarningUseCase getCongestionWarning;

    private final User user = new User(1L, "Ana", Email.of("ana@x.co"),
            "$2a$12$hash", "3101234567", UserRole.USER, true, 0);

    // --- GET /api/users/me ---

    @Test
    void sin_token_responde_401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_perfil_devuelve_200_sin_hash() throws Exception {
        when(getProfile.getProfile(1L)).thenReturn(user);

        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.email").value("ana@x.co"))
                .andExpect(jsonPath("$.phone").value("3101234567"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // --- PUT /api/users/me ---

    @Test
    void update_valido_responde_200() throws Exception {
        User updated = new User(1L, "Ana Maria", Email.of("nueva@x.co"),
                "$2a$12$hash", "3109876543", UserRole.USER, true, 0);
        when(updateProfile.updateProfile(any())).thenReturn(updated);

        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana Maria","email":"nueva@x.co","phone":"3109876543"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Maria"))
                .andExpect(jsonPath("$.email").value("nueva@x.co"))
                .andExpect(jsonPath("$.phone").value("3109876543"));
    }

    @Test
    void update_datos_invalidos_responde_400_con_errors() throws Exception {
        when(updateProfile.updateProfile(any())).thenThrow(
                new InvalidProfileException(List.of(new FieldViolation("name", "El nombre es obligatorio"))));

        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"ana@x.co","phone":null}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void update_correo_duplicado_responde_409() throws Exception {
        when(updateProfile.updateProfile(any())).thenThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"ocupado@x.co","phone":null}"""))
                .andExpect(status().isConflict());
    }

    // --- PUT /api/users/me/password ---

    @Test
    void cambio_de_contrasena_valido_responde_204() throws Exception {
        doNothing().when(changePassword).changePassword(any());

        mockMvc.perform(put("/api/users/me/password")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"ViejaPass1","newPassword":"NuevaPass1"}"""))
                .andExpect(status().isNoContent());
    }

    @Test
    void contrasena_actual_incorrecta_responde_400() throws Exception {
        doThrow(new IncorrectCurrentPasswordException()).when(changePassword).changePassword(any());

        mockMvc.perform(put("/api/users/me/password")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Mala1234","newPassword":"NuevaPass1"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contrasena actual no es correcta"));
    }

    @Test
    void nueva_contrasena_invalida_responde_400_con_errors() throws Exception {
        doThrow(new InvalidProfileException(
                List.of(new FieldViolation("password", "La contrasena debe tener al menos 8 caracteres"))))
                .when(changePassword).changePassword(any());

        mockMvc.perform(put("/api/users/me/password")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"ViejaPass1","newPassword":"abc"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    // --- Destino favorito (RF-10, SCRUM-21) ---

    private static final Instant MARCADO = Instant.parse("2026-11-04T09:15:00Z");

    private static TrafficSector sectorCentro() {
        return new TrafficSector(1L, "Centro - Carrera 1", Set.of("Centro"),
                LocalTime.of(17, 0), LocalTime.of(19, 0));
    }

    @Test
    void favorito_sin_token_responde_401() throws Exception {
        mockMvc.perform(get("/api/users/me/favorite-destination"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void marcar_favorito_sin_token_responde_401() throws Exception {
        mockMvc.perform(put("/api/users/me/favorite-destination")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\":\"Centro\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void quitar_favorito_sin_token_responde_401() throws Exception {
        mockMvc.perform(delete("/api/users/me/favorite-destination"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sin_favorito_marcado_responde_204_y_no_un_404() throws Exception {
        // No tener favorito es el estado normal de una cuenta nueva, no un recurso
        // que falte: un 404 obligaria al frontend a tratarlo como error.
        when(getFavoriteDestination.of(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/me/favorite-destination")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void get_favorito_devuelve_el_destino_y_el_aviso_de_tranco() throws Exception {
        when(getFavoriteDestination.of(1L))
                .thenReturn(Optional.of(new FavoriteDestination(1L, "Centro", MARCADO)));
        when(getCongestionWarning.forRoute(List.of("Centro")))
                .thenReturn(new CongestionWarning(List.of(sectorCentro())));

        mockMvc.perform(get("/api/users/me/favorite-destination")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("Centro"))
                .andExpect(jsonPath("$.congestionSectors.length()").value(1))
                .andExpect(jsonPath("$.congestionSectors[0].name").value("Centro - Carrera 1"))
                // El favorito es "mi favorito": el id del dueno no le aporta nada al cliente.
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void el_aviso_del_favorito_se_calcula_sobre_su_barrio_no_sobre_una_ruta() throws Exception {
        // Antes de buscar no hay origen, asi que no hay recorrido: lo unico evaluable
        // es el barrio del favorito.
        when(getFavoriteDestination.of(1L))
                .thenReturn(Optional.of(new FavoriteDestination(1L, "La Pampa", MARCADO)));
        when(getCongestionWarning.forRoute(List.of("La Pampa")))
                .thenReturn(new CongestionWarning(List.of()));

        mockMvc.perform(get("/api/users/me/favorite-destination")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.congestionSectors.length()").value(0));
    }

    @Test
    void marcar_favorito_devuelve_200_con_el_aviso_ya_calculado() throws Exception {
        when(setFavoriteDestination.set(1L, "Centro"))
                .thenReturn(new FavoriteDestination(1L, "Centro", MARCADO));
        when(getCongestionWarning.forRoute(List.of("Centro")))
                .thenReturn(new CongestionWarning(List.of(sectorCentro())));

        mockMvc.perform(put("/api/users/me/favorite-destination")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\":\"Centro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("Centro"))
                .andExpect(jsonPath("$.congestionSectors[0].startsAt").value("17:00:00"));
    }

    @Test
    void el_favorito_se_marca_siempre_sobre_el_usuario_del_token() throws Exception {
        // El id no se acepta del cliente en ningun punto: sale del claim sub. Aunque
        // el cuerpo trajera otro usuario, el favorito es el de quien manda el token.
        when(setFavoriteDestination.set(7L, "Centro"))
                .thenReturn(new FavoriteDestination(7L, "Centro", MARCADO));
        when(getCongestionWarning.forRoute(any())).thenReturn(new CongestionWarning(List.of()));

        mockMvc.perform(put("/api/users/me/favorite-destination")
                        .with(jwt().jwt(j -> j.subject("7").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\":\"Centro\",\"userId\":1}"))
                .andExpect(status().isOk());

        verify(setFavoriteDestination).set(7L, "Centro");
    }

    @Test
    void destino_vacio_responde_400_y_no_500() throws Exception {
        when(setFavoriteDestination.set(any(), any()))
                .thenThrow(new InvalidFavoriteDestinationException("El destino favorito no puede ir vacio"));

        mockMvc.perform(put("/api/users/me/favorite-destination")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El destino favorito no puede ir vacio"));
    }

    @Test
    void cuerpo_sin_el_campo_destino_responde_400() throws Exception {
        when(setFavoriteDestination.set(any(), any()))
                .thenThrow(new InvalidFavoriteDestinationException("El destino favorito no puede ir vacio"));

        mockMvc.perform(put("/api/users/me/favorite-destination")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quitar_favorito_responde_204() throws Exception {
        doNothing().when(clearFavoriteDestination).clear(1L);

        mockMvc.perform(delete("/api/users/me/favorite-destination")
                        .with(jwt().jwt(j -> j.subject("1").claim("scope", "full"))))
                .andExpect(status().isNoContent());

        verify(clearFavoriteDestination).clear(1L);
    }
}
