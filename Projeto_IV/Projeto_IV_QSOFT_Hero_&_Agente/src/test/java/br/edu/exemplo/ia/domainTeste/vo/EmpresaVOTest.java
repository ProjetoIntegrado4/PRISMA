package br.edu.exemplo.ia.domain.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmpresaVOTest {

    @Test
    void empresaVODeveAceitarNomeValido() {
        assertEquals("Prisma", new EmpresaVO("Prisma").value());
    }

    @Test
    void empresaVODeveRejeitarNomeNuloOuEmBranco() {
        assertThrows(IllegalArgumentException.class, () -> new EmpresaVO(null).validar());
        assertThrows(IllegalArgumentException.class, () -> new EmpresaVO(" ").validar());
    }

    @Test
    void empresaNameDeveAceitarNomeValido() {
        assertEquals("Prisma", new EmpresaName("Prisma").value());
    }

    @Test
    void empresaNameDeveRejeitarNomeNuloOuEmBranco() {
        assertThrows(IllegalArgumentException.class, () -> new EmpresaName(null).validar());
        assertThrows(IllegalArgumentException.class, () -> new EmpresaName("").validar());
    }
}
