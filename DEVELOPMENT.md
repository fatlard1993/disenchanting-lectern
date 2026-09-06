# Disenchanting Lectern - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side. Vanilla clients need nothing at all: the lectern, the book and the result are
all vanilla. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and
`fabric.mod.json` (Java).

## Writing descriptions

Minecraft has no prose for its enchantments. An enchantment's `description` field is its display
name, so the game can tell you an item has Fortune III and cannot tell you what Fortune does, which
is the one thing somebody reading an item most wants to know. This mod ships lines for all 43
vanilla enchantments, and anything else supplies its own as data:

```
data/<namespace>/enchantment_descriptions/<name>.json
```

```json
{ "description": "Mine blocks faster." }
```

The path mirrors where the enchantment itself lives, so `data/useful-hoe/enchantment/reach.json`
is described by `data/useful-hoe/enchantment_descriptions/reach.json`.

A mod adds prose for its own enchantment by shipping that one file. **No dependency on this mod, no
reflection, no load-order arrangement** - if this mod is not installed the file is inert, and if it
is, the line turns up. Data packs can do the same, and can overwrite lines they disagree with by
sitting later in the pack order.

The vanilla set is loaded through exactly this mechanism rather than being special-cased beside it,
which is the only way to know the extension path works.

An enchantment nobody has written a line for still gets its facts, so a modded or datapack
enchantment reads as well as it can rather than not at all.
