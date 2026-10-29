package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.SignLine;

/** Una linea de texto de una placa de letrero (SCRUM-176). */
public record SignLineResponse(String text, String background, String foreground) {

    static SignLineResponse from(SignLine line) {
        return new SignLineResponse(line.text(), line.background(), line.foreground());
    }
}
