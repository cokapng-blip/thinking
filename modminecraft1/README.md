# TLGD Assault Regions

Sistema de **Assault Regions** estilo CCNet Nations para servidores **TLGD (Territory Land Grid Defense)** no Minecraft 1.21.1 NeoForge.

## 🎮 Features

- **Regiões Neutras Capturáveis** - Territórios permanentes no mapa com bounds personalizáveis
- **Janelas de Ataque Semanais** - 1-2 janelas por semana com horários configuráveis (padrão: Sáb 20:00, Dom 15:00)
- **Sistema de Pontuação** - Presença na área, kills, veículos destruídos, capturas de bandeira
- **Integração com Nações TLGD** - Requer cargo General/Co-líder/Líder para declarar assalto
- **Suporte a Veículos** - Integração com mods de veículos (Movecraft, Archipelago, etc.)
- **Recompensas Diárias Automáticas** - Dinheiro + recursos para nação dona
- **GUIs Intuitivas** - Mapa de regiões (tecla **M**), info detalhada, leaderboard
- **HUD em Tempo Real** - Timer, placar e stats durante assaltos ativos
- **Totalmente Configurável** - Via `serverconfig/tlgdassault.toml` ou Cloth Config

## 📦 Instalação

1. Coloque o `.jar` na pasta `mods` do servidor e clientes
2. Reinicie o servidor
3. Configure as regiões com `/assault create` (precisa OP level 2+)
4. Ajuste configurações em `serverconfig/tlgdassault.toml`

## 🎯 Comandos

| Comando | Descrição | Permissão |
|---------|-----------|-----------|
| `/assault create <id> <nome> <min> <max> <dimension>` | Criar região | OP 2+ |
| `/assault remove <id>` | Remover região | OP 2+ |
| `/assault list` | Listar todas regiões | OP 2+ |
| `/assault info <id>` | Info detalhada da região | OP 2+ |
| `/assault setwindow <id> <dia> <HH:mm> [duração]` | Adicionar janela de ataque | OP 2+ |
| `/assault begin <id>` | **Iniciar assalto** (jogador) | General+ na nação |
| `/assault status [id]` | Ver assaltos ativos | Todos |
| `/assault leaderboard <id>` | Ver placar da região | Todos |
| `/assault rewards <id>` | Ver recompensas da região | Todos |
| `/assault admin forceend <id>` | Forçar fim do assalto | OP 3+ |
| `/assault admin setowner <id> <nationId> <nationName>` | Definir dono manual | OP 3+ |
| `/assault admin reloadconfig` | Recarregar config | OP 3+ |

## 🗺️ GUIs

- **Tecla M** - Abre mapa de todas as regiões (filtros por dimensão, estado, busca)
- **Clique em região** - Painel lateral com detalhes e ações (TP, Info, Iniciar)
- **Botão "Ver Info Completa"** - Tela com abas: Geral, Assalto Ativo, Recompensas, Histórico

## ⚙️ Configuração (`serverconfig/tlgdassault.toml`)

```toml
[general]
enabled = true
debugMode = false

[attack_windows]
duration_minutes = 30
max_per_week = 2
cooldown_hours = 48
min_players = 3

[scoring]
presence_per_sec = 1.0
kill_points = 50
vehicle_destroy_points = 200
flag_capture_points = 100
max_score = 10000

[rewards]
daily_money = 3456
resource_rewards_enabled = true
resource_items = ["minecraft:diamond:5:10", "minecraft:netherite_ingot:2:5"]

[vehicles]
allowed_classes = ["destroyer", "cruiser", "dreadnought", "carrier", "tank", "apc", "ifv"]
max_per_nation = 5

[regions]
min_size = 10000
max_size = 500000
height_limit = 256

[nation_integration]
require_nation = true
required_rank = "general"

[gui]
show_hud = true
show_markers = true
announcement_sound = "minecraft:entity.ender_dragon.growl"
```

## 🏗️ Criando Regiões (Exemplo)

```bash
# Região naval no Atlântico Sul (Overworld)
/assault create south_atlantic "South Atlantic" -5000 60 -5000 5000 200 5000 minecraft:overworld

# Adicionar janelas personalizadas
/assault setwindow south_atlantic SATURDAY 20:00 30
/assault setwindow south_atlantic SUNDAY 15:00 30

# Região terrestre no Saara
/assault create sahara "Sahara Desert" -10000 60 -10000 10000 120 10000 minecraft:overworld
```

## 🔧 Integração com Sistema de Nações (TLGD)

**IMPORTANTE**: Edite `NationIntegration.kt` para conectar com seu sistema de nações:

```kotlin
// Em NationIntegration.kt - método init()
fun init() {
    // Hook no seu sistema TLGD
    TLGDAPI.nations.onNationCreate { nation -> cacheNation(nation) }
    TLGDAPI.nations.onPlayerJoinNation { player, nation -> updatePlayerCache(player, nation) }
    TLGDAPI.nations.onPlayerLeaveNation { player -> clearPlayerCache(player) }
    TLGDAPI.nations.onRankChange { player, newRank -> updatePlayerCache(player) }
}

private fun fetchPlayerNation(player: ServerPlayer): NationData? {
    return TLGDAPI.nations.getPlayerNation(player.uuid)?.let { convertToNationData(it) }
}
```

## 🚗 Integração com Mods de Veículos

Edite `NationIntegration.kt` para detectar veículos:

```kotlin
fun getVehicleClass(entity: Entity): String? {
    // Movecraft
    return MovecraftAPI.getCraft(entity)?.className?.lowercase()
    
    // Archipelago
    // return ArchipelagoAPI.getVehicle(entity)?.getClassName()
    
    // Custom
    // return entity.getTag("vehicle_class")?.getString()
}
```

## 🎨 Personalização

- **Ícone**: Coloque `icon.png` (64x64) em `src/main/resources/assets/tlgdassault/`
- **Traduções**: Adicione arquivos em `assets/tlgdassault/lang/`
- **Sons**: Use resource packs para sons customizados de anúncio

## 📋 Requisitos

- **Minecraft**: 1.21.1
- **NeoForge**: 21.1.0+
- **Java**: 21
- **Servidor**: Paper/Spigot/Purpur compatível com NeoForge (via Mohist/Arclight) ou servidor NeoForge dedicado

## 🛠️ Desenvolvimento

```bash
# Build
./gradlew build

# Run client
./gradlew runClient

# Run server
./gradlew runServer
```

## 📝 Licença

MIT - Livre para uso, modificação e distribuição.

---

**Desenvolvido para o sistema TLGD (Territory Land Grid Defense)**