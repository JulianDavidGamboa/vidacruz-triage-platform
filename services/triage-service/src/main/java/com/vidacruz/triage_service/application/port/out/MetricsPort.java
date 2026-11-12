package com.vidacruz.triage_service.application.port.out;

import com.vidacruz.triage_service.domain.model.NivelTriaje;

public interface MetricsPort {

    void clasificacionRegistrada(NivelTriaje nivel);

    void escalamientoRegistrado();
}
