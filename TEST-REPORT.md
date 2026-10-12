# Relatório de testes e build — SAM TV OFICIAL

## Versão 1.1.0 (12/10/2026) — painel, layout estilo IBO, TV Box e atualização

Somente o que foi realmente executado. Mesmo ambiente da 1.0.0: **sem Android SDK e sem Google Maven** (bloqueados pela rede) — o APK é gerado pelo workflow do GitHub ou pelo Android Studio.

### `:core` — 62 testes, 62 aprovados (12 execuções seguidas, sem falha intermitente)

| Classe | Novos | Cobertura |
|---|---|---|
| `PanelTest` | 9 | JSON enviado ao `device.php` (plataforma android, segredo só no corpo), leitura de listas Xtream/M3U, senha com espaços preservada, lista inválida descartada, versão da fonte só muda com conteúdo, cache ida e volta, listas só com status ativo, erros do painel (401, 429, timeout, rede, resposta inválida), login usuário/senha, painel sem HTTPS recusado, atualização só HTTPS e versão maior, SHA-256 inválido ignorado, fases do app, ID do aparelho (alfabeto, formato, 500 IDs) e segredo |
| `PanelManagerTest` | 11 | primeiro uso pendente → ativo, lista removida no painel, troca de DNS, 7 dias sem painel e depois "sem conexão", reinício só com o cache cifrado, vencido, vencido + login usuário/senha, bloqueado, erro de login, colisão de ID, atualização obrigatória, armazenamento seguro indisponível, consulta imediata pelo botão |

### `:app` (JVM) — 8 testes, 8 aprovados
`ValidateConnectionUseCaseTest`, `ErrorMessagesTest` e `PanelTextTest` (datas do painel).

### Integração do núcleo Android com o **painel PHP real** — 13/13 aprovados
Painel `samtv-painel` (o mesmo publicado no cPanel) em PHP 8.3 + MariaDB 10.11, Xtream simulado, e o `PanelManager`/`PanelClient` do APK rodando na JVM (HTTP do JDK no lugar do OkHttp):

1. app registra no painel e mostra ID + código · 2. painel guarda plataforma android, versão e modelo · 3. admin ativa com ID + código · 4. app libera com cliente e vencimento · 5. recebe Xtream (DNS do painel) e M3U · 6. M3U exibida sem credenciais · 7. lista ativa definida · 8. Xtream autentica com os dados vindos do painel · 9. URL M3U nos segredos · 10. senha errada recusada · 11. login só com usuário e senha (painel acha o DNS) · 12. lista removida no painel sai do app, login permanece · 13. bloqueio no painel bloqueia o app e esconde o login.

### Código Android (Compose, Keystore, instalador)
Compilado com o compilador Kotlin sem as bibliotecas Android: **nenhum erro de sintaxe**; todos os erros restantes são de referências ao SDK/AndroidX ausentes (conferidos um a um nos arquivos novos). A checagem de tipos completa acontece no build do GitHub/Android Studio.

### Não executado
- Build Gradle/APK e testes instrumentados (`AppFlowTest` reescrito para a nova tela inicial; `PanelScreensTest` novo).
- Teste em aparelho real (celular e TV Box) — roteiro em `docs/MANUAL-TESTS.md`.
- Atualização pelo painel de ponta a ponta: depende do campo `update` no painel.

---

## Versão 1.0.0

Data da execução: 12/10/2026 (UTC). Este relatório registra **apenas o que foi realmente executado**.

## Ambiente de desenvolvimento desta entrega

- Linux, OpenJDK 21, Gradle 8.14.3 local, compilador Kotlin 2.0.21 (embutido no Gradle).
- **Sem Android SDK e sem acesso ao Google Maven / Maven Central** (bloqueados pela política de rede do ambiente).

Consequências:

| Item | Situação |
|---|---|
| `./gradlew` (build Gradle completo) | **Não executado** — o Gradle não consegue baixar AGP, Kotlin plugin e bibliotecas. |
| APK de debug / release | **Não gerado aqui.** Gere pelo workflow `.github/workflows/android.yml` ou pelo Android Studio. |
| Módulo `:core` | **Compilado e testado** com o compilador Kotlin local (`kotlinc` via `K2JVMCompiler`) + JUnit 4.13.2, com `-Werror` (zero avisos). |
| Testes JVM do `:app` sem dependência Android | **Compilados e executados** junto com o core. |
| Demais fontes do `:app` | Verificação sintática completa (nenhum erro de sintaxe); a checagem de tipos depende do SDK/AndroidX e **não pôde ser feita aqui**. |
| Testes instrumentados | **Escritos, não executados** (exigem dispositivo/emulador). |

## Resultados executados

### `:core` — 42 testes, 42 aprovados, 0 falhas

| Classe | Testes | Resultado |
|---|---|---|
| `M3uParserTest` | 12 — playlist válida; URL com parâmetros; playlist vazia; comentários; atributos com aspas simples/sem aspas/vírgula dentro de aspas; atributos ausentes; linhas inválidas e entradas incompletas; `#EXTGRP`/`#EXTVLCOPT`; duplicatas; CRLF/BOM/espaços; ordem preservada; playlist grande (100.000 entradas, incremental) | ✅ |
| `XtreamConnectorTest` | 17 — login válido; codificação de parâmetros e host único; credenciais inválidas (auth=0, 401, 403); conta expirada/banida; HTTP 200 sem estrutura válida; JSON malformado; timeout/erro de rede/TLS (com redação de senha); erro HTTP 500/404; HTTP sem consentimento não envia nada; campos ausentes e tipos variados; categorias vazias e listas `[]`/`{}`/`null`; respostas inesperadas; séries (objeto e array); detalhes de filme; EPG Base64; URLs de reprodução | ✅ |
| `UrlAndSecurityTest` | 6 — normalização de endereço; rejeição de endereços inválidos; extração de `get.php`; política de redirecionamento; redação de credenciais; endereço de exibição | ✅ |
| `CatalogRulesTest` | 7 — busca sem acento/ordem; organização por letra; "Sem categoria"; IDs estáveis; histórico sem duplicação e com limite; conector M3U (cache, HTTP); playlist vazia/HTML/erro HTTP | ✅ |

Saída real:

```
JUnit version 4.13.2
..........................................
Time: 2.468

OK (42 tests)
```

### `:app` (JVM, sem Android) — 7 testes, 7 aprovados

| Classe | Testes | Resultado |
|---|---|---|
| `ValidateConnectionUseCaseTest` | 5 — cadastro Xtream (perfil × segredo); campos obrigatórios; consentimento HTTP; M3U guarda URL como segredo; edição mantém senha | ✅ |
| `ErrorMessagesTest` | 2 — toda falha tem mensagem e nenhuma vaza credenciais; data de expiração formatada | ✅ |

```
JUnit version 4.13.2
.......
Time: 0.11

OK (7 tests)
```

Durante a verificação, os testes encontraram e levaram à correção de: (1) página HTML retornada no lugar de playlist era classificada como "playlist vazia" — agora é "não é uma playlist M3U"; (2) expectativa errada no próprio teste de catálogo Xtream (contagem de itens válidos).

## Testes instrumentados (escritos, aguardando execução)

Executar com `./gradlew :app:connectedDebugAndroidTest` ou no GitHub Actions (*Run workflow* → "Rodar também os testes instrumentados"). Usam servidor Xtream **simulado** (MockWebServer) e um clipe de vídeo sintético gerado para os testes (`androidTest/assets/samtv_test_clip.mp4`: barras de cor + tom de 440 Hz, 3 s, H.264/AAC).

| Classe | Cobertura |
|---|---|
| `DataLayerTest` | Persistência de favoritos (isolados por fonte); histórico sem duplicação, limite e limpeza; credenciais cifradas (sem texto puro nas preferências) |
| `NetworkAndPlayerTest` | HTTP sem consentimento não faz requisição; redirecionamento para outro host com credenciais bloqueado; timeout; player reproduz o arquivo de teste com cabeçalhos corretos e libera recursos; erro 404 sem novas tentativas |
| `AppFlowTest` | Inicialização; primeira utilização; rotação/recriação; validação do formulário; consentimento HTTP; cadastro; catálogo; navegação; busca; favorito; player e rotação durante a reprodução; remoção com confirmação |

**Resultado: não executado nesta entrega.** Preencha após rodar:

| Data | Dispositivo/emulador | Aprovados | Falhas | Observações |
|---|---|---|---|---|
| | | | | |

## Testes manuais

Roteiro em [`docs/MANUAL-TESTS.md`](docs/MANUAL-TESTS.md) (28 passos com uma fonte autorizada). **Não executado** nesta entrega.

## Build pendente — comandos a executar

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest   # com aparelho/emulador
```

Registre aqui o resultado do primeiro build completo (data, versão do Android Studio/AGP, sucesso/erros).
