package br.edu.exemplo.ia.service;

import br.edu.exemplo.ia.domain.entity.Admin;
import br.edu.exemplo.ia.domain.entity.Empresa;
import br.edu.exemplo.ia.domain.entity.Funcionario;
import br.edu.exemplo.ia.domain.vo.AdminVO;
import br.edu.exemplo.ia.domain.vo.EmpresaVO;
import br.edu.exemplo.ia.domain.vo.FuncionarioVO;
import br.edu.exemplo.ia.repository.AdminRepository;
import br.edu.exemplo.ia.repository.FuncionarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsuariosPrismaTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final String hash = encoder.encode("Senha@123");
    private final Empresa empresa = new Empresa(new EmpresaVO("Prisma"), "Tecnologia");
    private final AdminRepository admins = mock(AdminRepository.class);
    private final FuncionarioRepository funcionarios = mock(FuncionarioRepository.class);

    private UsuariosPrisma usuarios() {
        UsuarioPrisma temporario = new UsuarioPrisma("funcionario@prisma.com", encoder.encode("Prisma123!"),
                UsuarioPrisma.FUNCIONARIO, "Funcionário Prisma", null, null);
        return new UsuariosPrisma(List.of(temporario), admins, funcionarios);
    }

    private Funcionario funcionario(String email, String status) {
        return Funcionario.restaurar("C01-E0001", new FuncionarioVO("Luana", null, email, null, "Analista", null,
                "C01-E0001", hash, status), empresa, null);
    }

    @Test
    void adminDaBaseEntraComPerfilAdmin() {
        Admin admin = Admin.restaurar("C01-A001", new AdminVO("Letícia", null, "leticia@empresa.com", null,
                "Diretora", hash, "ATIVO", "TOTAL"), empresa);
        when(admins.findAll()).thenReturn(List.of(admin));

        UserDetails user = usuarios().loadUserByUsername("Leticia@Empresa.com");

        assertTrue(encoder.matches("Senha@123", user.getPassword()));
        assertEquals("ROLE_ADMIN", user.getAuthorities().iterator().next().getAuthority());
        UsuarioPrisma usuario = assertInstanceOf(UsuarioPrisma.class, user);
        assertEquals("Letícia", usuario.getNome());
        assertEquals(empresa.getId(), usuario.getEmpresaId());
        assertNull(usuario.getFuncionarioId());
    }

    @Test
    void funcionarioDaBaseEntraComPerfilFuncionarioEVinculado() {
        Funcionario funcionario = funcionario("luana@empresa.com", "ATIVO");
        when(admins.findAll()).thenReturn(List.of());
        when(funcionarios.findAll()).thenReturn(List.of(funcionario));

        UsuarioPrisma usuario = assertInstanceOf(UsuarioPrisma.class, usuarios().loadUserByUsername("luana@empresa.com"));

        assertEquals("ROLE_FUNCIONARIO", usuario.getAuthorities().iterator().next().getAuthority());
        assertEquals(funcionario.getId(), usuario.getFuncionarioId());
        assertEquals(empresa.getId(), usuario.getEmpresaId());
    }

    @Test
    void funcionarioInativoNaoEntra() {
        when(admins.findAll()).thenReturn(List.of());
        when(funcionarios.findAll()).thenReturn(List.of(funcionario("luana@empresa.com", "INATIVO")));

        assertThrows(UsernameNotFoundException.class, () -> usuarios().loadUserByUsername("luana@empresa.com"));
    }

    @Test
    void contaTemporariaDeFuncionarioTemPerfilFuncionario() {
        UserDetails user = usuarios().loadUserByUsername("funcionario@prisma.com");

        assertTrue(encoder.matches("Prisma123!", user.getPassword()));
        assertEquals("ROLE_FUNCIONARIO", user.getAuthorities().iterator().next().getAuthority());
    }
}
