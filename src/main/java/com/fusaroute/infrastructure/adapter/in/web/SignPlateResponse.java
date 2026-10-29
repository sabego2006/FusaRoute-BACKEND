package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.SignPlate;

import java.util.List;

/**
 * Una placa fisica dentro de un letrero (SCRUM-176).
 *
 * @param code codigo pintado en la placa; {@code null} si no trae
 */
public record SignPlateResponse(String template, String code, List<SignLineResponse> lines) {

    static SignPlateResponse from(SignPlate plate) {
        return new SignPlateResponse(
                plate.template().name(),
                plate.code(),
                plate.lines().stream().map(SignLineResponse::from).toList());
    }
}
