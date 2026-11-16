package br.edu.exemplo.ia.service;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.UUID;

/**
 * Usuário autenticado com o nome de exibição e, quando vem da base, a empresa e o funcionário a que pertence.
 * O perfil é ADMIN (visão completa), GERENTE (igual ao admin, mas sem cadastrar empresa) ou FUNCIONARIO (Início, Monitoramento e Governança sem publicar política).
 */
public class UsuarioPrisma extends User {
    public static final String ADMIN = "ADMIN";
    public static final String GERENTE = "GERENTE";
    public static final String FUNCIONARIO = "FUNCIONARIO";

    private final String nome;
    private final UUID empresaId;
    private final UUID funcionarioId;

    public UsuarioPrisma(String usuario, String senhaHash, String perfil, String nome, UUID empresaId, UUID funcionarioId) {
        super(usuario, senhaHash, List.of(new SimpleGrantedAuthority("ROLE_" + perfil)));
        this.nome = nome;
        this.empresaId = empresaId;
        this.funcionarioId = funcionarioId;
    }

    public String getNome() {
        return nome;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public UUID getFuncionarioId() {
        return funcionarioId;
    }
}
