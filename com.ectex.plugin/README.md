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
4. In the runtime workbench open `samples/sample.tex`. The PDF Preview view opens next to the editor
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
The view follows the active `.tex` editor (editors without a PDF of their own, e.g. `\input`-ed sub-files,
leave it unchanged).

## Notes
* PDF rendering uses the bundled Apache PDFBox 2.0.31 (`lib/`, Apache License 2.0).
* Sync works through the `.synctex.gz` file written by the build command; PDFs compiled elsewhere need `-synctex=1` too.
