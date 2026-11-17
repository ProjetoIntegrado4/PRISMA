import csv
import os
import io
import unicodedata
from collections import Counter
from datetime import date, datetime, timedelta
from pathlib import Path
from catalog import CATALOG


def norm(value):
    return "".join(c for c in unicodedata.normalize("NFKD", str(value or ""))
                   if not unicodedata.combining(c)).lower().strip()


def rows(name, required=True):
    path = Path(os.getenv("PRISMA_DADOS_DIR", "/data")) / (name + ".csv")
    if not path.exists():
        if required:
            raise ValueError("Base necessária indisponível: " + name)
        return []
    raw = path.read_bytes()
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        text = raw.decode("cp1252")
    header = text.splitlines()[0] if text.splitlines() else ""
    delimiter = ";" if header.count(";") > header.count(",") else ","
    reader = csv.DictReader(io.StringIO(text), delimiter=delimiter)
    return [{str(k).strip(): str(v or "").strip() for k, v in r.items() if k is not None} for r in reader]


def day(value):
    try:
        return date.fromisoformat(str(value)[:10])
    except (ValueError, TypeError):
        return None


def training_quality(record, today=None):
    today = today or date.today()
    enrollment, completion = day(record.get("enrollment_date")), day(record.get("completion_date"))
    status = norm(record.get("completion_status"))
    issues = []
    if not enrollment or enrollment > today:
        issues.append("Matrícula sem data válida ou futura")
    if completion and enrollment and completion < enrollment:
        issues.append("Conclusão anterior à matrícula")
    if status != "concluido" and record.get("completion_date"):
        issues.append("Data de conclusão com status não concluído")
    if status == "concluido" and (not completion or completion > today):
        issues.append("Conclusão sem data válida ou futura")
    if status not in {"concluido", "em andamento", "nao iniciado", "abandonado"}:
        issues.append("Status de matrícula desconhecido")
    return issues


def options(company=None):
    companies = rows("companies")
    result = {"companies": [{"id": x["company_id"], "name": x["company_name"]} for x in companies],
              "courses": CATALOG}
    if company:
        if company not in {c["company_id"] for c in companies}:
            raise ValueError("Empresa inexistente")
        result["employees"] = [{"id": e["employee_id"], "role": e.get("job_role", ""),
                                 "name": e.get("nome") or e.get("name") or e["employee_id"]}
                                for e in rows("employees") if e["company_id"] == company]
    return result


# Cargo (normalizado, sem acento e sem "(a)") -> cursos tematicamente pertinentes.
# Indica oportunidade temática do cargo, nunca deficiência.
ROLE_RULES = [
    ("CSV08", ("desenvolv", "developer", "engenheiro de software", "programador", "devops", "engenheiro de testes", " qa ")),
    ("CSV11", ("dados", "data analyst", "data scientist", "financeir", "controller", "contas a pagar", "contas a receber",
               "contab", "fiscal", "supply chain", "compras", "logistica", "planejamento", "bi ")),
    ("CSV12", ("comercial", "vendas", "vendedor", "sales", "executivo de contas", "relacionamento com cliente",
               "produtos bancarios", "atendimento", "caixa", "loja", "marketing")),
    ("CSV05", ("gerente", "gestor", "diretor", "manager", "coordenador", "supervisor", "lider", "head", "business partner")),
    ("CSV10", ("operac", "operador", "producao", "tecnico", "logistica", "suporte", "infraestrutura", "administrador de sistemas",
               "processos", " qa ", "testes", "caixa", "atendimento", "compras", "contas a pagar")),
    ("CSV03", ("infraestrutura", "administrador de sistemas", "seguranca", "suporte", "redes")),
    ("CSV04", (" rh ", "recrut", "recursos humanos", "pessoas", "juridico", "advogado", "dpo", "privacidade")),
]
# Categoria da ferramenta em ai_tools.csv -> cursos ligados ao uso registrado.
TOOL_CATEGORY_RULES = {
    "assistente generativo": ("CSV09",),
    "copiloto de desenvolvimento": ("CSV08",),
    "ia integrada a produtividade": ("CSV10",),
    "plataforma corporativa de ia": ("CSV10", "CSV04"),
    "api de modelo": ("CSV10", "CSV04"),
}
TOOL_NAME_RULES = {"chatgpt enterprise": "CSV06", "microsoft 365 copilot": "CSV07"}
# Trilha básica: oferecida quando os dados do colaborador geram menos de 3 cursos específicos.
BASIC_TRACK = ("CSV01", "CSV09", "CSV02")


def profile(company, employee, interest=None, today=None):
    today = today or date.today()
    people = [e for e in rows("employees") if e["company_id"] == company and e["employee_id"] == employee]
    if len(people) != 1:
        raise ValueError("Colaborador inexistente, duplicado ou de outra empresa")
    if interest and interest not in {c["id"] for c in CATALOG}:
        raise ValueError("Curso de interesse inválido")
    person = people[0]
    history = [r for r in rows("training_records") if r["company_id"] == company and r["employee_id"] == employee]
    warnings, completed, pending = [], set(), set()
    for filename in ("ai_usage_logs", "ai_tools", "security_events"):
        if not (Path(os.getenv("PRISMA_DADOS_DIR", "/data")) / (filename + ".csv")).exists():
            warnings.append({"record": filename, "issues": ["Fonte indisponível; ausência de dados não significa ausência de uso ou de eventos."]})
    for r in history:
        issues = training_quality(r, today)
        if issues:
            warnings.append({"record": r.get("training_id"), "issues": issues})
        elif norm(r.get("completion_status")) == "concluido":
            completed.add(norm(r["course_name"]))
        elif norm(r.get("completion_status")) in {"em andamento", "nao iniciado"}:
            pending.add(norm(r["course_name"]))
    all_usage = [r for r in rows("ai_usage_logs", False) if r["company_id"] == company
                 and day(r.get("timestamp")) and day(r["timestamp"]) <= today]
    end = max((day(r["timestamp"]) for r in all_usage), default=today)
    start = end - timedelta(days=89)
    usage = [r for r in all_usage if r["employee_id"] == employee and start <= day(r["timestamp"]) <= end
             and norm(r.get("request_status")) == "success"]
    tool_rows = {r["ai_tool_id"]: r for r in rows("ai_tools", False)}
    used_tools = sorted({r["ai_tool_id"] for r in usage})
    tool_names = sorted({tool_rows.get(t, {}).get("tool_name") or t for t in used_tools})
    events = [r for r in rows("security_events", False) if r["company_id"] == company
              and r["employee_id"] == employee and day(r.get("timestamp"))
              and start <= day(r["timestamp"]) <= end]
    counts = []
    for r in usage:
        try:
            count = float(r["request_count"])
            if count < 0 or not count.is_integer():
                raise ValueError()
            counts.append(int(count))
        except (ValueError, KeyError, OverflowError):
            warnings.append({"record": r.get("usage_id"), "issues": ["Volume de requisições inválido; ignorado"]})
    evidence = [{"id": "role", "text": "Cargo registrado: " + person.get("job_role", "Não informado")},
                {"id": "usage", "text": f"{sum(counts)} requisições bem-sucedidas registradas na janela; volume não mede competência."}]
    reasons = {}
    def add(course, eid, text):
        reasons.setdefault(course, []).append(eid)
        if not any(e["id"] == eid for e in evidence):
            evidence.append({"id": eid, "text": text})
    if interest:
        add(interest, "interest", "Interesse de capacitação informado explicitamente nesta solicitação: " +
            next(c["name"] for c in CATALOG if c["id"] == interest))
    role = " " + norm(person.get("job_role")).replace("(a)", "").replace("  ", " ") + " "
    for cid, terms in ROLE_RULES:
        if any(t in role for t in terms):
            add(cid, "role", evidence[0]["text"])
    for tid in used_tools:
        tool = tool_rows.get(tid, {})
        name, category = tool.get("tool_name") or tid, norm(tool.get("tool_category"))
        text = f"Uso registrado da ferramenta {name} na janela; oportunidade de capacitação, sem inferir dificuldade."
        for cid in TOOL_CATEGORY_RULES.get(category, ()) + ((TOOL_NAME_RULES[norm(name)],) if norm(name) in TOOL_NAME_RULES else ()):
            add(cid, "tool:" + tid, text)
    if events:
        text = f"{len(events)} eventos de segurança vinculados ao colaborador na janela; não comprovam culpa ou falta de competência."
        for cid in ("CSV02", "CSV03"):
            add(cid, "security", text)
        if any(any(t in norm(e.get("event_type")) for t in ("lgpd", "dados pessoais", "privacidade")) for e in events):
            add("CSV04", "privacy", "Há evento individual que menciona dados pessoais, privacidade ou LGPD.")
    catalog_names = {norm(r.get("course_name")) for r in rows("training_records")}
    def eligible():
        return [dict(c, evidence_ids=reasons[c["id"]],
                     action="retomar_matricula" if norm(c["name"]) in pending else "conhecer_curso")
                for c in CATALOG if c["id"] in reasons and norm(c["name"]) not in completed
                and norm(c["name"]) in catalog_names]
    candidates = eligible()
    if len(candidates) < 3:
        for cid in BASIC_TRACK:
            add(cid, "basic", "Trilha básica: não há conclusão registrada deste curso de base no histórico de capacitação; "
                "é um ponto de partida, não indica falta de conhecimento.")
        candidates = eligible()
    return {"company_id": company, "employee_id": employee, "role": person.get("job_role", ""),
            "window": {"start": str(start), "end": str(end), "basis": "Última data válida de uso da empresa, limitada a hoje"},
            "successful_requests": sum(counts), "tools": tool_names, "valid_completed_courses": len(completed),
            "evidence": evidence, "warnings": warnings, "candidates": candidates,
            "competence": "Não avaliada pelos registros de uso; ML requer avaliação específica.",
            "limitations": ["Baixo uso não comprova pouca experiência.",
                            "Sucesso da requisição não comprova qualidade da resposta.",
                            "Ausência de registro não comprova ausência de capacitação.",
                            "Cargo e ferramenta indicam pertinência temática, não deficiência."]}
