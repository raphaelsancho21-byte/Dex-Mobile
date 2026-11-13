# DEX Security — MVP V0.1

**Duas pastas principais:** `PC/` e `Mobile/`. A pasta técnica `.github/` contém o workflow necessário para compilar o APK na nuvem. O notebook é somente a central; o Android executa o agente.

## O que funciona nesta entrega

- Interface real Electron no PC e aplicativo Android Kotlin
- Servidor HTTPS local, impressão digital SHA-256 exibida no PC e verificada no telefone
- Pareamento com PIN temporário de 6 dígitos, limite de 5 tentativas por IP
- Monitoramento Android em serviço de primeiro plano com notificação visível e heartbeats de ~15s
- Notificação no painel do PC quando um dispositivo pareia ou quando o Android reporta mudança da categoria de rede (Wi-Fi/dados/etc)
- Envio manual de alerta de teste
- Revogação de pareamento no notebook (bloqueia requisições do dispositivo)
- Ponte de atualização: escolher APK no PC, o Android solicita download por HTTPS, verifica SHA-256, abre instalador do sistema com confirmação
- GitHub Actions: compilar APK debug e disponibilizar como artefato para download

**Ainda não implementado:** VPN/firewall Android, detecção real de invasão, bloqueio de IP, Last Stand, auditoria de senhas, Tor, monitoramento de desinstalação, instalação silenciosa. Não confunda heartbeat com antimalware. A ausência de heartbeat NÃO prova ataque.

## PC (Windows)

Requisitos: Node.js LTS e npm. Abra PowerShell dentro da pasta `PC`:

```powershell
npm install
npm start
```

Na interface, anote o IP local do PC (por exemplo `192.168.0.107`), o PIN e a impressão digital TLS de 64 caracteres. Windows Firewall pode solicitar permissão para receber conexões de rede privada; permita **somente rede privada**. Porta TCP `38443`. Não encaminhe esta porta na internet.

Para tentar montar `.exe` portátil:

```powershell
npm run dist:win
```

## Criar APK sem Android Studio usando GitHub

1. Crie um repositório **privado** no GitHub.
2. Envie o conteúdo completo de `DEX-Security/` para a raiz do repositório, **inclusive `.github/workflows/build-android.yml`**. No GitHub web, as pastas ocultas podem não aparecer no ZIP de forma óbvia — confira.
3. Abra `Actions` > `Compilar DEX Mobile APK` > `Run workflow`.
4. Ao terminar, abra a execução > `Artifacts` > `DEX-Mobile-debug-APK`.
5. Baixe o artefato, extraia o `app-debug.apk` e instale no seu Samsung (autorize a instalação por essa origem quando o Android pedir).

O Gradle usado é 8.11.1, Android Gradle Plugin 8.9.2, SDK Android 35 e Java 17.

### Atenção a atualizações reais

O APK **debug** gerado em execuções diferentes no GitHub pode ser assinado com chaves de debug distintas. Nesse caso o Android **não aceitará atualizar** a instalação anterior, mesmo com o Update Bridge. Para atualizações sucessivas, configure uma **chave de assinatura de release persistente e privada** no GitHub Secrets. Jamais coloque o keystore no repositório.

Workflow oferece **release opcional**: crie um keystore usando `keytool -genkeypair -v -keystore release.keystore -alias dex -keyalg RSA -keysize 3072 -validity 10000` em um computador seguro, faça backup offline e gere seu Base64. Configure no repositório os secrets `DEX_KEYSTORE_BASE64`, `DEX_STORE_PASSWORD`, `DEX_KEY_ALIAS`, `DEX_KEY_PASSWORD`. Com esses secrets, o workflow também envia `DEX-Mobile-release-APK`. Não compartilhe a chave ou a senha. Para atualizar, mantenha a mesma chave de release, aumente `versionCode` em `Mobile/app/build.gradle.kts` e instale sempre o APK **release**. O Android não permite atualizar diretamente uma instalação debug assinada com outra chave: desinstale a versão debug antes de instalar a primeira release (isso apaga dados locais do DEX e requer pareamento novo).

## Parear

1. PC e Samsung no mesmo Wi-Fi (sem isolamento entre clientes).
2. Abra o programa PC; mantenha-o aberto.
3. Abra DEX Mobile no Samsung, digite IP do notebook, fingerprint TLS e PIN.
4. Toque `Parear`, depois `Iniciar monitoramento` e aceite a permissão de notificações.
5. Teste `Enviar evento de teste`; acompanhe na lista de eventos do desktop.

## Atualização pelo Wi-Fi

- Compile o APK e selecione-o em `Selecionar APK` no notebook.
- No Samsung pareado, toque `Buscar APK pelo Update Bridge`.
- O arquivo é transferido via HTTPS com certificado pinado, conferido contra SHA-256 exibido pelo servidor autenticado, e enviado ao instalador oficial Android.
- O Android poderá solicitar permissão para instalar apps desta origem e confirmação da instalação. O APK deve ter o mesmo `applicationId`, assinatura compatível e `versionCode` apropriado.
- O serviço DEX pode ser interrompido durante a atualização.

## Limitações conhecidas

- A notificação de monitoramento do Android é permanente enquanto o serviço está ativo; isto é intencional.
- O Android pode interromper serviços em segundo plano por economia de bateria e limites de execução, sobretudo em versões modernas.
- A versão atual não salva eventos remotamente após o fechamento do PC; seu histórico fica em memória enquanto a janela está aberta.
- A conexão HTTPS usa certificado autoassinado gerado no primeiro uso pelo PC. Se a identidade TLS armazenada em `~/.dex-control-center/tls.json` for apagada, será preciso conferir a nova impressão digital e parear novamente.
- Não abra a porta 38443 fora da rede privada e não instale APKs não verificados.
- Pareamento e notificações são manuais e visíveis: não há instalação oculta, exclusão automática ou proteção contra desinstalação.
