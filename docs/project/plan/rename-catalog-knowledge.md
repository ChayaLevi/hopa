# Rename catalog/knowledge terminology

Three naming inconsistencies left over from the `knowledge → catalog` module
rename. All are mechanical find-and-replace work with no logic changes.

---

## 1. `catalog/knowledge/` subpackage and its classes

The module is `catalog`. The subpackage is `knowledge`. Every class inside
carries the old name. `com.hopa.catalog.knowledge.Knowledge` reads as if
"catalog" and "knowledge" are different things — they are not.

`Knowledge` is "all catalog tables assembled into one value" (apps, sites,
approval, prompt rules). It is a catalog snapshot.

| Current | Proposed |
|---|---|
| `catalog/knowledge/` subpackage | `catalog/store/` |
| `Knowledge` | `CatalogSnapshot` |
| `KnowledgeBundle` | `CatalogBundle` |
| `KnowledgeCodec` | `CatalogCodec` |
| `KnowledgeSource` | `CatalogSource` |
| `FileKnowledgeStore` | `FileCatalogStore` |

---

## 2. `KnowledgeClient` in `backend/link/`

Fetches catalog data from the backend. Same stale name.

| Current | Proposed |
|---|---|
| `KnowledgeClient` | `CatalogClient` |
| `KnowledgeClientTest` | `CatalogClientTest` |
| `KnowledgeUpdater` (Android) | `CatalogUpdater` |

---

## 3. `agent/kinds/` subpackage

`kinds/` describes the subject domain (action kinds) rather than the package
role. The package contains `ActionClassifier` and `ActionKindRules` — both
about classifying actions by risk level.

| Current | Proposed |
|---|---|
| `agent/kinds/` | `agent/classify/` |
