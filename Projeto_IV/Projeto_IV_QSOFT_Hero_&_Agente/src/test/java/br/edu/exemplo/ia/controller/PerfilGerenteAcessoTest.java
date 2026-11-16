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
import org.springframework.test.web.servlet.ResultMatcher;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Perfil Gerente: igual ao Administrador, mas não cria empresa. */
@WebMvcTest({AdminController.class, CertificacaoController.class, EmpresaController.class, FuncionarioController.class,
        PoliticaController.class, UsageController.class, CustosController.class})
@Import(SecurityConfig.class)
@WithMockUser(roles = "GERENTE")
class PerfilGerenteAcessoTest {

    private static final ResultMatcher PERMITIDO = resultado -> assertTrue(
            resultado.getResponse().getStatus() != 401 && resultado.getResponse().getStatus() != 403,
            "esperava acesso permitido, veio " + resultado.getResponse().getStatus());

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
    void naoCriaEmpresa() throws Exception {
        mvc.perform(post("/api/empresa").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void acessaCadastrosDeAdminsECapacitacoes() throws Exception {
        mvc.perform(get("/api/admin")).andExpect(status().isOk());
        mvc.perform(get("/api/certificacao")).andExpect(PERMITIDO);
        mvc.perform(get("/api/empresa")).andExpect(status().isOk());
    }

    @Test
    void podeCadastrarFuncionarioEPublicarPolitica() throws Exception {
        // Basta passar da segurança: com corpo vazio a validação pode responder 400, mas nunca 401/403.
        mvc.perform(post("/api/funcionario").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(PERMITIDO);
        mvc.perform(post("/api/politicas").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(PERMITIDO);
    }
}
