package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.UserNotification;

import java.util.List;

/** Avisos in-app del usuario (RF-12): rutas suspendidas ahora mismo que le afectan. */
public interface GetUserNotificationsUseCase {

    /** Vacia si nada aplica. Solo suspensiones efectivamente vigentes: una proxima o una terminada no avisa. */
    List<UserNotification> notificationsOf(Long userId);
}
