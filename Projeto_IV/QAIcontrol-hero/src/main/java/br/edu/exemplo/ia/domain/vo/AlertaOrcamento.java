package br.edu.exemplo.ia.domain.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Compara o consumo de IA de um centro de custo com o orçamento do período. */
public record AlertaOrcamento(BigDecimal orcamento) {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal LIMITE_ATENCAO = new BigDecimal("80");

    public AlertaOrcamento {
        if (orcamento == null || orcamento.signum() <= 0) {
            throw new IllegalArgumentException("Orçamento deve ser maior que zero");
        }
    }

    /** Compara sem arredondar, para que 799,99 de 1000 não vire 80% por arredondamento. */
    public NivelAlerta avaliar(BigDecimal consumo) {
        validarConsumo(consumo);
        if (consumo.compareTo(orcamento) > 0) {
            return NivelAlerta.ESTOURADO;
        }
        boolean atingiuAtencao = consumo.multiply(CEM).compareTo(orcamento.multiply(LIMITE_ATENCAO)) >= 0;
        return atingiuAtencao ? NivelAlerta.ATENCAO : NivelAlerta.OK;
    }

    /** Percentual consumido, arredondado a 2 casas, só para exibição. */
    public BigDecimal percentual(BigDecimal consumo) {
        validarConsumo(consumo);
        return consumo.multiply(CEM).divide(orcamento, 2, RoundingMode.HALF_UP);
    }

    private static void validarConsumo(BigDecimal consumo) {
        if (consumo == null || consumo.signum() < 0) {
            throw new IllegalArgumentException("Consumo não pode ser negativo");
        }
    }
}
