# pack/ — local modpack source

This folder mirrors what will land under the game directory on the user's machine.
Everything here gets published as a **GitHub Release asset** + indexed in `manifest.json`.

## Expected layout

```
pack/
├── mods/
│   ├── nylecontent-1.3.3.jar
│   ├── nyleessentials-1.1.8.jar
│   ├── geckolib-fabric-1.21.1-4.8.4.jar
│   └── ...
├── config/
│   └── ...          # config files to ship pre-configured
├── resourcepacks/
│   └── ...          # optional
└── shaderpacks/
    └── ...          # optional
```

## Workflow

1. Drop/remove files under `pack/`.
2. Run `python3 scripts/generate-manifest.py` — it writes `manifest.json` at the repo root with SHA-256 + size + download URLs.
3. Commit and tag `git tag pack-v<DATE>` (or let the GitHub Action `publish-modpack` do the release upload automatically).
4. Users get the update on next launch — existing files are verified via SHA-256 and only deltas are re-downloaded.

**Anything present on the player's side that is not in the manifest gets deleted** (under `mods/`, `config/`, `resourcepacks/`, `shaderpacks/`) — so removing a file here = removing it on all players at the next start.
