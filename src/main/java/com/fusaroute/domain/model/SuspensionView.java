package com.fusaroute.domain.model;

/** Una suspension junto con su estado calculado con el reloj del caso de uso, para que el controlador no necesite reloj. */
public record SuspensionView(RouteSuspension suspension, SuspensionState state) {
}
