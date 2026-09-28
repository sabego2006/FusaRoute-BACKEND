package com.fusaroute.infrastructure.adapter.in.web;

/** Cuerpo del PUT /api/users/me. */
public record UpdateProfileRequest(String name, String email, String phone) {
}
