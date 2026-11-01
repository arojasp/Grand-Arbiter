# Redesign changelog (branch `feature/home-redesign`)

Running log of user-visible changes made during the redesign, one entry per step.
Plan: `claude/home-redesign-plan.md` in the Claude project.

When the redesign is released: copy the **Play draft** below into
`whatsnew/whatsnew-en-US` and `whatsnew/whatsnew-es-ES` (max 500 characters each, CI checks it)
together with the versionCode/versionName bump, as usual. The `whatsnew` files are not touched on
this branch, so it does not conflict with releases made from develop in the meantime.

## Play draft (keep it under 500 characters per language)

**en-US**

Redesign, part 1: a cleaner Home. The timer and the documents fit on one screen, and an «Updated» label marks what changed until you open it. High contrast mode now gives cards and panels clear edges, secondary text is easier to read, and the light theme and navigation bar are fixed.

**es-ES**

Rediseño, parte 1: un Inicio más limpio. El temporizador y los documentos caben en una pantalla, y una etiqueta «Updated» marca lo que ha cambiado hasta que lo abres. El modo de alto contraste ahora marca los bordes de tarjetas y paneles, los textos secundarios se leen mejor y se corrigen el tema claro y la barra de navegación.

## Entries

### Phase 0 — Fixes

| Commit | English | Español |
|---|---|---|
| `70b158a` | Light theme: the document badges on Home are visible again. | Tema claro: las etiquetas de los documentos en Inicio vuelven a verse. |
| `83fdf34` | Navigation bar: labels laid out correctly; screen readers no longer read each tab twice. | Barra de navegación: etiquetas bien colocadas; los lectores de pantalla ya no leen cada pestaña dos veces. |
| `c417b19`, `133b952` | Secondary text, hints and icons are easier to read across the app. | Los textos secundarios, sugerencias e iconos se leen mejor en toda la app. |
| `133b952` | High contrast mode now makes a visible difference: cards and panels get clear edges, and borders and dividers are drawn at full strength. | El modo de alto contraste ahora se nota: tarjetas y paneles tienen bordes claros, y bordes y separadores se dibujan con su color completo. |

### Phase 1 — Home

| Commit | English | Español |
|---|---|---|
| Home: document cards with the acronym as the title | Home: simpler document cards. The acronym (CR, IPG, MTR, JAR) is the title, with the full name and effective date below; no more descriptions. | Inicio: tarjetas de documentos más simples. El acrónimo (CR, IPG, MTR, JAR) es el título, con el nombre completo y la fecha de vigencia debajo; sin descripciones. |
| Home: «Updated» label until you open the section | Home: an «Updated» label marks a document or the set release notes when they change, until you open them. | Inicio: una etiqueta «Updated» marca un documento o las release notes de sets cuando cambian, hasta que los abres. |
| Home: documents as one grouped list | Home: the documents are one compact list, so the timer and all of them fit on one screen. | Inicio: los documentos forman una sola lista compacta, así el temporizador y todos ellos caben en una pantalla. |
| Home: compact judge call timer | Home: the judge call timer is compact and left-aligned (time and buttons on one row), with a line explaining what it is for. Buttons now read "Start" / "Pause". | Inicio: el temporizador de llamada a juez es compacto y alineado a la izquierda (tiempo y botones en una fila), con una línea que explica para qué sirve. Los botones dicen "Start" / "Pause". |
