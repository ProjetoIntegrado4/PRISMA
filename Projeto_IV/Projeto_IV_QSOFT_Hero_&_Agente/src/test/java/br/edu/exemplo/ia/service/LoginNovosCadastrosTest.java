package br.edu.exemplo.ia.service;

import br.edu.exemplo.ia.config.BaseDadosProperties;
import br.edu.exemplo.ia.dto.AdminRequest;
import br.edu.exemplo.ia.dto.FuncionarioRequest;
import br.edu.exemplo.ia.repository.csv.AdminCsvRepository;
import br.edu.exemplo.ia.repository.csv.BaseDadosCsv;
import br.edu.exemplo.ia.repository.csv.EmpresaCsvRepository;
import br.edu.exemplo.ia.repository.csv.FuncionarioCsvRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Quem é cadastrado pelo site (admin ou funcionário) já entra com o e-mail e a senha que recebeu. */
class LoginNovosCadastrosTest {

    @TempDir
    Path pasta;

    private EmpresaCsvRepository empresas;
    private AdminCsvRepository admins;
    private FuncionarioCsvRepository funcionarios;
    private ProviderManager login;
    private UUID empresaId;

    @BeforeEach
    void preparar() throws IOException {
        Files.writeString(pasta.resolve("companies.csv"), """
                company_id,company_name,industry,company_size,employee_count
                C01,NexoCode Solucoes,Tecnologia,Grande,1450
                """);
        Files.writeString(pasta.resolve("departments.csv"), "department_id,company_id,department_name\n");
        Files.writeString(pasta.resolve("admins.csv"),
                "admin_id,company_id,nome,cpf,email,telefone,cargo,senha_hash,status_conta,nivel_acesso\n");
        Files.writeString(pasta.resolve("employees.csv"), "employee_id,company_id,department_id,job_role,seniority,"
                + "hire_date,cost_center,active,nome,cpf,email_corporativo,telefone,matricula,senha_hash,gestor_admin_id\n");
        BaseDadosCsv base = new BaseDadosCsv(new BaseDadosProperties(pasta.toString()));
        empresas = new EmpresaCsvRepository(base);
        admins = new AdminCsvRepository(base, empresas);
        funcionarios = new FuncionarioCsvRepository(base, empresas, admins);
        empresaId = empresas.findAll().get(0).getId();

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new UsuariosPrisma(List.of(), admins, funcionarios));
        provider.setPasswordEncoder(new BCryptPasswordEncoder());
        login = new ProviderManager(provider);
    }

    private Authentication entrar(String usuario, String senha) {
        return login.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(usuario, senha));
    }

    private AdminRequest admin(String email, String status) {
        return new AdminRequest("Nova Admin", "529.982.247-25", email, 11999990000L, "Gestora",
                empresaId, "Senha@Nova1", "Senha@Nova1", status, "ADMIN");
    }

    private FuncionarioRequest funcionario(String email, String status) {
        return new FuncionarioRequest("Novo Funcionário", "111.444.777-35", email, 11999990001L, "Analista",
                "Financeiro", empresaId, "M-100", "Senha@Func1", status, null);
    }

    @Test
    void adminCadastradoEntraComPerfilAdmin() {
        new AdminService(admins, empresas, funcionarios).create(admin("Nova.Admin@Empresa.com", "ATIVO"));

        Authentication auth = entrar("nova.admin@empresa.com", "Senha@Nova1");

        assertEquals("ROLE_ADMIN", auth.getAuthorities().iterator().next().getAuthority());
        assertEquals("Nova Admin", assertInstanceOf(UsuarioPrisma.class, auth.getPrincipal()).getNome());
    }

    @Test
    void funcionarioCadastradoEntraComPerfilFuncionario() {
        new FuncionarioService(funcionarios, empresas, admins).create(funcionario("novo@empresa.com", "ativo"));

        Authentication auth = entrar("novo@empresa.com", "Senha@Func1");

        assertEquals("ROLE_FUNCIONARIO", auth.getAuthorities().iterator().next().getAuthority());
        UsuarioPrisma usuario = assertInstanceOf(UsuarioPrisma.class, auth.getPrincipal());
        assertEquals(empresaId, usuario.getEmpresaId());
    }

    @Test
    void senhaErradaOuContaInativaNaoEntram() {
        new FuncionarioService(funcionarios, empresas, admins).create(funcionario("inativo@empresa.com", "INATIVO"));
        new AdminService(admins, empresas, funcionarios).create(admin("admin@empresa.com", "ATIVO"));

        assertThrows(BadCredentialsException.class, () -> entrar("inativo@empresa.com", "Senha@Func1"));
        assertThrows(BadCredentialsException.class, () -> entrar("admin@empresa.com", "outra"));
    }

    @Test
    void naoAceitaEmailDeLoginRepetido() {
        new AdminService(admins, empresas, funcionarios).create(admin("ana@empresa.com", "ATIVO"));

        assertThrows(IllegalArgumentException.class, () ->
                new FuncionarioService(funcionarios, empresas, admins).create(funcionario("ANA@empresa.com", "ATIVO")));
        assertThrows(IllegalArgumentException.class, () ->
                new AdminService(admins, empresas, funcionarios).create(admin("ana@empresa.com", "ATIVO")));
    }

    @Test
    void gerenteCadastradoNaTelaDeAdminsEntraComPerfilGerente() {
        new AdminService(admins, empresas, funcionarios).create(new AdminRequest("Novo Gerente", "529.982.247-25",
                "gerente@empresa.com", 11999990002L, "Gerente de TI", empresaId, "Senha@Ger1", "Senha@Ger1",
                "ATIVO", "gerente"));

        Authentication auth = entrar("gerente@empresa.com", "Senha@Ger1");

        assertEquals("ROLE_GERENTE", auth.getAuthorities().iterator().next().getAuthority());
        assertEquals("GERENTE", admins.findAll().get(0).getNivelAcesso());
    }

    @Test
    void nivelDeAcessoDiferenteDeAdminOuGerenteERecusado() {
        assertThrows(IllegalArgumentException.class, () -> new AdminService(admins, empresas, funcionarios)
                .create(new AdminRequest("X", "529.982.247-25", "x@empresa.com", 11999990002L, "TI", empresaId,
                        "Senha@1", "Senha@1", "ATIVO", "SUPER")));
    }
}
