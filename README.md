# Everton MP3 Player

Android nativo para tocar MP3 locais com navegação por pastas e controles grandes.

## Recursos
- Várias pastas principais salvas; uma ativa por vez, com leitura recursiva das subpastas.
- Árvore expansível com um caminho aberto por vez.
- Número + nome da faixa sem `.mp3`.
- Busca global em todas as pastas cadastradas.
- Capas por arte embutida, `cover`/`folder`/`capa` na pasta e fallback padrão.
- Media3 em segundo plano, tela bloqueada e controles externos.
- Volume na tela principal e **Luffy** (+6 dB via LoudnessEnhancer) liga/desliga.
- Sem equalizador.

O workflow `Android CI` gera o APK de debug como artefato `mp3-player-debug-apk`.
