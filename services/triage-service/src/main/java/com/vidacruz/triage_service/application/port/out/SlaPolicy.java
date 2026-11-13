package com.vidacruz.triage_service.application.port.out;

import com.vidacruz.triage_service.domain.model.NivelTriaje;

/** SLA en minutos por nivel. Configurable por propiedades de aplicación. */
public interface SlaPolicy {

    int minutosPara(NivelTriaje nivel);
}
