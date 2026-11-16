package br.edu.exemplo.ia.domainTeste.vo;

import br.edu.exemplo.ia.domain.vo.AlertaOrcamento;
import br.edu.exemplo.ia.domain.vo.NivelAlerta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AlertaOrcamentoTest {

    @ParameterizedTest(name = "CT{index}: consumo {0} de orçamento {1} -> {2}")
    @CsvSource({
            "0,       1000, OK",
            "799.99,  1000, OK",
            "800,     1000, ATENCAO",
            "1000,    1000, ATENCAO",
            "1000.01, 1000, ESTOURADO",
            "2500,    1000, ESTOURADO"
    })
    void deveClassificarConsumoEmRelacaoAoOrcamento(String consumo, String orcamento, NivelAlerta esperado) {
        // Arrange
        AlertaOrcamento alerta = new AlertaOrcamento(new BigDecimal(orcamento));
        // Act
        NivelAlerta nivel = alerta.avaliar(new BigDecimal(consumo));
        // Assert
        assertEquals(esperado, nivel);
    }

    @Test
    void deveInformarPercentualConsumido() {
        AlertaOrcamento alerta = new AlertaOrcamento(new BigDecimal("2000"));
        assertEquals(new BigDecimal("45.00"), alerta.percentual(new BigDecimal("900")));
    }

    @Test
    void deveRejeitarOrcamentoZeroOuNegativo() {
        assertThrows(IllegalArgumentException.class, () -> new AlertaOrcamento(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new AlertaOrcamento(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> new AlertaOrcamento(null));
    }

    @Test
    void deveRejeitarConsumoNegativo() {
        AlertaOrcamento alerta = new AlertaOrcamento(new BigDecimal("1000"));
        assertThrows(IllegalArgumentException.class, () -> alerta.avaliar(new BigDecimal("-0.01")));
    }
}
