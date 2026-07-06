# Changelog

## [1.2.0] - 2026-07-05

### Features
- New exclusion setting that lets you exclude specific relics from Picky Relics
  - Excluded relics won't be provide as choices, and won't provide extra choices
  - Excluded relics will drop normally from combat, shops, chests, and events

## [1.1.1] - 2026-06-21

### Fixed
- Fixed a crash (StackOverflowError) that could occur with relic pool refill enabled — most often when the shop rolled a relic late in a run, where a tier's remaining never-seen relics couldn't currently spawn

## [1.1.0] - 2026-04-21

### Features
- Relic pool refill: when a tier runs out of relics you haven't obtained, choices are filled with relics you've never seen before
  - Applies to both relic reward screens and shop relic slots
- New tab bar for the settings screen, replacing the previous page navigator
  - Added contextual explanations throughout each settings tab

### Changed
- Renamed the `choice` config option to `additional` for clarity (existing settings migrate automatically)
- Cleaner layout and wording in the relic choice settings

### Fixed
- Tab labels now display in the correct translation for every supported language
- Clearer wording when a relic tier is exhausted and fewer choices can be offered

## [1.0.2] - 2025-12-20

### Fixed
- First relic in chest rewards now properly highlights red when hovering other linked relics

## [1.0.1] - 2025-12-16

### Changed
- Updated Steam Workshop tags and description

## [1.0.0] - 2025-12-11

### Features
- Configurable number of relic options from 1-5 (default: 2)
  - Each relic tier can be configured independently
- Configurable chance for relic options to shift reward tier (common to uncommon for example).
- Optionally add labels of the relic's tier to reward screens
- Live simulation of settings
  - Preview the impact when changing the number of choices for each tier
  - Preview the probability breakdown when changing the upgrade and downgrade algorithm
- AI Generated Localizations: Simplified Chinese, Japanese, Korean, German, French, Spanish, Russian, Portuguese, Turkish, Italian, Greek, Ukrainian, Vietnamese, Polish, Indonesian, Thai, Serbian
