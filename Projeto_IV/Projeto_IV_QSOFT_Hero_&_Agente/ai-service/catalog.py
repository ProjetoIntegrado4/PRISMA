"""Local curation of the 12 names in training_records.csv; not provider syllabi."""
COURSES = [
    ("CSV01", "Fundamentos de IA Generativa", "basico", "Possibilidades, limites e uso responsável de IA generativa.", "Interesse explícito ou trilha básica (sem conclusão registrada); baixo uso não comprova inexperiência."),
    ("CSV02", "IA Responsavel: Vieses e Limitacoes", "basico", "Limitações, vieses, revisão humana e responsabilidade no uso de IA.", "Interesse explícito, trilha básica ou evento de segurança vinculado ao colaborador, como oportunidade de prevenção."),
    ("CSV03", "Seguranca da Informacao e Uso de IA", "basico", "Cuidados com informações, ferramentas autorizadas e políticas de uso.", "Interesse explícito, cargo de infraestrutura/suporte/segurança ou evento de segurança individual; o evento não comprova culpa."),
    ("CSV04", "LGPD e Ferramentas de IA", "intermediario", "Cuidados com dados pessoais no uso de ferramentas de IA.", "Interesse explícito, cargo de RH/jurídico, uso de plataforma ou API de IA, ou evento que mencione dados pessoais/LGPD."),
    ("CSV05", "Etica e Governanca de Dados em IA", "intermediario", "Responsabilidades, políticas e governança de dados em processos com IA.", "Interesse explícito ou cargo de gestão/supervisão registrado; não presume problema na equipe."),
    ("CSV06", "Introducao ao ChatGPT Enterprise", "basico", "Recursos e cuidados no uso corporativo do ChatGPT Enterprise.", "Interesse explícito ou uso registrado de ChatGPT Enterprise; não presume licença disponível."),
    ("CSV07", "Microsoft 365 Copilot na Pratica", "intermediario", "Aplicações do Microsoft 365 Copilot em atividades profissionais.", "Interesse explícito ou uso registrado de Microsoft 365 Copilot."),
    ("CSV08", "Copilot para Desenvolvedores", "intermediario", "Assistência ao desenvolvimento e revisão de código com Copilot.", "Interesse explícito, cargo de desenvolvimento/QA ou uso de copiloto de desenvolvimento."),
    ("CSV09", "Prompt Engineering na Pratica", "basico", "Objetivo, contexto, restrições, formato e revisão de instruções para IA.", "Interesse explícito, trilha básica ou uso de assistente generativo; não atribuir falhas à qualidade dos prompts sem evidência."),
    ("CSV10", "Automatizacao de Tarefas com IA", "intermediario", "Identificação de tarefas automatizáveis, supervisão e verificação de resultados.", "Interesse explícito, cargo operacional/suporte ou uso de IA integrada/plataforma; volume de chamadas não demonstra ganho de produtividade."),
    ("CSV11", "Analise de Dados com IA", "intermediario", "Análise assistida, conferência de cálculos e interpretação de resultados.", "Interesse explícito ou cargo de dados, finanças, compras ou logística registrado."),
    ("CSV12", "IA para Times Comerciais", "intermediario", "Aplicações de IA em atividades comerciais com revisão e cuidados com dados.", "Interesse explícito ou cargo comercial, de atendimento ou relacionamento registrado."),
]
CATALOG = [dict(id=i, name=n, level=l, content=c, criteria=r,
                version="csv-catalog-v2", provenance="Curadoria local; validar ementa com o responsável pelo curso")
           for i, n, l, c, r in COURSES]
