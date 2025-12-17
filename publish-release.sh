#!/bin/bash
set -e

# Publish a staged release to Steam Workshop
# Usage: ./publish-release.sh [--force]

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
LAST_PUBLISHED_FILE="$SCRIPT_DIR/workshop/.last-published"

# Parse arguments
FORCE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --force)
            FORCE=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--force]"
            echo ""
            echo "Options:"
            echo "  --force    Skip validation checks"
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
echo -e "${BLUE}  Picky Relics - Publish Release${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Step 1: Validate tag exists on HEAD
log_info "Checking for release tag..."

CURRENT_TAG=$(git describe --tags --exact-match HEAD 2>/dev/null || echo "")

if [ -z "$CURRENT_TAG" ]; then
    log_error "HEAD is not tagged. Run stage-release.sh first."
    exit 1
fi

log_success "Found tag: $CURRENT_TAG"

# Step 2: Check if already published
if [ -f "$LAST_PUBLISHED_FILE" ]; then
    LAST_PUBLISHED=$(cat "$LAST_PUBLISHED_FILE")
    if [ "$LAST_PUBLISHED" = "$CURRENT_TAG" ] && [ "$FORCE" = false ]; then
        log_error "Tag $CURRENT_TAG has already been published."
        log_info "Use --force to publish anyway."
        exit 1
    fi

    if [ "$LAST_PUBLISHED" = "$CURRENT_TAG" ]; then
        log_warn "Re-publishing $CURRENT_TAG (--force)"
    fi
fi

# Step 3: Validate workspace exists and has content
log_info "Validating workspace..."

if [ ! -d "$WORKSPACE" ]; then
    log_error "Workspace not found: $WORKSPACE"
    log_error "Run stage-release.sh first."
    exit 1
fi

if [ ! -f "$WORKSPACE/content/PickyRelics.jar" ]; then
    log_error "JAR not found in workspace. Run stage-release.sh first."
    exit 1
fi

if [ ! -f "$WORKSPACE/config.json" ]; then
    log_error "config.json not found in workspace. Run stage-release.sh first."
    exit 1
fi

if [ ! -f "$WORKSPACE/image.jpg" ]; then
    log_error "image.jpg not found in workspace. Run stage-release.sh first."
    exit 1
fi

log_success "Workspace validated"

# Step 4: Confirm with user
echo ""
echo -e "${YELLOW}You are about to publish:${NC}"
echo -e "  Tag:       ${BLUE}$CURRENT_TAG${NC}"
echo -e "  Workspace: ${BLUE}$WORKSPACE${NC}"
echo ""
read -p "Continue? (y/N) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    log_info "Cancelled."
    exit 0
fi

# Step 5: Upload to Steam Workshop
log_info "Uploading to Steam Workshop..."

cd "$STS_DIR"

# mod-uploader.jar requires x86_64 JDK (steamworks4j lacks ARM64 support)
if [[ "$(uname -m)" == "arm64" ]]; then
    X86_JAVA_DIR="$SCRIPT_DIR/.java-x86_64"
    X86_JAVA="$X86_JAVA_DIR/jdk-21.0.5+11/Contents/Home/bin/java"

    if [ ! -f "$X86_JAVA" ]; then
        log_info "Downloading x86_64 JDK for mod-uploader (one-time setup)..."
        mkdir -p "$X86_JAVA_DIR"
        curl -fsSL "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_mac_hotspot_21.0.5_11.tar.gz" \
            | tar -xz -C "$X86_JAVA_DIR"

        if [ ! -f "$X86_JAVA" ]; then
            log_error "Failed to download/extract x86_64 JDK"
            exit 1
        fi
        log_success "x86_64 JDK installed to $X86_JAVA_DIR"
    fi

    arch -x86_64 "$X86_JAVA" -jar mod-uploader.jar upload -w pickyrelics
else
    java -jar mod-uploader.jar upload -w pickyrelics
fi

log_success "Uploaded to Steam Workshop!"

# Step 6: Record published version
echo "$CURRENT_TAG" > "$LAST_PUBLISHED_FILE"
log_success "Recorded published version: $CURRENT_TAG"

# Step 7: Push tag to origin
log_info "Pushing tag to origin..."

if git remote get-url origin &>/dev/null; then
    git push origin "$CURRENT_TAG"
    log_success "Tag pushed to origin"
else
    log_warn "No git remote 'origin' found. Tag not pushed."
fi

# Done!
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Release Published Successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "  Version: ${BLUE}$CURRENT_TAG${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "  1. Visit Steam Workshop to verify your mod page"
echo "  2. Add screenshots/GIFs via the Steam Workshop web interface"
echo "  3. Set visibility to 'public' when ready"
echo ""
