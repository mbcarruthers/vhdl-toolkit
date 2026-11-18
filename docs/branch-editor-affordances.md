# Branch: `editor-affordances`

Merge record. Nothing here is merged until every item is ticked by the repo
owner. **Do not merge this branch automatically.**

Two features: Complete Statement (Ctrl+Shift+Enter) and gutter navigation
between the halves of a design.

---

## 1. Complete Statement — Ctrl+Shift+Enter

Finishes the line the caret is on, then moves on. If the finished line turns
out to open a block, the block is completed too.

**Code:** `VhdlSmartEnter.kt` (logic), `VhdlSmartEnterProcessor.kt` (glue),
`lang.smartEnterProcessor` in `plugin.xml`.

**Automated:** `VhdlSmartEnterTest` — 15 cases.

| | Behaviour | Verified |
|---|---|---|
| 1.1 | `q <= '1'` → adds `;` | [ ] |
| 1.2 | `q <= std_logic_vector(count` → closes bracket, adds `;` | [ ] |
| 1.3 | `q <= f(g(x` → closes both brackets | [ ] |
| 1.4 | `port (clk : in std_logic` → closes as `);` | [ ] |
| 1.5 | `if x = '1'` → adds ` then` **and** `end if;` | [ ] |
| 1.6 | `case sel` → adds ` is` and `end case;` | [ ] |
| 1.7 | `while i < 8` → adds ` loop` and `end loop;` | [ ] |
| 1.8 | `process (clk)` → adds `begin` and `end process;` | [ ] |
| 1.9 | `if x then` (already complete) → no `then then` | [ ] |
| 1.10 | `architecture rtl of top is` → no stray `is;` | [ ] |
| 1.11 | Blank line → nothing happens | [ ] |
| 1.12 | Comment line → nothing happens | [ ] |
| 1.13 | Line already ending in `;` → falls through to plain behaviour | [ ] |
| 1.14 | Caret ends up somewhere sensible every time | [ ] |

**Known limits**
- `if (x and ` with an open bracket deliberately gets no `then` — the
  condition is unfinished and `then` would land inside it.
- Does not add a missing `=>` in a case alternative.

---

## 2. Gutter navigation between design units

Three links: entity → its architectures, architecture → its entity,
instantiation → the entity or component it names.

**Code:** `VhdlDesignUnits.kt` (scanner), `VhdlDesignUnitIndex.kt` (project
index, cached), `VhdlLineMarkerProvider.kt` (gutter icons),
`codeInsight.lineMarkerProvider` in `plugin.xml`.

**Automated:** `VhdlDesignUnitsTest` — 15 cases on the scanner. The index and
the marker provider are IDE components and are **not** covered; see the gap
note below.

| | Behaviour | Verified |
|---|---|---|
| 2.1 | Icon beside `entity counter` jumps to `architecture rtl of counter` | [ ] |
| 2.2 | Icon beside `architecture rtl` jumps back to the entity | [ ] |
| 2.3 | Works when entity and architecture are in **different files** | [ ] |
| 2.4 | An entity with two architectures offers both | [ ] |
| 2.5 | `u1 : entity work.counter` jumps to the entity | [ ] |
| 2.6 | `u1 : counter port map (...)` jumps to the entity or component | [ ] |
| 2.7 | No icon on `end entity counter;` | [ ] |
| 2.8 | No icon on an ordinary signal or port declaration | [ ] |
| 2.9 | No icon on a labelled process (`regs : process`) | [ ] |
| 2.10 | Icons appear in a large project without a visible stall | [ ] |
| 2.11 | Adding a new architecture makes its icon appear without restart | [ ] |

**Known limits**
- Configurations are scanned but not linked in the gutter.
- An instantiation's library prefix is recorded but not used to disambiguate;
  two entities of the same name in different libraries both appear as targets.

---

## 3. Regression — must still hold

| | Behaviour | Verified |
|---|---|---|
| 3.1 | Plain Enter after a block header still inserts `begin`/`end` | [ ] |
| 3.2 | `port (` still auto-closes as `();` | [ ] |
| 3.3 | Keyword completion unchanged | [ ] |
| 3.4 | Diagnostics still debounce; cascade collapsing still works | [ ] |
| 3.5 | Indentation and dedent-on-type unchanged | [ ] |
| 3.6 | Vivado sync and run configuration still work | [ ] |
| 3.7 | Full test suite green in CI | [ ] |

---

## 4. Gap this branch does not close

The scanner and the statement logic are covered headlessly. The three IDE
components added here — the smart-enter processor, the project index and the
marker provider — are **not** tested, because the project has no
`BasePlatformTestCase` harness.

That is the same gap that produced every production bug so far: each one
compiled and passed every test. Items 1.x and 2.x above are therefore
**manual** verification, and the checkboxes are the only evidence they work.

Worth building the platform-test harness before the next feature branch.

---

## Merge gate

- [ ] Every box above ticked
- [ ] CI green on the branch head
- [ ] Reviewed by repo owner
- [ ] Merged by repo owner — **not by anyone else**
