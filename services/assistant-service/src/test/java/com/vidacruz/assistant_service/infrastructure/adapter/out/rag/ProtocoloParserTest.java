package com.vidacruz.assistant_service.infrastructure.adapter.out.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocoloParserTest {

    @Test
    void generaUnFragmentoPorVinetaConElProtocoloEnElTexto() {
        String contenido = """
                # PROTO-001 — Crisis Hipertensiva, manejo inicial

                - **Definición:** Presión arterial ≥ 180/120 mmHg.
                - **Medidas iniciales:** Reconfirmar la presión tras 5 minutos.
                """;

        List<ProtocoloParser.Fragmento> fragmentos = ProtocoloParser.parsear(contenido);

        assertThat(fragmentos).hasSize(2);
        assertThat(fragmentos.get(0).protocolo()).isEqualTo("PROTO-001");
        assertThat(fragmentos.get(0).seccion()).isEqualTo("Definición");
        assertThat(fragmentos.get(0).texto())
                .contains("PROTO-001")
                .contains("Crisis Hipertensiva")
                .contains("180/120");
    }

    @Test
    void rechazaUnProtocoloSinIdentificador() {
        assertThatThrownBy(() -> ProtocoloParser.parsear("# Sin identificador\n- **A:** b"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
