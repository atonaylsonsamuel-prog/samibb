# SAM TV OFICIAL

Aplicativo Android de streaming da **SAM DEVELOP** para **celular e TV Box / Android TV** (mesmo APK). As listas vêm do **painel** `https://painelsamtvpc.brasilapps.com.br` — o mesmo do app de Windows: o cliente só vê o ID do aparelho e um código, e o revendedor ativa e cadastra as listas (Xtream Codes com o DNS do painel, ou M3U).

**Novidades da 1.1.0:** tela inicial no estilo painel IPTV (TV ao vivo em destaque, Filmes, Séries, Conta, Trocar lista, Ajustes, Recarregar, Sair), uso com controle remoto, ativação pelo painel, login só com usuário e senha, bloqueio/vencimento pelo painel e **atualização do app pelo painel** ([docs/ATUALIZACAO-PELO-PAINEL.md](docs/ATUALIZACAO-PELO-PAINEL.md)).

> O aplicativo não fornece canais nem conteúdo. Não contém dados de canais, logos ou sinopses: tudo o que aparece vem da fonte configurada pelo usuário.

Esta é a primeira implementação de um produto multiplataforma. A versão Roku será um projeto separado; os contratos estão em [`docs/ROKU-CONTRACTS.md`](docs/ROKU-CONTRACTS.md).

---

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.1 (JDK 17) |
| UI | Jetpack Compose + Material 3, Navigation Compose |
| Arquitetura | MVVM + Repository Pattern + casos de uso; módulo `:core` em Kotlin puro |
| Assíncrono | Coroutines + Flow |
| Player | AndroidX Media3 / ExoPlayer (HLS, DASH, progressivo/MPEG-TS) com OkHttp |
| Rede | OkHttp (redirecionamentos controlados manualmente) |
| Persistência | Room (catálogo em cache, favoritos, histórico, perfis) + DataStore (preferências) |
| Segurança | Android Keystore (AES-256/GCM) para senhas e URLs de playlist |
| Imagens | Coil 2 |
| Build | Gradle Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`) |
| Testes | JUnit 4, testes instrumentados (Compose UI Test, MockWebServer) |

## Estrutura de diretórios

```
SamTvOficial/
├── core/                       # Kotlin puro — reutilizável e referência para o Roku
│   └── src/main/kotlin/com/samdevelop/samtv/core/
│       ├── model/              # Modelos de domínio (ConnectionProfile, Channel, Movie, Series…)
│       ├── repository/         # Contratos: AuthenticationRepository, LiveTvRepository, VODRepository…
│       ├── source/             # SourceConnector (interface comum dos conectores)
│       ├── xtream/             # Conector Xtream Codes (player_api.php)
│       ├── m3u/                # Parser M3U incremental + conector M3U
│       ├── catalog/            # Regras: busca, IDs estáveis, agrupamento M3U, histórico
│       ├── net/                # HttpClient abstrato, validação de URL, redação de segredos
│       └── common/             # AppResult / AppError
│   └── src/test/               # 42 testes + fixtures (m3u/json) reutilizáveis pelo Roku
├── app/                        # Aplicativo Android
│   └── src/main/java/com/samdevelop/samtv/
│       ├── MainActivity.kt, SamTvApplication.kt, AppContainer.kt (DI manual)
│       ├── navigation/         # Rotas e NavHost
│       ├── ui/theme, ui/components, ui/common
│       ├── ui/screens/         # Home, Connection, LiveTv, ChannelPlayer, Movies/Series (VodScreens),
│       │                       # Details, Search/Favorites/History (LibraryScreens), Settings
│       ├── domain/usecase/     # ValidateConnectionUseCase, SessionManager (sincronização/estado)
│       ├── data/remote/        # OkHttpHttpClient
│       ├── data/local/         # Room (entidades, DAOs), DataStore, mapeadores
│       ├── data/repository/    # Implementações dos contratos do core
│       ├── player/             # PlayerFactory + PlayerController (integração Media3)
│       └── security/           # CredentialStore (Keystore)
│   └── src/test/               # Testes JVM (validação de formulário, mensagens de erro)
│   └── src/androidTest/        # Testes instrumentados (Room, Keystore, rede, player, fluxo completo)
├── .github/workflows/android.yml  # Build na nuvem (APK) + testes
├── docs/ROKU-CONTRACTS.md, docs/MANUAL-TESTS.md
├── README.md, INSTALL-WINDOWS.md, ARCHITECTURE.md, TEST-REPORT.md, CHANGELOG.md
```

## Requisitos

- Android Studio (versão estável recente, com suporte a AGP 8.10) — inclui o JDK 17+ (JetBrains Runtime).
- Android SDK Platform 36 e Build-Tools (o Android Studio instala ao sincronizar).
- Celular com **Android 8.0 (API 26)** ou superior.

## Opção 1 — Gerar o APK sem instalar nada (GitHub Actions)

1. Crie um repositório (pode ser privado) em github.com.
2. Envie o conteúdo deste zip para o repositório (pelo site: *Add file → Upload files*, arrastando todas as pastas, incluindo `.github`).
3. Abra a aba **Actions** → workflow **Android build**. Ele roda sozinho a cada envio (ou clique em *Run workflow*).
4. Ao terminar (≈ 8–12 min), abra a execução e baixe o artefato **samtv-apks**.
5. Dentro dele: `app-debug.apk` (para instalar e testar) e `app-release-unsigned.apk` (para assinar).

O mesmo workflow roda os testes de unidade e publica os relatórios (`test-reports`). Os testes instrumentados podem ser executados num emulador da própria nuvem: *Run workflow* → marque "Rodar também os testes instrumentados".

## Opção 2 — Android Studio

1. *File → Open* e selecione a pasta `SamTvOficial`.
2. Aguarde o *Gradle Sync* (primeira vez baixa as dependências).
3. Selecione o módulo `app` e o seu celular → ▶ *Run*.

Linha de comando (na pasta do projeto):

```bash
./gradlew :core:test :app:testDebugUnitTest   # testes de unidade
./gradlew :app:assembleDebug                  # APK de debug  → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease                # APK de release não assinado → app/build/outputs/apk/release/
./gradlew :app:connectedDebugAndroidTest      # testes instrumentados (celular/emulador conectado)
```

No Windows use `gradlew.bat` no lugar de `./gradlew`. Detalhes em [INSTALL-WINDOWS.md](INSTALL-WINDOWS.md).

## Instalar no celular ou na TV Box

- **Celular:** copie o `app-release.apk` para o aparelho, abra pelo gerenciador de arquivos e permita "instalar apps de fontes desconhecidas".
- **TV Box / Android TV:** pendrive + gerenciador de arquivos, ou o app *Downloader* com o link do APK. O SAM TV aparece na tela inicial da TV (banner próprio).
- **Pelo cabo/rede (ADB):** `adb install -r app-release.apk`.

O build de debug usa o id `com.samdevelop.samtv.debug` (pode coexistir com o de release) e **não recebe atualização pelo painel** — distribua aos clientes sempre o `app-release.apk`.

### Assinatura (obrigatória para atualizar pelo painel)

O Android só instala uma atualização se ela for assinada com a **mesma chave** do app instalado. Crie a chave **uma única vez** e guarde-a (sem ela, nenhum cliente consegue atualizar — teria que desinstalar e ativar de novo):

```bash
keytool -genkeypair -v -keystore samtv-release.jks -alias samtv -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 samtv-release.jks > samtv-release.jks.b64        # Windows: certutil -encode samtv-release.jks samtv-release.jks.b64
```

No GitHub do projeto: *Settings → Secrets and variables → Actions → New repository secret*:

| Secret | Valor |
|---|---|
| `SAMTV_KEYSTORE_BASE64` | conteúdo do `.b64` (no Windows, sem as linhas `-----BEGIN/END-----`) |
| `SAMTV_KEYSTORE_PASSWORD` | senha do keystore |
| `SAMTV_KEY_ALIAS` | `samtv` |
| `SAMTV_KEY_PASSWORD` | senha da chave |

A cada build, o artefato `samtv-apks` traz `release/app-release.apk` (assinado) e `SHA256.txt` (o valor para publicar a atualização no painel). No Android Studio, use as mesmas variáveis de ambiente (`SAMTV_KEYSTORE_PATH` etc.) ou *Build → Generate Signed APK* com o mesmo `.jks`. **Nunca** coloque o `.jks` ou as senhas no repositório.

### Publicar uma nova versão

1. Aumente `versionCode` e `versionName` em `app/build.gradle.kts` (1.1.0 = código 2).
2. Gere o `app-release.apk` assinado com a mesma chave.
3. Publique no painel (contrato em [docs/ATUALIZACAO-PELO-PAINEL.md](docs/ATUALIZACAO-PELO-PAINEL.md)). Os apps oferecem a atualização na próxima consulta ao painel.

## Ativação pelo painel

1. O cliente abre o app: aparecem o **ID do aparelho** (ex.: `AB23-CD45-EF67`) e um **código de 6 números**.
2. No painel, **Ativar aparelho** com ID + código, cliente e validade; depois cadastre as **listas** do aparelho (Xtream com DNS do painel, ou M3U).
3. Em até 15 segundos o app libera a tela inicial e baixa o catálogo. Nada de servidor ou senha para digitar na TV.
4. Se o painel permitir (Ajustes → login só com usuário e senha), o cliente também pode tocar em **Entrar com usuário e senha**: o painel testa os DNS ativos e devolve o certo.

O app consulta o painel ao abrir, ao voltar para ele, pelo botão **Verificar painel** (Conta/Ajustes) e no intervalo definido pelo painel. Bloqueio, vencimento, troca de DNS, listas novas ou removidas chegam nessa consulta. Sem internet, as listas salvas continuam valendo por até 7 dias.

Identidade do aparelho (ID + segredo de 288 bits), senhas e URLs das listas ficam cifradas com o Android Keystore. A API do painel é sempre HTTPS; o segredo vai no corpo, nunca na URL.

## Controle remoto (TV Box)

| Tela | Teclas |
|---|---|
| Início | setas movem entre os cartões (borda branca = foco), OK abre, Voltar pergunta se quer sair |
| TV ao vivo / Filmes / Séries | categorias à esquerda, conteúdo à direita; OK abre |
| Player sem controles | ↑/↓ e CH+/CH− trocam de canal, ←/→ voltam/avançam 10 s (filmes), OK mostra os controles, Play/Pause |
| Player com controles | setas navegam entre os botões, Voltar esconde os controles |

## Diagnóstico de falhas

| Sintoma | Causa provável / ação |
|---|---|
| "Usuário ou senha incorretos" | Credenciais erradas ou conta bloqueada no painel. |
| "Sua assinatura expirou" | O servidor informou `status: Expired`. Renove com o provedor. |
| "formato inesperado" / HTTP 404 | Endereço não é de um servidor Xtream (confira porta e prefixo de caminho). |
| "certificado de segurança" | HTTPS com certificado inválido. O app não ignora isso; use o endereço correto ou `http://` com consentimento. |
| "tentou redirecionar… bloqueada" | O servidor redirecionou a requisição com sua senha para outro domínio. Fale com o provedor. |
| Canal não abre / "Formato não suportado" | Em Ajustes → *Formato da TV ao vivo*, alterne entre HLS e MPEG-TS. |
| "não suporta o codec" | O aparelho não tem decodificador para aquele stream (ex.: HEVC 10-bit em aparelhos antigos). |
| "Acesso ao stream negado (HTTP 403)" | Limite de conexões simultâneas da conta atingido, ou bloqueio do provedor. |
| Catálogo vazio | Ajustes → *Atualizar catálogo*; veja o estado em *Catálogo*. |
| Fica em "Ative este aparelho" | Aparelho ainda não ativado, ou ativado sem nenhuma lista ativa no painel (ou com o DNS da lista desativado). |
| "Sem conexão com o painel" | Internet do aparelho, ou o painel fora do ar/sem HTTPS válido. |
| Atualização: "App não instalado" | O APK novo foi assinado com outra chave. Use sempre o mesmo `.jks`. |

O app não grava logs com URLs ou senhas; mensagens de erro passam por um redator de segredos (`SecretRedactor`).

## Limitações conhecidas

- **APK não foi gerado no ambiente de desenvolvimento** desta entrega (sem acesso ao repositório Maven do Google). Use o workflow do GitHub ou o Android Studio. Ver [TEST-REPORT.md](TEST-REPORT.md).
- O cadastro manual de fontes saiu da interface: as listas vêm do painel (ou do login usuário/senha).
- A atualização pelo painel depende do campo `update` no `device.php`, ainda não implementado no painel.
- Teclado na TV: o login por usuário e senha usa o teclado do sistema da TV Box.
- Catch-up/timeshift (TV arquivada) não implementado — não há botão para isso.
- EPG: apenas a programação curta do Xtream (`get_short_epg`). Guia XMLTV completo não implementado.
- M3U: todas as entradas são tratadas como canais (playlists não distinguem filmes/séries de forma confiável).
- Fontes com DRM não são suportadas (e não haverá mecanismo para contorná-lo).
- Textos da interface estão em português no código (sem `strings.xml` para tradução).
- Catálogos muito grandes (centenas de milhares de itens) são baixados por inteiro em cada atualização; o uso de memória pode ser alto em aparelhos modestos.
- Picture-in-Picture e Chromecast não implementados.
- Criptografia local reduz a exposição das credenciais, mas não protege contra um aparelho comprometido (root/malware).

## Licença e uso

Propriedade da SAM DEVELOP. Use apenas com fontes e conteúdos que você tem autorização para acessar.
