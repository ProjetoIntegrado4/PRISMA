package br.edu.exemplo.ia.service;

import br.edu.exemplo.ia.domain.entity.Admin;
import br.edu.exemplo.ia.domain.entity.Empresa;
import br.edu.exemplo.ia.domain.vo.AdminVO;
import br.edu.exemplo.ia.domain.vo.Campos;
import br.edu.exemplo.ia.domain.vo.Cpf;
import br.edu.exemplo.ia.domain.vo.NivelAcesso;
import br.edu.exemplo.ia.dto.AdminRequest;
import br.edu.exemplo.ia.dto.AdminResponse;
import br.edu.exemplo.ia.repository.AdminRepository;
import br.edu.exemplo.ia.repository.EmpresaRepository;
import br.edu.exemplo.ia.repository.FuncionarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AdminService implements AdminUseCase {
    private final AdminRepository adminRepository;
    private final EmpresaRepository empresaRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminService(AdminRepository adminRepository, EmpresaRepository empresaRepository) {
        this(adminRepository, empresaRepository, null);
    }

    @Autowired
    public AdminService(AdminRepository adminRepository, EmpresaRepository empresaRepository,
                        FuncionarioRepository funcionarioRepository) {
        this.adminRepository = adminRepository;
        this.empresaRepository = empresaRepository;
        this.funcionarioRepository = funcionarioRepository;
    }

    @Override
    public AdminResponse create(AdminRequest request) {
        if (!request.senha().equals(request.confirmacaoSenha())) {
            throw new IllegalArgumentException("Senha e confirmacao de senha nao conferem");
        }
        Empresa empresa = loadEmpresa(request.empresaId());
        String email = Campos.email(request.email(), "E-mail do admin");
        EmailsDeLogin.garantirLivre(email, adminRepository, funcionarioRepository);
        Admin admin = adminRepository.save(new Admin(new AdminVO(
                Campos.texto(request.nome(), "Nome do admin"),
                Cpf.normalizar(request.cpf(), "CPF do admin"),
                email,
                Campos.telefone(request.telefone(), "Telefone do admin"),
                Campos.texto(request.cargo(), "Cargo do admin"),
                passwordEncoder.encode(Campos.senha(request.senha(), "Senha do admin")),
                Campos.statusConta(request.statusConta(), "Status da conta do admin"),
                NivelAcesso.normalizar(request.nivelAcesso(), "Nível de acesso do admin").name()
        ), empresa));
        return toDto(admin);
    }

    @Override
    public List<AdminResponse> list(UUID empresaId) {
        List<Admin> admins = empresaId == null
                ? adminRepository.findAll()
                : adminRepository.findByEmpresaId(empresaId);

        return admins.stream().map(this::toDto).toList();
    }

    @Override
    public AdminResponse getById(UUID id) {
        Admin admin = adminRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Admin inexistente: " + id));
        return toDto(admin);
    }

    private Empresa loadEmpresa(UUID empresaId) {
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa inexistente: " + empresaId));
    }

    private AdminResponse toDto(Admin admin) {
        Empresa empresa = admin.getEmpresa();
        return new AdminResponse(
                admin.getId(),
                admin.getNome(),
                admin.getCpf(),
                admin.getEmail(),
                admin.getTelefone(),
                empresa.getId(),
                empresa.getNome(),
                empresa.getArea(),
                admin.getCargo(),
                admin.getStatusConta(),
                admin.getNivelAcesso(),
                admin.getCodigo()
        );
    }
}