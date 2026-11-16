package br.edu.exemplo.ia.estresse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Cenário E4: 50 usuários listando todos os usos de IA ao mesmo tempo (GET /api/usages).
 * Fica mais lento quanto maior a base: rode depois dos outros cenários.
 * Rodar só este: mvn test "-Dgroups=estresse" "-Dtest=E4ListagemTest"
 */
@EnabledIfSystemProperty(named = "groups", matches = ".*estresse.*") // só roda com -Dgroups=estresse
class E4ListagemTest extends EstresseBase {

    private static final int USUARIOS = 50;
    private static final int REQUISICOES_POR_USUARIO = 5;

    @Test
    void deveListarUsosCom50UsuariosAoMesmoTempo() throws Exception {
        // Act
        rodar(USUARIOS, REQUISICOES_POR_USUARIO, (usuario, i) -> get(usuario, "/api/usages"));

        // Assert
        verificarDesempenho("E4 listagem (50 usuarios)", USUARIOS * REQUISICOES_POR_USUARIO);
    }
}
