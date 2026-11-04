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

Requirements on the machine: `pdflatex`, `bibtex` and `synctex` on the PATH (MiKTeX / TeX Live), or set their
paths in Window > Preferences > EcTeX (use `biber` as the "bibtex command" for biblatex projects).

## Keys (M1 = Ctrl on Windows/Linux). Build, BibTeX and Ctrl+Space work in the editor and in the PDF view.

**All shortcuts are configurable:** Window > Preferences > General > Keys, filter by "EcTeX" (the EcTeX
preference page links there). Each command has a single binding that applies to both the editor and the
PDF view; messages in the status line always show the key currently bound.

Defaults:

| Key | Action |
|---|---|
| `Ctrl+F2` | Build PDF: save, run `pdflatex -synctex=1`, reload the PDF on success (output in the *EcTeX Build* console, errors as problem markers) |
| `Ctrl+F3` | Run BibTeX on the document (needs the `.aux` file from a previous build; then build twice to resolve citations) |
| `Ctrl+Space` in the editor | Forward search: scroll to and highlight the PDF location of the caret line |
| `Ctrl+Space` in the PDF view | Inverse search: jump to the source line at the mouse position (or last click) |
| `Ctrl+click` in the PDF view | Inverse search at the clicked position |
| `Ctrl+B` / `Ctrl+I` / `Ctrl+U` | `\textbf{}` / `\textit{}` / `\underline{}` – wraps the selection or puts the caret inside |
| `Ctrl+Shift+E` / `Ctrl+Shift+M` | `\emph{}` / `$ $` |

PDF view: wheel scrolls, `Ctrl`+wheel (or `+` / `-`) zooms, right-click for "Fit Width".
The view follows the active `.tex` editor and shows the PDF of that editor's document (the project's main file
if one is set); an editor whose document has no PDF yet leaves the view unchanged.

## Documents with several files (main file)
Right-click in the Project Explorer (or Navigator / Package Explorer), or in the editor's own right-click menu,
and use the **EcTeX** submenu. There is no menu-bar entry and no default key.

| Right-click on | Command | Action |
|---|---|---|
| a `.tex` file that is not the main file (or in its editor) | `EcTeX > Set as Main File` | Make this file the main file of its project |
| the main file (or in its editor) | `EcTeX > Clear Main File` | Remove the main file of the project |
| a project that has a main file | `EcTeX > Clear Main File` | Remove the main file of the project |

The main file is shown in the explorers as `ectex-demo-multifile.tex [main file]` (switch this off under
Preferences > General > Appearance > Label Decorations > "EcTeX main file").

With a main file set, in every `.tex` file of the project: build and BibTeX run on the main file (in its folder,
after saving the project's unsaved files), the PDF view shows the main file's PDF, forward search works from any
file, inverse search opens the file that produced the text, and LaTeX errors become markers on the file that
contains them. The setting is saved per project in `.settings/com.ectex.plugin.prefs`. There is one main file per
project: to build another document of the same project, set another main file or clear it. Main files only work
for files in a workspace project. Try it with the `ectex-demo-multifile` sample: set
`ectex-demo-multifile.tex` as the main file, then build and sync from any file in its `sections` folder.

## Notes
* PDF rendering uses the bundled Apache PDFBox 2.0.31 (`lib/`, Apache License 2.0).
* Sync works through the `.synctex.gz` file written by the build command; PDFs compiled elsewhere need `-synctex=1` too.
