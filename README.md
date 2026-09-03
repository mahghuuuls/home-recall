# Home Recall

A Minecraft 1.12.2 Forge mod. Press a key, stand still through a six second cast, and return to
your own spawn. One ability, one destination, no waypoint system, no cooldown.

Player-facing description: [MOD-PAGE.md](MOD-PAGE.md). Release history: [CHANGELOG.md](CHANGELOG.md).
License: [MIT](LICENSE). Upstream notices: [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

## Install side

Both. The server decides everything: whether a recall may begin, how long it runs, whether it is
cancelled, where it ends, and when the player is moved. The client sends one request per key press
and draws what it is told. A modified client cannot bypass a restriction.

## Dependency

**[Inventory Button Bar](https://github.com/mahghuuuls/inventory-button-bar) 1.0.0 or newer,
required on both sides.** It supplies the button that opens the Recall Stone screen. Its jar must
be present on a dedicated server too, where it loads and does nothing; Forge has no way to declare
a dependency as client-only. The declared relation is an inclusive minimum with no upper bound;
1.0.0 is the version this release was built and tested against.

Also runs on Cleanroom, verified against Cleanroom 0.6.12 in the owner's test pack. No Mixin,
coremod, or access transformer is used.

## What it does

- A **Recall Stone** item, crafted from one ender pearl, two diamonds, and stone. It is never
  held, used, or consumed; it grants the ability while equipped and teaches the mechanic through
  its tooltip, which names the live keybind.
- A **Recall Stone Slot**, reached from an Inventory Button Bar button. Persists across login,
  death, dimension change, and restart. Operators can inspect or set it with
  `/recallequip <show|set|clear|open> [player]` (permission level 2).
- A **cast**: press the key, stand still for `castTimeSeconds`, arrive at your bed, or at the
  world spawn when no bed spawn is valid. Moving, attacking, using or placing anything, taking
  damage that lands, dying, logging out, changing dimension, or pressing the key again breaks
  it, and the player is told why when the cause is not their own obvious action. Cross-dimension
  recall travels without generating a portal.
- **Presentation**: a cast bar, a particle circle that thickens as the cast progresses, the
  vanilla portal hum, and a Recall Stone drawn in the casting player's hand in first and third
  person. Nearby players see and hear all of it.
- **Two modes**: Equipment-Gated (the default, a stone must be equipped) and Innate
  (`requireRecallStone=false`, every player may recall).

## Configuration

`config/homerecall.cfg`, five sections, fourteen options. Every option's generated comment
explains its real effect. Options marked *restart* are read once at game start.

| Section | Key | Default | Effect |
| --- | --- | --- | --- |
| general | `requireRecallStone` | `true` | Equipment-Gated Mode when true, Innate Mode when false. Forced to false when the stone system is off. |
| general | `castTimeSeconds` | `6` | Cast length, 1 to 300. |
| general | `allowCrossDimension` | `true` | Allow a recall to finish in another dimension. |
| general | `cancelOnDamage` | `true` | Break the cast on damage that lands. |
| general | `fallbackToWorldSpawn` | `true` | Use the world spawn when no personal spawn is valid; false refuses instead. |
| equipment | `registerRecallStone` | `true` | *restart.* The whole stone system: creative entry, recipe, button, screen. The item itself stays registered so existing worlds never lose stones. |
| equipment | `registerRecallStoneRecipe` | `true` | *restart.* The crafting recipe. |
| equipment | `keepRecallStoneOnDeath` | `true` | Keep the equipped stone through death; false makes it follow the inventory, graves included. |
| equipment | `giveRecallStoneToNewPlayers` | `false` | One stone per player on first join, never repeated. |
| equipment | `showInventoryButton` | `true` | Hide the button; the screen then needs `/recallequip open`. |
| visual | `enableParticles` | `true` | The cast circle and the departure and arrival bursts. |
| visual | `enableCastHud` | `true` | The cast bar. |
| audio | `enableRecallSounds` | `true` | The channelling hum and the departure and arrival sounds. |
| diagnostics | `enableDiagnostics` | `false` | Diagnostic Mode: log why every recall was allowed, refused, or ended. Silent when off. |

Player-side options (`enableParticles`, `enableCastHud`, `enableRecallSounds`) follow each
client's own file for what that client sees and hears; everything that decides a recall follows
the server's file. The tooltip's required-versus-innate wording and the button's visibility are
also read from the client's file, so a client whose file differs from the server's sees the
client's wording while the server still enforces its own rule.

## Scope boundaries

One destination, resolved from vanilla respawn data. No multiple homes, named destinations,
waypoints, cooldowns, experience or item costs, or integration with other teleport mods. The
Recall Stone is the only equipment the slot accepts.

## Building

```
./gradlew build
```

The upload-ready artifact is the normal jar under `build/libs/`. `libs/inventorybuttonbar-1.0.0-dev.jar`
is committed because the build resolves it as a file. `libs/agenttesttoolkit-*.jar` is
development-only test tooling, is not committed, is never shipped, and must be fetched by hand
from its [releases](https://github.com/mahghuuuls/agent-test-toolkit/releases) before `runClient`
or `runServer` will start.

## License

MIT. See `LICENSE`. The build environment and initial project skeleton derive from the
CleanroomMC ForgeDevEnv template, which remains under its own MIT license; see
`THIRD-PARTY-NOTICES.md`.
