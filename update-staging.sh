#!/bin/bash
set -e

# Stage a release for Picky Relics mod
# Usage: ./stage-release.sh [--dry-run]

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
WORKSHOP_CONFIG="$SCRIPT_DIR/workshop/config.json"
WORKSHOP_IMAGE="$SCRIPT_DIR/workshop/image.jpg"

# Parse arguments
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--dry-run]"
            echo ""
            echo "Options:"
            echo "  --dry-run    Show what would happen without making changes"
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

# Step 2: Build the JAR
log_info "Building JAR..."
run_cmd "cd \"$SCRIPT_DIR\" && mvn clean package -q"
log_success "JAR built successfully"

# Verify JAR exists
JAR_FILE="$SCRIPT_DIR/target/PickyRelics.jar"
if [ ! -f "$JAR_FILE" ] && [ "$DRY_RUN" = false ]; then
    log_error "JAR file not found after build: $JAR_FILE"
    exit 1
fi

# Step 3: Create/update Steam workspace
log_info "Setting up Steam Workshop workspace..."

# Create workspace if it doesn't exist
if [ ! -d "$WORKSPACE" ]; then
    log_info "Creating new workspace..."
    run_cmd "(cd \"$STS_DIR\" && java -jar mod-uploader.jar new -w pickyrelics)"
fi

# Copy files to workspace
log_info "Copying files to workspace..."
run_cmd "cp \"$WORKSHOP_CONFIG\" \"$WORKSPACE/config.json\""
run_cmd "cp \"$WORKSHOP_IMAGE\" \"$WORKSPACE/image.jpg\""
run_cmd "mkdir -p \"$WORKSPACE/content\""
run_cmd "cp \"$JAR_FILE\" \"$WORKSPACE/content/PickyRelics.jar\""
log_success "Files copied to workspace"

# Step 4: Copy to local mods folder for QA
log_info "Copying JAR to local mods folder for QA testing..."
run_cmd "cp \"$JAR_FILE\" \"$MODS_DIR/PickyRelics.jar\""
log_success "JAR copied to mods folder"

# Step 5: Print summary
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Release Staged Successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "  Workspace:  ${BLUE}$WORKSPACE${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "  1. Launch Slay the Spire and test the mod"
echo "  2. When ready, run: ./publish-release.sh"
echo ""
