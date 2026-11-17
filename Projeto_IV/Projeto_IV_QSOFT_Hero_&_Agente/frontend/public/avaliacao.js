'use strict';
// Página de avaliação RAGAS: lista os experimentos (GET /api/agente/evaluation/experiments) e desenha as
// métricas em barras. Enquanto houver avaliação em andamento, consulta de novo a cada 5 segundos.
const NOMES = {
    faithfulness: 'Faithfulness (fidelidade ao contexto)',
    answer_relevancy: 'Answer relevancy (relevância da resposta)',
    llm_context_precision_with_reference: 'Context precision (com referência)',
    context_recall: 'Context recall (com referência)'
};
let timer = null;

async function carregar() {
    const response = await fetch('/api/agente/evaluation/experiments', {credentials: 'same-origin'});
    if (response.status === 401) { location.replace('login.html'); return; }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || 'Serviço RAGAS indisponível.');
    return data.experiments || [];
}

function barra(nome, valor) {
    const linha = document.createElement('div');
    linha.className = 'avaliacao-metrica';
    const rotulo = document.createElement('span');
    rotulo.textContent = NOMES[nome] || nome;
    const trilho = document.createElement('div');
    trilho.className = 'avaliacao-trilho';
    const preenchido = document.createElement('div');
    preenchido.className = 'avaliacao-barra';
    preenchido.style.width = valor == null ? '0' : Math.max(0, Math.min(1, valor)) * 100 + '%';
    trilho.append(preenchido);
    const numero = document.createElement('b');
    numero.textContent = valor == null ? 'indisponível' : valor.toFixed(3);
    linha.append(rotulo, trilho, numero);
    return linha;
}

function cartao(e) {
    const card = document.createElement('article');
    card.className = 'avaliacao-card';
    const titulo = document.createElement('h3');
    titulo.textContent = new Date(e.created_at).toLocaleString('pt-BR') + ' · ' + e.status;
    const info = document.createElement('p');
    info.className = 'note';
    info.textContent = (e.has_reference ? 'Com resposta de referência' : 'Sem resposta de referência') + ' · experimento ' + e.id;
    card.append(titulo, info);
    if (e.status === 'succeeded' && e.metrics) {
        for (const [nome, valor] of Object.entries(e.metrics.metrics || {})) card.append(barra(nome, valor));
        const limite = document.createElement('p');
        limite.className = 'note';
        limite.textContent = e.metrics.limitations || '';
        card.append(limite);
    } else if (e.status === 'failed') {
        const erro = document.createElement('p');
        erro.className = 'message';
        erro.textContent = e.error || 'Falha na avaliação.';
        card.append(erro);
    } else {
        const andamento = document.createElement('p');
        andamento.textContent = 'Avaliação em andamento…';
        card.append(andamento);
    }
    return card;
}

async function atualizar() {
    clearTimeout(timer);
    try {
        const lista = await carregar();
        if (!lista) return;
        document.getElementById('experiments').replaceChildren(...lista.map(cartao));
        document.getElementById('status').textContent = lista.length
            ? `${lista.length} avaliações recentes.`
            : 'Nenhuma avaliação ainda. Gere uma recomendação no agente e clique em "Avaliar com RAGAS".';
        if (lista.some(e => e.status === 'pending' || e.status === 'running')) timer = setTimeout(atualizar, 5000);
    } catch (error) {
        document.getElementById('status').textContent = error.message;
    }
}

document.addEventListener('visibilitychange', () => { if (!document.hidden) atualizar(); });
atualizar();
