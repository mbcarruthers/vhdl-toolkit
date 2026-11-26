# VHDL Toolkit

VHDL support for JetBrains IDEs, built on the open-source
[VHDL-LS](https://github.com/VHDL-LS/rust_hdl) language server. Free, MIT
licensed, no subscription.

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

- Reads Vivado `.xpr`, Tcl project scripts, `.f` file lists, or falls back to
  scanning the tree for sources
- Runs a project-creating Tcl script through Vivado and imports the result,
  for repositories that ship a build script instead of a project
- Generates `vhdl_ls.toml`, and offers to regenerate it when the project file
  has changed. A `vhdl_ls.toml` it did not write is never replaced without
  asking.
- Keeps multi-library projects multi-library — each source keeps the library
  it was assigned
- Reads your sources for the libraries they reference and warns when the
  mapping does not define one
- Maps vendor libraries (`unisim`, `unimacro`, `xpm`) from the Vivado install
- Spots a stale `~/.vhdl_ls.toml` left behind by another tool, and renames it
  rather than deleting it
- Restarts the language server when the mapping changes, so a regenerated
  `vhdl_ls.toml` takes effect without restarting the IDE
- Runs synthesis, implementation, bitstream generation and XSim simulation
  from the IDE, with clickable errors in the log

**Constraints**

- `.xdc` highlighting that follows Tcl's rules

## Install

### 1. The language server

Download the release for your platform from
[VHDL-LS releases](https://github.com/VHDL-LS/rust_hdl/releases), extract it,
and put its `bin` directory on your `PATH`. Check it:

```bash
vhdl_ls --version
```

The executable is `vhdl_ls`. A release may contain other binaries with similar
names — `vhdl_ls` is the one the plugin runs.

### 2. The plugin

1. Download the latest `vhdl-toolkit-plugin` artifact from
   [Actions](https://github.com/mbcarruthers/vhdl-toolkit/actions).
2. Unzip it **once**. GitHub wraps artifacts, so inside is
   `vhdl-toolkit-X.Y.Z.zip` — that inner zip is the plugin.
3. **Settings | Plugins | ⚙ | Install Plugin from Disk…** and choose the inner
   zip. Not its contents, and not the `.jar` inside it.
4. Restart the IDE.

Diagnostics and navigation need an IDE with the LSP API — CLion, IDEA
Ultimate, PyCharm Professional and similar. In Community editions the plugin
still loads and provides highlighting, indentation and completion.

## Setup

Open a Vivado project and accept the prompt; the plugin derives the library
mapping from the `.xpr`.

Otherwise, create `vhdl_ls.toml` in the project root:

```toml
standard = "2008"

[libraries]
defaultlib.files = ["**/*.vhd"]
```

Do not name a library `work`. VHDL-LS rejects it, because `work` is an alias
for whichever library a file is compiled into rather than a library itself.
Writing `work.my_entity` in your source is still correct and still resolves.

## Settings

| Where | What |
| --- | --- |
| Languages & Frameworks \| VHDL Toolkit | Error collapsing, diagnostic delay, closing style, caret placement after a completed block, `when` alignment, Vivado path |
| Editor \| Code Style \| VHDL | Indent size, tabs, `.editorconfig` |
| Editor \| Color Scheme \| VHDL | Every colour the plugin paints |

## Build from source

```bash
./gradlew test buildPlugin    # zip lands in build/distributions/
```

Requires JDK 21. CI builds and tests on every push.

## Roadmap

- Aligning `:` and `=>` into columns, and a formatter so Reformat Code works
- Quick fixes: adding a missing context clause, fixing a mismatched operand
- Checks the language server does not make, starting with incomplete process
  sensitivity lists
- Code folding
- Simulation without Vivado — GHDL, then waveform viewing, then cocotb
- Bundling the language server so step 1 is unnecessary
- Publishing to the JetBrains Marketplace

## License

MIT — see [LICENSE](LICENSE).

VHDL-LS is separate software under the
[MPL-2.0](https://github.com/VHDL-LS/rust_hdl).
