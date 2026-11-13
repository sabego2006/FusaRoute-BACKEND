package com.fusaroute.domain.port.in;

import com.fusaroute.domain.model.SuspensionFilter;
import com.fusaroute.domain.model.SuspensionView;

import java.util.List;

/** Listado de suspensiones del administrador (RF-12): vigentes, luego proximas, luego pasadas. */
public interface ListRouteSuspensionsUseCase {

    List<SuspensionView> list(SuspensionFilter filter);
}
