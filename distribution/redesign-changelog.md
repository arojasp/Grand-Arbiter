# Redesign changelog (branch `feature/home-redesign`)

Running log of user-visible changes made during the redesign, one entry per step.
Plan: `claude/home-redesign-plan.md` in the Claude project.

When the redesign is released: copy the **Play draft** below into
`whatsnew/whatsnew-en-US` (max 500 characters, CI checks it; Spanish dropped from 1.5, the app is English only)
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

Released in **1.3.0 (versionCode 10)**. Play text, copied to `whatsnew/`:

**en-US** (431 characters)

A new look made for reading: black and white with softer contrast and warm paper tones, larger rule text, and a dotted underline on glossary terms. New app icon. In Settings you can now pick a light or dark theme or follow the phone, which is the default. Color stays only where it means something, such as penalties, legality and mana. Fixes: less empty space above screen titles, and no dark flash at launch with the light theme.

**es-ES** (478 characters)

Un aspecto nuevo pensado para leer: blanco y negro con un contraste más suave y tonos cálidos de papel, texto de reglas más grande y subrayado punteado en los términos del glosario. Icono nuevo. En Ajustes ya puedes elegir tema claro u oscuro, o seguir al móvil, que es la opción por defecto. El color queda solo donde significa algo, como penalizaciones, legalidad y maná. Correcciones: menos espacio vacío sobre los títulos y ningún destello oscuro al abrir con el tema claro.

| Commit | English | Español |
|---|---|---|
| Penalties: own severity scale; Palette Tinta; Tinta: cues that relied on gold; Glossary terms: dotted underline | New black-and-white look: the interface is neutral grey, and color is kept for what means something in Magic — penalties (now a clear scale from No Penalty to Disqualification), legality and mana. Glossary terms now have a dotted underline (rule links keep the solid one), an active Card Search filter is easier to spot, and the «Updated» label stands out. | Nuevo aspecto en blanco y negro: la interfaz es gris neutro y el color se reserva para lo que significa algo en Magic — penalizaciones (ahora una escala clara de No Penalty a Disqualification), legalidad y maná. Los términos del glosario llevan ahora subrayado punteado (los enlaces a reglas mantienen el continuo), un filtro activo en Card Search se ve mejor y la etiqueta «Updated» destaca. |
| Settings: choose dark, light or system theme | Settings: choose a dark or light theme, or follow the system setting (under Text size). | Ajustes: elige tema oscuro o claro, o que siga la configuración del sistema (debajo de Tamaño de texto). |
| Splash: Tinta background; Splash follows the in-app theme; App icon: H2 tapped card with the gavel cut out | New app icon: a tapped card with a gavel cut out of it, also used by the themed icon on Android 13+. The launch screen uses it too, dark or light to match the theme chosen in Settings (Android 12+). | Nuevo icono de la app: una carta girada con un mazo recortado, también en el icono temático de Android 13+. La pantalla de inicio lo usa, oscura o clara según el tema elegido en Ajustes (Android 12+). |
| Tinta softened for reading; rule text 16 sp | Easier reading: softer contrast and warm paper tones in both light and dark themes, and larger text (16 sp) in the rules reader, glossary and release notes. High contrast mode stays pure black and white. | Lectura más cómoda: contraste más suave y tonos cálidos de papel en los temas claro y oscuro, y texto más grande (16 sp) en el lector de reglas, el glosario y las release notes. El modo de alto contraste sigue en blanco y negro puros. |
| Status bar space counted once | Less empty space between the status bar and screen titles (it was added twice), and at the bottom of screens without the navigation bar. | Menos espacio vacío entre la barra de estado y el título de las pantallas (se sumaba dos veces), y abajo en las pantallas sin barra de navegación. |
| No dark flash at launch | The app opens directly in the chosen theme: no more dark screen for a moment when the light theme is selected. | La app se abre directamente con el tema elegido: ya no aparece un instante en oscuro con el tema claro seleccionado. |
| Theme follows the system by default | Until you pick a theme in Settings, the app follows the phone's dark or light mode (it used to always start dark), so the first launch screen and the app match. | Hasta que elijas un tema en Ajustes, la app sigue el modo oscuro o claro del móvil (antes arrancaba siempre en oscuro), así que la pantalla de inicio y la app coinciden desde el primer arranque. |

### Draft times (develop)

Judge feedback from casual store drafts. Plan: `claude/draft-times-plan.md` in the Claude project.
Released in **1.4.0 (versionCode 11)**. Play text, copied to `whatsnew/`:

**en-US** (425 characters)

Draft Timer: your own times. For casual drafts, the new Times screen lets you change every pick time (one list per pack size), the reviews and deck construction, all at once by ±5 s or one by one, up to 999. Or turn timed picks off: picks and reviews count up and you move on with one tap. "Reset to MTR" brings back Appendix B. Each format remembers its times, and the setup keeps your changes even before you start a draft.

**es-ES** (445 characters)

Draft Timer: tus propios tiempos. Para drafts casuales, la nueva pantalla Times permite cambiar cada pick (una lista por tamaño de sobre), las revisiones y la construcción, todo a la vez con ±5 s o uno a uno, hasta 999. O quita el tiempo de los picks: picks y revisiones cuentan hacia arriba y avanzas con un toque. "Reset to MTR" vuelve al Apéndice B. Cada formato recuerda sus tiempos y el setup guarda tus cambios aunque no empieces el draft.

| Commit | English | Español |
|---|---|---|
| Shared setting controls; type a value | Deck construction shows its unit ("25 min") and goes up to 999; tap the value to type it. | La construcción muestra su unidad («25 min») y llega hasta 999; toca el valor para escribirlo. |
| Times screen; Times section in the setup; tappable edge to edge; "Duration" | Booster and Two-Headed Giant: a Times section in the setup says whether the draft uses the MTR's times, custom ones or untimed picks, and opens the new Times screen: timed picks on or off, all picks ±5 s, one list per pack size with every pick editable, reviews and deck construction, and "Reset to MTR". | Booster y Two-Headed Giant: una sección Times en el setup indica si el draft usa los tiempos del MTR, unos propios o picks sin tiempo, y abre la nueva pantalla Times: picks con o sin tiempo, todos los picks ±5 s, una lista por tamaño de sobre con cada pick editable, revisiones, construcción y «Reset to MTR». |
| Running an untimed draft | Untimed picks and reviews show the time spent and move on with one tap ("Next pick", "Start pack 2", "Start deck construction"); going back still needs a hold. Deck construction keeps its countdown. | Los picks y revisiones sin tiempo muestran el tiempo que llevan y avanzan con un toque («Next pick», «Start pack 2», «Start deck construction»); volver atrás sigue pidiendo mantener pulsado. La construcción mantiene su cuenta atrás. |
| The setup keeps its changes without starting a draft | The setup remembers each format's packs and cards per pack as soon as you change them, even if you go back without starting a draft. | El setup recuerda los sobres y las cartas por sobre de cada formato en cuanto los cambias, aunque vuelvas atrás sin empezar el draft. |


### Judge feedback (branch `feature/judge-feedback`)

Feedback from judges after 1.4. Plan: `claude/judge-feedback-plan.md` in the Claude project.
Release **1.5.0001 (versionCode 13)**. From this release the app's texts and notes are English only
(one `whatsnew-en-US`; the rows below have no Spanish column). Play text, copied to `whatsnew/`:

**en-US** (464 characters)

Card Search understands Scryfall syntax (t:goblin o:haste…), and the new Advanced search builds it for you. Searches the app can't do offline go to Scryfall; turn off "Search Scryfall automatically" in Settings to send them only when you ask. Results show the mana cost. New in Banned & Restricted: your own lists for store or community formats, with each card banned, restricted or worth points. Paste a list as text or share it. Deck Counter now adds any number.

| Commit | English |
|---|---|
| `c721bce` | Screens change with a short fade instead of the slow default animation. |
| `93159b7` | Card Search: each result shows its mana cost on the right. |
| `44f00ec`, `8d4c404`, `57bc493`, `0f333c4`, `5b6d1aa` | Card Search: the field takes Scryfall syntax (t:, o:, c:, mv and more), searched offline on the installed cards. The field waits until the cards are ready. |
| `1eb23f5`, `f47cefa` | Card Search: what the offline search can't do (prints by set and number, and other Scryfall-only filters) is searched on Scryfall, in the same list. |
| `d84fec9`, `add6567`, `3e767ca`, `3efa860` | Card Search: a new Advanced search form (name, rules text, type, colors, mana value, power, toughness, set and number) writes the syntax into the search field. Leaving it with changes asks first. |
| `9a248f3`, `eda2734` | Settings › Card search: "Search Scryfall automatically". Off: searches that need Scryfall wait for a "Search on Scryfall" button. |
| `26754e4`, `4094d01` | Deck Counter: add any number ("Other number…", up to 999) and repeat it with "+N again". |
| `0f1d7b0`, `c3c8997`, `a0a0009`, `1201028` | Banned & Restricted: custom lists for your own formats, with each card banned, restricted or worth points. Create and edit them from the Banned & Restricted screen. |
| `1510209`, `c248542`, `7867361` | Custom lists: paste a list as text ("3 Sol Ring", "Black Lotus restricted"…) and share a list in the same format. |
| `e5805c8` | One "Discard changes?" dialog everywhere; no icons on the online search buttons. |
| `ba63a42` | Section labels in sentence case instead of small capitals (Settings, card details, Banned, Draft Timer, HJ Announcement); in the Draft Timer setup and Times each label now sits above its card. Card details: "Pauper Commander" was shown as a second "Pauper" in the legality list. Banned: the loading message no longer touches the screen edges. |

### UI debt (branch `feature/ui-debt`)

Plan: `claude/ui-debt-plan.md` in the Claude project. Not released yet.

| Commit | English |
|---|---|
| Card search: readable end-of-results caption | Card Search: the card count and data date on the empty search, and "End of results", are readable now (they were drawn at 20 % opacity). |
| Quick References: the editor works like the other editors | Quick References: the editor works like Advanced search and custom lists — a back arrow, Save at the bottom (above the keyboard) and "Delete reference" in the ⋮ menu instead of at the end of the page. |
| Status tags: one color for Restricted, a shape that does not look tappable | Banned & Restricted, custom lists and card legality: Restricted is amber on every screen (it was solid black in the lists, louder than Banned). The draft labels (Pack 1 of 3, Pass left) and Home's "Updated" are square-cornered tags instead of pills, so they no longer look like buttons. Ruling dates and Release Notes set codes are plain text. |
| One control for choosing one option | Deck Counter (format, Main deck / Sideboard) and HJ Announcement (REL) use the same segmented control as the rest of the app. When an option doesn't fit on one line (long names, large text sizes) the options stack one per row: at large text sizes "Commander" no longer breaks letter by letter. Screen readers now hear which option is selected. |

### Judge feedback, second round (branch `feature/judge-feedback-2`)

From Donald Sutherland and Andrew Anastasi on the judges' forum. Not released yet.

| Commit | English |
|---|---|
| Card detail: rulings start collapsed | Card details: rulings start collapsed, so you can show a player the Oracle text without them. Tap "Show 5 rulings" to open them; the count is in that row ("5 Total" is gone). |
| Card search: never "0 cards found" before searching | Card Search: "0 cards found" only appears once the search you typed has really been run. Before, it could show for a moment while you typed, or while the card data was getting ready, as if the card didn't exist. If an answer takes longer than usual, it says "Searching…" instead of a blank screen. |
| Card detail: your custom lists | Card details: a "Custom lists" block under Legality shows what your own lists say about the card (banned, restricted or its points). Only lists that have the card appear. |
