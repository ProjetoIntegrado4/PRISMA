import csv
from datetime import date
import pytest
import domain
from rag import Suggestion, validate_suggestion


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
    assert p['candidates'] == []
    assert p['successful_requests'] == 0
    assert 'Não avaliada' in p['competence']


def test_explicit_interest_is_evidence(base):
    p = domain.profile('C1', 'E1', 'CSV01')
    assert [c['id'] for c in p['candidates']] == ['CSV01']
    assert p['candidates'][0]['evidence_ids'] == ['interest']


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
    assert not domain.profile('C1', 'E1', today=date(2026,11,15))['candidates']


def test_invalid_status_date():
    warnings = domain.training_quality(dict(enrollment_date='2026-01-01', completion_date='2026-02-01', completion_status='Nao Iniciado'), date(2026,11,15))
    assert 'Data de conclusão com status não concluído' in warnings


def test_future_completion():
    assert domain.training_quality(dict(enrollment_date='2026-01-01', completion_date='2027-02-01', completion_status='Concluido'), date(2026,11,15))


def test_hallucinated_course_and_evidence_rejected():
    candidates = [{'id': 'CSV01', 'evidence_ids': ['interest']}]
    for cid, evidence in [('fake', ['interest']), ('CSV01', ['security']), ('CSV01', [])]:
        with pytest.raises(ValueError):
            validate_suggestion(Suggestion(course_id=cid, explanation='texto', evidence_ids=evidence), candidates)
    assert validate_suggestion(Suggestion(course_id='CSV01', explanation='texto', evidence_ids=['interest']), candidates)['id'] == 'CSV01'


def test_excel_semicolon_and_windows_encoding(tmp_path, monkeypatch):
    monkeypatch.setenv('PRISMA_DADOS_DIR', str(tmp_path))
    (tmp_path / 'companies.csv').write_bytes('company_id;company_name\nC1;Soluções\n'.encode('cp1252'))
    assert domain.rows('companies')[0]['company_name'] == 'Soluções'


def test_gender_variant_of_engineer_role(base):
    base('employees', [dict(company_id='C1', employee_id='E1', job_role='Engenheiro(a) de Software')])
    base('training_records', [dict(company_id='C2', employee_id='E2', course_name='Copilot para Desenvolvedores',
         enrollment_date='2026-01-01', completion_date='2026-02-01', completion_status='Concluido')])
    assert domain.profile('C1', 'E1')['candidates'][0]['id'] == 'CSV08'
