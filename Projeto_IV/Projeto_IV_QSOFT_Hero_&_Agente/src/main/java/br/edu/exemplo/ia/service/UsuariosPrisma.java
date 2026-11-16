package br.edu.exemplo.ia.service;

import br.edu.exemplo.ia.domain.entity.Admin;
import br.edu.exemplo.ia.domain.entity.Funcionario;
import br.edu.exemplo.ia.repository.AdminRepository;
import br.edu.exemplo.ia.repository.FuncionarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

/**
 * Procura quem está entrando, nesta ordem: contas temporárias de desenvolvimento (application.yml),
 * admins de admins.csv (perfil ADMIN, ou GERENTE quando nivel_acesso = GERENTE) e funcionários de employees.csv (perfil FUNCIONARIO).
 * Na base, o login é o e-mail e a senha é conferida com o hash BCrypt da coluna senha_hash;
 * só entram contas ativas e com senha cadastrada.
 */
public class UsuariosPrisma implements UserDetailsService {
    private final List<UsuarioPrisma> temporarios;
    private final AdminRepository admins;
    private final FuncionarioRepository funcionarios;

    public UsuariosPrisma(List<UsuarioPrisma> temporarios, AdminRepository admins, FuncionarioRepository funcionarios) {
        this.temporarios = List.copyOf(temporarios);
        this.admins = admins;
        this.funcionarios = funcionarios;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String login = username == null ? "" : username.trim();
        return temporario(login)
                .or(() -> admin(login))
                .or(() -> funcionario(login))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado"));
    }

    private Optional<UserDetails> temporario(String login) {
        return temporarios.stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(login))
                .findFirst()
                .map(u -> new UsuarioPrisma(u.getUsername(), u.getPassword(), perfil(u), u.getNome(),
                        u.getEmpresaId(), u.getFuncionarioId()));
    }

    private Optional<UserDetails> admin(String login) {
        if (admins == null) return Optional.empty();
        return admins.findAll().stream()
                .filter(a -> login.equalsIgnoreCase(a.getEmail()) && ativo(a.getStatusConta()) && temSenha(a.getSenhaHash()))
                .findFirst()
                .map(a -> new UsuarioPrisma(a.getEmail(), a.getSenhaHash(),
                        a.isGerente() ? UsuarioPrisma.GERENTE : UsuarioPrisma.ADMIN, a.getNome(),
                        empresaId(a), null));
    }

    private Optional<UserDetails> funcionario(String login) {
        if (funcionarios == null) return Optional.empty();
        return funcionarios.findAll().stream()
                .filter(f -> login.equalsIgnoreCase(f.getEmailCorporativo()) && ativo(f.getStatusConta()) && temSenha(f.getSenha()))
                .findFirst()
                .map(f -> new UsuarioPrisma(f.getEmailCorporativo(), f.getSenha(), UsuarioPrisma.FUNCIONARIO, f.getNome(),
                        f.getEmpresa() == null ? null : f.getEmpresa().getId(), f.getId()));
    }

    private static java.util.UUID empresaId(Admin admin) {
        return admin.getEmpresa() == null ? null : admin.getEmpresa().getId();
    }

    private static String perfil(UsuarioPrisma usuario) {
        return usuario.getAuthorities().iterator().next().getAuthority().replaceFirst("^ROLE_", "");
    }

    private static boolean ativo(String status) {
        return status == null || status.isBlank() || status.trim().equalsIgnoreCase("ATIVO");
    }

    private static boolean temSenha(String hash) {
        return hash != null && hash.startsWith("$2");
    }
}
