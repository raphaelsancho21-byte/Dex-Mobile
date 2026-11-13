# DEX V0.2 — Kit de pareamento por QR Code (protótipo)

**Isto é um projeto de demonstração SEPARADO**, não uma atualização já integrada ao DEX-Security V0.1. O ZIP original com o código do DEX não foi disponibilizado nesta conversa. Nenhum arquivo do aplicativo original foi alterado. A versão produzida aqui não contém o motor de proteção, os módulos de segurança nem a ponte de atualização de APK.

## O que funciona nesta base

- Central PC em Node.js: exibe QR Code temporário de 60 segundos, mostra solicitações de conexão, aceita/nega e revoga dispositivos.
- App Android **nativo Java**: escaneia QR com câmera, solicita aprovação, salva o vínculo e verifica conexão com o PC.
- A mesma rede Wi-Fi deve conectar notebook e telefone. Não precisa digitar chave de 64 caracteres neste protótipo.
- Para **integrar ao DEX existente**, será necessário mesclar o serviço PC ao Electron atual e a tela Android ao app Mobile atual.

## Como abrir no PC

1. Instale Node.js 20 ou superior.
2. Abra PowerShell nesta pasta `PC`.
3. Execute `npm install` e `npm start`.
4. Abra **http://127.0.0.1:4343** no navegador do PC.
5. Clique em **Gerar novo QR Code**.
6. Confira que a rede Wi-Fi do PC permite conexões de entrada TCP na porta 4343 (faça uma regra restrita à rede privada, se necessário).

O QR indica o IP local escolhido automaticamente. Em notebooks com VPN e múltiplos adaptadores, pode escolher o IP errado; desligue a VPN ou ajuste a função `lanIp()` no arquivo `PC/server.js`.

## Como criar o APK no GitHub

1. Envie a estrutura deste ZIP ao repositório, mantendo `.github/workflows/build-apk.yml` na raiz do repositório.
2. Na aba Actions execute `DEX QR Demo APK` (ou deixe o push iniciar a compilação).
3. Baixe o artefato `DEX-QR-V02-Demo-APK` na execução concluída.
4. Instale o APK no Android (possivelmente com consentimento para apps de fontes externas).
5. Abra o app **DEX QR V0.2 Demo**, clique em **Escanear QR do PC**, e aprove o pedido na tela do PC.

**Atenção:** `applicationId` é `com.dex.security.qrdemo` para não sobrescrever seu DEX já instalado. Este APK é separado. O Gradle do runner precisa de conexão aos repositórios Maven/Google; a compilação ainda não foi testada neste ambiente.

## Limitações importantes de segurança

**Não use este código como sistema de segurança de produção.** A comunicação é HTTP sem TLS, e o vínculo é armazenado em SharedPreferences em texto claro. Em redes monitoradas, credenciais e conteúdo podem ser interceptados. Use somente em rede doméstica de teste, sem dados sensíveis; não abra a porta para internet.

Os vínculos são mantidos na memória do processo PC, e são perdidos ao reiniciar a central. Isto é intencional neste protótipo. Para liberar versão integrada segura, implemente TLS com validação/pinning de certificado, credenciais no Android Keystore, autenticação de endpoints (replay protection), persistência segura no PC, políticas de revogação, limite de solicitações e testes de segurança.

## Atualizações PC → Android

**Não implementado neste kit**. Uma atualização genuína exige baixar/receber APK com integridade e assinatura verificadas, manter a identidade de assinatura do app já instalado e obter a confirmação do Android para instalação. Nunca instale APK arbitrário recebido pela rede.
