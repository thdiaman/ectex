# EcTeX

Tex editor and builder plugin for the Eclipse IDE.

- LaTeX editor for `.tex` files with syntax highlighting
- **PDF Preview** view next to the editor (zoom, scroll, highlight of the synced spot)
- `Ctrl+F2` runs `pdflatex`, `Ctrl+F3` runs `bibtex`
- `Ctrl+Space` / `Ctrl+click` jump between source and PDF (SyncTeX forward and inverse search)
- Productivity shortcuts such as `Ctrl+B` for `\textbf{}`; every shortcut can be changed in
  Preferences > General > Keys (filter "EcTeX")

## Install

In Eclipse: **Help > Install New Software… > Add…** and enter

```
https://thdiaman.github.io/ectex/
```

Requirements: Eclipse on Java 17 or newer, and a TeX distribution (MiKTeX or TeX Live) with `pdflatex`,
`bibtex` and `synctex` on the `PATH`. Install, usage and troubleshooting details are on the project site:
<https://thdiaman.github.io/ectex/>. An offline copy of the update site (`ectex-updatesite-<version>.zip`)
is attached to each GitHub release (Add… > Archive…).

The content is not signed, so Eclipse shows an "unsigned content" warning on install.

## Repository layout

| Folder | Contents |
|---|---|
| `com.ectex.plugin/` | The Eclipse plug-in (Java sources, `plugin.xml`, bundled PDFBox in `lib/`). See its [README](com.ectex.plugin/README.md) |
| `com.ectex.feature/` | Feature that wraps the plug-in for installation |
| `com.ectex.updatesite/` | The p2 update site (generated files) and the project website (`index.html`, `usage.html`, `publish.html`) |
| `.github/workflows/pages.yml` | Publishes `com.ectex.updatesite/` with GitHub Pages |

## Develop

1. Eclipse with PDE (e.g. the Eclipse SDK): File > Import > Existing Projects into Workspace > select this folder.
2. Right-click `com.ectex.plugin` > Run As > Eclipse Application.

## Release a new version

1. Change the version in `com.ectex.plugin/META-INF/MANIFEST.MF` (`Bundle-Version`) and in
   `com.ectex.feature/feature.xml` (both `x.y.z.qualifier`, identical).
2. Run the build script in `com.ectex.updatesite/build/` (needs a JDK 17+ on the `PATH` and an Eclipse that
   includes the p2 tools, e.g. an Eclipse SDK):
   - Windows: `build-site.bat --eclipse-home "C:\Program Files\eclipsesdk"`
   - Linux/macOS: `sh build-site.sh --eclipse-home /path/to/eclipse`

   Instead of `--eclipse-home` you can set the `ECLIPSE_HOME` environment variable. Both scripts only start
   `BuildSite.java`, which does the work. It rebuilds the p2 repository in `com.ectex.updatesite/` (older
   versions are kept; `--clean` starts from an empty repository) and writes
   `com.ectex.updatesite/dist/ectex-updatesite-<version>.zip`.
3. Commit and push the changed plug-in, feature and `com.ectex.updatesite/` files. The workflow publishes the site.
4. Attach the zip from `dist/` to a GitHub release.

## License

Apache License 2.0, see [LICENSE](LICENSE). EcTeX bundles Apache PDFBox, FontBox and Commons Logging
(also Apache License 2.0) in `com.ectex.plugin/lib/`.
