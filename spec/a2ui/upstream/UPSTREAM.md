# A2UI 1.0 schemas (vendored)

Unmodified copies of the A2UI 1.0 JSON schemas and basic catalog, used only by
`tools/checks/check-a2ui.py` to prove that the HOPA catalog and every accepted
fixture are valid A2UI 1.0. No HOPA code loads them at runtime. A2UI is
Apache-2.0: the licence text is in `A2UI-LICENSE.txt`, and the root `NOTICE`
names the project (ADR-0031).

| Field | Value |
|---|---|
| Project | https://github.com/a2ui-project/a2ui |
| Version | 1.0 |
| Commit | `d4f2761b4629028b7995fdd861a7792eeba529ff` (2026-11-19) |
| License | Apache-2.0 |

## Copied files

| Here | Upstream path | sha256 |
|---|---|---|
| `agent_to_renderer.json` | `specification/v1_0/json/agent_to_renderer.json` | `2f575f783fdda0e4d175fe5d331661655e4a4901613262322792dae7e823761d` |
| `renderer_to_agent.json` | `specification/v1_0/json/renderer_to_agent.json` | `58ecde02101f0f37566b5e238dc6ef6af628c3ff312fce0e17ab7670b7215465` |
| `common_types.json` | `specification/v1_0/json/common_types.json` | `c4e78305be1de73eecb37f8179ba169730250611703683af291b18135a6430dc` |
| `catalog_definition.json` | `specification/v1_0/json/catalog_definition.json` | `836a48b2deb7ae76c3b68cb4e79c2faa24cba36d4d5396474894796797364d44` |
| `renderer_capabilities.json` | `specification/v1_0/json/renderer_capabilities.json` | `3ebd2889764d892df16d034643ecd9e3a3fc02f298fa05577b95d9e287e703de` |
| `basic_catalog.json` | `catalogs/basic/v1/catalog.json` | `d4ce11f8fff2da8d5fd0b2793db524f6aa7eef541eefb6fe2b71f0efc17cbfa9` |
| `A2UI-LICENSE.txt` | `LICENSE` | `8c8b5b632d56c4c1658296963c693db5396772a33caaddb9a22593c4155c437a` |

Refresh only together with `spec/a2ui/VERSION` and a new ADR.
