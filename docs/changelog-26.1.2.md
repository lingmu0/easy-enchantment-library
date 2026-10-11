# Easy Enchantment Library 1.1.0 — Minecraft 26.1.2 / NeoForge

- Port to Minecraft 26.1.2, NeoForge 26.1.2.107+ and Java 25.
- Adapt GUI extraction, vanilla item rendering, tooltips, sliders and keyboard/mouse events.
- Update block/item registration, registry identifiers, client packet sending and block entity persistence.
- Update item model definitions, crafting ingredients and pack metadata for 26.1.2.
- Preserve level splitting/combining, runtime enchantment limits, per-row sliders, multiple selection, atomic batch enchanting, book withdrawal and optional JECh search.
- Preserve the existing stored-points field and component encoding, including IDs for temporarily absent enchantments.

Install the matching JAR on both the client and server. JECh is optional and must match the game/loader version.

Validation: Gradle build, 20,062 ledger checks, 27 batch checks, JSON/NBT codecs, 26.1.2 ValueInput/ValueOutput and previous block storage format checks, resource/metadata validation and client/server isolation checks passed. Minecraft was not launched; GUI, multiplayer and third-party integration need in-game verification.

Port references: [NeoForge setup](https://docs.neoforged.net/docs/gettingstarted/), [26.1 migration primer](https://docs.neoforged.net/primer/docs/26.1/) and the locally installed Minecraft 26.1.2 / NeoForge sources.
