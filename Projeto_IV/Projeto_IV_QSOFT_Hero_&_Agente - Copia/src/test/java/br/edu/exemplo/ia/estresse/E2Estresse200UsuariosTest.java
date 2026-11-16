package br.edu.exemplo.ia.estresse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Cenário E2: estresse, 200 usuários registrando uso de IA ao mesmo tempo. Rodar só este: mvn test "-Dgroups=estresse" "-Dtest=E2Estresse200UsuariosTest" */
@EnabledIfSystemProperty(named = "groups", matches = ".*estresse.*") // só roda com -Dgroups=estresse
class E2Estresse200UsuariosTest extends EstresseBase {

    private static final int USUARIOS = 200;
    private static final int REQUISICOES_POR_USUARIO = 50;

    @Test
    void deveAguentar200UsuariosRegistrandoUsoAoMesmoTempo() throws Exception {
        // Arrange
        String empresaId = criarEmpresa();
        long antes = contarUsos(empresaId);

        // Act: todos os usuários registrando uso de IA ao mesmo tempo
        rodar(USUARIOS, REQUISICOES_POR_USUARIO, (usuario, i) -> registrarUso(usuario, empresaId, i));

        // Assert: tempo de resposta, erros e nenhum registro perdido no banco
        int total = USUARIOS * REQUISICOES_POR_USUARIO;
        verificarDesempenho("E2 estresse (200 usuarios)", total);
        assertEquals(total - erros.get(), contarUsos(empresaId) - antes, "registros perdidos no banco");
    }
}
