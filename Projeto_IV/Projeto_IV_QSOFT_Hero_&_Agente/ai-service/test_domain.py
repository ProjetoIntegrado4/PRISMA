import csv
from datetime import date
import pytest
import domain


@pytest.fixture
def base(tmp_path, monkeypatch):
    monkeypatch.setenv("PRISMA_DADOS_DIR", str(tmp_path))
    def write(name, records):
        with (tmp_path / (name + '.csv')).open('w', newline='', encoding='utf-8') as stream:
            writer = csv.DictWriter(stream, fieldnames=list(records[0])); writer.writeheader(); writer.writerows(records)
    write('employees', [dict(company_id='C1', employee_id='E1', job_role='Assistente')])
    write('training_records', [dict(company_id='C2', employee_id='E2', course_name='Fundamentos de IA Generativa',
                                    enrollment_date='2026-01-01', completion_date='2026-02-01', completion_status='Concluido')])
    return write


def test_low_usage_does_not_mean_beginner(base):
    p = domain.profile('C1', 'E1')
    # Sem dados específicos, só a trilha básica é oferecida; o volume de uso não vira evidência de curso.
    assert [c['id'] for c in p['candidates']] == ['CSV01']
    assert p['candidates'][0]['evidence_ids'] == ['basic']
    assert p['successful_requests'] == 0
    assert 'Não avaliada' in p['competence']


def test_explicit_interest_is_evidence(base):
    p = domain.profile('C1', 'E1', 'CSV01')
    assert [c['id'] for c in p['candidates']] == ['CSV01']
    assert p['candidates'][0]['evidence_ids'][0] == 'interest'


ALL_COURSES = ['Fundamentos de IA Generativa', 'IA Responsavel: Vieses e Limitacoes', 'Seguranca da Informacao e Uso de IA',
               'LGPD e Ferramentas de IA', 'Etica e Governanca de Dados em IA', 'Introducao ao ChatGPT Enterprise',
               'Microsoft 365 Copilot na Pratica', 'Copilot para Desenvolvedores', 'Prompt Engineering na Pratica',
               'Automatizacao de Tarefas com IA', 'Analise de Dados com IA', 'IA para Times Comerciais']


@pytest.fixture
def full(base):
    def setup(role, completed=(), tools=()):
        base('employees', [dict(company_id='C1', employee_id='E1', job_role=role)])
        records = [dict(training_id=f'T{i}', company_id='C9', employee_id='X', course_name=n, enrollment_date='2026-01-01',
                        completion_date='', completion_status='Em andamento') for i, n in enumerate(ALL_COURSES)]
        records += [dict(training_id=f'D{i}', company_id='C1', employee_id='E1', course_name=n, enrollment_date='2026-01-01',
                         completion_date='2026-02-01', completion_status='Concluido') for i, n in enumerate(completed)]
        base('training_records', records)
        base('ai_tools', [dict(ai_tool_id='T02', tool_name='Claude for Work', tool_category='Assistente Generativo'),
                          dict(ai_tool_id='T08', tool_name='Microsoft 365 Copilot', tool_category='IA Integrada a Produtividade'),
                          dict(ai_tool_id='T13', tool_name='OpenAI API', tool_category='API de Modelo')])
        base('ai_usage_logs', [dict(usage_id=f'U{i}', company_id='C1', employee_id='E1', ai_tool_id=t, timestamp='2026-10-01T10:00:00',
                                    request_status='success', request_count='3') for i, t in enumerate(tools)]
             or [dict(usage_id='U0', company_id='C1', employee_id='Z', ai_tool_id='T02', timestamp='2026-10-01T10:00:00',
                      request_status='success', request_count='1')])
        return lambda: domain.profile('C1', 'E1', today=date(2026, 11, 17))
    return setup


@pytest.mark.parametrize('role, expected', [
    ('Operador(a) de Producao', 'CSV10'), ('Vendedor(a)', 'CSV12'), ('Especialista em Produtos Bancarios', 'CSV12'),
    ('Analista de Logistica', 'CSV11'), ('Business Partner de RH', 'CSV04'), ('Analista de QA', 'CSV08'),
    ('Supervisor(a) de Producao', 'CSV05'), ('Analista de Infraestrutura', 'CSV03')])
def test_role_rules_cover_operational_and_business_roles(full, role, expected):
    p = full(role)()
    by_id = {c['id']: c for c in p['candidates']}
    assert expected in by_id and 'role' in by_id[expected]['evidence_ids']


def test_role_rules_do_not_match_inside_words(full):
    ids = [c['id'] for c in full('Arquiteto(a) de Qualidade')()['candidates']]
    assert 'CSV04' not in ids and 'CSV08' not in ids


def test_tool_category_links_courses(full):
    p = full('Assistente', tools=('T02', 'T08', 'T13'))()
    by_id = {c['id']: c['evidence_ids'] for c in p['candidates']}
    assert 'tool:T02' in by_id['CSV09'] and 'tool:T08' in by_id['CSV07'] and 'tool:T08' in by_id['CSV10']
    assert 'tool:T13' in by_id['CSV04']


def test_basic_track_only_completes_short_lists(full):
    many = full('Analista Financeiro', tools=('T02', 'T08', 'T13'))()
    assert all('basic' not in c['evidence_ids'] for c in many['candidates'])
    few = full('Assistente')()
    assert sorted(c['id'] for c in few['candidates']) == ['CSV01', 'CSV02', 'CSV09']


def test_completed_basic_course_is_not_offered_again(full):
    p = full('Assistente', completed=('Fundamentos de IA Generativa',))()
    assert 'CSV01' not in [c['id'] for c in p['candidates']]


def test_cross_company_employee_rejected(base):
    with pytest.raises(ValueError): domain.profile('C2', 'E1')


def test_invalid_training_is_not_completion(base):
    base('training_records', [dict(company_id='C1', employee_id='E1', course_name='Fundamentos de IA Generativa',
        enrollment_date='2026-02-01', completion_date='2026-01-01', completion_status='Concluido')])
    p = domain.profile('C1', 'E1', 'CSV01', date(2026, 11, 15))
    assert p['valid_completed_courses'] == 0
    assert p['warnings'] and p['candidates']


def test_valid_completed_course_not_recommended(base):
    base('training_records', [dict(company_id='C1', employee_id='E1', course_name='Fundamentos de IA Generativa',
        enrollment_date='2026-01-01', completion_date='2026-02-01', completion_status='Concluido')])
    assert not domain.profile('C1', 'E1', 'CSV01', date(2026, 11, 15))['candidates']


def test_pending_course_is_resume(base):
    base('training_records', [dict(company_id='C1', employee_id='E1', course_name='Fundamentos de IA Generativa',
        enrollment_date='2026-01-01', completion_date='', completion_status='Em Andamento')])
    assert domain.profile('C1', 'E1', 'CSV01')['candidates'][0]['action'] == 'retomar_matricula'


def test_other_employee_security_not_used(base):
    base('security_events', [dict(company_id='C1', employee_id='E2', timestamp='2026-11-01', event_type='LGPD')])
    cands = domain.profile('C1', 'E1', today=date(2026,11,15))['candidates']
    assert all('security' not in c['evidence_ids'] for c in cands)


def test_invalid_status_date():
    warnings = domain.training_quality(dict(enrollment_date='2026-01-01', completion_date='2026-02-01', completion_status='Nao Iniciado'), date(2026,11,15))
    assert 'Data de conclusão com status não concluído' in warnings


def test_future_completion():
    assert domain.training_quality(dict(enrollment_date='2026-01-01', completion_date='2027-02-01', completion_status='Concluido'), date(2026,11,15))


# A validação de cursos e evidências da resposta da IA agora fica no Spring Boot
# (OpenAiRecommendationAdapterTest.keepsOnlyRetrievedUniqueAndAtMostThree).


def test_excel_semicolon_and_windows_encoding(tmp_path, monkeypatch):
    monkeypatch.setenv('PRISMA_DADOS_DIR', str(tmp_path))
    (tmp_path / 'companies.csv').write_bytes('company_id;company_name\nC1;Soluções\n'.encode('cp1252'))
    assert domain.rows('companies')[0]['company_name'] == 'Soluções'


def test_gender_variant_of_engineer_role(base):
    base('employees', [dict(company_id='C1', employee_id='E1', job_role='Engenheiro(a) de Software')])
    base('training_records', [dict(company_id='C2', employee_id='E2', course_name='Copilot para Desenvolvedores',
         enrollment_date='2026-01-01', completion_date='2026-02-01', completion_status='Concluido')])
    assert domain.profile('C1', 'E1')['candidates'][0]['id'] == 'CSV08'
