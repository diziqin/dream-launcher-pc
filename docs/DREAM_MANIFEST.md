# Dream Server Client Manifest v1

Dream Launcher consumes this document without requiring any change to a game
server. A server owner publishes the manifest and the referenced modpack over
HTTPS; clients use it to prepare an isolated Minecraft instance before joining.

## Transport rules

- The manifest URL and the bundle URL **must** use HTTPS.
- The bundle SHA-256 and exact byte size are mandatory and are checked before
  any modpack importer is invoked.
- The initial clients intentionally reject redirects. Publish the final HTTPS
  URL in the manifest.
- A future v2 envelope will add a required Ed25519 signature. Until then, the
  manifest must be served from a TLS endpoint controlled by the server owner.

## Manifest

```json
{
  "format": "dream.server-manifest",
  "schema": 1,
  "id": "example-survival",
  "revision": "2026-08-08",
  "name": "Example Survival",
  "minecraft": {
    "version": "1.21.1",
    "loader": { "type": "fabric", "version": "0.16.14" }
  },
  "bundle": {
    "format": "mrpack",
    "url": "https://cdn.example.com/dream/example-survival-2026-08-08.mrpack",
    "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "size": 12345678
  },
  "server": { "address": "play.example.com", "port": 25565 }
}
```

`bundle.format` currently supports `mrpack` and `curseforge`. The bundle must
be a standard Modrinth `.mrpack` or CurseForge modpack archive so the upstream
launcher importers can resolve files, loaders, and overrides safely.

## Client entry points

- PC: launch Dream Launcher with `--dream-manifest <https-url>`.
- Android: open **Dream server** in the launcher and paste the manifest URL.

The first release imports the published bundle into the upstream launcher's
normal instance model. This retains the upstream account, Java runtime,
downloader, mod-loader, renderer, and game-launch logic while keeping each
Dream server bundle independently manageable.
