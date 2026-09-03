# Changelog

This project follows the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) convention and
[Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-09-03

### Added

- The recall ability: press the key (default R), stand still for six seconds, and return to your
  bed, or to the world spawn when no bed spawn is valid. Works across dimensions without
  generating a portal.
- The Recall Stone, crafted from one ender pearl, two diamonds, and stone. Equipping it in the
  Recall Stone Slot grants the ability; its tooltip explains the mechanic and names the live
  keybind.
- The Recall Stone screen, opened from an Inventory Button Bar button, and the operator command
  `/recallequip <show|set|clear|open> [player]`.
- Cast presentation: a cast bar that fills, freezes red on interruption, and fades; a particle
  circle that thickens over the cast; the vanilla portal hum; departure and arrival effects; and
  the Recall Stone drawn in the casting player's hand in first and third person. Nearby players
  see and hear all of it.
- Cancellation with stated reasons: moving, acting, damage that lands, death, logout, dimension
  change, or a second key press ends the cast.
- Fourteen configuration options: cast length, cross-dimension travel, damage cancelling, the
  world-spawn fallback, Innate Mode without a stone, death behavior, a one-time starter stone for
  new players, the inventory button, the recipe, the whole stone system, particles, the cast bar,
  sounds, and Diagnostic Mode.
- Diagnostic Mode, off by default and silent when off, recording why each recall was allowed,
  refused, or ended.

### Compatibility

- Requires Inventory Button Bar 1.0.0 or newer on both the client and the server.
- Runs on Cleanroom as well as standard Forge.
- Coexists with mods that replace the player's held-item render layer, such as Everfilling
  Flasks.
