# Home Recall

A Minecraft 1.12.2 Forge mod. Press a key, stand through an eight second cast, and return to your
own spawn. One ability, one destination, no waypoint system.

## Status

Initialized. No gameplay behavior is implemented yet.

## Install side

Both. The server is authoritative over every decision: whether a recall may begin, how long it
runs, whether it is cancelled, where it ends, and when the player is moved. A modified client
cannot bypass any restriction.

## Dependencies

- **[Inventory Button Bar](https://github.com/mahghuuuls/inventory-button-bar) 1.0.0 or newer,
  required.** Supplies the inventory button that opens the Recall Stone screen. Its jar must be
  installed on the dedicated server as well, where it loads and does nothing. That is the price of
  Forge having no way to say "required on the client only".
- **Inhibited, optional.** When the `inhibited:inhibited` effect is present, recall is blocked
  while a player has it and an active cast is cancelled. Detected by registry identity, so any mod
  supplying that effect works and none is required. With it absent the mod runs normally and says
  nothing about it.

## Building

```
./gradlew build
```

The upload-ready artifact is the normal jar under `build/libs/`.

`libs/inventorybuttonbar-1.0.0-dev.jar` is committed because the build resolves it as a file.
`libs/agenttesttoolkit-*.jar` is development-only test tooling, is not committed, and must be
fetched by hand from its
[releases](https://github.com/mahghuuuls/agent-test-toolkit/releases) before `runClient` or
`runServer` will start.

## License

MIT. See `LICENSE`.

The build environment and initial project skeleton derive from the CleanroomMC ForgeDevEnv
template, which remains under its own MIT license. See `THIRD-PARTY-NOTICES.md`.
