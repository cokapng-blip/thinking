# TLGD Assault Regions - Quick Start

## 🚀 Build & Install (3 passos)

```bash
# 1. Build do mod
./gradlew build

# 2. O JAR estará em:
# build/libs/TLGD-Assault-Regions-1.0.0.jar

# 3. Copie para a pasta mods do servidor E dos clientes
cp build/libs/TLGD-Assault-Regions-1.0.0.jar /path/to/server/mods/
cp build/libs/TLGD-Assault-Regions-1.0.0.jar /path/to/client/mods/
```

## ⚙️ Configuração Inicial (Servidor)

1. **Inicie o servidor** uma vez para gerar configs
2. **Edite** `serverconfig/tlgdassault.toml`:
   - Ajuste `required_rank` para seu sistema (general/coleader/leader)
   - Configure `allowed_classes` para seus veículos
   - Defina `resource_items` para recompensas
3. **Reinicie** o servidor

## 🗺️ Criar Regiões (In-Game, OP 2+)

```bash
# Exemplo: South Atlantic (naval)
/assault create south_atlantic "South Atlantic" -5000 60 -5000 5000 200 5000 minecraft:overworld

# Exemplo: Sahara (terrestre)
/assault create sahara "Sahara Desert" -10000 60 -10000 10000 120 10000 minecraft:overworld

# Adicionar janelas personalizadas
/assault setwindow south_atlantic SATURDAY 20:00 30
/assault setwindow south_atlantic SUNDAY 15:00 30
```

## 🔗 **CRÍTICO: Integração com seu Sistema TLGD**

**EDITE** `src/main/kotlin/com/tlgd/assault/nation/NationIntegration.kt`:

```kotlin
// Linha ~30 - init()
fun init() {
    // SUA API TLGD AQUI
    TLGDAPI.nations.onNationCreate { nation -> cacheNation(nation) }
    TLGDAPI.nations.onPlayerJoinNation { player, nation -> updatePlayerCache(player, nation) }
    TLGDAPI.nations.onPlayerLeaveNation { player -> clearPlayerCache(player) }
    TLGDAPI.nations.onRankChange { player, newRank -> updatePlayerCache(player) }
}

// Linha ~50 - fetchPlayerNation()
private fun fetchPlayerNation(player: ServerPlayer): NationData? {
    return TLGDAPI.nations.getPlayerNation(player.uuid)?.let { convertToNationData(it) }
}

// Linha ~90 - getVehicleClass() para seus mods de veículos
fun getVehicleClass(entity: Entity): String? {
    // Movecraft:
    return MovecraftAPI.getCraft(entity)?.className?.lowercase()
    // Ou Archipelago:
    // return ArchipelagoAPI.getVehicle(entity)?.getClassName()
}
```

**Recompile após editar:** `./gradlew build`

## 🎮 Como Jogar

| Ação | Comando/Tecla |
|------|---------------|
| Abrir mapa de regiões | **Tecla M** |
| Ver info de região | Clique na região no mapa → "Ver Info Completa" |
| Iniciar assalto | General+ na nação: `/assault begin <id>` OU botão no mapa |
| Ver placar | `/assault leaderboard <id>` OU aba no mapa |
| Ver recompensas | `/assault rewards <id>` |

## 📋 Checklist Pós-Instalação

- [ ] Mod instalado no servidor e clientes
- [ ] `serverconfig/tlgdassault.toml` configurado
- [ ] `NationIntegration.kt` editado para sua API TLGD
- [ ] `getVehicleClass()` editado para seus mods de veículos
- [ ] Regiões criadas com `/assault create`
- [ ] Janelas de ataque configuradas com `/assault setwindow`
- [ ] Testado: General inicia assalto → HUD aparece → Timer conta → Vencedor ganha recompensas

## 🐛 Debug

```bash
# Ver logs
tail -f logs/latest.log | grep TLGD

# Comando debug in-game
/assault admin debug

# Habilitar debug mode no config
debugMode = true
```

## 📁 Estrutura do Projeto

```
TLGD-Assault-Regions/
├── build.gradle.kts          # Build config
├── gradle.properties         # Versões
├── settings.gradle.kts       # Projeto
├── src/main/
│   ├── kotlin/com/tlgd/assault/
│   │   ├── TLGAssaultMod.kt          # Entry point
│   │   ├── config/TLGDAssaultConfig.kt  # Configuração
│   │   ├── region/
│   │   │   ├── AssaultRegion.kt      # Data classes
│   │   │   └── AssaultRegionManager.kt # Core logic
│   │   ├── nation/NationIntegration.kt # SUA INTEGRAÇÃO AQUI
│   │   ├── command/TLGDAssaultCommands.kt # Comandos
│   │   ├── network/NetworkHandler.kt   # Packets
│   │   ├── event/ServerEvents.kt       # Event listeners
│   │   └── client/                     # Client side
│   │       ├── AssaultHUD.kt           # HUD overlay
│   │       ├── gui/AssaultMapScreen.kt # Mapa (tecla M)
│   │       ├── gui/AssaultRegionInfoScreen.kt
│   │       ├── ClientEvents.kt         # Key bindings
│   │       └── TLGDAssaultClient.kt    # Client setup
│   └── resources/
│       ├── META-INF/neoforge.mods.toml
│       ├── assets/tlgdassault/lang/    # Traduções
│       ├── data/tlgdassault/regions/   # Regiões exemplo
│       └── pack.mcmeta
└── README.md
```

## 🆘 Problemas Comuns

| Problema | Solução |
|----------|---------|
| "Região não encontrada" | Verifique ID exato: `/assault list` |
| "Não é horário de janela" | Use `/assault info <id>` para ver próximas janelas |
| "Cargo insuficiente" | Edite `required_rank` no config ou dê cargo no jogo |
| Veículos não pontuam | Edite `getVehicleClass()` em NationIntegration.kt |
| Recompensas não pagam | Verifique `ownerNationId` na região (`/assault info`) |
| HUD não aparece | Tecla M abre mapa, HUD só mostra durante assalto ativo |

---

**Pronto para guerra!** ⚔️🌍