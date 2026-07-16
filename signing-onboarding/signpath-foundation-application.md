# SignPath Foundation — Application Text

**URL** : https://signpath.org/foundation/apply

Soumets le formulaire avec ces réponses (copier/coller).

---

## Project name
NyleLauncher

## Project URL
https://github.com/NYL3E/NyleLauncher

## Project description (1-2 paragraphs)

NyleLauncher is the official cross-platform launcher for **NyleRP**, a French
roleplay Minecraft server (`play.nylerp.fr`). It handles Microsoft account
authentication via the official Xbox Live OAuth flow, auto-updates a curated
modpack from the server's official manifest at each launch, and starts the
game directly into the server. The launcher is built around a small native
bootstrap installer (~300 KB Java jar wrapped into an MSI/DMG/DEB) that
fetches an updateable payload from GitHub Releases — this design keeps the
signed binary tiny and stable while letting us iterate features without ever
re-installing.

The project is fully open source under the MIT license, with all releases
built reproducibly via GitHub Actions from the public `main` branch. Source
code, build scripts, release workflows and the signed-asset CI integration
all live in the repository. The only payment surface anywhere in the
codebase is Microsoft's official Live OAuth — we never see player passwords.

## License
MIT (https://github.com/NYL3E/NyleLauncher/blob/main/LICENSE)

## Maintainers

- **Nyle (NYL3E)** — Project owner & lead maintainer
  - GitHub: https://github.com/NYL3E
  - Email: lennytridat@gmail.com

## Release artifacts to sign (per platform)

We need signing for the bootstrap installer (the small native installer the
user runs once). The payload jar that the bootstrap fetches at runtime is
verified via SHA256 against a signed manifest, so it does not need direct
signing.

- **Windows** : `NyleLauncher-windows-v{X.Y.Z}.msi` (~80 MB, jpackage MSI)
- **Windows portable** : `NyleLauncher-windows-portable-v{X.Y.Z}.zip` (~77 MB,
  if SignPath supports signing the `.exe` inside the zip — otherwise we can
  drop the portable variant or sign separately)
- **macOS** : optional — we plan to use Apple Developer notarization for the
  `.dmg` (covered separately), so SignPath is not needed on macOS

## Build / release process

1. Maintainer pushes a `vX.Y.Z` git tag to the public `main` branch
2. `.github/workflows/bootstrap.yml` runs on `ubuntu-latest` / `windows-latest`
   / `macos-14` build matrix, all from public CI logs
3. WiX Toolset 3.14 (bundled with jpackage) produces the MSI on `windows-latest`
4. The MSI is uploaded as a GitHub Release asset
5. **Proposed addition with SignPath** : the bootstrap workflow uploads the
   unsigned MSI to SignPath via the official `SignPath/GitHubActionsTemplate`
   action, awaits the signed artifact, and re-uploads it to the same Release

CI workflow files are public at
https://github.com/NYL3E/NyleLauncher/tree/main/.github/workflows

## Distribution URLs (where users download)

- Direct from GitHub Releases:
  https://github.com/NYL3E/NyleLauncher/releases/latest
- Public website redirector :
  https://nyle-mc-server.pages.dev/api/launcher/windows
  (Cloudflare Pages function that 302s to the latest `*-windows-v*.msi`)

## Volume / scale

~few hundred active users (small French Minecraft community), growing.
We expect a few thousand cumulative downloads in 2026.

## Anything else SignPath should know

- The launcher does not collect telemetry, does not phone home, does not
  open network sockets except :
  1. Microsoft Live OAuth (login flow)
  2. GitHub Releases CDN (payload + modpack files download)
  3. `mc-heads.net` for player avatar rendering (optional, UI only)
- The launcher does not install any system service, does not modify the
  registry outside its own keys, does not request elevation beyond the
  standard MSI install prompt
- We are committed to keeping the project open source, the maintainers
  publicly identified, and the release artifacts reproducible from CI
