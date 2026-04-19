#!/bin/bash
set -e

# Build the mod and copy it to the local Slay the Spire mods folder for in-game testing.
# Does not touch the Steam Workshop workspace or git state — pure local iteration.
# Usage: ./test-locally.sh [--clean]

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

# Parse arguments
CLEAN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --clean)
            CLEAN=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--clean]"
            echo ""
            echo "Options:"
            echo "  --clean    Run 'mvn clean package' instead of 'mvn package'"
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

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Picky Relics - Test Locally${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

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

# Done
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Ready to Test!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "  Launch Slay the Spire and verify the mod loads under ModTheSpire."
echo ""
echo -e "${YELLOW}When you're confident the build is good:${NC}"
echo "  1. Run /release-prep (Claude Code skill) to bump version, write changelog, and stage the workshop workspace."
echo "  2. Then run ./publish-release.sh to upload to Steam Workshop."
echo ""
