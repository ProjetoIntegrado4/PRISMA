(() => {
    const navigation = document.querySelector('.top-nav');
    if (!navigation) return;

    // Centraliza os acessos da plataforma para todas as páginas.
    const groups = [
        {label: 'Cadastros', pages: [
            ['empresa.html', 'Empresas', 'Cadastre e consulte as empresas da plataforma.'],
            ['admin.html', 'Administradores', 'Gerencie os administradores de cada empresa.'],
            ['funcionario.html', 'Funcionários', 'Organize os colaboradores e seus acessos.'],
            ['capacitacao.html', 'Capacitações', 'Acompanhe cursos, certificações e AI Literacy.']
        ]},
        {label: 'Monitoramento', pages: [
            ['dashboard.html', 'Dashboard', 'Consulte a visão consolidada dos indicadores.'],
            ['consumo.html', 'Consumo de IA', 'Registre tokens e acompanhe alertas de consumo.'],
            ['produtividade.html', 'Produtividade de IA', 'Compare adoção, licenças e entrega por área.'],
            ['custos.html', 'Custos de IA', 'Analise gastos por departamento, pessoa e ferramenta.']
        ]}
    ];
    const currentPage = window.location.pathname.split('/').pop() || 'index.html';
    const active = (page) => page === currentPage ? ' class="active" aria-current="page"' : '';
    const menus = groups.map(({label, pages}) => `
        <details class="nav-group">
            <summary${pages.some(([page]) => page === currentPage) ? ' class="active"' : ''}>${label}</summary>
            <div class="nav-dropdown">${pages.map(([page, title, description]) => `
                <a href="./${page}"${active(page)}><strong>${title}</strong><span>${description}</span></a>
            `).join('')}</div>
        </details>`).join('');

    navigation.innerHTML = `
        <a class="nav-brand" href="./index.html" aria-label="QAIcontrol, início"><span class="nav-mark" aria-hidden="true">Q</span>QAI<span class="nav-brand-light">control</span></a>
        <button class="nav-toggle" type="button" aria-controls="platform-navigation" aria-expanded="false">Menu <span aria-hidden="true">☰</span></button>
        <div class="nav-content" id="platform-navigation">
            <div class="nav-links">
                <a href="./index.html"${active('index.html')}>Início</a>
                ${menus}
                <a href="./governanca.html"${active('governanca.html')}>Governança</a>
            </div>
            <div class="nav-actions">${currentPage === 'login.html' ? '<a href="./login.html" aria-current="page">Entrar</a>' : ''}</div>
        </div>`;

    const toggle = navigation.querySelector('.nav-toggle');
    const details = [...navigation.querySelectorAll('details')];
    const closeGroups = () => details.forEach((group) => { group.open = false; });
    const closeMenu = () => {
        navigation.classList.remove('is-open');
        toggle.setAttribute('aria-expanded', 'false');
        closeGroups();
    };
    toggle.addEventListener('click', () => {
        const open = navigation.classList.toggle('is-open');
        toggle.setAttribute('aria-expanded', String(open));
        if (!open) closeGroups();
    });
    details.forEach((group) => {
        group.querySelector('summary').addEventListener('click', () => {
            details.forEach((other) => { if (other !== group) other.open = false; });
        });
    });
    document.addEventListener('click', (event) => {
        if (!navigation.contains(event.target)) closeMenu();
    });
    navigation.addEventListener('keydown', (event) => {
        if (event.key !== 'Escape') return;
        const openGroup = details.find((group) => group.open);
        if (openGroup) {
            openGroup.open = false;
            openGroup.querySelector('summary').focus();
        } else {
            closeMenu();
            toggle.focus();
        }
    });
    navigation.addEventListener('focusout', (event) => {
        if (!navigation.contains(event.relatedTarget)) closeMenu();
    });
    window.matchMedia('(max-width: 760px)').addEventListener('change', closeMenu);
})();
