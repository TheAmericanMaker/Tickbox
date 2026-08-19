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

1. **48dp touch targets** for indent/outdent (24dp today), item delete (32dp), and the drag
   handle (measured 17×28dp on a Fold cover display). Half-size targets are the single most
   felt roughness on a real device.

   Not a matter of removing the `.size()` overrides: `IconButton` already reserves 48dp
   until something shrinks it, so restoring the default would put handle + outdent + indent
   + checkbox at 192dp of controls on a ~344dp-wide screen and squeeze the text field to
   nothing. The vertical axis is free — rows already measure ~48dp tall — so height costs
   nothing and width is the whole decision. It probably needs the indent controls to stop
   being permanently visible on every row, which is a design change rather than a sizing
   one. **Decide on a device with a real list.**
2. **Completion micro-animation — half done.** `animateItem()` is on the checked rows, so an
   item animates *into* the checked section instead of teleporting. The unchecked half is
   missing: those rows live inside `ReorderableItem`, where `animateItem` and the drag
   gesture both want to own placement. Doing it properly means finding the combination the
   reorderable library supports, and re-running section K afterwards — drag-to-reorder is
   verified working today and is not worth regressing for a flourish.
3. **M3 top bar scroll behaviour.** `enterAlways` on the editor so the bar tucks away while
   a long checklist scrolls; consider `LargeTopAppBar` collapsing on the list.
4. **Swipe-to-delete reveal.** Icon plus the word "Delete", revealed progressively with
   swipe fraction, instead of a full-red flash at first pixel.
5. **M3 SearchBar.** Replace the OutlinedTextField with the proper search component:
   autofocus on open, a clear (×) button, keyboard search action.
6. **Haptics.** Subtle ticks on drag pick-up/drop and on checking an item. The app had
   haptics in Smart Toolkit's other tools; notes never got them.
7. **Real launcher icon.** Current art is a placeholder tick drawn in this repo. Needs an
   actual identity pass; keep the monochrome layer for themed icons.
8. **Colour label as accent, not wash.** Try the label as a leading edge bar or a dot
   beside the date instead of tinting the whole card — calmer list, label still legible.
   Decide on a device with real data; whole-card tint may win.
9. **Label name inline in the colour picker.** Selecting a colour in the editor shows
   only a check mark; echo the name ("Green") beside the row.
10. **Harmonise label colours with Material You.** The 10 fixed label colours can clash
    with a dynamic theme; blend them toward the scheme's primary the way `ColorUtils`
    harmonisation does. Low urgency, high subtlety.
11. **Title field ime polish.** Auto-capitalise the title field, "next" action moves into
    the body/first item.
12. **Editor colour/style pickers into a bottom sheet.** The collapsing header carries a
    lot of chrome; moving pickers behind a palette icon would calm the editor. Sketch
    first — it trades discoverability for calm.

## Explicitly not doing

Animated splash screens, onboarding carousels, custom fonts, and theme marketplaces.
Each adds surface without adding capability, and the app's identity is that it opens
instantly and gets out of the way.
