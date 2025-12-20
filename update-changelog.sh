#!/bin/bash
set -e

# Update changelog and version for Picky Relics mod
# Usage: ./update-changelog.sh [--version X.Y.Z] [--bump patch|minor|major] [--dry-run]

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_JSON="$SCRIPT_DIR/src/main/resources/ModTheSpire.json"
CHANGELOG="$SCRIPT_DIR/CHANGELOG.md"

# Parse arguments
DRY_RUN=false
VERSION=""
BUMP=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --version)
            VERSION="$2"
            shift 2
            ;;
        --bump)
            BUMP="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--version X.Y.Z] [--bump patch|minor|major] [--dry-run]"
            echo ""
            echo "Options:"
            echo "  --version X.Y.Z    Set specific version (e.g., 1.2.0)"
            echo "  --bump TYPE        Bump version: patch (1.0.0->1.0.1), minor (1.0.0->1.1.0), major (1.0.0->2.0.0)"
            echo "  --dry-run          Show what would happen without making changes"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Require either --version or --bump
if [ -z "$VERSION" ] && [ -z "$BUMP" ]; then
    echo -e "${RED}Error: Must specify --version or --bump${NC}"
    echo "Usage: $0 [--version X.Y.Z] [--bump patch|minor|major] [--dry-run]"
    exit 1
fi

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Get current version from ModTheSpire.json
get_current_version() {
    grep '"version"' "$MOD_JSON" | sed 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/'
}

# Bump version number
bump_version() {
    local version=$1
    local bump_type=$2

    IFS='.' read -r major minor patch <<< "$version"

    case $bump_type in
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        patch)
            patch=$((patch + 1))
            ;;
        *)
            log_error "Invalid bump type: $bump_type"
            exit 1
            ;;
    esac

    echo "$major.$minor.$patch"
}

# Update version in ModTheSpire.json
update_mod_version() {
    local new_version=$1
    if [ "$DRY_RUN" = true ]; then
        echo -e "${YELLOW}[DRY-RUN]${NC} Would update ModTheSpire.json version to $new_version"
    else
        sed -i '' "s/\"version\"[[:space:]]*:[[:space:]]*\"[^\"]*\"/\"version\": \"$new_version\"/" "$MOD_JSON"
    fi
}

# Style cleanup via second LLM call with strict rules
cleanup_changelog_style() {
    local raw_notes="$1"

    if command -v claude &> /dev/null; then
        local cleaned=$(claude --print "Clean up this changelog entry to follow these strict rules:

NEVER:
- Use **bold** formatting
- Use 'Feature: explanation' format (describe features directly)
- Include technical/engineering sections
- Use jargon players won't understand

ALWAYS:
- Group related features under section headers
- Use plain, clear language
- Use nested bullets for sub-features

Return ONLY the cleaned changelog, no explanation.

Changelog to clean:
$raw_notes" 2>/dev/null || echo "")

        if [ -n "$cleaned" ]; then
            echo "$cleaned"
            return
        fi
    fi

    echo "$raw_notes"
}

# Programmatic enforcement of blanket formatting rules
enforce_programmatic_rules() {
    local notes="$1"
    # Remove all ** markers (silent)
    echo "$notes" | sed 's/\*\*//g'
}

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Picky Relics - Update Changelog${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

if [ "$DRY_RUN" = true ]; then
    log_warn "DRY RUN MODE - No changes will be made"
    echo ""
fi

# Step 1: Determine version
CURRENT_VERSION=$(get_current_version)
log_info "Current version: $CURRENT_VERSION"

if [ -n "$VERSION" ]; then
    # Explicit version provided
    NEW_VERSION="$VERSION"
elif [ -n "$BUMP" ]; then
    # Bump version
    NEW_VERSION=$(bump_version "$CURRENT_VERSION" "$BUMP")
    log_info "Bumping version ($BUMP): $CURRENT_VERSION -> $NEW_VERSION"
fi

log_info "New version: $NEW_VERSION"

# Step 2: Update version in ModTheSpire.json
if [ "$NEW_VERSION" != "$CURRENT_VERSION" ]; then
    log_info "Updating version in ModTheSpire.json..."
    update_mod_version "$NEW_VERSION"
    log_success "Version updated to $NEW_VERSION"
fi

# Step 3: Find last release tag
LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
if [ -n "$LAST_TAG" ]; then
    log_info "Last release tag: $LAST_TAG"
else
    log_info "No previous release tags found (first release)"
fi

# Step 4: Generate release notes
if [ -z "$LAST_TAG" ]; then
    # First release - use existing CHANGELOG
    log_info "First release - using existing CHANGELOG.md"
    RELEASE_NOTES="Initial release"
else
    # Generate release notes from commits
    log_info "Generating release notes from commits since $LAST_TAG..."

    COMMITS=$(git log "$LAST_TAG"..HEAD --oneline --no-merges)

    if [ -z "$COMMITS" ]; then
        log_warn "No commits since last release"
        RELEASE_NOTES="Bug fixes and improvements"
    else
        # Use Claude to generate release notes
        if command -v claude &> /dev/null; then
            log_info "Using Claude to generate release notes..."

            # Read existing changelog for style reference
            EXISTING_STYLE=$(head -30 "$CHANGELOG")

            RELEASE_NOTES=$(claude --print "Generate a concise changelog entry for a Slay the Spire mod release.

STYLE GUIDE:
- Write for players, not engineers
- Describe features directly (not 'Feature: explanation' format)
- Group related items under section headers
- Use nested bullets for sub-features
- Max 5-7 bullet points total
- Follow this existing style:

$EXISTING_STYLE

Commits since last release:
$COMMITS" 2>/dev/null || echo "")

            if [ -z "$RELEASE_NOTES" ]; then
                log_warn "Claude failed to generate notes, using commit summary"
                RELEASE_NOTES=$(echo "$COMMITS" | head -5 | sed 's/^[a-f0-9]* /- /')
            else
                # Phase 2: Style cleanup via LLM (always runs)
                log_info "Running style cleanup pass..."
                RELEASE_NOTES=$(cleanup_changelog_style "$RELEASE_NOTES")
            fi

            # Phase 3: Programmatic enforcement (silent)
            RELEASE_NOTES=$(enforce_programmatic_rules "$RELEASE_NOTES")
        else
            log_warn "Claude CLI not found, using commit summary"
            RELEASE_NOTES=$(echo "$COMMITS" | head -5 | sed 's/^[a-f0-9]* /- /')
        fi

        # Update CHANGELOG.md
        if [ "$DRY_RUN" = true ]; then
            echo -e "${YELLOW}[DRY-RUN]${NC} Would update CHANGELOG.md with:"
            echo "$RELEASE_NOTES"
        else
            log_info "Updating CHANGELOG.md..."
            DATE=$(date +%Y-%m-%d)

            # Create new changelog entry
            NEW_ENTRY="## [$NEW_VERSION] - $DATE

$RELEASE_NOTES
"
            # Insert after the first line (# Changelog)
            sed -i '' "2i\\
\\
$NEW_ENTRY" "$CHANGELOG"

            log_success "CHANGELOG.md updated"
        fi
    fi
fi

# Step 5: Create git commit and tag
TAG_NAME="v$NEW_VERSION"
if git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
    log_warn "Tag $TAG_NAME already exists"
else
    log_info "Creating git commit and tag: $TAG_NAME"
    if [ "$DRY_RUN" = true ]; then
        echo -e "${YELLOW}[DRY-RUN]${NC} Would commit changes and create tag: $TAG_NAME"
    else
        git add -A
        git commit -m "Release $NEW_VERSION" --allow-empty
        git tag -a "$TAG_NAME" -m "Release $NEW_VERSION"
        log_success "Tag created: $TAG_NAME"
    fi
fi

# Print summary
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Changelog Updated Successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "  Version:  ${BLUE}$NEW_VERSION${NC}"
echo -e "  Tag:      ${BLUE}$TAG_NAME${NC}"
echo ""
echo -e "${YELLOW}Release notes:${NC}"
echo "$RELEASE_NOTES"
echo ""
