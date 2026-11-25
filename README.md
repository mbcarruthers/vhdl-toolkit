# VHDL Toolkit

VHDL support for JetBrains IDEs, powered by the open-source
[VHDL-LS](https://github.com/VHDL-LS/rust_hdl) language server. Free, no
subscription.

## Features

**Editing**

- Syntax highlighting for `.vhd` / `.vhdl`, VHDL-2008 lexing
- Semantic colouring — types, design units, libraries, packages, subprogram
  calls, labels, declared objects and attributes, each tunable
- Indentation, with closing keywords pulling back to the construct they close
- Block completion: finish a header and the `begin` and matching `end` appear
- Complete Statement with <kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>Enter</kbd>
- `port (`, `generic (` and `port map (` close with their semicolon
- Keyword completion by region, with standard types and skeletons for
  entities, architectures, processes, if and case statements
- Gutter icons between an entity and its architectures, and from an
  instantiation to what it names
- Comment/uncomment, bracket matching

**Diagnostics**

- Errors, go to definition, find references, hover and rename through VHDL-LS
- Cascaded errors collapsed to their root cause, with a count of what follows
- Diagnostics held until you stop typing

**Projects**

- Reads Vivado `.xpr`, Sigasi library mappings, Tcl project scripts, `.f` file
  lists, or falls back to scanning for sources
- Runs a project-creating Tcl script through Vivado and imports what it
  produces, for repositories that ship a build script instead of a project
- Generates `vhdl_ls.toml` and keeps it in step
- Keeps multi-library projects multi-library: each source keeps the library it
  was assigned, rather than everything landing in one
- Reads the sources for the libraries they actually reference and warns when
  the mapping does not define one, which catches a `.f` list with no library
  information or a project whose vendor libraries are unmapped
- Maps vendor libraries (`unisim`, `unimacro`, `xpm`) from the Vivado install
- Spots a stale `~/.vhdl_ls.toml` left behind by another tool
- Restarts the language server when the mapping changes, so a regenerated
  `vhdl_ls.toml` takes effect without restarting the IDE
- Runs Vivado synthesis, implementation and bitstream generation from the IDE,
  with clickable errors

**Constraints**

- `.xdc` highlighting that follows Tcl's rules

## Install

1. Download the latest `vhdl-toolkit-plugin` artifact from
   [Actions](https://github.com/mbcarruthers/vhdl-toolkit/actions).
2. Unzip it once. GitHub wraps artifacts, so inside is `vhdl-toolkit-X.Y.Z.zip`
   — that inner zip is the plugin.
3. **Settings | Plugins | ⚙ | Install Plugin from Disk…** → select the inner
   zip, not its contents and not the `.jar` inside it.
4. Restart.

The language server is bundled for Linux and Windows x86_64, so there is
nothing else to install. Other platforms fall back to `vhdl_ls` on `PATH`.

Diagnostics and navigation need an IDE with the LSP API — CLion, IDEA
Ultimate, PyCharm Professional and so on. In Community editions the plugin
still loads and provides highlighting, indentation and completion.

## Setup

For a Vivado or Sigasi project, none — accept the prompt when you open it.
Otherwise create `vhdl_ls.toml` in the project root:

```toml
standard = "2008"

[libraries]
defaultlib.files = ["**/*.vhd"]
```

Do not name a library `work`. VHDL-LS rejects it, because `work` is an alias
for the current library rather than a library itself. Writing `work.my_entity`
in source is still correct and still resolves.

## Settings

**Settings | Languages & Frameworks | VHDL Toolkit** — error collapsing,
diagnostic delay, closing style (`end;` / `end if;` / `end if my_label;`),
where the caret lands after a block is completed, `when` alignment in a case,
and the Vivado path.

Indent size, tabs and `.editorconfig` support live under
**Settings | Editor | Code Style | VHDL**, and colours under
**Settings | Editor | Color Scheme | VHDL**.

## Build from source

```bash
bash scripts/fetch-vhdl-ls.sh   # stage VHDL-LS binaries into bundled/
./gradlew test buildPlugin      # zip lands in build/distributions/
```

Requires JDK 21. CI does this on every push and uploads the result.

## Roadmap

- Aligning `:` and `=>` into columns
- Quick fixes: adding a missing context clause, fixing a mismatched operand
- Checks the language server does not make, starting with incomplete process
  sensitivity lists
- Code folding
- Simulation from the IDE — GHDL, then waveforms, then cocotb
- Publishing to the JetBrains Marketplace

## License

MIT. See [LICENSE](LICENSE).

Bundled VHDL-LS binaries are redistributed under the
[MPL-2.0](https://github.com/VHDL-LS/rust_hdl/blob/master/LICENSE.txt); the
plugin builds them unmodified from upstream releases.
