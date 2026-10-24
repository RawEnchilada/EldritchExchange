# TODO — Eldritch Exchange 1.4: Thal'ggor, the Abyss, and the Hungry Codex

Plan of record for the next version. Work top to bottom; each phase must build and
keep the existing 30 unit tests + 6 game tests green before moving on.

**Do not deploy to the live server.** `/storage/Games/Minecraft Server/winter` keeps
`eldritch-1.2.0.jar` until the user says otherwise. Build locally into
`build/libs/` only.

---

## Locked design constants

| Rule | Value |
|------|-------|
| Madness decay | **−1 every 20 s** (one per 400 ticks) |
| Decay pause after a burn/withdrawal | **1 minute** (1200 ticks) |
| Burner starvation grace | 10 min without an offering |
| Starvation warnings | every 3 min after grace |
| Starvation madness | +1 per warning, +2 per extra warning tier |
| Tentacle harassment tier | madness **≥ 75** |
| Tentacle grasp tier | madness **≥ 90** |
| Demand unlock tier | madness **≥ 50** |
| Demand cooldowns | 20 min (calm) / 12 min / 6 min (deep) |
| Items sealed per demand | 3–6 |
| Madness on fulfilled demand | **−15**, clamped at 0 |
| Name reveal | after **3** fulfilled demands |
| Book thief cooldown | 30 min |
| Book auto-drop safety | 10 min |
| Grasp escape | attack the tentacle; no terrain edits |
| Tithe reclamation window | 5 min, offer equal/greater value at the burner |

**Sealed-set rule:** when a demand is issued, the sealed set contains **the demanded
item itself** plus 3–5 other learned items. The demanded item is sealed so the
player cannot withdraw it and hand it back, and cannot put an existing stack back
into the Codex. Sealing is enforced on withdrawal, not on inventory possession.

---

## Phase 0 — Foundations and data (no new visuals yet)

- [x] **0.1 Madness pacing** (`core/Madness.java`)
  - Change decay from 1 per 200 ticks to **1 per 400 ticks**.
  - Track `lastGainTick` per account; deny decay for **1200 ticks** after a burn or
    withdrawal. Add `Madness.canDecay(lastGainTick, now)`.
  - Keep gains unchanged (`1 + essence/256` burn, `1 + cost/64` withdrawal) unless
    a test proves they now overshoot.
  - Update the unit tests: decay after 400 ticks, no decay inside 1200 ticks,
    decay resumes exactly after the pause.

- [x] **0.2 Ledger schema v2** (`core/LedgerData.java`, `Ledger.java`)
  - Add per-account: `lastFedTick`, `lastWarningTick`, `starvationTier`,
    `lastGainTick`, `demand` (item id, count, issuedTick, deadlineTick, state),
    `sealed` (list of item ids), `fulfilledDemands`, `nameRevealed`, and `tithe`
    (pending stack NBT, takenTick, reclaimDeadlineTick).
  - Load old saves by defaulting every new field. Unknown/removed demand item ids
    must be dropped on load, not crash the table.
  - `setDirty()` on every mutation. Unit-test NBT round-trip + missing-field load.

- [x] **0.3 Thal'ggor identity registry**
  - `Thalggor.java` (root package): canonical display name, reveal check, and the
    message helper used by chat, Codex banner, and demand text. Before reveal the
    name is printed as `The Bound Thing` / `???`; after reveal, `Thal'ggor`.
  - All user-facing strings go into `lang/en_us.json`; no more ad-hoc literals for
    this feature.

- [x] **0.4 Demand engine** (`core/Demand.java` or `core/Demands.java`)
  - Pick a demand only when madness ≥ 50 and the player has ≥ 6 learned items.
  - Demanded item must be learned and currently unsealed. Count 1–3, scaled by
    tier; never demand an item the player is already sealed out of.
  - Build the sealed set: demanded item **plus** 3–5 other learned items.
  - Cooldown by tier; deadline = cooldown + grace. Pure logic, fully unit tested
    (candidate filtering, sealed-set size, cooldown gate, expiry).
  - Ledger helpers: `issueDemand`, `fulfilDemand`, `expireDemand`, `isSealed`.

- [x] **0.5 Starvation engine** (`core/Starvation.java`)
  - Track the last feeding per bound burner. 10 min grace, then a warning every
    3 min with escalating text and +1/+2 madness.
  - Reset on any burn. Pure function `evaluate(lastFed, now)` → (stage, madness
    delta), unit tested.
  - **The clock only advances while the player is online.** On logout, freeze
    `lastFedTick`/`lastWarningTick` against wall time and restore on login so a
    player never returns to a starvation burst or a lost burner.

- [x] **0.6 Wiring** (`Events.java`, `BurnerService.java`, `MadnessService.java`)
  - Server tick drives decay pause, starvation evaluation, demand expiry.
  - Burn/withdraw update `lastGainTick` and feed state.
  - Demand fulfilment grants progress; at 3, reveal the name.

**Exit:** `./gradlew test` green with new tests; no gameplay visuals changed yet.

---

## Phase 1 — Tentacles (entity, model, texture, grasp)

Design reference: `art/tentacle/tentacle_preview_v2.png`, source `art/tentacle/tentacle_v2.blend`.
Minecraft cannot load the GLB — rebuild as a cuboid entity model with a 64×64 texture.

- [x] **1.1 Entity** (`entity/TentacleEntity.java`)
  - `Monster` (or `PathfinderMob`) with no pathfinding, no AI goals, no drops, no XP.
  - Spawned only by `MadnessService`, never naturally. Despawn on a timer
    (~200–300 ticks) or when madness drops below 75.
  - Invulnerable to everything except player melee, which triggers release if grasping.
  - Silent (`getAmbientSound`/hurt/death null) and no collision push.

- [x] **1.2 Model + renderer + texture**
  - `client/TentacleModel.java` (root `ModelPart`, 6–9 tapered cuboid segments,
    slight idle sway), `client/TentacleRenderer.java`, `ClientEvents` registration.
  - `assets/eldritch/textures/entity/tentacle.png`, 64×64, sickly abyss palette.
  - Register `EntityType` + attributes in `Eldritch.java` (`DeferredRegister.Entities`).

- [x] **1.3 Light harassment (madness ≥ 75)**
  - Spawn 1–3 tentacles in a ring 3–6 blocks from the player, facing them.
  - Lash for 1–2 damage with a cooldown; never lethal on its own; plays a wet slap.
  - Excluded while the player is in a safe zone (bed spawn radius is not enough —
    use `player.isCreative()`/`isSpectator()` and a configurable-safe check).

- [x] **1.4 Grasp (madness ≥ 90)**
  - Tentacle roots the player: `setPos` lock / movement input zeroing, `BLOCKED`
    state, and suffocation damage while held (1 damage every 2 s after a 3 s grace).
  - Player breaks free by **attacking the tentacle** 3 times (or killing it);
    also release on death, dimension change, logout, and server stop.
  - Cleanup is idempotent and must never leave a player permanently rooted.

- [x] **1.5 Tests**
  - Pure tests for release conditions and damage cadence (extract to `core/Grasp.java`).
  - Game test: spawn a grasp tentacle on a fake player, advance ticks, assert
    release on hit and no lingering root.

---

## Phase 2 — Thal'ggor offerings: demands, seals, starvation feedback

- [x] **2.1 Burner demands**
  - When a demand is active, the burner label and chat show what is wanted:
    `Thal'ggor hungers for 2× Iron Ingot`.
  - Burning the demanded item counts toward the count; extra burn of anything
    else does **not** advance it.
  - On fulfilment: unseal, **reduce madness by 15**, message, increment
    `fulfilledDemands`, reset cooldown.
  - On expiry: keep the seal for one extra cooldown? **No** — expiry unseals and
    applies +2 madness with a scolding line (avoid permanent lockout).

- [x] **2.2 Sealed withdrawals** (`CodexMenu.java`, `CodexScreen.java`)
  - `withdraw` refuses sealed items with a clear message and no cost charged.
  - Grid icon for sealed items gets a dark overlay / lock glyph and a tooltip:
    `Sealed by Thal'ggor — offer what it demands`.
  - **The demanded item is in the sealed set**, so it cannot be withdrawn and
    re-burned as a shortcut; the player must already own a copy or obtain one
    outside the Codex.
  - Do not seal the last copy of a quest-critical item if the user asks; default
    is seals are unweighted.

- [x] **2.3 Starvation feedback**
  - Escalating messages at each warning; madness gain per constants table.
  - At the third tier, the burner's particles turn violent and the label reads
    `Thal'ggor is starving.`
  - Warning state is per-account and survives relog.

- [x] **2.4 Name reveal**
  - Before 3 fulfilled demands every reference is `The Bound Thing` / `???`.
  - On the third fulfilment, a one-time reveal sequence (sound + chat + Codex
    header) switches all future text to `Thal'ggor`.

---

## Phase 3 — Codex search & sort

- [x] **3.1 State + packet**
  - Add `SearchState` to the menu (query + sort mode) and a small serverbound
    payload `SetCodexFilterPayload` (registered in `Eldritch.java`).
  - Server filters/sorts the learned list and syncs the resulting 54-slot page via
    the existing container sync — no per-slot custom packets.

- [x] **3.2 Filter semantics (JEI-like)**
  - Plain text: case-insensitive match on the item's display name.
  - `@mod`: match the item id namespace, e.g. `@create`.
  - `#tag`: optional, match item tags if cheap to do; otherwise document as out of scope.
  - Empty query: everything, current behaviour.

- [x] **3.3 Sort modes**
  - Cost ascending, name A–Z, mod (namespace) then name.
  - Sort applies to the filtered list; page count recomputes; page clamps to 0
    when the result set shrinks.

- [x] **3.4 UI** (`CodexScreen.java`)
  - Search box at the top of the frame (EditBox), focus handling, Esc clears.
  - Sort button cycles modes with a label/tooltip.
  - Keep slot alignment: no overlap with header, bars, or page buttons.
  - Debounce keystrokes (~150 ms) before sending.

---

## Phase 4 — ABYSS effects (in addition to the existing trick pool)

Each effect needs a per-player cooldown, a darkness/abyss palette, and full cleanup.

- [x] **4.1 The Gaze**
  - A ring of pale eyes opens in the dark around the player; camera is forced to
    look at the nearest pair for ~2 s. Silent, no damage, heavy vignette/darkness.
  - Implement with particles + a short forced-look, not a new entity if avoidable.

- [x] **4.2 Tithe of the Abyss**
  - Thal'ggor takes an offering from the player's inventory. Hotbar and offhand
    slots **are** fair game, but the effect is deliberately gentle:
    - Never the Codex, never a sealed item, never the stack in the currently
      selected hotbar slot while it is in use.
    - Never the last food item, a tool/armor piece, or a stack the player is
      actively holding/using (check `getUseItem`, cooldowns, and equipped slot).
    - It announces itself first: a whisper names the item and slot, then there is
      a **3-second** dread build-up (darkness pulse) before the stack is taken, so
      an attentive player can move it.
  - Taken stack is **not destroyed**: it is held as a per-account "tithe debt" for
    **5 minutes**. The player can reclaim it by burning any offering of equal or
    greater value at their bound burner (the burner label says
    `A debt is owed — offer its worth`). Reclaim closes the debt and returns the
    exact stack.
  - If the window lapses, the item is gone for good and a fraction of its value is
    credited as essence, with one final whisper. Never credit essence twice.
  - Debt state is persisted and cleared on death/logout only after the timer, so a
    crash cannot silently eat the item.
  - Pure `core/Tithe.java` for slot eligibility, warning timing, reclaim value
    check, and expiry; unit tested, including "never twice for the same stack".

- [x] **4.5 ABYSS test pass**
  - Pure tests for effect cooldowns, tithe reclaim, and book-thief timeout.
  - Verify every effect releases its player state on death, logout, dimension
    change, and server stop.

- [x] **4.3 Hungering Dark**
  - Local light drain / blindness bubble around the player for ~6 s; hostile
    spawns are **not** forced (server-friendly). Ends with a single deep rumble.
  - Cancel early if the player enters a lit, enclosed base (light ≥ 8 nearby).

- [x] **4.4 Book thief (very rare)**
  - Trigger from the ABYSS pool with a low weight **and** a 30 min per-player
    cooldown. Never while another theft is active.
  - A silent, invisible bat takes the Codex; the Codex item is rendered floating
    just ahead of the bat so the player can track it.
  - Kill the bat → Codex drops at its position. 10 min timeout → Codex drops at
    the bat's position and the bat despawns.
  - The Codex is **never deleted**: logout, death, chunk unload, server stop, or
    entity removal all route to "drop the item".
  - Only one Codex per player; re-opening the menu while stolen is impossible.

---

## Phase 5 — Polish, balance, docs, release

- [x] **5.1 Balance pass**
  - Simulate 30/60/120 min sessions: time to reach 50/75/90, demands per hour,
    starvation frequency, theft frequency. Tune weights in one place.
  - Confirm no effect can soft-lock a player (seals expire, theft drops the book,
    grasp always releasable).

- [x] **5.2 Docs**
  - Update `AGENTS.md`: new entity, packets, ledger schema, constants, test count.
  - Update `README.md` player-facing summary.
  - Bump `mod_version` to `1.4.0` when the feature set is complete.

- [x] **5.3 Verification**
  - `./gradlew test` (unit) and `./gradlew runGameTestServer` (in-game).
  - Manual client pass: tentacle model/texture, search box, sort button, seal
    overlay, demand banner, name reveal, all four ABYSS effects, book theft.
  - Build `build/libs/eldritch-1.4.0.jar` locally. **Do not copy to the live server.**

---

## Explicitly out of scope for this pass

- No terrain edits for tentacles or Hungering Dark.
- No stack-size purchases in the Codex.
- No new dimensions or structures.
- No server-side anti-cheat beyond the existing menu validation.
- No localization beyond `en_us` yet.

## Resolved design questions

1. **Starvation pauses while offline.** The timer only advances for online players;
   logout/logout restores the frozen state on next login.
2. **Fulfilling a demand lowers madness by 15.** Clamped at 0, applied before the
   fulfilment message so the relief is visible immediately.
3. **Tithe may take from hotbar slots**, but only after a 3-second named warning,
   never from the active/selected slot, and the stack is recoverable for 5 minutes
   by burning an offering of equal or greater value at the player's burner.

## Still open

1. Should the 5-minute tithe window also pause while the player is offline, like
   starvation? (Current plan: no — the debt is real time, but it is persisted.)
2. Should Thal'ggor demand the same item twice in a row if it is the only learned
   item above a value threshold? (Current plan: never repeat back-to-back.)

