# Relic exclusion list (v1): settings tab with relic grid + both exclusion behaviors

Add a user-configurable list of excluded relics. Excluded relics are subject to two
behaviors, both driven by a single exclusion list:

1. **Never offered as an extra choice.** When Picky Relics generates additional relic
   options, excluded relics are filtered out of the candidate draws.
2. **No extra choices when awarded.** When the original reward itself is an excluded
   relic, Picky Relics leaves it alone — no linked group, no alternatives. The relic is
   awarded exactly as vanilla would.

Exclusion does **not** remove a relic from the game. Excluded relics still drop as
normal vanilla rewards, appear in shops, chests, and events — they just don't
participate in Picky Relics' multi-choice treatment in either direction.

## Context

A user requested a way to exclude specific relics from the picky-relic treatment.
Motivations include: modded relics with special on-obtain logic that misbehave when
offered as linked alternatives, and players who don't want the ability to dodge or
fish for specific relics.

Requirements from the request:
- A dedicated settings page
- A UI for browsing relics
- A way to toggle which ones are excluded
- A way to easily see the excluded ones and toggle them back on
- Picky Relics skips providing options for excluded relics when they are awarded

v1 scope decisions (already made — don't relitigate):
- **Both** exclusion behaviors, controlled by the one list. No per-behavior toggles.
- **No search box.** Tier filter buttons keep per-page relic counts small (~20–40 for
  vanilla). BaseMod ships `ModTextInput` if search is wanted later (v2).
- An "excluded only" filter button provides the review/re-enable view — no separate
  list UI.

## Implementation Notes

### Project shape

Slay the Spire mod built with ModTheSpire + BaseMod. Build with `mvn clean package`;
`test-locally.sh` copies the jar into the local game for manual testing. Game and
BaseMod class files for API reference live in `references/` — inspect with
`javap -p -classpath references/slaythespire <class>` (same for `references/basemod`).

Key files:
- `src/main/java/pickyrelics/PickyRelicsMod.java` — config load/save (SpireConfig),
  settings panel construction in `receivePostInitialize()`.
- `src/main/java/pickyrelics/patches/RelicLinkPatch.java` — all reward-time logic:
  candidate relic selection and linked-group creation.
- `src/main/java/pickyrelics/ui/` — custom `IUIElement` widgets: `TabBar`,
  `PagedElement` (shows an element only on its tab), `RelicChoicePreview` (proof that
  relic textures render fine inside a ModPanel), `ProbabilityDisplay`.
- `src/main/java/pickyrelics/util/RelicPoolTracker.java` — pool snapshot/refill for the
  cycle-pools feature.
- `src/main/resources/pickyrelicsResources/localization/<lang>/UIStrings.json` —
  localized strings; `eng` is the source of truth, `update-locales.sh` handles the rest.

### Settings UI infrastructure (already exists)

The settings panel is a BaseMod `ModPanel` with a custom tab system. `TabBar`
(`ui/TabBar.java`) renders clickable tabs; every element is wrapped in `PagedElement`
via `PickyRelicsMod.addPagedElement(panel, TAB_X, element)` so it only renders/updates
on its tab. Tabs are declared as `TAB_CHOICES = 0`, `TAB_PROBABILITIES = 1`,
`TAB_REFILLS = 2` in `PickyRelicsMod.java:120`; tab labels come from the
`pickyrelics:Tabs` UIStrings entry. Adding a fourth tab = new constant + new label
string + `addPagedElement` calls. Tab width is 200px each, centered at x=640 on the
1920px virtual canvas, so 4 tabs fit fine.

The panel's usable content area is roughly x 380–1500, y 380–760 (virtual 1920×1080
coordinates, multiply by `Settings.scale` when rendering — see existing widgets).

### Enumerating relics

`com.megacrit.cardcrawl.helpers.RelicLibrary` exposes public static per-tier lists:
`starterList`, `commonList`, `uncommonList`, `rareList`, `bossList`, `shopList`,
`specialList` — pre-sorted, includes relics registered by other mods. Each
`AbstractRelic` has `relicId` (stable String key), `name`, `description`, `tier`, and
`img` (a `Texture`, 128×128) for grid rendering. The lists are empty very early in the
mod lifecycle; anything built in `receivePostInitialize()` can read them (the existing
slider labels already do), but lazy reads at render time are the established pattern
(see `RelicChoicePreview.getPreviewRelics()`).

Localized tier display names already exist in the `pickyrelics:TierNames` UIStrings
entry ("Starter", "Common", … "Event").

### Enforcement points (all in `RelicLinkPatch.java`)

Extra-choice candidates come from exactly two functions, both of which already
loop-and-skip on `relic.canSpawn()` with `MAX_ATTEMPTS = 10`:
- `getRelicWithFallback(tier)` — `RelicLinkPatch.java:112` (normal tiers + fallbacks)
- `getRandomNonEventRelic()` — `RelicLinkPatch.java:178` (extra choices for Event-tier
  rewards)

Behavior 1 is an exclusion check added to the same skip condition as `canSpawn()` in
both loops.

Behavior 2 goes in `processRelicRewards()` (`RelicLinkPatch.java:528`) — skip building
a group for an excluded original, right next to the existing `tierAdditional <= 0`
skip at `RelicLinkPatch.java:554`.

**Gotcha — the update-loop quick check must stay consistent.** The safety-net patch
`ProcessLateRelicRewards` (`RelicLinkPatch.java:486`) runs every frame and decides
whether any unlinked relic still needs processing (its `hasUnlinked` scan at
`RelicLinkPatch.java:496` mirrors the skip conditions in `processRelicRewards`). If an
excluded relic is skipped in `processRelicRewards` but not in this quick check,
`hasUnlinked` stays true forever → `processRelicRewards` runs every frame (log spam,
wasted work). Any new skip condition must be added to **both** places — consider
extracting a shared `shouldProcess(RewardItem)` helper so they can't drift.

**Gotcha — pool consumption.** `AbstractDungeon.returnRandomRelic(tier)` removes the
drawn relic from the run's pool before we can inspect it, so an excluded relic that
gets drawn-and-skipped is consumed from the pool for the rest of the run. The existing
`canSpawn()` skip already behaves this way, so this is consistent, and arguably fine
(the player excluded it on purpose). Accept it for v1; just don't be surprised by it.

**Interaction — cycle-pools refill.** `RelicPoolTracker.refillIfEmpty()`
(`util/RelicPoolTracker.java`) refills exhausted pools with skipped relics and already
filters by `canSpawn()` (see `plans/relic-pool-refill-stackoverflow.md` for why).
Excluded relics should also be filtered out of refills — they'd only be drawn and
skipped again, burning `MAX_ATTEMPTS` iterations. No recursion risk either way
(exclusion filtering lives in our own bounded outer loops, not vanilla's `canSpawn()`
retry-recursion), so this is an efficiency/cleanliness fix, not a crash fix.
Note: `refillIfEmpty` runs on vanilla draw paths too (shops, chests). Filtering
exclusions there means excluded relics won't *re-enter* pools via refill — that is an
acceptable, minor widening of the feature's footprint; do not otherwise touch vanilla
draws.

### Config storage

Follow the existing pattern in `loadConfig()`/`saveConfig()` (`PickyRelicsMod.java:345`
and `:443`). SpireConfig supports string values (`config.getString`/`setString`).
Store the exclusion list as one comma-joined string of relic IDs under a new key
(e.g. `excludedRelics`), loaded into a `HashSet<String>` exposed via
`PickyRelicsMod.isExcluded(String relicId)` and a toggle method that saves on change
(matching how every other setting saves immediately on change).

Relic IDs are arbitrary strings: vanilla IDs contain spaces (`Bag of Preparation`) and
modded IDs typically contain colons (`somemod:SomeRelic`). Commas do not appear in
practice, but write the serializer defensively (trim entries, drop empties).

**Persistence rule:** never prune IDs that aren't in `RelicLibrary` (relic's mod may be
temporarily uninstalled). Unknown IDs simply don't render in the grid but survive
load/save round-trips.

### New UI component: the exclusion grid

One new `IUIElement` (suggested: `ui/RelicExclusionGrid.java`), same category of work
as `RelicChoicePreview`/`ProbabilityDisplay`. Responsibilities:

- **Tier filter row:** buttons for each tier (reuse `pickyrelics:TierNames` labels),
  plus an "Excluded only" toggle that shows all excluded relics across tiers. Follow
  `TabBar`'s hitbox/`InputHelper.justClickedLeft` pattern for clicks.
- **Icon grid:** render `relic.img` at ~64px in a grid (e.g. 10 columns) for the
  selected tier. Excluded relics render dimmed (multiply a dark `Color`, see
  `SILHOUETTE_COLOR` usage in `RelicChoicePreview`) with a red X or similar overlay.
- **Toggle on click:** hitbox per cell; click flips membership in the exclusion set
  and saves config.
- **Hover tooltip:** `TipHelper.renderGenericTip(x, y, relic.name, relic.description)`
  — see the existing usage in `RelicLinkPatch.RenderLinkPatch` for coordinates/scale.
- **Pagination fallback:** vanilla tiers max out around ~40 relics (fits one page at
  10×4), but modded games can have hundreds per tier. Add simple prev/next page arrows
  that appear only when the tier's list exceeds one page. Don't build scrolling.

Ballpark: 200–350 lines, self-contained in one file.

### Localization

Add to `eng/UIStrings.json`: the 4th entry in `pickyrelics:Tabs`, and a new
`pickyrelics:Exclusions` section (page explanation text, "Excluded only" label,
pagination/count text as needed). Run `update-locales.sh` to propagate to the other
17 locales.

## Suggested Approach

Two commits/phases; the enforcement half is independently shippable and testable via
config-file editing before the UI exists.

1. **Model + enforcement.** Exclusion set in config (load/save, `isExcluded`,
   `setExcluded`), skip checks in `getRelicWithFallback`, `getRandomNonEventRelic`,
   `processRelicRewards` + the `ProcessLateRelicRewards` quick check (extract the
   shared predicate), and the `refillIfEmpty` filter. Verify in-game by hand-editing
   the config file (`SpireConfig` writes to
   `preferences/`-adjacent ModTheSpire config dir; the file is
   `pickyrelics/config.properties` under the ModTheSpire config root).
2. **UI.** `TAB_EXCLUSIONS` constant, tab label, `RelicExclusionGrid`, localization
   strings, page explanation text (follow the bottom-of-page explanation pattern used
   by the other tabs).

Keep the exclusion set + (de)serialization in a plain utility class with no game-class
dependencies (the `TierUtils` pattern) so it stays unit-testable.

## Testing

Avoid introducing boilerplate tests; we do not want excessive pointless tests as these
do not serve anyone. It's extremely important that the tests are meaningful, clear,
and validate core issues and behavior. It's important to figure out tests that
validate our business case, and that ensure healthy core architecture. They can and
should help engineers understand the intention behind the code.

Notes for this repo: there is currently no `src/test` tree or test harness, and game
classes (`AbstractRelic`, `AbstractDungeon`) can't be instantiated outside a running
ModTheSpire game — this is why `TierUtils` takes injected RNG lambdas. The testable
surface here is the exclusion-set serialization round-trip (including IDs with spaces
and colons, unknown-ID preservation, empty-string handling), which only justifies
tests if kept free of game classes per the approach above. The reward-time behavior
and UI must be validated manually in-game (see Validation). Follow the repo's
`test-writing` guidelines if adding tests.

## Validation

Build: `mvn clean package` succeeds; deploy locally with `test-locally.sh`.

In-game checklist (use the BaseMod dev console — backtick key; `relic r <id>` awards a
relic, and the mod's own `pickypool` command shows pool state):

- [ ] Settings panel shows a 4th "Exclusions" tab; existing 3 tabs unchanged.
- [ ] Grid shows the selected tier's relics; clicking toggles excluded state with a
      clear visual difference; hover shows name + description tooltip.
- [ ] "Excluded only" view lists everything excluded across tiers; clicking re-enables.
- [ ] Exclusions survive a full game restart (config round-trip).
- [ ] Behavior 1: exclude a common relic, clear the rest of the tier from
      contention (or repeat elite fights); the excluded relic never appears as an
      *additional* choice. It can still drop as the original reward.
- [ ] Behavior 2: when the original reward IS an excluded relic (award via console or
      natural drop), it appears alone — no chain icon, no linked alternatives — and
      other (non-excluded) relic rewards in the same screen still get their choices.
- [ ] No per-frame log spam after a reward screen containing an excluded relic (the
      `ProcessLateRelicRewards` quick-check consistency gotcha).
- [ ] With cycle-pools ON: exhaust a tier, confirm refill never re-offers excluded
      relics (`pickypool` to inspect).
- [ ] Sapphire Key linking (chest relics) still works when the chest relic is excluded
      (behavior 2 path leaves the vanilla `relicLink` untouched).
- [ ] Non-English locale (any) shows no missing-string crashes on the new tab.

## Documentation

- `readme.md` — add the exclusion feature to the feature list / settings description.
- `CHANGELOG.md` — entry is generated during `/release-prep`; no manual edit needed
  now.
- `src/main/resources/pickyrelicsResources/localization/eng/UIStrings.json` — new
  strings (source of truth), then `update-locales.sh` for the other locales.
- Workshop description (under `workshop/`) — update alongside the next release if it
  enumerates features.
