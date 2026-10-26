# Spectre

A fully open-source Fabric client for **Minecraft 26.2**, with a custom interface, 114 built-in modules, schematic previews and building, world inspection, media playback, and theme-matched skies.

**Created by Cody (cody-raves).**

**[Download the JAR](https://github.com/cody-raves/Spectre/releases/latest)** · **[Documentation & module guide](https://cody-raves.github.io/Spectre/)** · **[Report an issue](https://github.com/cody-raves/Spectre/issues)**

## Behavior and security

Read the [behavior report](SECURITY-REPORT.md) for the account-switcher token flow, feature connections, local storage, known weaknesses, and verification limits. Version 0.9.1 removes the Discord invite, Presence integration, and webhook notification features.

## Install

1. Install **Java 25**, **Minecraft 26.2**, and **Fabric Loader 0.19.3 or later**.
2. Add **Fabric API 0.155.2+26.2 or later compatible with 26.2** to the instance's `mods` folder.
3. Download `spectre-0.9.1+mc26.2.jar` from Releases and put it in the same folder. The `-sources.jar` is for developers and is not the installable mod.
4. Launch Minecraft and press **Right Shift** to open Spectre. Left-click a module to toggle it or open its tool; right-click for settings.

## Highlights

- **Base Builder:** import `.schem` and `.litematic` files, position a textured ghost preview, inspect materials, and build in creative single-player with normal movement and placement interactions.
- **Scaffolding access:** reuse access columns, climb with Jump, connect supported spans between owned towers, then descend and remove temporary scaffolding after a successful build.
- **Redstone verification:** functional fixtures exercise pistons, sticky pistons, repeater delay, subtract comparators, hoppers, a furnace, a crafter, a dispenser, and a dropper.
- **World inspection:** Hive Map, Region Map, block/entity overlays, storage outlines, and configurable activity clues from received world data.
- **Media Player:** YouTube and SoundCloud inside Minecraft, with an optional desktop media HUD.
- **Spectre Sky:** GUI-matched palettes, nebulae, auroras, stars, a spectral sun, and a ghost moon.
- **Custom interface:** themed vanilla menus, configurable appearance, profiles, hotkeys, and HUD editing.

The construction executor currently requires **creative single-player** and the relevant items in inventory. Survival resupply and restoration of block-entity contents are not implemented. Importing a schematic does not guarantee every machine or block recipe is supported. See the guide for current limits and tested behavior.

## Build from source

Use a **Java 25 JDK**. On Windows:

```powershell
.\build.ps1 -JavaHome "C:\path\to\jdk-25"
```

The script downloads Gradle 9.5.1 with SHA-256 verification, stages the active project outside synced folders, and writes the mod and sources JARs into `dist/`. It does not bundle Minecraft itself.

With Java 25 and Gradle 9.5.1 installed, the standard build is:

```sh
gradle :Spectre-26.2:build
```

The complete active source, assets, test fixtures, and build configuration are in this repository. GitHub's source archives for each release include the build scripts.

## Validate

These opt-in checks launch disposable creative worlds. Their test initializer is excluded from the normal JAR.

```powershell
.\build.ps1 -BuilderScaffoldTest -JavaHome "C:\path\to\jdk-25"
.\build.ps1 -BuilderRedstoneDemo -JavaHome "C:\path\to\jdk-25"
.\build.ps1 -VanillaUiTest -JavaHome "C:\path\to\jdk-25"
```

The release's redstone demo is a continuous **1× first-person recording**. Results from the verified build are in `validation/scaffold-smoke-pass.txt` and `validation/builder-redstone/builder-demo-pass.txt`. Detailed local usage notes are in [README.txt](README.txt).

The GitHub Pages site is static HTML/CSS/JavaScript in `docs/`. Regenerate its module catalog after changing module metadata or settings:

```sh
python tools/generate-docs.py
```

## Open source

Spectre was created by Cody and builds on existing open-source work. The source is public so others can inspect, modify, and build on it.

**GPL-3.0-only.** Applicable license notices are included with the source. GoNuts-derived portions retain their MIT notice, and dependencies retain their respective licenses.

## Verify, don’t just trust

A JAR is a ZIP-based archive: anyone can open it and inspect its resources, metadata, dependencies, and compiled classes. Java bytecode can often be decompiled. Obfuscation makes that code harder to understand, not secret; decompilation does not recover the exact original source or comments.

Do not rely only on the maintainer’s claims. Read the source and dependencies, seek independent review, or build the release tag yourself. Open source enables inspection; it is not an automatic safety guarantee.

Release assets include `SHA256SUMS.txt`. Check your download in PowerShell:

```powershell
Get-FileHash -Algorithm SHA256 .\spectre-0.9.1+mc26.2.jar
```

Compare it with the published value. To list the archive contents with a JDK, run `jar tf spectre-0.9.1+mc26.2.jar`, or open it with an archive tool. A checksum verifies that the download matches the published artifact, not that the code is safe. Byte-for-byte reproducible builds have not been established; your local rebuild is not promised to have an identical hash.
