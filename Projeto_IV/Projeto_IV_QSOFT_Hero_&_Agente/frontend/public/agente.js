'use strict';
const el = id => document.getElementById(id);
let recommendationId = null;
let selectionVersion = 0;
let companyVersion = 0;
const jobs = new Map();

async function api(path, body) {
    const headers = {};
    if (body !== undefined) {
        await fetch('/api/auth/csrf', {credentials: 'same-origin'});
        const cookie = document.cookie.split('; ').find(v => v.startsWith('XSRF-TOKEN='));
        headers['X-XSRF-TOKEN'] = cookie ? decodeURIComponent(cookie.slice(11)) : '';
        headers['Content-Type'] = 'application/json';
    }
    const response = await fetch('/api/agente' + path, {
        method: body === undefined ? 'GET' : 'POST', headers,
        credentials: 'same-origin', body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (response.status === 401) {
        location.replace('login.html');
        throw new Error('Sessão expirada.');
    }
    const data = await response.json().catch(() => ({message: response.status === 502 ? 'Backend Java indisponível. Inicie o backend e recarregue a página.' : 'Resposta inválida do servidor (' + response.status + ').'}));
    if (!response.ok) throw new Error(data.message || (typeof data.detail === 'string' ? data.detail : 'Não foi possível concluir. Confira os campos informados.'));
    return data;
}

function option(select, text, value) {
    const node = document.createElement('option');
    node.textContent = text;
    node.value = value;
    select.append(node);
}

function clearRecommendation() {
    selectionVersion++;
    recommendationId = null;
    el('evaluate').disabled = true;
    el('metrics').replaceChildren();
    el('recommendation').textContent = 'Consulte a recomendação para esta seleção.';
    el('profile').textContent = '';
}

function request() {
    if (!el('company').value || !el('employee').value) throw new Error('Selecione empresa e colaborador.');
    const data = {
        company_id: el('company').value,
        employee_id: el('employee').value,
        interest: el('interest').value || null
    };
    if (el('use-assessment').checked) {
        if (!el('scores').reportValidity()) throw new Error('Preencha as três notas da avaliação real.');
        data.assessment = Object.fromEntries(['fundamentos', 'verificacao', 'aplicacao'].map(id => [id, Number(el(id).value)]));
    }
    return data;
}

async function action(button, fn) {
    button.disabled = true;
    el('message').textContent = 'Processando…';
    try {
        await fn();
        el('message').textContent = '';
    } catch (error) {
        el('message').textContent = error.message;
    } finally {
        button.disabled = false;
        if (button.id === 'evaluate') button.disabled = !recommendationId;
    }
}

function showProfile(p) {
    el('profile').textContent = `Cargo: ${p.role || 'Não informado'}\nJanela: ${p.window.start} a ${p.window.end}\nRequisições bem-sucedidas: ${p.successful_requests}\nCursos com conclusão válida: ${p.valid_completed_courses}\n${p.competence}\n\nEvidências:\n` +
        p.evidence.map(e => '• ' + e.text).join('\n') + '\n\nAvisos de qualidade:\n' +
        (p.warnings.length ? p.warnings.map(w => `${w.record}: ${w.issues.join('; ')}`).join('\n') : 'Nenhuma inconsistência identificada nos registros considerados.') +
        (p.assessment ? '\n\nAvaliação ML: ' + (p.assessment.message || `${p.assessment.profile}. ${p.assessment.limitations}`) : '');
}

el('company').addEventListener('change', async () => {
    clearRecommendation();
    const version = ++companyVersion;
    el('employee').replaceChildren();
    el('employee').disabled = true;
    option(el('employee'), 'Selecione', '');
    if (!el('company').value) return;
    try {
        const data = await api('/options?company=' + encodeURIComponent(el('company').value));
        if (version !== companyVersion) return;
        data.employees.forEach(e => option(el('employee'), `${e.name} · ${e.role}`, e.id));
        el('employee').disabled = false;
    } catch (e) {
        el('message').textContent = e.message;
    }
});
['employee', 'interest', 'use-assessment', 'fundamentos', 'verificacao', 'aplicacao'].forEach(id => el(id).addEventListener('change', clearRecommendation));
el('inspect').onclick = () => action(el('inspect'), async () => {
    const version = selectionVersion;
    const data = await api('/profile', request());
    if (version === selectionVersion) showProfile(data);
});
el('recommend').onclick = () => action(el('recommend'), async () => {
    const payload = request();
    clearRecommendation();
    const version = selectionVersion;
    const data = await api('/recommend', payload);
    if (version !== selectionVersion) return;
    showProfile(data.profile);
    if (!data.recommendations || !data.recommendations.length) {
        el('recommendation').textContent = data.message;
        return;
    }
    recommendationId = data.id;
    el('evaluate').disabled = false;
    // Até 3 cursos (RAG com Spring AI + OpenAI), do mais ao menos pertinente.
    el('recommendation').textContent = data.recommendations.map((r, i) =>
        `${i + 1}. ${r.course.name}\nNível do curso: ${r.course.level}\n${r.course.content}\n\n${r.explanation}\n\nAção: ${r.course.action === 'retomar_matricula' ? 'Retomar matrícula existente' : 'Conhecer o curso'}\nEvidências: ${r.evidence_ids.join(', ')}`
    ).join('\n\n────────\n\n') + `\n\nRecomendação gerada por IA (${data.model}); requer revisão humana.`;
});

function showMetrics(metrics) {
    el('metrics').replaceChildren();
    for (const [name, score] of Object.entries(metrics)) {
        const label = document.createElement('p');
        label.textContent = `${name}: ${score == null ? 'indisponível' : score.toFixed(3)}`;
        el('metrics').append(label);
        if (score != null) {
            const bar = document.createElement('progress');
            bar.max = 1;
            bar.value = score;
            bar.setAttribute('aria-label', name);
            el('metrics').append(bar);
        }
    }
}

function jobLine(text) {
    const node = document.createElement('li');
    node.textContent = text;
    el('jobs').prepend(node);
    return node;
}

// Treino assíncrono: o FastAPI responde 202 e publica no RabbitMQ; a página consulta o status.
async function enqueue(path, payload = {}) {
    const data = await api(path, payload);
    jobs.set(data.id, {node: jobLine(`${data.kind}: na fila (${data.id})`)});
}

// Indexação síncrona: o Spring Boot gera os embeddings (OpenAI) e grava no pgvector.
el('index').onclick = () => action(el('index'), async () => {
    const data = await api('/index', {});
    jobLine(`index: ${data.status} · ${data.catalog_courses} cursos no catálogo, ${data.newly_indexed} indexados agora`);
});
el('train').onclick = () => action(el('train'), () => enqueue('/training/jobs'));

// RAGAS por REST: POST /evaluation/experiments e consulta periódica de GET /evaluation/experiments/{id}.
let experiment = null;
el('evaluate').onclick = () => action(el('evaluate'), async () => {
    const data = await api('/evaluation/experiments', {recommendation_id: recommendationId, reference: el('reference').value.trim()});
    experiment = {id: data.id, recommendationId};
    el('metrics').replaceChildren();
    const p = document.createElement('p');
    p.textContent = 'Avaliação em andamento. Acompanhe também em avaliacao.html.';
    el('metrics').append(p);
});
el('scores').onsubmit = event => {
    event.preventDefault();
    action(event.submitter, async () => {
        const data = await api('/predict', Object.fromEntries(['fundamentos', 'verificacao', 'aplicacao'].map(id => [id, Number(el(id).value)])));
        el('prediction').textContent = data.message || `Perfil estimado: ${data.profile}\n${Object.entries(data.probabilities).map(([k, v]) => `${k}: ${(100 * v).toFixed(1)}%`).join('\n')}\n${data.limitations}`;
    });
};

async function poll() {
    if (!document.hidden) for (const [id, item] of jobs) {
        try {
            const job = await api('/jobs/' + id);
            item.node.textContent = `${job.kind}: ${job.status}${job.error ? ' · ' + job.error : ''} (${id})`;
            if (['succeeded', 'failed'].includes(job.status)) {
                jobs.delete(id);
                if (job.result) item.node.textContent += '\n' + JSON.stringify(job.result, null, 2);
            }
        } catch (e) {
            item.node.textContent = `Consulta temporariamente indisponível: ${e.message} (${id})`;
        }
    }
    if (experiment && !document.hidden) {
        try {
            const e = await api('/evaluation/experiments/' + experiment.id);
            if (e.status === 'succeeded') {
                if (experiment.recommendationId === recommendationId) showMetrics(e.metrics.metrics);
                experiment = null;
            } else if (e.status === 'failed') {
                el('metrics').textContent = 'Avaliação falhou: ' + (e.error || 'erro desconhecido');
                experiment = null;
            }
        } catch (err) {
            el('metrics').textContent = 'Consulta da avaliação indisponível: ' + err.message;
        }
    }
    setTimeout(poll, 3000);
}

(async () => {
    // Empresas e cursos não dependem da OpenAI: carregam mesmo se a verificação de saúde falhar.
    try {
        const data = await api('/options');
        data.companies.forEach(c => option(el('company'), c.name, c.id));
        data.courses.forEach(c => {
            option(el('interest'), c.name, c.id);
            const p = document.createElement('p');
            p.textContent = `${c.name} (${c.level}): ${c.content} Critério: ${c.criteria} ${c.provenance}.`;
            el('catalog').append(p);
        });
        if (!data.companies.length) el('message').textContent = 'Nenhuma empresa encontrada em companies.csv.';
    } catch (e) {
        el('message').textContent = 'Não foi possível carregar as empresas: ' + e.message;
    }
    try {
        const status = await api('/health');
        el('service').textContent = status.openai_configured
            ? `IA com OpenAI (${status.model}) via Spring AI. Consulte o perfil ou gere uma recomendação.`
            : 'Chave da OpenAI não configurada. Preencha OPENAI_API_KEY no arquivo .env e reinicie o agente.';
    } catch (e) {
        el('service').textContent = e.message;
    }
    poll();
})();
