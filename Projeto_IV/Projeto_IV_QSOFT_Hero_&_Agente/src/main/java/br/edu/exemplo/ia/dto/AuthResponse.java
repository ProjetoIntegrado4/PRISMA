package br.edu.exemplo.ia.dto;

import java.util.UUID;

/** Quem está logado. perfil: ADMIN ou FUNCIONARIO. empresaId e funcionarioId vêm preenchidos quando a conta é da base. */
public record AuthResponse(String nome, String usuario, String perfil, UUID empresaId, UUID funcionarioId) {
    public AuthResponse(String nome, String usuario, String perfil) {
        this(nome, usuario, perfil, null, null);
    }
}
