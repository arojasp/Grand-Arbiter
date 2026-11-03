# Redesign changelog (branch `feature/home-redesign`)

Running log of user-visible changes made during the redesign, one entry per step.
Plan: `claude/home-redesign-plan.md` in the Claude project.

When the redesign is released: copy the **Play draft** below into
`whatsnew/whatsnew-en-US` and `whatsnew/whatsnew-es-ES` (max 500 characters each, CI checks it)
together with the versionCode/versionName bump, as usual. The `whatsnew` files are not touched on
this branch, so it does not conflict with releases made from develop in the meantime.

## Play text (released in 1.2.0, versionCode 9)

Copied to `whatsnew/whatsnew-en-US` and `whatsnew/whatsnew-es-ES` with the version bump.

**en-US** (471 characters)

A redesign of the whole app. The rules reader, Release Notes, Banned & Restricted, Quick References and Tools are clearer, more compact lists. An «Updated» label marks changed documents until you open them. One consistent gold, bigger touch targets, and text that reads well in the light and high contrast themes. Quick References: long-press a rule to copy it. HJ Announcement: simpler REL choice and the MTR round times. Resetting the judge call timer now needs a hold.

**es-ES** (493 characters)

Rediseño de toda la app. El lector de reglas, las Release Notes, Banned & Restricted, Quick References y Tools son listas más claras y compactas. Una etiqueta «Updated» marca los documentos que cambian hasta que los abres. Un solo dorado, zonas táctiles más grandes y textos legibles en tema claro y alto contraste. Quick References: mantén pulsada una regla para copiarla. Anuncio del HJ: REL más simple y tiempos de ronda del MTR. Reiniciar el temporizador de juez requiere mantener pulsado.

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
| Home: timer reset needs a hold | Home: to reset the judge call timer, press and hold the button, so an accidental tap doesn't lose the time. | Inicio: para reiniciar el temporizador de llamada a juez hay que mantener pulsado el botón, así un toque accidental no borra el tiempo. |

### Phase 2 — Design system

| Commit | English | Español |
|---|---|---|
| Typography: Bold only for the top level | Calmer typography: bold is kept for the main titles, so what matters stands out (rule reader, Quick References, Card Search). | Tipografía más tranquila: la negrita se reserva para los títulos principales, así destaca lo importante (lector de reglas, Quick References, Card Search). |

### Phase 3 — Rest of the app

| Commit | English | Español |
|---|---|---|
| One chevron icon: ic_chevron_right everywhere; Blocks: Label and Tag from the theme's labelSmall | The same arrow on every list, and slightly larger tags (TOKEN, set codes). | La misma flecha en todas las listas y etiquetas algo más grandes (TOKEN, códigos de set). |
| Reader contents and search: one grouped list; Reader: glossary as one list, spacing and weights | Rules reader: the contents, search results and glossary are compact lists with the section numbers in a column, and IPG infractions read "2.1 Missed Trigger" under their chapter. | Lector de reglas: el índice, los resultados de búsqueda y el glosario son listas compactas con los números de sección en columna, y las infracciones del IPG se leen "2.1 Missed Trigger" bajo su capítulo. |
| Draft components from the theme; Draft format/setup/run: theme roles | Draft timer: steadier clocks and counters (fixed-width digits), calmer text, and the "End draft" link is easier to tap. | Temporizador de draft: relojes y contadores más estables (cifras de ancho fijo), textos más tranquilos y el enlace "End draft" es más fácil de pulsar. |
| Card detail: theme roles, rulings as one list, legal tag readable in light theme; Card search: theme roles | Card details: cleaner header, rulings in one list, easier-to-read Oracle text, and the "Legal" tag is readable in the light theme again. | Detalle de carta: cabecera más limpia, resoluciones en una sola lista, texto Oracle más legible y la etiqueta "Legal" vuelve a leerse en el tema claro. |
| Quick References list, editor and detail: theme roles, grouped lists, one card per rule | Quick References: each saved rule is one card with its subrules as rows, and a long press copies a rule (no more copy buttons on every line). Lighter list title and a tidier editor. | Quick References: cada regla guardada es una tarjeta con sus subreglas como filas, y una pulsación larga copia la regla (sin botones de copiar en cada línea). Título de la lista más ligero y editor más ordenado. |
| Copy: always confirm with a toast | Copying a rule always shows a "copied" message, on every phone. | Al copiar una regla siempre aparece un aviso de "copiado", en cualquier móvil. |
| Release Notes list, card and viewer; set notes as one grouped list | Release Notes: a set's sections are one list, like the rules contents, with each open section and its cards inside it. Bigger titles and tidier spacing. | Release Notes: las secciones de un set forman una sola lista, como el índice de las reglas, con cada sección abierta y sus cartas dentro. Títulos más grandes y espaciado más ordenado. |
| Deck Counter: theme roles, readable "OK" in light theme, larger touch targets | Deck Counter: the green "OK" status is readable in the light theme, the format chips are easier to tap, and the numbers no longer shift as they change. | Contador de mazos: el estado verde "OK" se lee bien en el tema claro, los chips de formato son más fáciles de pulsar y los números ya no bailan al cambiar. |
| Tools: two grouped lists, tournament tools marked with gold icons | Tools: the tools are two compact lists, like the Home documents, and everything fits on one screen. The tournament tools stand out with gold icons. | Herramientas: las herramientas forman dos listas compactas, como los documentos del Inicio, y todo cabe en una pantalla. Las herramientas de torneo destacan con iconos dorados. |
| Banned lists: one grouped list, search matches without a side stripe, the app's search field | Banned & Restricted: the formats are one list with each open format's cards inside it, search matches are easier to spot, and the search field is the same as in the rules reader. | Baneadas y restringidas: los formatos forman una sola lista con las cartas de cada formato abierto dentro, las coincidencias de búsqueda se ven mejor y el buscador es el mismo que el del lector de reglas. |
| Settings: theme roles, section labels like the rest of the app, readable Fan Content notice | Settings: calmer section headings, an easier-to-tap text size selector, and the Wizards Fan Content notice is fully legible. | Ajustes: encabezados de sección más discretos, el selector de tamaño de texto es más fácil de pulsar y el aviso de contenido de fans de Wizards se lee por completo. |
| HJ Announcement: checklist in normal case, reminders as one list, larger touch targets | Head Judge Announcement: an easier-to-read checklist with bigger boxes, REL and round time chips that are easier to tap, and the reminders as one list. | Anuncio del Juez Principal: lista de comprobación más legible con casillas más grandes, chips de REL y duración de ronda más fáciles de pulsar y los recordatorios en una sola lista. |
| HJ Announcement: REL as Regular or Competitive/Professional, round time from the MTR | Head Judge Announcement: the REL choice is Regular or Competitive / Professional (they share the tardiness rule), and the round time shows what the MTR asks for instead of a selector that did nothing. | Anuncio del Juez Principal: el REL se elige entre Regular y Competitive / Professional (comparten la regla de tardanza), y la duración de ronda muestra lo que pide el MTR en lugar de un selector que no hacía nada. |
| HJ Announcement: venue info as a reminder, not a note field | Head Judge Announcement: "Venue info" is now a reminder of what to mention (restrooms, water, food), not a field to fill in. | Anuncio del Juez Principal: "Venue info" es ahora un recordatorio de qué mencionar (baños, agua, comida), no un campo que rellenar. |
| One gold: GoldDeep unified with the theme's primary | One gold across the app: buttons, chips, switches and tags use the theme's gold, so they follow the light and high-contrast themes (the Settings title was hard to read in the light theme). | Un solo dorado en toda la app: botones, chips, interruptores y etiquetas usan el dorado del tema, así siguen al tema claro y al de alto contraste (el título de Ajustes se leía mal en el tema claro). |
| Titles: tabs centered, every other screen left-aligned, tab titles one size; Home settings button named for TalkBack | Consistent titles: the three main tabs keep a centered title of the same size, and every other screen has its title on the left, next to the back arrow. TalkBack now names the Settings button on Home. | Títulos coherentes: las tres pestañas principales mantienen el título centrado y del mismo tamaño, y el resto de pantallas lo tienen a la izquierda, junto a la flecha atrás. TalkBack ya nombra el botón de Ajustes del Inicio. |

### Phase 4 — Palette "Tinta" (branch `feature/palette-tinta`)

| Commit | English | Español |
|---|---|---|
| Penalties: own severity scale; Palette Tinta; Tinta: cues that relied on gold | New black-and-white look: the interface is neutral grey, and color is kept for what means something in Magic — penalties (now a clear scale from No Penalty to Disqualification), legality and mana. Glossary links stay visible, an active Card Search filter is easier to spot, and the «Updated» label stands out. | Nuevo aspecto en blanco y negro: la interfaz es gris neutro y el color se reserva para lo que significa algo en Magic — penalizaciones (ahora una escala clara de No Penalty a Disqualification), legalidad y maná. Los enlaces del glosario siguen viéndose, un filtro activo en Card Search se ve mejor y la etiqueta «Updated» destaca. |
