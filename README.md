# VHDL Toolkit for CLion

VHDL support for CLion (and other paid JetBrains IDEs), powered by the
open-source [VHDL-LS](https://github.com/VHDL-LS/rust_hdl) language server.
Free, no subscription.

## Features (v0.1)

- Syntax highlighting for `.vhd` / `.vhdl` (VHDL-2008 lexing: block comments,
  based literals, extended identifiers, char-literal vs. attribute-tick)
- Live diagnostics, go to definition, find references, completion, hover and
  rename via VHDL-LS over CLion's built-in LSP support
- Comment/uncomment with `--` (Ctrl+/), parenthesis matching
- VHDL-LS bundled for Linux and Windows x86_64 — zero setup. Other platforms:
  install `vhdl_ls` on PATH.

## Roadmap

- Smart `end` completion (`end architecture rtl;`, `end if;`, … inserted like
  `};` in C++)
- Vivado project recognition: parse `.xpr`, auto-generate the VHDL-LS library
  mapping, vendor libraries (`unisim`, `xpm`) marked third-party
- Testbench scaffolding and more VHDL tooling

## Install

1. Download `vhdl-toolkit-*.zip` from Releases (or a CI build artifact).
2. CLion → **Settings → Plugins → ⚙ → Install Plugin from Disk…** → pick the zip.
3. Restart CLion and open a `.vhd` file.

## Project setup

For whole-project analysis, create `vhdl_ls.toml` in the project root:

```toml
standard = "2008"

[libraries]
my_lib.files = ["src/**/*.vhd"]
```

Without it, VHDL-LS still analyzes open files standalone.

## Build from source

```bash
bash scripts/fetch-vhdl-ls.sh   # stage VHDL-LS binaries into bundled/
./gradlew test buildPlugin      # zip lands in build/distributions/
```

Requires JDK 21. CI (GitHub Actions) does exactly this on every push and
uploads the zip as the `vhdl-toolkit-plugin` artifact.

## License

MIT. VHDL-LS binaries are redistributed under their
[MPL-2.0 license](https://github.com/VHDL-LS/rust_hdl/blob/master/LICENSE.txt).
