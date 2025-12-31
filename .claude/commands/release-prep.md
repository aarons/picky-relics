Prepare the repository for a new release.

1. Review recent commits since the last release to understand what changed
2. Bump the version number in both:
   - pom.xml (line 9)
   - src/main/resources/ModTheSpire.json (version field)
3. Update workshop/config.json's changeNote field with a brief summary of changes (e.g., "v1.0.4: Fixed bug with relic selection")

Use `git log --oneline` to see recent commits and summarize the key changes for the changeNote.
