// Login e perfil de acesso de todas as páginas (menos o login).
// ADMIN vê tudo. GERENTE vê o mesmo que o admin, mas não cadastra empresa.
// FUNCIONARIO vê Início, Monitoramento e Governança (sem publicar política).
// Atributos que controlam o que aparece (regras no Estilo.css):
//   data-somente-admin          gestão: administrador e gerente
//   data-somente-administrador  só o administrador (ex.: cadastrar empresa)
//   data-somente-funcionario    só o funcionário
// O backend também barra o funcionário nos cadastros; aqui é só para a tela ficar coerente.
const PAGINAS_SO_ADMIN = ['empresa.html', 'admin.html', 'funcionario.html', 'capacitacao.html', 'agente.html', 'avaliacao.html'];
const PERFIS = ['ADMIN', 'GERENTE', 'FUNCIONARIO'];

window.prismaUsuario = fetch('/api/auth/me', {credentials: 'same-origin'})
    .then((response) => (response.ok ? response.json() : null))
    .catch(() => null);

document.addEventListener('DOMContentLoaded', async () => {
    const navigation = document.querySelector('.top-nav');
    if (!navigation) return;

    const user = await window.prismaUsuario;
    if (!user) {
        window.location.replace('./login.html');
        return;
    }

    const perfil = PERFIS.includes(user.perfil) ? user.perfil : 'FUNCIONARIO';
    const gestao = perfil !== 'FUNCIONARIO';
    document.documentElement.dataset.perfil = perfil;
    if (gestao) document.documentElement.dataset.gestao = '';
    const pagina = window.location.pathname.split('/').pop() || 'index.html';
    if (!gestao && PAGINAS_SO_ADMIN.includes(pagina)) {
        window.location.replace('./index.html');
        return;
    }

    const logout = document.createElement('button');
    logout.type = 'button';
    logout.className = 'logout-button';
    logout.textContent = `Sair (${user.nome})`;
    logout.title = {ADMIN: 'Perfil Administrador', GERENTE: 'Perfil Gerente', FUNCIONARIO: 'Perfil Funcionário'}[perfil];
    logout.addEventListener('click', async () => {
        await fetch('/api/auth/csrf', {credentials: 'same-origin'});
        const csrfCookie = document.cookie.split('; ').find((item) => item.startsWith('XSRF-TOKEN='));
        const csrfToken = csrfCookie ? decodeURIComponent(csrfCookie.substring('XSRF-TOKEN='.length)) : '';
        await fetch('/api/auth/logout', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {'X-XSRF-TOKEN': csrfToken}
        });
        window.location.replace('./login.html');
    });
    navigation.appendChild(logout);
});
