# Eldritch Exchange

NeoForge 1.21.1 mod: burn offerings on a soul campfire for essence, then withdraw items from a custom Codex UI.

## Build

```
./gradlew build
```

Output: `build/libs/eldritch-1.0.0.jar`

## Tests

```
./gradlew test              # 23 unit tests, pure logic (values, madness, risk, economy)
./gradlew runGameTestServer # 4 in-game tests, real recipes + real burner
```

The in-game tests confirm:

- the codex item registers as `eldritch:codex`
- values are derived from the server's actual recipes
  (`iron_ingot` 64, `iron_block` 576, `diamond_sword` 513, `netherite_ingot` 2304, `stick` 1)
- dropping 5 dirt on a bound burner awards 5 essence, learns dirt, and consumes the item
- an unbound campfire consumes nothing

## Install

The Codex screen is client-side, so the jar must go in **both** places:

- server: `<server>/mods/`
- every player: `.minecraft/mods/` (NeoForge 21.1.262)

Then restart the server and the clients.

**Important:** remove or rename the old KubeJS script before starting, or burners will be
handled twice:

```
<server>/kubejs/server_scripts/eldritch.js
```

The mod replaces it completely (values, ledger, burners, madness, Codex). Existing KubeJS
ledger data is not migrated; players start at 0 essence and an empty Codex.

## How it works

**Burner.** Place a soul campfire on a redstone block. The placing player binds it. Dropped
items are scanned every 5 ticks, valued, consumed, and credited as essence; new items are
learned by the Codex. Breaking the campfire does not unbind the burner; the binding is kept
but does nothing until a valid campfire-on-redstone stands at that position again.

**Codex.** Right-click the Codex of the Void to open the UI. Learned items are listed with
their value, 54 per page. Click an item to withdraw one for its essence cost. Essence and
madness are shown in the header, with page buttons at the bottom.

**Values.** Seeds are fixed (dirt/cobblestone 1, logs/redstone 4, coal 16, copper 32,
iron/gold 64, diamond 256, nether star 1024, ...). Everything else is the cheapest recipe
cost per unit, recomputed from the server recipe manager. Vanilla plus all modded recipes
are included automatically.

**Madness.** Rises with burns and withdrawals, decays once every 10 seconds.
25+ nausea and whispers, 50+ blindness, obfuscated UI, hallucination creepers that vanish
when hit, 75+ darkness.

**Withdrawal risk.** 256-1023 essence: 5%, 1024-4095: 12%, 4096+: 25%. Outcomes: lightning,
blindness/nausea, a brief vex swarm, or the item crumbling to nothing.

## Changing the book colors

`tools/recolor_book.py` remaps the vanilla book texture into a violet gradient. Edit
`TINT_DARK` / `TINT_LIGHT` and re-run:

```
python3 tools/recolor_book.py
```

`book_original.png` is the untouched vanilla texture. The item model is
`assets/eldritch/models/item/codex.json`.

## Known gaps

- The Codex screen compiles and its menu logic is tested, but the rendered layout has not
  been opened in a real client yet. Try it in singleplayer first.
- The value cache refreshes when the recipe manager changes (reload/datapack change) and
  every second otherwise.
