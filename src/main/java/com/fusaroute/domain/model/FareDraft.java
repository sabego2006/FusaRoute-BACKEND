package com.fusaroute.domain.model;

import java.math.BigDecimal;

/** Tarifa tal como la envia el administrador: sin orden ni fecha, que las decide el sistema. */
public record FareDraft(BigDecimal amount, String referencePoint) {
}
