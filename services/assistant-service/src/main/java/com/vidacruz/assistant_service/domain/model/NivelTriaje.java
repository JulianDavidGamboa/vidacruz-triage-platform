package com.vidacruz.assistant_service.domain.model;

public enum NivelTriaje {
    CRITICO(1),
    URGENTE(2),
    URGENTE_2(3),
    NO_URGENTE(4),
    NO_URGENTE_2(5);

    private final int codigo;

    NivelTriaje(int codigo) {
        this.codigo = codigo;
    }

    public int codigo() {
        return codigo;
    }

    public static NivelTriaje desdeCodigo(int codigo) {
        for (NivelTriaje nivel : values()) {
            if (nivel.codigo == codigo) {
                return nivel;
            }
        }
        throw new IllegalArgumentException("Nivel de triaje desconocido: " + codigo);
    }
}
