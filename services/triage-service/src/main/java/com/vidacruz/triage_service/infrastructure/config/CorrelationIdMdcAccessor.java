package com.vidacruz.triage_service.infrastructure.config;

import com.vidacruz.triage_service.application.CorrelationContext;
import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;

public class CorrelationIdMdcAccessor implements ThreadLocalAccessor<String> {

    @Override
    public Object key() {
        return CorrelationContext.KEY;
    }

    @Override
    public String getValue() {
        return MDC.get(CorrelationContext.KEY);
    }

    @Override
    public void setValue(String value) {
        MDC.put(CorrelationContext.KEY, value);
    }

    @Override
    public void setValue() {
        MDC.remove(CorrelationContext.KEY);
    }
}
