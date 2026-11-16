package br.edu.exemplo.ia.domain.vo;

import java.util.Locale;

/**
 * Nível de acesso de quem é cadastrado na tela de Administradores.
 * ADMIN tem a visão completa; GERENTE tem o mesmo acesso, mas não cadastra empresas.
 */
public enum NivelAcesso {
    ADMIN,
    GERENTE;

    /** Valor de um novo cadastro: só ADMIN ou GERENTE (aceita minúsculas e "Administrador"). */
    public static NivelAcesso normalizar(String valor, String campo) {
        String texto = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if (texto.equals("ADMINISTRADOR")) return ADMIN;
        for (NivelAcesso nivel : values()) {
            if (nivel.name().equals(texto)) return nivel;
        }
        throw new IllegalArgumentException(campo + " deve ser ADMIN ou GERENTE");
    }

    /**
     * Nível de um registro já gravado. Só "GERENTE" vira gerente; valores antigos da base
     * (TOTAL, GESTOR etc.) continuam como administrador, como eram antes.
     */
    public static NivelAcesso doRegistro(String valor) {
        return valor != null && valor.trim().equalsIgnoreCase(GERENTE.name()) ? GERENTE : ADMIN;
    }
}
