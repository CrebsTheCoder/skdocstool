# SkriptDocsTool

A Paper plugin that generates documentation for installed [Skript](https://github.com/SkriptLang/Skript) and their addons
for [skdocs](https://skdocs.org).

## Requirements

- Paper 1.21.11+
- Skript 2.16.0+

## Download Release
### [Download](github.com/CrebsTheCoder/skdocstool/releases/latest)

## Usage

Drop the jar into your server's `plugins/` folder and use the in-game or console commands:

| Command | Permission | Description |
| --- | --- | --- |
| `/gendocs` | `skdocstool.gendocs` | Generates documentation for all installed Skript addons. |
| `/docs preview <addon>` | `skdocstool.docspreview` | Uploads the generated skdocs JSON for an addon to the skdocs preview API and returns a link. |

Generated files land in `plugins/skdocstool/documentation/`:

- `skdocs-<addon>.json`: skdocs-format documentation.
- `skripthub-<addon>.json`: SkriptHub-format documentation.


## License

See the repository for licensing details.
