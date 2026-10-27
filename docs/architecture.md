# Arquitetura

Hexagonal (ports & adapters), repositório único, um pacote instalável. O domínio é o
centro; tudo que é I/O (rede, disco, IA, navegador) fica atrás de uma interface.

## Fluxo

```
CLI (picocli)  →  app/ScanService  →  domain/Scanner
                                         ├─ chama cada VulnModule
                                         │    └─ usa HttpClient (porta) + Detector
                                         └─ monta ScanReport
                                              └─ ReportWriter (porta) grava a saída
```

- A CLI é o adaptador de entrada. Ela só traduz argumentos e chama `ScanService`.
- `Scanner` orquestra: para o alvo, roda os `VulnModule` pedidos e junta os `Finding`.
- Cada `VulnModule` monta requisições, envia pela porta `HttpClient` e passa a evidência
  para um `Detector`, que devolve um `Verdict`.
- O `LlmClient` (porta) só é usado pela implementação de IA do `Detector`
  (`LlmDetector`). A heurística não toca em IA.

## Camadas e regra de dependência

| Camada     | Pacote                         | Pode importar                    |
| ---------- | ------------------------------ | -------------------------------- |
| Domínio    | `domain/` (model, detection, module) | só `domain` e `port`       |
| Portas     | `port/`                        | só `domain.model`                |
| Adaptadores| `adapter/` (http, llm, report) | `port`, `domain.model`, libs externas |
| Aplicação  | `app/`                         | tudo (é a raiz de composição)    |
| CLI        | `cli/`                         | `app`                            |

A seta de dependência aponta sempre para dentro. Um `import br.ufv.vulnscan.adapter.*`
dentro de `domain/` ou `port/` é um erro de arquitetura.

## Por que assim

- O experimento IA vs. heurística é uma troca de implementação do `Detector`. A
  arquitetura serve ao requisito de avaliação empírica, não é enfeite.
- O domínio roda e é testado sem subir rede nem Ollama: nos testes, `HttpClient` e
  `LlmClient` entram como fakes que devolvem respostas fixas.
- Trocar Ollama por um provedor de nuvem no futuro é escrever outro adaptador de
  `LlmClient`; nada no domínio muda.

## Diagrama

O diagrama da arquitetura está no documento de planejamento do grupo
(Claude Docs → "Plano do Trabalho"), seção "Decisões de arquitetura".
