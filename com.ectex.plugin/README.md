# EcTeX – LaTeX workspace for Eclipse

Bundle-SymbolicName: `com.ectex.plugin`. Opens `*.tex` files in a LaTeX editor with syntax highlighting; the
compiled PDF is shown in a separate **PDF Preview** view that opens to the right of the editor area.

## Install without PDE
The sibling projects `com.ectex.feature` and `com.ectex.updatesite` package the plug-in as a p2 update site
with a small website (install, usage, publishing). Rebuild with
`com.ectex.updatesite/build/build-site.bat` or `build-site.sh` (see the repository root README); install via Help > Install New Software (Add > Local or Archive).

## Run it from source
1. Use an Eclipse with PDE (e.g. "Eclipse IDE for RCP and RAP Developers" / Eclipse SDK).
2. File > Import > General > Existing Projects into Workspace > select this folder.
3. Right-click the project > Run As > Eclipse Application.
4. In the runtime workbench, import the sample projects: File > Import > General > Existing Projects into
   Workspace > select the `samples` folder at the root of this repository (`../samples`). It contains
   `ectex-demo` (one file) and `ectex-demo-multifile` (the same document split into files, for the main file
   feature, see below). Open `ectex-demo.tex` and build it. The PDF Preview view opens next to the editor
   (or use Window > Show View > Other... > EcTeX > PDF Preview). Drag the view anywhere you like.

Requirements on the machine: a LaTeX engine (`pdflatex`, `xelatex` or `lualatex`), a bibliography tool (`bibtex` or
`biber`) and `synctex` on the PATH (MiKTeX / TeX Live), or set their paths in Window > Preferences > EcTeX.

## Keys (M1 = Ctrl on Windows/Linux). The build commands and Ctrl+Space work in the editor and in the PDF view.

**All shortcuts are configurable:** Window > Preferences > General > Keys, filter by "EcTeX" (the EcTeX
preference page links there). Each command has a single binding that applies to both the editor and the
PDF view; messages in the status line always show the key currently bound.

Defaults:

| Key | Action |
|---|---|
| `Ctrl+F2` | Build PDF: save the project's files, run the LaTeX engine **once**, reload the PDF on success (output in the *EcTeX Build* console, errors as problem markers) |
| `Ctrl+F3` | Run Bibliography Tool: BibTeX or Biber, as chosen in the preferences (needs the `.aux` file from a previous LaTeX run) |
| `Ctrl+F5` | **Build All**: the sequence chosen in the preferences, stopping at the first error. With a bibliography: LaTeX, bibliography tool, LaTeX, LaTeX; without: LaTeX, LaTeX |
| `Ctrl+Space` in the editor | Forward search: scroll to and highlight the PDF location of the caret line |
| `Ctrl+Space` in the PDF view | Inverse search: jump to the source line at the mouse position (or last click) |
| `Ctrl+click` in the PDF view | Inverse search at the clicked position |
| `Ctrl+B` / `Ctrl+I` / `Ctrl+U` | `\textbf{}` / `\textit{}` / `\underline{}` – wraps the selection or puts the caret inside |
| `Ctrl+Shift+E` / `Ctrl+Shift+M` | `\emph{}` / `$ $` |

PDF view: wheel scrolls, `Ctrl`+wheel (or `+` / `-`) zooms, right-click for "Fit Width". On high-DPI (scaled) screens
pages are rendered at the screen's real resolution so text stays sharp. This is the experimental setting **Sharp text on
high-DPI screens** (Preferences > EcTeX, on by default). It uses several times more memory per page; if memory runs out,
EcTeX switches to normal rendering for the rest of the session.
The view follows the active `.tex` editor and shows the PDF of that editor's document (the project's main file
if one is set); an editor whose document has no PDF yet leaves the view unchanged.

## Building and the tools (Window > Preferences > EcTeX)
Everything is an explicit choice, nothing is detected automatically:

* **LaTeX engine:** `pdflatex` (default), `xelatex`, `lualatex`, or any command / full path you type, plus its arguments.
* **Bibliography tool:** `bibtex` (default) for `\bibliography{...}`, or `biber` for biblatex with `backend=biber`.
  If it does not match the document, the bibliography step fails and the console says what to check.
* **Build All runs:** *LaTeX, bibliography tool, LaTeX, LaTeX* (default) or *LaTeX, LaTeX* for a document without a
  bibliography (BibTeX reports an error on such a document, so the longer sequence stops there).

## LaTeX projects, the EcTeX menu and the LaTeX perspective
A **LaTeX project** is a project with the LaTeX nature. It is what gives a project a main file. Everything else
works without it: `.tex` files in any project open in the EcTeX editor and build on their own.

* **EcTeX menu** (menu bar, always visible; items are disabled when they do not apply): New LaTeX Project...,
  Open LaTeX Perspective, Build PDF, Build All, Run Bibliography Tool, Forward / Inverse Search, and the formatting
  commands.
* **File > New > Other > EcTeX > LaTeX Project** (or `EcTeX > New LaTeX Project...`): creates a LaTeX project with
  a starter main file (set as the project's main file) and optionally `references.bib`, then offers to switch to
  the LaTeX perspective.
* **Existing project:** right-click the project > Configure > **Convert to LaTeX Project** (also offered in the
  EcTeX submenu of a `.tex` file in a plain project). **Remove LaTeX Nature** (Configure menu or EcTeX submenu of
  the project) turns it back into a plain project and clears its main file.
* **LaTeX perspective** (Window > Perspective > Open Perspective > Other... > LaTeX, or `EcTeX > Open LaTeX
  Perspective`): Project Explorer on the left, editors in the middle, Problems and the *EcTeX Build* console below;
  the PDF Preview opens to the right of the editors with the first `.tex` file.

## Documents with several files (main file)
In a LaTeX project, right-click in the Project Explorer (or Navigator / Package Explorer), or in the editor's own
right-click menu, and use the **EcTeX** submenu. There is no default key.

| Right-click on | Command | Action |
|---|---|---|
| a `.tex` file that is not the main file (or in its editor) | `EcTeX > Set as Main File` | Make this file the main file of its project |
| the main file (or in its editor) | `EcTeX > Clear Main File` | Remove the main file of the project |
| a project that has a main file | `EcTeX > Clear Main File` | Remove the main file of the project |
| a `.tex` file in a plain project (or in its editor) | `EcTeX > Convert Project to LaTeX Project` | Make the project a LaTeX project first |

The main file is shown in the explorers as `ectex-demo-multifile.tex [main file]` (switch this off under
Preferences > General > Appearance > Label Decorations > "EcTeX main file").

With a main file set, in every `.tex` file of the project: Build PDF, Build All and the bibliography tool run on the main file (in its folder,
after saving the project's unsaved files), the PDF view shows the main file's PDF, forward search works from any
file, inverse search opens the file that produced the text, and LaTeX errors become markers on the file that
contains them. The setting is saved per project in `.settings/com.ectex.plugin.prefs`. There is one main file per
project: to build another document of the same project, set another main file or clear it. Main files only work
for files in a workspace project. Try it with the `ectex-demo-multifile` sample: right-click the project >
Configure > Convert to LaTeX Project, right-click `ectex-demo-multifile.tex` > EcTeX > Set as Main File, then
build and sync from any file in its `sections` folder.

## Notes
* PDF rendering uses the bundled Apache PDFBox 2.0.31 (`lib/`, Apache License 2.0).
* Sync works through the `.synctex.gz` file written by the build command; PDFs compiled elsewhere need `-synctex=1` too.
