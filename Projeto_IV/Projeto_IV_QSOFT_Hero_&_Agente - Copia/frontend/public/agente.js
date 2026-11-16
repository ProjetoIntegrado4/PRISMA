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
    const response = await fetch('/api/agente' + path, {method: body === undefined ? 'GET' : 'POST', headers,
        credentials: 'same-origin', body: body === undefined ? undefined : JSON.stringify(body)});
    if (response.status === 401) { location.replace('login.html'); throw new Error('Sessão expirada.'); }
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || (typeof data.detail === 'string' ? data.detail : 'Não foi possível concluir. Confira os campos informados.'));
    return data;
}
function option(select, text, value) { const node = document.createElement('option'); node.textContent = text; node.value = value; select.append(node); }
function clearRecommendation() { selectionVersion++; recommendationId = null; el('evaluate').disabled = true; el('metrics').replaceChildren(); el('recommendation').textContent = 'Consulte a recomendação para esta seleção.'; el('profile').textContent = ''; }
function request() {
    if (!el('company').value || !el('employee').value) throw new Error('Selecione empresa e colaborador.');
    const data = {company_id: el('company').value, employee_id: el('employee').value, interest: el('interest').value || null};
    if (el('use-assessment').checked) {
        if (!el('scores').reportValidity()) throw new Error('Preencha as três notas da avaliação real.');
        data.assessment = Object.fromEntries(['fundamentos','verificacao','aplicacao'].map(id => [id, Number(el(id).value)]));
    }
    return data;
}
async function action(button, fn) {
    button.disabled = true; el('message').textContent = 'Processando…';
    try { await fn(); el('message').textContent = ''; }
    catch (error) { el('message').textContent = error.message; }
    finally { button.disabled = false; if (button.id === 'evaluate') button.disabled = !recommendationId; }
}
function showProfile(p) {
    el('profile').textContent = `Cargo: ${p.role || 'Não informado'}\nJanela: ${p.window.start} a ${p.window.end}\nRequisições bem-sucedidas: ${p.successful_requests}\nCursos com conclusão válida: ${p.valid_completed_courses}\n${p.competence}\n\nEvidências:\n` +
        p.evidence.map(e => '• ' + e.text).join('\n') + '\n\nAvisos de qualidade:\n' +
        (p.warnings.length ? p.warnings.map(w => `${w.record}: ${w.issues.join('; ')}`).join('\n') : 'Nenhuma inconsistência identificada nos registros considerados.') +
        (p.assessment ? '\n\nAvaliação ML: ' + (p.assessment.message || `${p.assessment.profile}. ${p.assessment.limitations}`) : '');
}
el('company').addEventListener('change', async () => {
    clearRecommendation(); const version = ++companyVersion;
    el('employee').replaceChildren(); el('employee').disabled = true; option(el('employee'), 'Selecione', '');
    if (!el('company').value) return;
    try { const data = await api('/options?company=' + encodeURIComponent(el('company').value));
        if (version !== companyVersion) return;
        data.employees.forEach(e => option(el('employee'), `${e.name} · ${e.role}`, e.id)); el('employee').disabled = false;
    } catch (e) { el('message').textContent = e.message; }
});
['employee', 'interest','use-assessment','fundamentos','verificacao','aplicacao'].forEach(id => el(id).addEventListener('change', clearRecommendation));
el('inspect').onclick = () => action(el('inspect'), async () => { const version = selectionVersion; const data = await api('/profile', request()); if (version === selectionVersion) showProfile(data); });
el('recommend').onclick = () => action(el('recommend'), async () => {
    const payload = request(); clearRecommendation(); const version = selectionVersion;
    const data = await api('/recommend', payload); if (version !== selectionVersion) return;
    showProfile(data.profile);
    if (!data.recommendation) { el('recommendation').textContent = data.message; return; }
    recommendationId = data.id; el('evaluate').disabled = false;
    const r = data.recommendation;
    el('recommendation').textContent = `${r.course.name}\nNível do curso: ${r.course.level}\n${r.course.content}\n\n${r.explanation}\n\nAção: ${r.course.action === 'retomar_matricula' ? 'Retomar matrícula existente' : 'Conhecer o curso'}\nEvidências: ${r.evidence_ids.join(', ')}\n\nRecomendação gerada por IA; requer revisão humana.`;
});
function showMetrics(metrics) {
    el('metrics').replaceChildren();
    for (const [name, score] of Object.entries(metrics)) {
        const label = document.createElement('p'); label.textContent = `${name}: ${score == null ? 'indisponível' : score.toFixed(3)}`; el('metrics').append(label);
        if (score != null) { const bar = document.createElement('progress'); bar.max = 1; bar.value = score; bar.setAttribute('aria-label', name); el('metrics').append(bar); }
    }
}
async function enqueue(path, payload = {}) {
    const data = await api(path, payload); const node = document.createElement('li');
    node.textContent = `${data.kind}: aguardando processamento (${data.id})`; el('jobs').prepend(node);
    jobs.set(data.id, {node, recommendationId});
}
el('index').onclick = () => action(el('index'), () => enqueue('/index/jobs'));
el('train').onclick = () => action(el('train'), () => enqueue('/training/jobs'));
el('evaluate').onclick = () => action(el('evaluate'), () => enqueue('/evaluation/jobs', {recommendation_id: recommendationId, reference: el('reference').value.trim()}));
el('scores').onsubmit = event => { event.preventDefault(); action(event.submitter, async () => {
    const data = await api('/predict', Object.fromEntries(['fundamentos', 'verificacao', 'aplicacao'].map(id => [id, Number(el(id).value)])));
    el('prediction').textContent = data.message || `Perfil estimado: ${data.profile}\n${Object.entries(data.probabilities).map(([k,v]) => `${k}: ${(100*v).toFixed(1)}%`).join('\n')}\n${data.limitations}`;
}); };
async function poll() {
    if (!document.hidden) for (const [id, item] of jobs) {
        try { const job = await api('/jobs/' + id); item.node.textContent = `${job.kind}: ${job.status}${job.error ? ' · ' + job.error : ''} (${id})`;
            if (['succeeded','failed'].includes(job.status)) {
                jobs.delete(id);
                if (job.result?.metrics && job.kind === 'evaluate' && item.recommendationId === recommendationId) showMetrics(job.result.metrics);
                if (job.result && job.kind !== 'evaluate') item.node.textContent += '\n' + JSON.stringify(job.result, null, 2);
            }
        } catch (e) { item.node.textContent = `Consulta temporariamente indisponível: ${e.message} (${id})`; }
    }
    setTimeout(poll, 3000);
}
(async () => {
    try { const [data, status] = await Promise.all([api('/options'), api('/health')]);
        data.companies.forEach(c => option(el('company'), c.name, c.id));
        data.courses.forEach(c => { option(el('interest'), c.name, c.id); const p = document.createElement('p'); p.textContent = `${c.name} (${c.level}): ${c.content} Critério: ${c.criteria} ${c.provenance}.`; el('catalog').append(p); });
        el('service').textContent = status.local_ready ? 'IA local com Ollama. Consulte o perfil ou gere uma recomendação.' : 'Ollama indisponível. Verifique os modelos locais.';
    } catch (e) { el('service').textContent = e.message; }
    poll();
})();
