# Eldritch Exchange

NeoForge mod for Minecraft 1.21.1. Burn offerings on a soul campfire for **essence**,
then spend essence in the **Codex of the Void** to withdraw items. Item values are
derived from the server's actual recipes, so modded items work automatically.

## Dependencies

- Minecraft 1.21.1
- NeoForge 21.1.262
- Java 21

No other mods are required.

## Quick start

1. Install NeoForge 21.1.262 for Minecraft 1.21.1.
2. Put `eldritch-1.3.0.jar` in both:
   - `<server>/mods/`
   - every player's `.minecraft/mods/`
3. Start the server and clients.
4. Place a soul campfire on a redstone block. Drop items on it to earn essence.
5. Right-click the Codex of the Void to open the UI and withdraw items.

## How it works

- **Burner:** place a soul campfire on a redstone block. Dropped items are scanned,
  valued, consumed, and credited as essence. New items are learned by the Codex.
- **Codex:** learned items are listed with their cost, 54 per page. Click an item to
  withdraw one for its essence cost.
- **Values:** seeds are fixed (dirt/cobblestone 1, logs/redstone 4, coal 16, copper
  32, iron/gold 64, diamond 256, nether star 1024, ...). Everything else is priced
  from the cheapest recipe cost per unit.
- **Madness:** rises with burns and withdrawals and decays over time. Higher madness
  brings whispers, hallucinations, and other effects.
- **Withdrawal risk:** high-cost withdrawals can trigger lightning, blindness,
  vexes, or a crumbled item.

## Build and test

```bash
./gradlew build              # compile + tests + jar -> build/libs/eldritch-1.3.0.jar
./gradlew test               # 30 unit tests
./gradlew runGameTestServer  # 8 in-game tests
```

Requires Java 21 and the committed Gradle wrapper. The first build downloads
NeoForge/Minecraft and takes a few minutes.
