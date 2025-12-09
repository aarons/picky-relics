#!/bin/bash
set -e

# Stage a release for Picky Relics mod
# Usage: ./stage-release.sh [--version X.Y.Z] [--bump patch|minor|major] [--dry-run]

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STS_DIR="$HOME/Library/Application Support/Steam/steamapps/common/SlayTheSpire/SlayTheSpire.app/Contents/Resources"
WORKSPACE="$STS_DIR/pickyrelics"
MODS_DIR="$STS_DIR/mods"
MOD_JSON="$SCRIPT_DIR/src/main/resources/ModTheSpire.json"
WORKSHOP_CONFIG="$SCRIPT_DIR/workshop/config.json"
WORKSHOP_IMAGE="$SCRIPT_DIR/workshop/image.jpg"
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

run_cmd() {
    if [ "$DRY_RUN" = true ]; then
        echo -e "${YELLOW}[DRY-RUN]${NC} $1"
    else
        eval "$1"
    fi
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

# Update changeNote in workshop/config.json
update_change_note() {
    local note=$1
    if [ "$DRY_RUN" = true ]; then
        echo -e "${YELLOW}[DRY-RUN]${NC} Would update workshop/config.json changeNote"
    else
        # Use jq if available, otherwise sed
        if command -v jq &> /dev/null; then
            local tmp=$(mktemp)
            jq --arg note "$note" '.changeNote = $note' "$WORKSHOP_CONFIG" > "$tmp" && mv "$tmp" "$WORKSHOP_CONFIG"
        else
            sed -i '' "s/\"changeNote\"[[:space:]]*:[[:space:]]*\"[^\"]*\"/\"changeNote\": \"$note\"/" "$WORKSHOP_CONFIG"
        fi
    fi
}

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Picky Relics - Stage Release${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

if [ "$DRY_RUN" = true ]; then
    log_warn "DRY RUN MODE - No changes will be made"
    echo ""
fi

# Step 1: Validate prerequisites
log_info "Validating prerequisites..."

# Check for clean git working directory
if [ -n "$(git status --porcelain)" ]; then
    log_error "Git working directory is not clean. Commit or stash changes first."
    git status --short
    exit 1
fi
log_success "Git working directory is clean"

# Check for workshop image
if [ ! -f "$WORKSHOP_IMAGE" ]; then
    log_error "Workshop image not found: $WORKSHOP_IMAGE"
    log_error "Please add a preview image (JPG format, under 1MB) before staging."
    exit 1
fi
log_success "Workshop image exists"

# Check for required tools
if ! command -v mvn &> /dev/null; then
    log_error "Maven (mvn) is required but not installed"
    exit 1
fi

if [ ! -f "$STS_DIR/mod-uploader.jar" ]; then
    log_error "mod-uploader.jar not found in STS directory"
    exit 1
fi
log_success "Required tools found"

# Step 2: Determine version
CURRENT_VERSION=$(get_current_version)
log_info "Current version: $CURRENT_VERSION"

if [ -n "$VERSION" ]; then
    # Explicit version provided
    NEW_VERSION="$VERSION"
elif [ -n "$BUMP" ]; then
    # Bump version
    NEW_VERSION=$(bump_version "$CURRENT_VERSION" "$BUMP")
    log_info "Bumping version ($BUMP): $CURRENT_VERSION -> $NEW_VERSION"
else
    # Use current version
    NEW_VERSION="$CURRENT_VERSION"
fi

log_info "Release version: $NEW_VERSION"

# Step 3: Update version in ModTheSpire.json if changed
if [ "$NEW_VERSION" != "$CURRENT_VERSION" ]; then
    log_info "Updating version in ModTheSpire.json..."
    update_mod_version "$NEW_VERSION"
    log_success "Version updated to $NEW_VERSION"
fi

# Step 4: Find last release tag
LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
if [ -n "$LAST_TAG" ]; then
    log_info "Last release tag: $LAST_TAG"
else
    log_info "No previous release tags found (first release)"
fi

# Step 5 & 6: Generate release notes
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

            RELEASE_NOTES=$(claude --print "Generate a concise changelog entry for a Slay the Spire mod release.
Format as bullet points, focusing on user-facing changes.
Be concise - max 5 bullet points.
Commits since last release:
$COMMITS" 2>/dev/null || echo "")

            if [ -z "$RELEASE_NOTES" ]; then
                log_warn "Claude failed to generate notes, using commit summary"
                RELEASE_NOTES=$(echo "$COMMITS" | head -5 | sed 's/^[a-f0-9]* /- /')
            fi
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

# Step 7: Update workshop/config.json changeNote
log_info "Updating workshop/config.json changeNote..."
update_change_note "v$NEW_VERSION: ${RELEASE_NOTES%%$'\n'*}"
log_success "Workshop config updated"

# Step 8: Build the JAR
log_info "Building JAR..."
run_cmd "cd \"$SCRIPT_DIR\" && mvn clean package -q"
log_success "JAR built successfully"

# Verify JAR exists
JAR_FILE="$SCRIPT_DIR/target/PickyRelics.jar"
if [ ! -f "$JAR_FILE" ] && [ "$DRY_RUN" = false ]; then
    log_error "JAR file not found after build: $JAR_FILE"
    exit 1
fi

# Step 9: Create/update Steam workspace
log_info "Setting up Steam Workshop workspace..."

# Create workspace if it doesn't exist
if [ ! -d "$WORKSPACE" ]; then
    log_info "Creating new workspace..."
    run_cmd "cd \"$STS_DIR\" && java -jar mod-uploader.jar new -w pickyrelics"
fi

# Copy files to workspace
log_info "Copying files to workspace..."
run_cmd "cp \"$WORKSHOP_CONFIG\" \"$WORKSPACE/config.json\""
run_cmd "cp \"$WORKSHOP_IMAGE\" \"$WORKSPACE/image.jpg\""
run_cmd "mkdir -p \"$WORKSPACE/content\""
run_cmd "cp \"$JAR_FILE\" \"$WORKSPACE/content/PickyRelics.jar\""
log_success "Files copied to workspace"

# Step 10: Copy to local mods folder for QA
log_info "Copying JAR to local mods folder for QA testing..."
run_cmd "cp \"$JAR_FILE\" \"$MODS_DIR/PickyRelics.jar\""
log_success "JAR copied to mods folder"

# Step 11: Create git tag
TAG_NAME="v$NEW_VERSION"
if git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
    log_warn "Tag $TAG_NAME already exists"
else
    log_info "Creating git tag: $TAG_NAME"
    if [ "$DRY_RUN" = true ]; then
        echo -e "${YELLOW}[DRY-RUN]${NC} Would create tag: $TAG_NAME"
    else
        git add -A
        git commit -m "Release $NEW_VERSION" --allow-empty
        git tag -a "$TAG_NAME" -m "Release $NEW_VERSION"
        log_success "Tag created: $TAG_NAME"
    fi
fi

# Step 12: Print summary
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Release Staged Successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "  Version:    ${BLUE}$NEW_VERSION${NC}"
echo -e "  Tag:        ${BLUE}$TAG_NAME${NC}"
echo -e "  Workspace:  ${BLUE}$WORKSPACE${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "  1. Launch Slay the Spire and test the mod"
echo "  2. When ready, run: ./publish-release.sh"
echo ""
