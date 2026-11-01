# Redesign changelog (branch `feature/home-redesign`)

Running log of user-visible changes made during the redesign, one entry per step.
Plan: `claude/home-redesign-plan.md` in the Claude project.

When the redesign is released: copy the **Play draft** below into
`whatsnew/whatsnew-en-US` and `whatsnew/whatsnew-es-ES` (max 500 characters each, CI checks it)
together with the versionCode/versionName bump, as usual. The `whatsnew` files are not touched on
this branch, so it does not conflict with releases made from develop in the meantime.

## Play draft (keep it under 500 characters per language)

**en-US**

Redesign, part 1: high contrast mode now gives cards and panels clear edges, secondary text is easier to read, and the light theme and the navigation bar are fixed.

**es-ES**

Rediseño, parte 1: el modo de alto contraste ahora marca con claridad los bordes de tarjetas y paneles, los textos secundarios se leen mejor y se corrigen el tema claro y la barra de navegación.

## Entries

### Phase 0 — Fixes

| Commit | English | Español |
|---|---|---|
| `70b158a` | Light theme: the document badges on Home are visible again. | Tema claro: las etiquetas de los documentos en Inicio vuelven a verse. |
| `83fdf34` | Navigation bar: labels laid out correctly; screen readers no longer read each tab twice. | Barra de navegación: etiquetas bien colocadas; los lectores de pantalla ya no leen cada pestaña dos veces. |
| `c417b19`, `133b952` | Secondary text, hints and icons are easier to read across the app. | Los textos secundarios, sugerencias e iconos se leen mejor en toda la app. |
| `133b952` | High contrast mode now makes a visible difference: cards and panels get clear edges, and borders and dividers are drawn at full strength. | El modo de alto contraste ahora se nota: tarjetas y paneles tienen bordes claros, y bordes y separadores se dibujan con su color completo. |
