# Changelog

## [1.1.0] — 2026-10-12 — painel, novo layout e TV Box

### Adicionado
- **Ligação com o painel** `painelsamtvpc.brasilapps.com.br` (o mesmo do Windows): ID do aparelho + código, ativação pelo revendedor, listas Xtream/M3U entregues pelo painel, bloqueio e vencimento, troca de DNS, tolerância de 7 dias sem internet.
- **Login só com usuário e senha** (quando o painel permite): o painel descobre o DNS; se o DNS mudar, o app redescobre sozinho.
- **Novo layout estilo painel IPTV**: TV ao vivo em destaque; Filmes, Séries, Conta e Trocar lista; Favoritos, Histórico, Ajustes, Recarregar e Sair; relógio e vencimento no topo. Adapta-se a retrato e paisagem.
- **TV Box / Android TV**: mesmo APK, banner na tela inicial da TV, foco visível, categorias em coluna lateral na horizontal e **controle remoto no player** (canal ↑/↓, CH+/−, ←/→, OK, play/pause).
- **Atualização do app pelo painel** (download HTTPS, SHA-256, pacote e versão conferidos, instalador do Android). Contrato em `docs/ATUALIZACAO-PELO-PAINEL.md`.
- Telas de ativação, sem conexão, vencido, bloqueado e atualização obrigatória; tela **Conta**.
- Workflow: APK de release assinado com chave fixa via secrets e `SHA256.txt`.
- Testes: +20 no core (cliente, regras e gerenciador do painel), +1 JVM no app, integração do núcleo Android com o painel PHP real (13 verificações), suíte instrumentada atualizada.

### Alterado
- Cadastro manual de Xtream/M3U removido da interface (as listas vêm do painel).
- Barra de navegação inferior substituída pela tela inicial em cartões.


## [1.0.0] — 2026-10-12 — primeira versão (celular Android)

### Adicionado
- Projeto multi-módulo: `:core` (Kotlin puro) e `:app` (Android, Compose + Material 3).
- Conexão **Xtream Codes**: validação real de `user_info`, estado/expiração da conta, formatos permitidos, TV ao vivo, filmes, séries (temporadas/episódios) e EPG curto.
- **Playlist M3U por URL**: parser incremental e tolerante (aspas, CRLF/BOM, `#EXTGRP`, `#EXTVLCOPT`, duplicatas, entradas incompletas), organização por grupo ou por letra.
- Catálogo em cache (Room) com paginação, busca sem acento e atualização sob demanda; funciona sem rede com o último catálogo.
- Player Media3: HLS/DASH/progressivo/MPEG-TS, tela cheia e paisagem, proporção, faixas de áudio e legendas (quando existirem), troca de canal, reconexão automática, retomada de VOD, liberação correta no ciclo de vida.
- Favoritos, histórico (sem duplicação, limite de 200 por fonte) e configurações (fontes, estado da conexão, catálogo, formato da TV, privacidade, versão).
- Segurança: credenciais cifradas com Android Keystore; HTTP somente com consentimento; redirecionamentos controlados; redação de segredos em erros; sem backup em nuvem.
- Testes: 42 no core + 7 JVM no app (executados) e suíte instrumentada com servidor simulado (pendente de execução).
- Workflow do GitHub Actions para gerar APKs e rodar testes.
- Documentação: README, INSTALL-WINDOWS, ARCHITECTURE, TEST-REPORT, contratos Roku e roteiro de testes manuais.

### Pendente
- Execução do build Gradle completo e dos testes instrumentados (ver TEST-REPORT.md).
- Catch-up/timeshift, guia XMLTV completo, PiP, Chromecast, internacionalização via `strings.xml`.
- Versão Roku (projeto separado).
