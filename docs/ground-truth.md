# Gabarito do experimento (`ground_truth.yaml`)

O gabarito é a verdade conhecida: para cada caso de teste, sabemos de antemão se o alvo é
vulnerável. É contra ele que medimos cada `Detector` (ver `docs/experiment.md`). Como os
alvos (DVWA, Juice Shop) são propositalmente vulneráveis e documentados, conhecemos a
resposta certa de cada caso.

O arquivo fica em `experiments/ground_truth.yaml`.

## Esquema

```yaml
targets:
  - name: dvwa
    base_url: http://localhost:8080
    cases:
      - id: dvwa-sqli-id-param        # id único do caso
        type: SQL_INJECTION           # um valor de VulnType
        location: "/vulnerabilities/sqli/?id="   # rota ou campo atacado
        security: low                 # nível do DVWA (opcional; distingue low/impossible no mesmo location)
        payload_ref: sqli-or-1-1      # id de um payload em payloads/ (opcional)
        vulnerable: true              # a verdade conhecida
        notes: "campo id injetável no nível de segurança 'low'"

  - name: juice-shop
    base_url: http://localhost:3000
    cases:
      - id: js-xss-search
        type: XSS_REFLECTED
        location: "/#/search?q="
        vulnerable: true
        notes: "parâmetro de busca reflete sem sanitizar"
      - id: js-xss-sanitized-field
        type: XSS_REFLECTED
        location: "/#/contact"
        vulnerable: false             # caso NEGATIVO: campo que sanitiza
        notes: "controle — serve para medir falso positivo"
      - id: js-idor-basket
        type: BROKEN_ACCESS_CONTROL
        location: "/rest/basket/{id}"
        vulnerable: true
        notes: "acessa cesta de outro usuário trocando o id"
```

## Campo `security` (opcional, DVWA)

No DVWA, o mesmo `location` é vulnerável ou não conforme o nível (`low`, `medium`, `high`,
`impossible`). Quando um caso depende do nível, informe-o em `security`; o `BenchmarkRunner`
o usa para montar a sessão (cookie `security=<nível>`). Ausente, vale o nível corrente do
alvo. No Juice Shop não se aplica.

## Campo `auth_mode` (opcional, casos `BROKEN_*`)

Para os casos de autenticação/autorização, `auth_mode` diz como o `ApiAuthModule` monta a
requisição: `NONE` (sem token), `USER_TOKEN` (token de usuário comum — papel indevido) ou
`USER_TOKEN_OTHER_RESOURCE` (token de um usuário acessando o recurso de outro — IDOR;
`{otherId}` no `location`). Distingue casos de mesmo `location` (ex.: `/api/Users` sem token
× com token comum). Ausente = `NONE`.

## Regras para montar um bom gabarito

- **Inclua casos negativos.** Um gabarito só com casos vulneráveis não mede falso
  positivo — e falso positivo é justamente onde se espera que a IA ajude. Mire em algo
  próximo de metade positivos, metade negativos por tipo de falha.
- **Um caso = uma afirmação checável.** `location` + `type` + `vulnerable` têm que ser
  suficientes para o módulo reproduzir o teste.
- **Cada caso tem que ser alcançável pelo módulo correspondente.** Se o módulo de SQLi não
  consegue montar a requisição de um caso, o caso está mal especificado.
- **Alvo no estado de fábrica.** O gabarito assume o alvo recém-subido via Docker. Casos de
  XSS armazenado sujam o banco; o experimento reseta o alvo entre execuções.

## Como é consumido

O `BenchmarkRunner` (em `experiments/`) lê este YAML, roda o módulo certo para cada caso
com cada `Detector`, compara o `Verdict.vulnerable` com o campo `vulnerable` do gabarito, e
acumula VP/FP/VN/FN. Parsing do YAML: use Jackson com o módulo YAML
(`jackson-dataformat-yaml`), já previsto no `pom.xml`.
