# Everton MP3 Player — Especificação

- Android nativo, tema escuro e controles grandes.
- O usuário cadastra várias pastas principais pelo seletor do Android; apenas uma fica ativa por vez.
- A biblioteca ativa inclui a pasta principal e todas as suas subpastas.
- Navegação em árvore expansível; um único caminho fica aberto por vez e existe botão grande de Voltar.
- Tocar em uma faixa inicia imediatamente a reprodução.
- Exibir número + nome da faixa sem extensão; ordenar pelo número inicial do arquivo e depois pelo nome.
- Uma subpasta que contém músicas é tratada como álbum. Ao terminar um álbum, seguir para o próximo álbum na árvore.
- Quando a pasta principal funciona como playlist sem subpastas, repetir a sequência desde a primeira faixa.
- Capa: primeiro arte embutida no MP3; depois cover/folder/capa JPG/PNG da pasta; depois imagem padrão.
- Busca global em todas as pastas principais cadastradas.
- Player em segundo plano com controles de notificação, tela bloqueada e dispositivos externos via MediaSession.
- Tela principal contém volume de 0 a 100 e botão Luffy liga/desliga.
- Luffy usa LoudnessEnhancer com ganho moderado de +6 dB (600 mB), sem equalizador.
- Sem botão Sair; o comportamento de voltar/minimizar segue o padrão do Android.
