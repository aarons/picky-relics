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

## Step 3: Run the changelog/version updater

```
./update-changelog.sh --bump <patch|minor|major>
```

(Or `--version X.Y.Z` if the user gave a specific number.)

This will:
- Bump the version in `src/main/resources/ModTheSpire.json`
- Generate a changelog entry in `CHANGELOG.md` (uses Claude CLI for prose; falls back to commit summary)
- Update `workshop/config.json` `changeNote`
- Commit with message "Release X.Y.Z" and create tag `vX.Y.Z`

⚠️ `update-changelog.sh` does NOT update `pom.xml`. After it finishes:
- Edit `pom.xml` line 9 (`<version>X.Y.Z</version>`) to match the new version.
- Amend it into the release commit: `git add pom.xml && git commit --amend --no-edit && git tag -f vX.Y.Z` (force-move the tag onto the amended commit).

Review the generated CHANGELOG.md entry with the user before proceeding — offer to edit it if the auto-generated prose needs polish.

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
