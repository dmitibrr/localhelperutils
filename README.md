# Local Player Help Utils

**A small client-side helper for Minecraft 1.21.1 (NeoForge): instant block replacement, storage sorting and a local chest index.**

A personal convenience mod for a private server. No server-side mod, no operator powers, no new packets — everything is done with plain vanilla player actions (item use, inventory clicks), so the server just sees a diligent player.

> Для своего сервера, чтобъ жить стало пріятнѣе. Русская редакція — дореформенная, съ приколами.

## Features

- **Идеальная замѣна (Instant Replacement)** — break a block and it instantly comes back, placed from your off-hand (or main hand if the off-hand is empty). No cheats: just an item-use packet sent the moment the block is gone.
- **Режимъ выбора (Selection Mode)** — right-click chests (and other openable containers) to mark them into your working set; they get highlighted with green boxes.
- **Обновить указатель (Chest Database)** — walk near your marked chests; the mod opens each one by itself, reads the contents into a local JSON database (`config/localhelperutils/db.json`) and closes it.
- **Совмѣщеніе стаковъ (Stack Merging)** — finds partial stacks across chests (e.g. 16 + 17 rotten flesh) and merges them into one, moving items through your inventory as a buffer with normal container clicks.
- **Сортировка (Sorting)** — reorders items inside each chest by name, tag or mod.
- **Розыскъ (Item Search)** — type an item id; chests that hold it light up red in the world.
- **Меню на «Ё»** — press the `` ` `` / «Ё» key to open the mod menu (the physical key above Tab works on any layout).
- Bilingual UI: English and pre-reform Russian (`ru_ru.json`), picked automatically from the game language.

## Usage

1. Install the mod into the client `mods/` folder (NeoForge 21.1.248).
2. Press `` ` `` / «Ё» to open the menu.
3. Turn on **Selection Mode**, walk around and right-click the containers you want to manage.
4. Press **Update Chest Database** and just walk near the highlighted containers — the mod does the rest.
5. Use **Merge Partial Stacks**, **Sort Containers**, **Find Item** whenever needed.

> While a storage task runs, stay near the containers, keep your inventory free, and do not open other screens or sneak.

## Artifacts

- `localhelperutils-<version>.jar` — NeoForge client mod (drop into `mods/`).

## Development

- Minecraft 1.21.1, NeoForge 21.1.248, Java 21.
- Build with `./gradlew build` — output in `build/libs`.
- Run the dev client with `./gradlew runClient`.

## License

MIT