package br.edu.exemplo.ia.service;

import br.edu.exemplo.ia.repository.AdminRepository;
import br.edu.exemplo.ia.repository.FuncionarioRepository;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
    private static final String[] GESTAO = {UsuarioPrisma.ADMIN, UsuarioPrisma.GERENTE};


    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository
    ) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/csrf", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Perfis: ADMIN (tudo), GERENTE (igual ao admin, mas sem cadastrar empresa) e FUNCIONARIO
                        // (só consulta; não acessa cadastros). Empresas e colaboradores podem ser lidos por todos,
                        // porque o Consumo de IA e a Governança usam essas listas.
                        // US PP-21 (RN04/CA-14.1): só a gestão (admin ou gerente) publica ou altera política de uso de IA.
                        .requestMatchers(HttpMethod.POST, "/api/politicas").hasAnyRole(GESTAO)
                        .requestMatchers(HttpMethod.PUT, "/api/politicas/*").hasAnyRole(GESTAO)
                        .requestMatchers(HttpMethod.POST, "/api/empresa").hasRole(UsuarioPrisma.ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/empresa/**").hasRole(UsuarioPrisma.ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/empresa/**").hasRole(UsuarioPrisma.ADMIN)
                        .requestMatchers("/api/admin", "/api/admin/**").hasAnyRole(GESTAO)
                        .requestMatchers("/api/certificacao", "/api/certificacao/**").hasAnyRole(GESTAO)
                        .requestMatchers(HttpMethod.POST, "/api/funcionario").hasAnyRole(GESTAO)
                        .requestMatchers(HttpMethod.PUT, "/api/funcionario/**").hasAnyRole(GESTAO)
                        .requestMatchers(HttpMethod.DELETE, "/api/funcionario/**").hasAnyRole(GESTAO)
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Autenticacao necessaria\"}");
                }).accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write(exception instanceof CsrfException
                            ? "{\"message\":\"Token CSRF invalido ou ausente\"}"
                            : "{\"message\":\"Acesso nao permitido para o seu perfil\"}");
                }))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    UserDetailsService usuarios(
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.username}") String username,
            @Value("${app.auth.password}") String password,
            @Value("${app.auth.name}") String nome,
            @Value("${app.auth.funcionario.username:}") String funcionarioUsername,
            @Value("${app.auth.funcionario.password:}") String funcionarioPassword,
            @Value("${app.auth.funcionario.name:Funcionario}") String funcionarioNome,
            @Value("${app.auth.gerente.username:}") String gerenteUsername,
            @Value("${app.auth.gerente.password:}") String gerentePassword,
            @Value("${app.auth.gerente.name:Gerente}") String gerenteNome,
            ObjectProvider<AdminRepository> admins,
            ObjectProvider<FuncionarioRepository> funcionarios
    ) {
        List<UsuarioPrisma> temporarios = new ArrayList<>();
        temporarios.add(new UsuarioPrisma(username, passwordEncoder.encode(password), UsuarioPrisma.ADMIN, nome, null, null));
        if (!gerenteUsername.isBlank() && !gerentePassword.isBlank()) {
            temporarios.add(new UsuarioPrisma(gerenteUsername, passwordEncoder.encode(gerentePassword),
                    UsuarioPrisma.GERENTE, gerenteNome, null, null));
        }
        if (!funcionarioUsername.isBlank() && !funcionarioPassword.isBlank()) {
            temporarios.add(new UsuarioPrisma(funcionarioUsername, passwordEncoder.encode(funcionarioPassword),
                    UsuarioPrisma.FUNCIONARIO, funcionarioNome, null, null));
        }
        return new UsuariosPrisma(temporarios, admins.getIfAvailable(), funcionarios.getIfAvailable());
    }

    /** Só a conta temporária de administrador, sem consultar a base (usado nos testes). */
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder, String username, String password) {
        return new UsuariosPrisma(List.of(new UsuarioPrisma(username, passwordEncoder.encode(password),
                UsuarioPrisma.ADMIN, username, null, null)), null, null);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        return repository;
    }
}