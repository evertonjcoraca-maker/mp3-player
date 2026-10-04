# Everton MP3 Player

Aplicativo Android nativo para tocar MP3/WAV locais, com navegação por pastas, controles grandes e comando de voz em segundo plano.

## Versão 1.5.0 — comando de voz

O comando de voz pode permanecer ativo com o aplicativo minimizado e com a tela apagada. O botão de voz continua visível na tela e mostra claramente o estado ligado/desligado.

- Palavra de ativação local/offline: **Player**.
- Idioma inicial dos comandos: **inglês (`en-US`)**.
- Troca de idioma: **“Player, linguagem português”** e **“Player, linguagem inglês”**; equivalentes em inglês também são aceitos.
- Transporte: tocar/continuar, pausar, parar, próxima e anterior.
- Volume: aumentar/diminuir em 10%, máximo, zero, mute e restaurar volume.
- Biblioteca: buscar/tocar música pelo nome, tocar álbum, tocar artista e tocar todas as músicas em ordem aleatória.
- Variações curtas são aceitas, como **“Player, próxima”** e **“Player, toca Queen”**.
- **“Player, encerrar comando de voz”** desliga a escuta. Depois disso, a voz só volta quando o botão for ligado manualmente.
- Voz e botões da tela funcionam juntos e controlam o mesmo `PlaybackService`/`MediaSession`.
- A escuta da palavra **Player** usa Vosk local; o áudio de voz não é salvo pelo aplicativo.

Ao ativar o recurso pela primeira vez, o Android solicita permissão de microfone. Enquanto a voz estiver ligada, uma notificação de serviço em primeiro plano informa **“Comando de voz ativo”**.

## Outros recursos

- Várias pastas principais salvas; uma ativa por vez, com leitura recursiva das subpastas.
- Árvore expansível com um caminho aberto por vez.
- Número + nome da faixa sem extensão.
- Busca global em todas as pastas cadastradas.
- Capas por arte embutida, `cover`/`folder`/`capa` na pasta e fallback padrão.
- Media3 em segundo plano, tela bloqueada e controles externos.
- Volume na tela principal e **Luffy** (+6 dB via `LoudnessEnhancer`) liga/desliga.
- Sem equalizador.

## Build

O workflow **Android CI** executa os testes unitários, monta o APK de debug e publica o artefato `mp3-player-debug-apk`.
