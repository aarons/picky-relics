#!/bin/bash
set -e

# Build the mod and copy it to the local Slay the Spire mods folder for in-game testing.
# Also overwrites the Steam-Workshop-downloaded copy so MTS doesn't load stale classes
# alongside the fresh build (which manifests as NoClassDefFoundError on newly-added
# patch classes). The first time we overwrite the workshop jar, we save a backup so
# `--restore` can put the original back.
# Usage: ./test-locally.sh [--clean] [--restore]

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STS_DIR="$HOME/Library/Application Support/Steam/steamapps/common/SlayTheSpire/SlayTheSpire.app/Contents/Resources"
MODS_DIR="$STS_DIR/mods"
JAR_FILE="$SCRIPT_DIR/target/PickyRelics.jar"
WORKSHOP_CONFIG="$SCRIPT_DIR/workshop/config.json"
WORKSHOP_ROOT="$HOME/Library/Application Support/Steam/steamapps/workshop/content/646570"

# Parse workshop id from workshop/config.json (single source of truth)
WORKSHOP_ID=""
if [ -f "$WORKSHOP_CONFIG" ]; then
    WORKSHOP_ID=$(sed -n 's/.*"steamPublishedID"[[:space:]]*:[[:space:]]*"\([0-9]*\)".*/\1/p' "$WORKSHOP_CONFIG" | head -n1)
fi

WORKSHOP_DIR=""
WORKSHOP_JAR=""
WORKSHOP_BACKUP=""
if [ -n "$WORKSHOP_ID" ]; then
    WORKSHOP_DIR="$WORKSHOP_ROOT/$WORKSHOP_ID"
    WORKSHOP_JAR="$WORKSHOP_DIR/PickyRelics.jar"
    WORKSHOP_BACKUP="$WORKSHOP_DIR/PickyRelics.jar.workshop-original"
fi

# Parse arguments
CLEAN=false
RESTORE=false
FORCE_RESTORE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --clean)
            CLEAN=true
            shift
            ;;
        --restore)
            RESTORE=true
            shift
            ;;
        --force-restore)
            FORCE_RESTORE=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--clean] [--restore | --force-restore]"
            echo ""
            echo "Options:"
            echo "  --clean          Run 'mvn clean package' instead of 'mvn package'"
            echo "  --restore        Restore the Steam-Workshop PickyRelics.jar from the local"
            echo "                   backup taken the first time this script overwrote it."
            echo "                   Does not build or touch the local mods folder."
            echo "  --force-restore  Delete the workshop jar (and our local backup) so Steam"
            echo "                   re-downloads the published version from the Workshop server."
            echo "                   Requires a Steam client restart to take effect."
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

if [ "$RESTORE" = true ] && [ "$FORCE_RESTORE" = true ]; then
    log_error "--restore and --force-restore are mutually exclusive"
    exit 1
fi

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Picky Relics - Test Locally${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# --restore: put the backup back and exit, no build
if [ "$RESTORE" = true ]; then
    if [ -z "$WORKSHOP_JAR" ]; then
        log_error "Could not determine workshop id from $WORKSHOP_CONFIG"
        exit 1
    fi
    if [ ! -f "$WORKSHOP_BACKUP" ]; then
        log_error "No backup to restore: $WORKSHOP_BACKUP"
        log_error "Either the script has never overwritten the workshop jar, or the backup was deleted."
        exit 1
    fi
    log_info "Restoring workshop jar from backup..."
    cp "$WORKSHOP_BACKUP" "$WORKSHOP_JAR"
    log_success "Restored $WORKSHOP_JAR from backup"
    echo ""
    echo -e "  Steam may still re-validate and overwrite on its own schedule."
    echo ""
    exit 0
fi

# --force-restore: delete the workshop jar + our backup so Steam re-downloads
# the published version from the Workshop server. Exits without building.
if [ "$FORCE_RESTORE" = true ]; then
    if [ -z "$WORKSHOP_JAR" ]; then
        log_error "Could not determine workshop id from $WORKSHOP_CONFIG"
        exit 1
    fi
    if [ ! -d "$WORKSHOP_DIR" ]; then
        log_error "Workshop directory not found: $WORKSHOP_DIR"
        log_error "Subscribe to the mod on the Steam Workshop first."
        exit 1
    fi
    if [ -f "$WORKSHOP_JAR" ]; then
        log_info "Deleting workshop jar..."
        rm "$WORKSHOP_JAR"
        log_success "Deleted $WORKSHOP_JAR"
    else
        log_warn "Workshop jar already missing: $WORKSHOP_JAR"
    fi
    if [ -f "$WORKSHOP_BACKUP" ]; then
        log_info "Deleting stale local backup..."
        rm "$WORKSHOP_BACKUP"
        log_success "Deleted $WORKSHOP_BACKUP"
    fi
    echo ""
    echo -e "${YELLOW}To trigger the re-download:${NC}"
    echo "  1. Fully quit Steam (Steam menu > Quit, not just close the window)."
    echo "  2. Re-launch Steam and let it run for a minute."
    echo "  3. If it hasn't re-downloaded, open the Workshop page for Picky Relics"
    echo "     (or unsubscribe then re-subscribe) to force a content check."
    echo ""
    exit 0
fi

# Validate prerequisites
if ! command -v mvn &> /dev/null; then
    log_error "Maven (mvn) is required but not installed"
    exit 1
fi

if [ ! -d "$MODS_DIR" ]; then
    log_error "STS mods folder not found: $MODS_DIR"
    log_error "Make sure Slay the Spire is installed and ModTheSpire has been run at least once."
    exit 1
fi

# Build the JAR
if [ "$CLEAN" = true ]; then
    log_info "Building JAR (mvn clean package)..."
    (cd "$SCRIPT_DIR" && mvn clean package -q)
else
    log_info "Building JAR (mvn package)..."
    (cd "$SCRIPT_DIR" && mvn package -q)
fi

if [ ! -f "$JAR_FILE" ]; then
    log_error "JAR file not found after build: $JAR_FILE"
    exit 1
fi
log_success "JAR built"

# Copy to local mods folder
log_info "Copying JAR to local mods folder..."
cp "$JAR_FILE" "$MODS_DIR/PickyRelics.jar"
log_success "JAR copied to $MODS_DIR/PickyRelics.jar"

# Overwrite the workshop copy (if subscribed) so MTS doesn't load the stale version alongside.
# MTS dedupes by modid but scans multiple directories; when the patch scan picks one jar and
# the runtime classloader picks the other, newly-added patch classes crash with
# NoClassDefFoundError when their patched method is invoked.
if [ -n "$WORKSHOP_JAR" ] && [ -d "$WORKSHOP_DIR" ]; then
    if [ -f "$WORKSHOP_JAR" ] && [ ! -f "$WORKSHOP_BACKUP" ]; then
        log_info "Backing up original workshop jar..."
        cp "$WORKSHOP_JAR" "$WORKSHOP_BACKUP"
        log_success "Backed up to $WORKSHOP_BACKUP"
    fi
    log_info "Overwriting workshop jar..."
    cp "$JAR_FILE" "$WORKSHOP_JAR"
    log_success "JAR copied to $WORKSHOP_JAR"
else
    log_warn "Workshop copy not found (not subscribed?); skipping workshop overwrite."
fi

# Done
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Ready to Test!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "  Launch Slay the Spire and verify the mod loads under ModTheSpire."
echo ""
echo -e "${YELLOW}To put the Steam-Workshop copy back:${NC}"
echo "  ./test-locally.sh --restore         # from local backup"
echo "  ./test-locally.sh --force-restore   # force Steam to re-download from the server"
echo ""
echo -e "${YELLOW}When you're confident the build is good:${NC}"
echo "  1. Run /release-prep (Claude Code skill) to bump version, write changelog, and stage the workshop workspace."
echo "  2. Then run ./publish-release.sh to upload to Steam Workshop."
echo ""
