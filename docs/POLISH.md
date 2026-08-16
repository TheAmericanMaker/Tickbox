# Visual polish backlog

The standard each item is judged against: Tickbox should feel like the notes app you'd
recommend to someone who wants Google Keep without the account. Every entry says what it
fixes and why it earns a place in a deliberately simple app. Ranked; work top to bottom.

## Done

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
- **Warning before a lossy checklist→note conversion**, and only when there is something to
  lose. Reported from real use: switching to note style to read a list, and losing the
  ticks by doing it.
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

1. **Decide what the item number is for.** After the row cleanup the remaining chrome on an
   unfocused row is the drag handle, the number and the checkbox. The number costs 24dp on
   every line — about 7% of the width on a Fold cover display — and on a shopping list it
   earns nothing; the owner named it as part of the crowding. Three ways out, in rising
   order of work:

   - Drop it. Simplest, and wrong for anyone who wants an ordered list.
   - Make it a sixth `ChecklistIconStyle` ("numbered"), reusing the picker that already
     exists in the editor. Fits the current model, and adding an enum value is the safe
     kind of backup-format change — every read is a tolerant `fromName(…) ?: CHECKBOX`.
   - A separate per-note toggle. More faithful, more surface.

   The middle option looks right, but it is a product decision rather than a layout one.
2. **A one-time hint for the indent gesture.** Help & about now documents it, but a page
   nobody opens is not discovery. The app already has the pattern — `ocrHintShown` in
   `UserPreferencesRepository` shows the OCR tip once and never again — so this is a second
   flag and a snackbar the first time a checklist with more than a couple of items is opened.
3. **48dp touch targets** for item delete (32dp) and the drag handle (measured 17×28dp on a
   Fold cover display). Half-size targets are the single most felt roughness on a real device.

   Much cheaper than it was. The blocker used to be width, and the indent buttons are now gone
   entirely — replaced by a gesture — while delete only shows on the focused row. The vertical
   axis was always free, since rows already measure ~48dp tall. **Decide on a device with a
   real list.**
4. **Completion micro-animation — half done.** `animateItem()` is on the checked rows, so an
   item animates *into* the checked section instead of teleporting. The unchecked half is
   missing: those rows live inside `ReorderableItem`, where `animateItem` and the drag
   gesture both want to own placement. Doing it properly means finding the combination the
   reorderable library supports, and re-running section K afterwards — drag-to-reorder is
   verified working today and is not worth regressing for a flourish.
5. **M3 top bar scroll behaviour.** `enterAlways` on the editor so the bar tucks away while
   a long checklist scrolls; consider `LargeTopAppBar` collapsing on the list.
6. **Swipe-to-delete reveal.** Icon plus the word "Delete", revealed progressively with
   swipe fraction, instead of a full-red flash at first pixel.
7. **M3 SearchBar.** Replace the OutlinedTextField with the proper search component:
   autofocus on open, a clear (×) button, keyboard search action.
8. **Haptics.** Subtle ticks on drag pick-up/drop and on checking an item. The app had
   haptics in Smart Toolkit's other tools; notes never got them.
9. **Real launcher icon.** Current art is a placeholder tick drawn in this repo. Needs an
   actual identity pass; keep the monochrome layer for themed icons.
10. **Colour label as accent, not wash.** Try the label as a leading edge bar or a dot
   beside the date instead of tinting the whole card — calmer list, label still legible.
   Decide on a device with real data; whole-card tint may win.
11. **Label name inline in the colour picker.** Selecting a colour in the editor shows
   only a check mark; echo the name ("Green") beside the row.
12. **Harmonise label colours with Material You.** The 10 fixed label colours can clash
    with a dynamic theme; blend them toward the scheme's primary the way `ColorUtils`
    harmonisation does. Low urgency, high subtlety.
13. **Title field ime polish.** Auto-capitalise the title field, "next" action moves into
    the body/first item.
14. **Editor colour/style pickers into a bottom sheet.** The collapsing header carries a
    lot of chrome; moving pickers behind a palette icon would calm the editor. Sketch
    first — it trades discoverability for calm.

## Explicitly not doing

Animated splash screens, onboarding carousels, custom fonts, and theme marketplaces.
Each adds surface without adding capability, and the app's identity is that it opens
instantly and gets out of the way.
