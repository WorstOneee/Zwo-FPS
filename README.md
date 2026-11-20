# Zwo FPS

Plugin para **Paper 1.21.11** que ajuda o FPS dos jogadores de PC fraco reduzindo o que o
servidor **envia** para o cliente: menos chunks, menos entidades acumuladas e sem chuva.
Não mexe no mundo, nos mobs, nas farms nem no ticking do servidor.

> O servidor não consegue medir o FPS do jogador. Por isso o Zwo FPS atua só no que
> comprovadamente pesa na renderização do cliente e pode ser controlado sem mods.

## O que ele faz

| Recurso | Como funciona |
|---|---|
| **Chunks enviados por jogador** | `Player#setSendViewDistance`: o cliente só renderiza o que recebe. Não altera a view-distance de carregamento nem a simulation-distance, então farms, spawn e redstone continuam iguais. |
| **Culling por distância + orçamento** | Por categoria (displays, hologramas, molduras, armor stands, itens no chão, animais, aldeões): esconde o que está além de `hide-beyond` blocos e mantém visíveis só os `max-visible` mais próximos. Perto do jogador (`always-visible-radius`) nada some, porque é ali que ele interage. |
| **Modo automático de verdade** | Lê o render distance e a opção de partículas que o próprio cliente informa (`ClientOption`). Se o jogador baixa as configurações, o perfil se ajusta na hora; com o jogo no alto, nada muda. |
| **Clima limpo** | Esconde chuva/neve (muitas partículas) por jogador. Cada um pode reativar no menu. |
| **Proteções** | Nunca esconde jogadores, entidades com scoreboard tag `zwofps_keep`, entidades com passageiros, nem nomes/textos de TextDisplay que contenham `NPC`, `LOJA`, `QUEST`... |
| **Dicas pelo client** | `/fps dicas` usa a brand do cliente (vanilla, Fabric, Forge, Lunar...) e as opções dele para sugerir o que realmente dá FPS (ex.: Sodium para quem joga vanilla). |
| **Aviso de PC fraco** | No login, quem está com render distance/partículas baixas e o modo sem efeito recebe um botão clicável para ativar (com cooldown). |
| **Hotspots para a staff** | `/zwofps hotspots` lista os chunks carregados com mais entidades e tile entities, com teleporte por clique. |

Preferências ficam no **PersistentDataContainer** do jogador (salvas junto com os dados dele,
sem arquivo extra e sem IO na thread principal).

## ClearLag

O Zwo FPS **não remove entidades e não mexe em IA**. Isso é carga do servidor e já é o papel do
ClearLag. Os dois atuam em camadas diferentes:

- **ClearLag:** remove/limita entidades e controla IA (menos trabalho para o servidor).
- **Zwo FPS:** decide o que cada cliente recebe e renderiza (menos trabalho para o PC do jogador).

Quando o ClearLag remove algo que estava escondido para alguém, o Zwo FPS limpa o próprio estado
pelo `EntityRemoveEvent`.

## Comandos

| Comando | Permissão | Descrição |
|---|---|---|
| `/fps` | `zwofps.use` (todos) | Abre o menu |
| `/fps <leve\|medio\|pvp\|auto\|off>` | `zwofps.use` | Troca o modo |
| `/fps status` | `zwofps.use` | Perfil aplicado, chunks enviados, entidades escondidas por categoria |
| `/fps dicas` | `zwofps.use` | Dicas pelo client e pelas configurações do jogador |
| `/fps area` | `zwofps.use` | Entidades num raio de 64 blocos e quantas estão escondidas pra você |
| `/fps chuva` | `zwofps.use` | Mostra/esconde a chuva |
| `/zwofps stats` | `zwofps.admin` | Modos em uso, total escondido, custo médio do scan, MSPT |
| `/zwofps check <jogador>` | `zwofps.admin` | Status e client do jogador |
| `/zwofps set <jogador> <modo>` | `zwofps.admin` | Troca o modo de alguém |
| `/zwofps hotspots [mundo] [qtd]` | `zwofps.admin` | Chunks mais pesados |
| `/zwofps reload` | `zwofps.admin` | Recarrega a config |

## PlaceholderAPI

`%zwofps_mode%`, `%zwofps_profile%`, `%zwofps_hidden%`, `%zwofps_send_distance%`

## Configuração

Tudo em `config.yml`: categorias (quais `EntityType` entram em cada grupo), perfis (send distance,
clima e regras por categoria), regras do modo automático, proteções, limites de desempenho do scan
e mensagens em MiniMessage.

O scan é distribuído entre ticks (`max-players-per-tick`, `max-millis-per-tick`) e o intervalo
dobra sozinho quando o MSPT passa de `slow-down-if-mspt-above`.

## Build

```
./mvnw package
```

O jar sai em `target/ZwoFPS-<versão>.jar`. Requer Java 21 e Paper 1.21.11.
