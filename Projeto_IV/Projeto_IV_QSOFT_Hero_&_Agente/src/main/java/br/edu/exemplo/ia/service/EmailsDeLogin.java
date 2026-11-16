package br.edu.exemplo.ia.service;

import br.edu.exemplo.ia.repository.AdminRepository;
import br.edu.exemplo.ia.repository.FuncionarioRepository;

/**
 * O e-mail é o login de admins e funcionários, então não pode se repetir entre os dois cadastros:
 * com e-mail repetido, a senha de um bloquearia o login do outro.
 */
final class EmailsDeLogin {
    private EmailsDeLogin() {
    }

    static void garantirLivre(String email, AdminRepository admins, FuncionarioRepository funcionarios) {
        boolean usado = (admins != null && admins.findAll().stream().anyMatch(a -> email.equalsIgnoreCase(a.getEmail())))
                || (funcionarios != null && funcionarios.findAll().stream()
                .anyMatch(f -> email.equalsIgnoreCase(f.getEmailCorporativo())));
        if (usado) {
            throw new IllegalArgumentException("E-mail já cadastrado como login de outro admin ou funcionário: " + email);
        }
    }
}
