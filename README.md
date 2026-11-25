# VHDL Toolkit for CLion

VHDL support for CLion and other JetBrains IDEs, powered by the open-source
[VHDL-LS](https://github.com/VHDL-LS/rust_hdl) language server. Free, no
subscription, no setup.

## What it does

**Language support**

- Syntax highlighting for `.vhd` / `.vhdl` with VHDL-2008 lexing — block
  comments, based literals (`16#FF#`), extended identifiers, and the
  `'1'`-vs-`clk'event` distinction that trips up regex highlighters
- Semantic colouring: types, design units, libraries, packages, subprogram
  calls, statement labels, declared objects and attributes each get their own
  colour, all tunable under **Settings | Editor | Color Scheme | VHDL**
- Diagnostics, go to definition, find references, completion, hover and rename
  through VHDL-LS
- Comment/uncomment with `--`, parenthesis matching
- VHDL-LS is bundled for Linux and Windows x86_64. Other platforms fall back to
  `vhdl_ls` on `PATH`.

**Cascaded errors are collapsed**

A typo in a `use` clause makes every name from that package unresolvable, so
one mistake becomes dozens of errors and the real one is buried. The plugin
reconstructs the causal chain and reports only the root cause, noting how many
others follow from it:

```
No declaration of 'al' within package 'std_logic_1164'
(32 further errors in this file follow from this one)
```

It only suppresses messages it can confidently attribute, never collapses a
file to zero diagnostics, and can be switched off under
**Settings | Languages & Frameworks | VHDL Toolkit**.

**Projects configure themselves**

Open a directory containing a Vivado `.xpr` or a Sigasi `.library_mapping.xml`
and the plugin offers to derive `vhdl_ls.toml` from it — filesets, per-file
libraries and the project default library included — then keeps it in step as
the project file changes. Re-run it any time from
**Tools | Sync VHDL Library Mapping**.

A hand-written `vhdl_ls.toml` is never replaced without asking.

**Constraints files**

`.xdc` files get their own highlighting. They are Tcl, so the tokenizer follows
Tcl's rules and distinguishes constraint commands (`set_property`,
`create_clock`) from property names (`PACKAGE_PIN`, `IOSTANDARD`) from the pin
and net names they apply to.

**Stale config detection**

VHDL-LS loads `~/.vhdl_ls.toml` for *every* project, so one left behind by
another tool reports missing files in projects that have nothing to do with it —
errors naming a directory you are not working in. The plugin spots a home
config whose files have all vanished and offers to rename it aside.

## Install

1. Download the latest `vhdl-toolkit-plugin` artifact from
   [Actions](https://github.com/mbcarruthers/vhdl-toolkit/actions).
2. Unzip it once. GitHub wraps artifacts, so inside is `vhdl-toolkit-X.Y.Z.zip`
   — that inner zip is the plugin.
3. CLion → **Settings | Plugins | ⚙ | Install Plugin from Disk…** → select the
   inner zip (not its contents, and not the `.jar` inside it).
4. Restart.

Requires a paid JetBrains IDE (CLion, IDEA Ultimate, PyCharm Professional, …),
since the LSP API is not available in Community editions.

## Project setup

For a Vivado or Sigasi project, none — accept the prompt when you open it.

Otherwise create `vhdl_ls.toml` in the project root:

```toml
standard = "2008"

[libraries]
defaultlib.files = ["**/*.vhd"]
```

Do not name a library `work`: VHDL-LS rejects it, because `work` is an alias
for the current library rather than a library itself. Writing `work.my_entity`
in source is still correct and still resolves.

## Build from source

```bash
bash scripts/fetch-vhdl-ls.sh   # stage VHDL-LS binaries into bundled/
./gradlew test buildPlugin      # zip lands in build/distributions/
```

Requires JDK 21. CI does this on every push and uploads the result.

## Layout

`core` holds everything that does not need an IDE: the tokenizer, block
structure, diagnostics analysis and the project file readers. It has no
IntelliJ dependency, so reaching into the platform from there fails to compile
rather than surfacing as a null at runtime, and its tests run without
downloading an IDE. `src` holds the half that only exists once the editor is
running, and depends on `core`.

## Roadmap

- Aligning `:` and `=>` into columns in interface lists, port maps and
  consecutive declarations
- Quick fixes: adding a missing context clause, converting an operand whose
  type does not match
- Checks the language server does not make, starting with incomplete process
  sensitivity lists
- Code folding over comment runs and design units
- Running simulation from the IDE — GHDL first, then waveforms, then cocotb
- Publishing to the JetBrains Marketplace

Longer term, the cascade collapsing here is a workaround. The real fix is
error-type poisoning inside rust_hdl, the way rustc and Clang suppress
downstream diagnostics after a failed resolution — that would help every
VHDL-LS user rather than only CLion ones.

## License

MIT. See [LICENSE](LICENSE).

Bundled VHDL-LS binaries are redistributed under the
[MPL-2.0](https://github.com/VHDL-LS/rust_hdl/blob/master/LICENSE.txt); the
plugin builds them unmodified from upstream releases.
