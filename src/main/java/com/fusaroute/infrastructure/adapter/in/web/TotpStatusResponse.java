package com.fusaroute.infrastructure.adapter.in.web;

/** Estado del segundo factor del usuario autenticado (SCRUM-179). */
public record TotpStatusResponse(boolean enabled) {
}
