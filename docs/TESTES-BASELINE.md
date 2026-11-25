# Issue #8 — Testes dos fluxos selecionados

## Escopo

Caracterizar o backend antes das refatorações da issue #9, conforme o documento
[Seleção dos problemas prioritários e mudanças propostas](<Seleção dos problemas prioritários e mudanças propostas.docx>).
Não modificar código de produção, dependências ou regras de negócio nesta entrega.

- Base: `96057f674b7572c141e2077bd75044f9919c6f70`.
- Branch: `test/issue-8-backend-baseline`.
- Execução local: 25/11/2026, Linux x86_64.
- Java: Eclipse Temurin `1.8.0_514-b01`, fixado em `mise.toml`.
- Maven: `3.6.3`, instalado pelo Maven Wrapper existente.
- Frameworks existentes: Spring Boot `2.3.12.RELEASE`, JUnit Jupiter `5.6.3`, Mockito e JaCoCo `0.8.7`.

## Preparar o ambiente

Com o `mise` instalado, executar a partir da raiz do repositório:

```bash
mise trust mise.toml
mise install
cd backend
mise exec -- java -version
mise exec -- ./mvnw -version
```

A primeira execução precisa de internet para baixar o JDK, Maven e dependências.
A configuração de Java é local ao projeto; não altera a versão global.
Os testes selecionados usam mocks, H2 em memória e cache local. Não exigem PostgreSQL, Redis ou frontend em execução.

## Resultado inicial, antes dos novos testes

Comando executado em `backend/`:

```bash
mise exec -- ./mvnw -B \
  -Dtest=AdvancedInventoryServiceTest,EnhancedInventoryReportControllerTest,IntegratedInventoryControllerTest \
  test
```

| Classe existente | Casos | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: |
| `AdvancedInventoryServiceTest` | 4 | 0 | 0 | 0 |
| `EnhancedInventoryReportControllerTest` | 4 | 0 | 0 | 0 |
| `IntegratedInventoryControllerTest` | 6 | 0 | 0 | 0 |
| **Total inicial** | **14** | **0** | **0** | **0** |

Resultado: `BUILD SUCCESS`. Tempo total: `02:12 min`, incluindo downloads iniciais.
Nenhuma falha preexistente foi encontrada **nesse conjunto**, não na suíte inteira.

## Cobertura acrescentada

Os testes usam as operações públicas. Não acessam métodos privados por reflexão.

| Arquivo em `backend/src/test/java/com/techbookstore/app/` | Comportamento protegido |
| --- | --- |
| `service/OrderServiceTest.java` | Confirmação pendente, baixa somente na loja, preservação de depósito e reservas, disponibilidade insuficiente após reservas, estoque ausente e nova confirmação proibida. Matriz dos 36 pares de estados, incluindo repetição e datas de confirmação, envio e entrega. |
| `controller/InventoryControllerTest.java` | Contrato HTTP de venda: baixa e data da venda, `400` vazio por falta de saldo na loja ou disponibilidade após reservas, e `404` vazio por estoque ausente. Rejeições não persistem alterações. |
| `service/ReportServiceTest.java` | Geração real do relatório sem filtros, métricas simuladas atuais, presença de itens e sugestões, limites dos indicadores aleatórios e combinação dos filtros de categoria e situação. Não usa mock de `ReportService`. |
| `service/IntegratedInventoryAnalysisServiceTest.java` | Os dois fluxos encaminham os mesmos seis filtros. Cobre ano válido, nulo, vazio, espaços, texto inválido, número com espaços e estouro de inteiro. |
| `service/IntelligentOrderingServiceTest.java` | Ajuste sazonal do mês atual na recomendação fornecida pelo calculador, com quantidade prévia e com cálculo da quantidade-base; preserva arredondamento. |
| `integration/OrderStockIntegrationTest.java` | Rollback real de pedido com dois itens, quando o primeiro sofre baixa e o segundo não tem saldo. Inclui reprodução opt-in do defeito de estoque negativo. |

### Por que o teste de rollback usa banco

O teste não mantém uma transação externa envolvendo a chamada do serviço.
Os dados são confirmados em uma transação de preparação; `OrderService` abre sua própria transação.
As estatísticas do Hibernate confirmam que houve atualização no banco antes da falha.
Depois, novas consultas verificam que o estoque inicial e o pedido pendente foram preservados.
Isso evita apresentar verificação de mocks ou rollback automático do framework de testes como prova de atomicidade do serviço.

O contexto JPA usa banco descartável e desativa `data.sql` apenas nesse teste, para manter as fixtures isoladas.

## Reprodução do defeito conhecido

Executar separadamente, em `backend/`:

```bash
mise exec -- ./mvnw -B -DrunKnownDefects=true \
  '-Dtest=OrderStockIntegrationTest#shouldRejectConfirmation_whenOnlyWarehouseHasEnoughStock' \
  test
```

**Resultado observado:** 1 teste, 1 falha, 0 erros, 0 ignorados; `BUILD FAILURE`.
O cenário usa loja = 2, depósito = 8, reservas = 0 e pedido = 5.
A confirmação não lançou `InsufficientInventoryException`, persistiu loja = −3 e mudou o pedido para `CONFIRMED`.

O teste exige a regra já confirmada para a correção: recusar a operação e preservar loja = 2 e pedido = `PENDING`.
Ele não considera saldo negativo um resultado correto.
Sem `-DrunKnownDefects=true`, aparece como **ignorado**, não como aprovado.
Na issue #9, corrigir a baixa e remover a condição `@EnabledIfSystemProperty` para executar essa regressão sempre.

## Validação final dos fluxos selecionados

Executar em `backend/`:

```bash
mise exec -- ./mvnw -B \
  -Dtest=AdvancedInventoryServiceTest,EnhancedInventoryReportControllerTest,IntegratedInventoryControllerTest,OrderServiceTest,InventoryControllerTest,ReportServiceTest,IntegratedInventoryAnalysisServiceTest,IntelligentOrderingServiceTest,OrderStockIntegrationTest \
  clean test
```

Resultado: `BUILD SUCCESS`. Tempo total: `01:02 min`.

| Conjunto | Casos | Aprovados | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: | ---: |
| Testes existentes selecionados | 14 | 14 | 0 | 0 | 0 |
| Novos testes | 58 | 57 | 0 | 0 | 1 |
| **Total final** | **72** | **71** | **0** | **0** | **1** |

O único ignorado é a regressão opt-in de estoque negativo, executada separadamente e reprovada conforme documentado acima.
Os totais foram conferidos nos arquivos XML do Surefire. O código de produção e o `pom.xml` permaneceram sem alterações.

Os relatórios por classe são gerados em `backend/target/surefire-reports/`.
O relatório de cobertura é gerado em `backend/target/site/jacoco/index.html`.
Esses artefatos locais não são versionados; os comandos acima permitem reproduzi-los.

## Limites e continuidade na issue #9

- **Sazonalidade:** somente o mês da execução é alcançável pelo fluxo público atual. Os 12 multiplicadores ainda não foram exercitados. Controlar a data na refatoração permitirá completar essa cobertura; o teste atual observa a quantidade alterada no DTO retornado pelo calculador, pois a resposta não expõe essa quantidade individualmente.
- **Indicadores:** as fórmulas atuais são simulações. Os testes caracterizam os cálculos estáveis e os limites dos valores aleatórios; não afirmam determinismo nem correção de métricas reais. Quando as fórmulas forem definidas e implementadas, atualizar essas expectativas e acrescentar casos determinísticos.
- **Integridade:** o rollback foi verificado em H2. Não foi validada concorrência, nem equivalência transacional em PostgreSQL.
- **Escopo:** esta execução não valida toda a suíte, execução manual da aplicação, frontend, cache Redis ou migração tecnológica.
- **Defeito aberto:** a confirmação com saldo insuficiente na loja continua incorreta. A reprodução automatizada acima orienta a correção na issue #9.
