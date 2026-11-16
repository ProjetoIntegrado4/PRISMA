package br.edu.exemplo.ia.domainTeste.vo;

import br.edu.exemplo.ia.domain.vo.NivelAcesso;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NivelAcessoTest {

    @Test
    void novoCadastroAceitaSoAdminOuGerente() {
        assertEquals(NivelAcesso.ADMIN, NivelAcesso.normalizar(" admin ", "Nível"));
        assertEquals(NivelAcesso.ADMIN, NivelAcesso.normalizar("Administrador", "Nível"));
        assertEquals(NivelAcesso.GERENTE, NivelAcesso.normalizar("Gerente", "Nível"));
        assertThrows(IllegalArgumentException.class, () -> NivelAcesso.normalizar("TOTAL", "Nível"));
        assertThrows(IllegalArgumentException.class, () -> NivelAcesso.normalizar(null, "Nível"));
    }

    @Test
    void registrosAntigosContinuamComoAdministrador() {
        assertEquals(NivelAcesso.ADMIN, NivelAcesso.doRegistro("TOTAL"));
        assertEquals(NivelAcesso.ADMIN, NivelAcesso.doRegistro("GESTOR"));
        assertEquals(NivelAcesso.ADMIN, NivelAcesso.doRegistro(null));
        assertEquals(NivelAcesso.GERENTE, NivelAcesso.doRegistro("gerente"));
    }
}
