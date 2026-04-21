Prepare a release of the Picky Relics mod: bump the version, write the changelog, commit & tag, and stage the Steam Workshop workspace so the user can run `./publish-release.sh` to upload.

You orchestrate the release prep but do NOT run `./publish-release.sh` yourself — that final step is the user's, so they can be ready to monitor Steam rollout and respond to feedback.

## Preconditions

Before starting, sanity-check:
- Working tree is clean (`git status --porcelain` returns nothing). If not, ask the user how to proceed.
- HEAD is on `main` (or whatever the user expects). Ask if uncertain.
- The user has already iterated on changes via `./test-locally.sh` and is happy with the build. Confirm if unclear.

## Step 1: Survey what's in this release

Find the last release tag and review what's changed since:

```
git describe --tags --abbrev=0
git log <last-tag>..HEAD --oneline --no-merges
git diff <last-tag>..HEAD --stat
```

Summarize the changes for the user in plain language (player-facing where possible — this is a game mod). Call out anything that smells like a breaking change, new feature, or notable bug fix.

## Step 2: Decide the version bump

Default mapping:
- `patch` — bug fixes, small tweaks, localization updates
- `minor` — new user-facing features, new config options
- `major` — breaking changes to mod config schema, save data, or compatibility

Propose a bump level, then confirm with the user. They may override with a specific version.

## Step 3: Bump versions and write the changelog

Edit all four version locations to the new `X.Y.Z`:
- `pom.xml` — `<version>` near line 9
- `src/main/resources/ModTheSpire.json` — `"version"` field
- `workshop/config.json` — `"changeNote"` field (format: `"vX.Y.Z: <one-sentence summary of player-facing changes>"`)
- `CHANGELOG.md` — prepend a new entry above the previous one

Draft the `CHANGELOG.md` entry yourself from the commit log. Use the existing style — look at the top few entries in `CHANGELOG.md` for tone and structure. Rules of thumb:
- Write for players, not engineers. Describe what changed in-game, not what changed in the code.
- Group by `### Features` / `### Changed` / `### Fixed` (only include sections that apply).
- Skip or fold together purely internal commits (refactors, infra, docs). A `refactor:` that players will notice as a config rename still belongs — as a player-facing note.
- Nested bullets are fine for sub-features (see 1.0.0 and 1.1.0 entries).
- Date format: `## [X.Y.Z] - YYYY-MM-DD` using today's date.

Show the drafted CHANGELOG entry and the workshop `changeNote` to the user for review before committing. Offer to edit if they want different wording.

Once approved, commit all four files in one commit and tag:

```
git add pom.xml src/main/resources/ModTheSpire.json workshop/config.json CHANGELOG.md
git commit -m "Release X.Y.Z"
git tag -a vX.Y.Z -m "Release X.Y.Z"
```

No amend, no `tag -f` — just one clean commit with the tag pointing at it.

## Step 4: Stage the Steam Workshop workspace

The workspace lives at `$STS_DIR/pickyrelics`, where:
```
STS_DIR="$HOME/Library/Application Support/Steam/steamapps/common/SlayTheSpire/SlayTheSpire.app/Contents/Resources"
```

Validate:
- `mvn` is on PATH.
- `$STS_DIR/mod-uploader.jar` exists.
- `workshop/image.jpg` exists in the project (under 1MB, JPG).

Build a fresh JAR and populate the workspace:

```
mvn clean package -q
```

If `$STS_DIR/pickyrelics` doesn't exist yet, create the workspace first:

```
(cd "$STS_DIR" && java -jar mod-uploader.jar new -w pickyrelics)
```

Then copy the release artifacts:

```
cp workshop/config.json "$STS_DIR/pickyrelics/config.json"
cp workshop/image.jpg "$STS_DIR/pickyrelics/image.jpg"
mkdir -p "$STS_DIR/pickyrelics/content"
cp target/PickyRelics.jar "$STS_DIR/pickyrelics/content/PickyRelics.jar"
```

Confirm all four paths exist with the right contents (config.json should have the new version's `changeNote`).

## Step 5: Hand off to the user

Tell the user:

> Release vX.Y.Z is staged. When you're ready to publish to Steam Workshop, run `./publish-release.sh`. That will upload the workspace, record the published version, and push the tag to origin.

Remind them publishing immediately updates the live mod entry — there is no draft/hidden state for an already-published Workshop item, so any visibility flag in `workshop/config.json` other than `public` will hide the mod from existing users.
