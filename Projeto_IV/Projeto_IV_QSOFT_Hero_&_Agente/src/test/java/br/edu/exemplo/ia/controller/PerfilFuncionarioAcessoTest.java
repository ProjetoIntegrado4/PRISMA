package br.edu.exemplo.ia.controller;

import br.edu.exemplo.ia.service.AdminUseCase;
import br.edu.exemplo.ia.service.CertificacaoUseCase;
import br.edu.exemplo.ia.service.EmpresaUseCase;
import br.edu.exemplo.ia.service.FuncionarioUseCase;
import br.edu.exemplo.ia.service.PoliticaUseCase;
import br.edu.exemplo.ia.service.SecurityConfig;
import br.edu.exemplo.ia.service.UsageUseCase;
import br.edu.exemplo.ia.service.custos.CustosUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Perfil Funcionário: lê monitoramento e governança, mas não acessa cadastros nem publica política. */
@WebMvcTest({AdminController.class, CertificacaoController.class, EmpresaController.class, FuncionarioController.class,
        PoliticaController.class, UsageController.class, CustosController.class})
@Import(SecurityConfig.class)
@WithMockUser(roles = "FUNCIONARIO")
class PerfilFuncionarioAcessoTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private AdminUseCase admins;
    @MockitoBean private CertificacaoUseCase certificacoes;
    @MockitoBean private EmpresaUseCase empresas;
    @MockitoBean private FuncionarioUseCase funcionarios;
    @MockitoBean private PoliticaUseCase politicas;
    @MockitoBean private UsageUseCase usages;
    @MockitoBean private CustosUseCase custos;

    @Test
    void naoAcessaCadastros() throws Exception {
        mvc.perform(get("/api/admin")).andExpect(status().isForbidden());
        mvc.perform(get("/api/certificacao")).andExpect(status().isForbidden());
        mvc.perform(post("/api/empresa").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/funcionario").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void naoPublicaPolitica() throws Exception {
        mvc.perform(post("/api/politicas").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultaEmpresasColaboradoresPoliticasECustos() throws Exception {
        mvc.perform(get("/api/empresa")).andExpect(status().isOk());
        mvc.perform(get("/api/funcionario")).andExpect(status().isOk());
        mvc.perform(get("/api/usages")).andExpect(status().isOk());
    }
}
