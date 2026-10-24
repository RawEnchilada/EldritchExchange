# AGENTS.md — Eldritch Exchange

Context for anyone (human or agent) continuing this mod. The mod is the complete
Eldritch Exchange system: item values, essence ledger, burners, madness, and the
Codex UI. There is no scripting layer involved — all behavior is in this project.

## What it is

NeoForge 1.21.1 mod. Players burn items on a soul campfire placed on a redstone
block to earn **essence**, then spend essence in the **Codex of the Void** UI to
withdraw items. Item values are derived from the server's actual recipes, so
modded items work automatically.

- Mod id: `eldritch`
- Group: `dev.eldritch.exchange`
- Version: `1.2.1`
- Java package root: `dev.eldritch.exchange`

## Toolchain

- Minecraft 1.21.1, NeoForge **21.1.262** (matches the live server)
- Java 21 (`java.toolchain.languageVersion = 21`)
- ModDevGradle `2.0.148`, Gradle wrapper `9.2.1`
- JUnit Jupiter 5.10.2 for unit tests
- Gradle/Java are installed system-wide; the wrapper is committed
- Live server: `/storage/Games/Minecraft Server/winter` (mod jar goes in `mods/`)

## Commands

```bash
./gradlew build              # compile + unit tests + jar -> build/libs/eldritch-1.3.0.jar
./gradlew test               # 30 unit tests only
./gradlew runGameTestServer  # 6 in-game tests, exits when done
./gradlew runServer          # dev dedicated server in runs/server
./gradlew runClient          # dev client (needs a display)
```

- `runServer` needs `runs/server/eula.txt` with `eula=true`; `runs/server/server.properties`
  has `online-mode=false` for local testing.
- `runGameTestServer` uses `run/` and needs no setup.
- The first build downloads NeoForge/Minecraft and takes a few minutes; later builds
  are under a second.

## Layout

```
src/main/java/dev/eldritch/exchange/
  Eldritch.java            @Mod entry point; registers items and menus
  ModItems.java            DeferredRegister.Items; eldritch:codex
  ModMenus.java            DeferredRegister<MenuType<?>>; eldritch:codex
  Events.java              game-bus handlers: server tick, block place, incoming damage
  Ledger.java              SavedData wrapper; NBT load/save for LedgerData
  ValueTable.java          reads server recipes, merges default values, builds item-id -> value table
  ValueConfig.java         config/eldritch/values.json load/save; manual overrides + last auto table
  BurnerService.java       burner binding, item scanning, particles, worth display
  MadnessService.java      madness scheduling, tricks, whispers, hallucinations
  ChatMemory.java          ring buffer of recent player chat for mad replays
  RiskEffects.java         applies withdrawal risk outcomes
  CodexItem.java           right-click opens CodexMenu
  CodexMenu.java           server menu: learned-item grid, data slots, withdraw clicks
  CodexScreen.java         client UI: frame, header, bars, page buttons
  ClientEvents.java        RegisterMenuScreensEvent -> CodexScreen

  core/                    PURE Java, no Minecraft imports, unit tested
    ValueCalc.java         recipe relaxation + usage inference algorithm
    LedgerData.java        accounts, learned items, burner bindings
    Madness.java           thresholds, gains, decay
    Risk.java              chance tiers and outcome roll
    Economy.java           burn/withdraw transactions

  gametest/                in-game tests (ship in the jar, harmless)
    EldritchGameTests.java codex registration + real recipe values
    BurnerGameTests.java   real burn, unbound campfire does nothing

src/test/java/dev/eldritch/exchange/core/   30 unit tests
tools/recolor_book.py     recolors vanilla book texture
tools/make_empty_structure.py  writes the empty game test structure NBT
```

Keep `core/` free of Minecraft types. That is what makes the unit tests fast and
is the main reason the logic can be validated without launching a server.

## Runtime flow

**Boot.** `Eldritch` registers `ModItems.ITEMS` and `ModMenus.MENUS`. `Events` and
`ClientEvents` auto-register through `@EventBusSubscriber`.

**Server tick** (`Events.onServerTick`, every tick):
- every 20 ticks: `ValueTable.refresh(server)`
- `BurnerService.tick(server)`
- `MadnessService.tick(server)`

**Burner.** Place a soul campfire on a redstone block:
- `BlockEvent.EntityPlaceEvent` checks block is soul campfire, placer is a
  `ServerPlayer`, dimension is overworld, and block below is a redstone block.
- Refuses if the player already has a burner that still stands.
- Binds `player UUID -> Burner(x,y,z)` in the ledger, messages the player, and
  gives one Codex item.
- `BurnerService.burnScan` runs every 5 ticks: for each bound burner that still
  stands, scans an AABB above the campfire for `ItemEntity`, values the item,
  discards it, credits `value * count` essence, learns the item, and sends
  feedback. Zero-value items are left alone.
- Burn feedback is batched per player and sent at most once per 100 ticks (5 s):
  one line with offerings eaten, essence gained, and any newly learned items.
  Automation can therefore not flood chat.
- Ambience: soul-fire ring every 4 ticks, soul particles every 20 ticks.
- Worth label: every 5 ticks, if a player looks (6-block pick) at a bound burner
  while holding an item, a `Display.TextDisplay` above the campfire shows
  `Worth: N`. Displays not seen for 10 ticks are discarded.
- Breaking the campfire does **not** unbind and does **not** refund.

**Withdrawal.** `CodexItem.use` opens `CodexMenu` server-side.
- Menu keeps a 54-slot `SimpleContainer` grid of learned items as icons and four
  `SimpleContainerData` slots: essence, madness, page, page count.
- Grid slots are display-only (`mayPickup`/`mayPlace` false). `CodexMenu.clicked`
  intercepts clicks on grid slots and calls `withdraw`; everything else defers to
  `super`.
- `withdraw` looks up the item for `page * 54 + slot`, takes the cost from the
  ledger via `Economy.withdraw`, rolls `Risk`, applies `RiskEffects`, and gives the
  item if the outcome allows it.
- Page buttons are plain `Button`s on the screen; they call
  `handleInventoryButtonClick`, which reaches `clickMenuButton` on the server.
  Button ids: 0 = previous, 1 = next.
- The screen reads state only through menu getters (`essence()`, `madness()`,
  `page()`, `pages()`). No custom packets are used anywhere.

## Gameplay constants

**Default values** hardcoded in `ValueTable.DEFAULT_VALUES`. This is more than a
seed list: anything without a recipe needs an entry here or it could never be
burned. The tiers below are the original anchors; the list also covers drops,
loot and structure items (heads, elytra, totems, tridents, horse armor, chainmail,
music discs, smithing templates, pottery sherds, amethyst buds, sculk, ...).

| value | items |
|------|-------|
| 1 | dirt, coarse dirt, rooted dirt, mud, cobblestone, stone, andesite, diorite, granite, deepslate, tuff, sand, gravel, netherrack, end stone, clay ball, cobbled deepslate, rotten flesh, string, flint, bone, feather, leather, rabbit hide, seeds, snowball, egg, plants |
| 2 | bamboo, sugar cane, melon slice |
| 4 | `#minecraft:logs`, redstone, gunpowder, spider eye, soul sand/soil, calcite, cocoa beans, crops, berries, podzol, mycelium, spore blossom |
| 8 | slime ball, phantom membrane, prismarine shard, amethyst shard, ink sac, nether wart, rabbit's foot, armadillo scute, amethyst buds, sculk vein, apple, chorus fruit, wheat, raw meat/fish, pointed dripstone |
| 16 | coal, lapis lazuli, quartz, ender pearl, glowstone dust, glow ink sac, prismarine crystals, honeycomb, honey bottle, crying obsidian, large amethyst bud, sculk |
| 32 | raw copper, blaze rod, breeze rod, copper ore, obsidian, amethyst cluster, disc fragment, poisonous potato, `#minecraft:decorated_pot_sherds` |
| 64 | raw iron, raw gold, iron ore, gold ore, ghast tear, nautilus shell, turtle scute, name tag, experience bottle, glowstone, sculk catalyst/shrieker, suspicious stew, sniffer seeds |
| 128 | shulker shell, saddle, skeleton/zombie/creeper/piglin head, goat horn, sculk sensors, wither rose, trial key, `#minecraft:trim_templates`, all music discs |
| 256 | diamond, emerald, echo shard, heart of the sea, sponge, wet sponge, enchanted book, ominous bottle/key, chainmail armor, chorus flower |
| 512 | netherite scrap, ancient debris, dragon breath, totem of undying, trident, enchanted golden apple, heavy core, budding amethyst, sniffer egg, dragon head, iron/golden horse armor, netherite upgrade template |
| 768 | wither skeleton skull |
| 1024 | nether star, elytra, diamond horse armor |
| 4096 | dragon egg |

Crafted items are deliberately absent so the recipe pass values them (iron ingot
64, iron block 576, netherite ingot 2304, ...). Tags in the table (`#...`) are
resolved through `BuiltInRegistries.ITEM.getTagOrEmpty`; explicit ids win over
tag entries.

**Value algorithm** (`ValueCalc.compute`):
- Start from the default values, relax through recipes up to 500 passes.
- Each recipe output value = sum of the cheapest known ingredient option per slot,
  divided by output count.
- Defaults are never overwritten; only strictly cheaper values replace existing ones.
- Ingredient slots with no known value make the recipe unusable.
- Final values are `ceil` and floored at 1, so no recipe can create value.
- Cycles are safe because values only decrease.
- **Usage inference**: items consumed by a recipe but produced by none (wither
  skulls have no recipe; modded drops often have none either) are estimated from
  the recipes that use them:
  - if the output is valued, the leftover `output * count - other ingredients` is
    split across the unknown slots;
  - if the output is unvalued too, the most valuable known ingredient stands in
    for the prediction;
  - the median over all recipes that use the item wins, and known values are never
    overwritten. Inference and relaxation alternate for at most 8 rounds, so a
    valued unknown can unlock the recipes that consume it.
  - inferred values are tracked separately and may fill unknown items, but they
    may **not** lower an already known value. Without that guard a guessed ore
    value can chain into a cheaper crushed ore and multiply one ore into seven
    ingots' worth of value.
- **Chance outputs**: recipes exposing `getRollableResults()` (Create crushing,
  milling, ...) are skipped when the first output's chance is below 1. A 40%
  byproduct must not be treated as guaranteed, or a cheap stone becomes free metal.
  The method is read reflectively so the mod still builds without Create.

**Value adapter** (`ValueTable`):
- Rebuilds when the `RecipeManager` instance or its recipe count changes, checked
  every 20 ticks.
- Uses `recipe.getResultItem(registryAccess)` and `recipe.getIngredients()`.
- Filters empty ingredients (shaped recipes include empty slots).
- Smithing recipes return no ingredients from `getIngredients()`, so
  `SmithingTransformRecipe`s are recovered by testing every registered item
  against `isTemplateIngredient` / `isBaseIngredient` / `isAdditionIngredient`.
  Without this netherite gear has no recipe value.
- Any registered item still without a value after the passes gets a rarity
  fallback (common 1, uncommon 8, rare 32, epic 128), so nothing is silently
  unburnable. Mod items are not exempt except `eldritch:*` itself.
- Sanity numbers with current data: iron ingot 64, iron block 576, stick 1,
  diamond sword 513, netherite ingot 2304, netherite axe ~3585,
  wither skeleton skull 768, elytra 1024.
- On the live modpack, Create's ore processing legitimately makes copper cheap
  (copper ingot ~6.4 before ceil), so `irons_artifice:bullet` is 3 from the
  cheaper copper recipe. The iron recipe alone would be 5; use `values.json` to
  pin it there if desired.

**Editable values** (`ValueConfig`, `config/eldritch/values.json`):
- Written after every rebuild with two maps: `values` (what the Codex uses) and
  `auto` (the table the algorithm produced when the file was written).
- On load, an entry in `values` that differs from `auto` is a manual override.
  Untouched entries keep following recipe changes. Deleting an entry restores the
  automatic cost; `0` makes the item unburnable.
- Overrides are applied as seeds and the table is recomputed, so dependent items
  (e.g. a bullet box made from bullets) reprice too.
- `ValueTable.refresh` compares the file's modification time, so edits are picked
  up within 20 ticks even while the server is running.
- The folder is created automatically. The file is not shipped; it appears on the
  first rebuild.

**Madness** (`core/Madness`, `MadnessService`, `ChatMemory`):
- Range 0–100. Gain: `1 + essence/256` per burn, `1 + cost/64` per withdraw.
- Decays by 1 per pulse (every 200 ticks = 10 s) for online players only.
- Tiers: 0 calm, 25 whisper, 50 cryptic, 75 deep.
- Effects are scheduled per player with random gaps (whisper 20–50 s, cryptic
  16–40 s, deep 10–30 s); the last 4 trick types are excluded, so nothing
  repeats back-to-back. There is no fixed per-pulse effect any more.
- Weighted trick pool, unlocked by tier:
  - whisper+: whisper line, named whisper (uses an online player's name), fake
    player chat (replays `ChatMemory` history, else fabricates a line), random
    mob sound behind the player, approaching footsteps, nearby chest/door noise,
    animals panic and bolt away.
  - cryptic+: hallucination (creeper/enderman/spider/witch, faces the player,
    silent, no AI, invulnerable, 180-tick life), blindness flicker, forced
    turn-around, burner call (soul sound, particles, forces the camera toward
    the burner), involuntary shove.
  - deep: darkness+slowness, anger nearby monsters/neutral mobs, drop the held
    item (never in lava/fire), fake join/leave messages using real player names.
- Deep madness within 6 blocks of your own standing burner: blindness, darkness
  and nausea every 4 s. This is the "heavy dread" effect.
- `LivingIncomingDamageEvent` cancels damage to hallucinations, plays an
  explosion, emits particles, and discards them.
- `ServerChatEvent` records chat into `ChatMemory` (80 lines, in-memory only);
  `ServerStoppedEvent` clears chat memory and per-player madness schedules.

**Risk** (`core/Risk`, `RiskEffects`):
- Chance: <256 = 0%, 256–1023 = 5%, 1024–4095 = 12%, 4096+ = 25%.
- Outcomes split evenly by a second roll: lightning, darkness+nausea, two vexes
  with 30 s limited life, or the item crumbles (cost still spent, nothing given).

## Persistence

`Ledger extends SavedData`, key `eldritch_ledger`, stored by the overworld data
storage (`world/data/eldritch_ledger.dat`). NBT schema:

```
learned:  list of strings (item ids, insertion order)
accounts: list of { uuid, essence(long), madness(int), lastGainTick, lastFedTick,
          lastWarningTick, starvationTier, demand*, sealed, fulfilledDemands,
          nameRevealed, tithe* }
burners:  list of { uuid, x, y, z }
```

- `Ledger.get(server)` is the only accessor.
- Call `ledger.setDirty()` after any mutation from outside `SavedData`.
- No migration layer exists; changing the schema requires handling old tags in
  `Ledger.load`.
- Burners are overworld coordinates only (`BurnerService` always uses
  `server.overworld()`).

## Client/server split

The jar must be installed on **both** the server and every client: the item and
menu are common code, the screen is client-only (`Dist.CLIENT`). There is no
fallback UI for vanilla clients.

## API notes for 1.21.1 / NeoForge 21.1.262

These cost time to discover; don't re-learn them:

- `SimpleContainerData` is `net.minecraft.world.inventory.SimpleContainerData`.
- Player inventory is `net.minecraft.world.entity.player.Inventory`.
- Nausea is `MobEffects.CONFUSION` in 1.21.1 (there is no `NAUSEA`).
- Component codec is `ComponentSerialization.CODEC`, not `Component.CODEC`.
- `Display.TextDisplay#setText` is **private**. 1.21.1 reads the `text` save tag as
  a **JSON string** (it calls `Component.Serializer.fromJson`), so storing an NBT
  component there is silently ignored. Do:
  `tag.putString("text", Component.Serializer.toJson(text, registries))`, set
  `billboard` to `"center"`, then `display.load(tag)`. Set the text **before**
  `addFreshEntity`, because the spawn packet is what syncs entity data to clients.
  See `BurnerService.setText`.
- `Rarity` is a NeoForge **extensible enum** (`IExtensibleEnum`): mods add
  constants such as custom rarities. Never write an exhaustive `switch` over it
  (or any `@IndexedEnum`) — it throws `MatchException` only on modded packs, not
  in the workspace test server. Compare with `==` and keep a fallback branch.
- `ValueTable.refresh` catches `RuntimeException` from the rebuild and keeps the
  previous table, so one odd modded item cannot crash the server tick.
- `@EventBusSubscriber(bus = ...)` is deprecated but still works; it produces two
  harmless removal warnings. `IModBusEvent` still routes correctly without it.
- A game test needs a structure template. Use `@GameTest(template = "empty")` plus
  `@PrefixGameTestTemplate(false)` on the class, with the NBT at
  `src/main/resources/data/eldritch/structure/empty.nbt` (note: singular
  `structure` in 1.21). Regenerate with `python3 tools/make_empty_structure.py`.
  The template is 3x3x3 air.
- Vanilla assets for copying textures are in
  `build/moddev/artifacts/neoforge-21.1.262-client-extra-aka-minecraft-resources.jar`.
- `Entity.load(CompoundTag)` is public, which is what makes the text display trick
  work.

## Testing

- Unit tests (`./gradlew test`): 30 tests over `ValueCalc`, `Madness`, `Risk`,
  `Economy`, and `LedgerData` behavior. No Minecraft classes involved.
- Game tests (`./gradlew runGameTestServer`): 8 tests, run against real recipes.
  - codex registers as `eldritch:codex`
  - real recipe values (iron ingot/block, stick, diamond sword, netherite ingot)
  - default values for uncraftable items (wither skull, elytra, totem, trident,
    enchanted golden apple, name tag, turtle scute, netherite gear via smithing)
  - every registered non-mod item has a value >= 1
  - 5 dirt on a bound burner gives 5 essence, learns dirt, consumes the entity
  - an unbound campfire consumes nothing
- The burner test measures the essence *delta* and unbinds its burner, because
  `run/world/data/eldritch_ledger.dat` persists between game test runs.
- When adding a game test: annotate `@GameTest(template = "empty")`, remember the
  template is only 3x3x3 (relative positions 0–2), and clean up world state you
  place (the burner test leaves blocks behind but uses unique relative positions).
- `BurnerService.burnOnce(server)` exists specifically so tests can trigger a scan
  without waiting for tick alignment.

## Assets

- `assets/eldritch/textures/item/codex.png` — the live item texture. **The user
  recolored this by hand; do not overwrite it** unless they ask.
- `book_original.png` — untouched vanilla book, kept as a reference input.
- `tools/recolor_book.py` — regenerates `codex.png` from `book_original.png`
  using `TINT_DARK` / `TINT_LIGHT`. Running it replaces the hand-recolored
  texture, so only run it on request.
- `codex.png~` is an editor backup left in the resources folder; it is unused but
  currently gets packaged into the jar. Safe to delete.
- Model: `assets/eldritch/models/item/codex.json` (parent `item/generated`).
- Names: `assets/eldritch/lang/en_us.json` — `item.eldritch.codex` and
  `container.eldritch.codex`, both "Codex of the Void".

## Known gaps / likely next work

- The Codex screen compiles and its menu logic is wired, but it has **not been
  visually opened in a real client** yet. First thing to verify in-game: frame and
  slot alignment, page buttons, tooltip text, obfuscated title at 50+ madness.
- Item costs are editable in `config/eldritch/values.json`, but seeds, madness
  tuning, risk chances, and burner radius are still constants in code.
- The value cache keys on recipe manager identity + recipe count. A datapack
  change that replaces recipes but keeps the exact same count would not refresh
  until the manager is replaced.
- Withdrawal gives exactly one item per click; no stack-size purchase.
- No server-side validation that the clicked slot still shows the same item if the
  page changes between click and handling (low risk, but possible).
- Usage-inferred values are estimates: a modded uncraftable item that only appears
  in recipes with no other valued ingredient falls back to its rarity value
  instead of a meaningful price.
- Only `en_us` localization.
- Game test classes are included in the release jar.
- Burner binding survives world edits; there is no feedback when a bound burner's
  campfire is broken other than the label/particles stopping.

## Conventions

- Pure rules go in `core/`; Minecraft glue goes in the root package.
- Mutating the ledger from a service requires `ledger.setDirty()`.
- Constants that define gameplay live next to the logic that uses them, not in a
  central config class yet.
- Keep new user-facing strings in `en_us.json` where practical; existing runtime
  messages are inline `Component.literal(...)`.
- Build and run both `test` and `runGameTestServer` before calling a change done.
