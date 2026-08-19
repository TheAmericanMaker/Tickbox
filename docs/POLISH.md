# Visual polish backlog

The standard each item is judged against: Tickbox should feel like the notes app you'd
recommend to someone who wants Google Keep without the account. Every entry says what it
fixes and why it earns a place in a deliberately simple app. Ranked; work top to bottom.

## Done

- **Text notes get out of the way too** (#28). The header collapse was checklist-only by
  construction — `showFullHeader` short-circuited on note type, and the signal behind it came from
  a `LazyColumn` a text note does not have. Focus is the text-note equivalent: once the body has
  focus the chrome folds to a title row, and tapping that row releases focus and brings it back.
  Attach/Take photo moved inside the header so they collapse with everything else.
- **Borderless title and body.** Both were `OutlinedTextField` — visible outline plus floating
  label, which reads as a form to fill in rather than a page to write on. Now `BasicTextField`
  with placeholders, matching the checklist rows. The title field is shared, so the checklist
  editor gains from it as well.
- **Checklist suggestions removed** (#27). A hardcoded keyword map produced generic office filler
  that was never useful, while taking roughly a third of the editor and never scrolling out of the
  way (#26, resolved by this). Dictation already beats tapping a chip and works for words no map
  contains.
- **Haptics.** A tick when an item is checked and one per row crossed while dragging, joining
  the one the indent gesture already had.
- **Colour label names itself.** A tick on a swatch says one is chosen but not which, and the
  swatches scroll; the name now appears under the row when a colour is set.
- **Title field IME.** Sentence capitalisation, and Next moves into the note — the first item
  on a checklist, the body on a note — rather than at whatever sits geometrically below,
  which was the dictation button.
- **Smaller remove button on image thumbnails.** It was drawing a 42dp disc that covered 52%
  of the 80dp thumbnail and spilled past its edges — `IconButton` enforces its own minimum
  interactive size and paints the background at *that* size, so the requested 20dp was
  ignored. Drawn explicitly now: 22dp visible, 48dp touch target.
- **Swipe-to-delete reveals progressively.** The red deepens with the swipe and the word
  "Delete" joins the icon only past 40%, instead of the card flashing full red at the first
  pixel. A gesture you can back out of should look like one.
- **Search behaves like a search field.** Opens focused with the keyboard up, carries a clear
  button while there is text, and the Search key dismisses the keyboard — results are live as
  you type, so that is all it has left to do.
- **Item numbers dropped.** They renumbered as you used the list: the count ran over the
  *unchecked* items, so ticking one shifted every number below it — measured, ticking item 2
  moved item 3 to 2. Not identifiers, a counter that moves while you shop. Text went to 59%
  of the row width, from 39% before this pass.
- **One-time hint for the indent gesture.** A snackbar the first time a checklist of four or
  more items is opened, using the same stored-flag pattern as the OCR tip.
- **Help & about screen**, from the list overflow. One screen rather than two entries: the
  same page answers "how do I indent" and "where is the source". The how-to half is
  load-bearing rather than decorative — indent-by-drag and reorder-by-grip are gestures with
  no visible affordance, and this is the only place either is written down for the person
  using the app. The about half carries version, licence, source and author links, and the
  no-network claim.
- **Readable checklist rows.** Item text wraps instead of scrolling, indent moved to a gesture
  on the tick box, and delete shows only on the row being edited. The field used to be `singleLine`, which scrolls to
  keep the caret in view — so long items displayed their *tail*, and a list read as
  "todos completed" / "via run_bench.sh" with the start of every line off the left edge.
  Text went from 39% of the row width to 57%.
- **Checklist→note round trip keeps indentation and ticks.** Indentation is written as two
  leading spaces and parsed back; ticks are remembered and restored if the body comes back
  unedited. The blocking confirmation this replaced is gone — the conversion is no longer
  destructive at the moment it happens, and a dialog on a lossless action only teaches people
  to dismiss dialogs. A snackbar states the rule instead, and only when something is ticked.
  Pasting a markdown checklist in from another app now works too.
- **Appearance setting.** Light / Dark / System, in the list screen's overflow above
  Export/Import. The preference was stored and applied from the start but nothing ever
  wrote it, so the setting existed with no way to reach it. Selection applies live with
  the dialog open — the subject of the dialog is what the app looks like.
- **Collapsible checked section.** The "N checked items" header folds the section away,
  with "Uncheck all" and "Delete checked" in a new editor overflow that appears only when
  something is checked. Unchecking preserves positions, so a weekly list comes back in the
  order it was built.
- **Snackbar clear of the FAB.** Undo used to sit underneath the + button.
- **FAB menu.** The + button opens a two-item menu (New checklist / New note). It used to
  create whichever type matched the active filter chip — the same button silently did
  different things depending on invisible state.
- **Checklist progress on list cards.** "3 of 8 done" plus a progress bar, instead of the
  word "Checklist". The list is where you decide which list needs attention; now it says so.
  Fully-done lists read "All N done".
- **Relative dates.** "Today" / "Yesterday" instead of a calendar date for recent notes,
  which in practice is most of the visible list.
- **Item placement animation.** Pinning, deleting, and reordering morph the list instead of
  snapping it (`Modifier.animateItem()`).
- **Drag feedback.** The row being dragged lifts onto a highlighted surface.
- **Distinct empty states.** Empty library, empty search, and empty filter each say
  something different (earlier pass).
- **Dark cold-launch.** `values-night` theme, so no white flash (earlier pass).

## Next up (ranked)

1. **48dp touch targets** for item delete (32dp) and the drag handle (measured 17×28dp on a
   Fold cover display). Half-size targets are the single most felt roughness on a real device.

   Much cheaper than it was. The blocker used to be width, and the indent buttons are now gone
   entirely — replaced by a gesture — while delete only shows on the focused row. The vertical
   axis was always free, since rows already measure ~48dp tall. **Decide on a device with a
   real list.**
2. **Completion micro-animation — half done.** `animateItem()` is on the checked rows, so an
   item animates *into* the checked section instead of teleporting. The unchecked half is
   missing: those rows live inside `ReorderableItem`, where `animateItem` and the drag
   gesture both want to own placement. Doing it properly means finding the combination the
   reorderable library supports, and re-running section K afterwards — drag-to-reorder is
   verified working today and is not worth regressing for a flourish.
3. **M3 top bar scroll behaviour.** `enterAlways` on the editor so the bar tucks away while
   a long checklist scrolls; consider `LargeTopAppBar` collapsing on the list.
4. **Real launcher icon.** Current art is a placeholder tick drawn in this repo. Needs an
   actual identity pass; keep the monochrome layer for themed icons.
5. **Colour label as accent, not wash.** Try the label as a leading edge bar or a dot
   beside the date instead of tinting the whole card — calmer list, label still legible.
   Decide on a device with real data; whole-card tint may win.
6. **Harmonise label colours with Material You.** The 10 fixed label colours can clash
    with a dynamic theme; blend them toward the scheme's primary the way `ColorUtils`
    harmonisation does. Low urgency, high subtlety.
7. **Editor colour/style pickers into a bottom sheet.** The collapsing header carries a
    lot of chrome; moving pickers behind a palette icon would calm the editor. Sketch
    first — it trades discoverability for calm.

## Explicitly not doing

Animated splash screens, onboarding carousels, custom fonts, and theme marketplaces.
Each adds surface without adding capability, and the app's identity is that it opens
instantly and gets out of the way.
