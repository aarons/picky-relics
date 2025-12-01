#!/bin/bash
# Extracts API reference from SlayTheSpire and BaseMod JARs for easier development
# Run this after installing the game and BaseMod via Steam Workshop

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
API_DIR="$PROJECT_DIR/references"

# Detect steamapps path based on OS
detect_steamapps() {
    case "$(uname -s)" in
        Darwin)
            echo "$HOME/Library/Application Support/Steam/steamapps"
            ;;
        Linux)
            # Check common Linux Steam locations
            if [ -d "$HOME/.steam/steam/steamapps" ]; then
                echo "$HOME/.steam/steam/steamapps"
            elif [ -d "$HOME/.local/share/Steam/steamapps" ]; then
                echo "$HOME/.local/share/Steam/steamapps"
            else
                echo "$HOME/.steam/steam/steamapps"  # Default fallback
            fi
            ;;
        MINGW*|MSYS*|CYGWIN*)
            echo "/c/Program Files (x86)/Steam/steamapps"
            ;;
        *)
            echo "Unknown OS: $(uname -s)" >&2
            exit 1
            ;;
    esac
}

STEAMAPPS="$(detect_steamapps)"
STS_GAME_DIR="$STEAMAPPS/common/SlayTheSpire"
STS_WORKSHOP_DIR="$STEAMAPPS/workshop/content/646570"  # 646570 = STS Steam App ID

echo "Detecting JAR locations..."
echo "  Steam apps: $STEAMAPPS"

# Find desktop-1.0.jar (game JAR) - location varies by OS
STS_JAR=$(find "$STS_GAME_DIR" -name "desktop-1.0.jar" 2>/dev/null | head -1)
if [ -n "$STS_JAR" ]; then
    echo "  Found SlayTheSpire: $STS_JAR"
else
    echo "  Error: Could not find desktop-1.0.jar in $STS_GAME_DIR"
    echo "  Make sure Slay the Spire is installed via Steam"
fi

# Find BaseMod.jar in workshop content (workshop ID varies)
BASEMOD_JAR=$(find "$STS_WORKSHOP_DIR" -name "BaseMod.jar" 2>/dev/null | head -1)
if [ -n "$BASEMOD_JAR" ]; then
    echo "  Found BaseMod: $BASEMOD_JAR"
else
    echo "  Error: Could not find BaseMod.jar in $STS_WORKSHOP_DIR"
    echo "  Make sure BaseMod is installed via Steam Workshop"
fi

# Exit if neither JAR found
if [ -z "$STS_JAR" ] && [ -z "$BASEMOD_JAR" ]; then
    echo "No JARs found. Exiting."
    exit 1
fi

echo ""
echo "Extracting API reference..."

# Clean and create directories
rm -rf "$API_DIR"
mkdir -p "$API_DIR/slaythespire" "$API_DIR/basemod"

# Extract JARs
if [ -n "$STS_JAR" ]; then
    echo "  Extracting SlayTheSpire..."
    unzip -q "$STS_JAR" -d "$API_DIR/slaythespire"
fi

if [ -n "$BASEMOD_JAR" ]; then
    echo "  Extracting BaseMod..."
    unzip -q "$BASEMOD_JAR" -d "$API_DIR/basemod"
fi

echo ""
echo "Cloning reference mod repositories..."

# Clone reference mod repositories (or update if they exist)
if [ -d "$API_DIR/ProTemplate" ]; then
    echo "  Updating ProTemplate..."
    git -C "$API_DIR/ProTemplate" pull --quiet
else
    echo "  Cloning ProTemplate..."
    git clone --quiet git@github.com:DarkVexon/ProTemplate.git "$API_DIR/ProTemplate"
fi

if [ -d "$API_DIR/sts-orison-mod" ]; then
    echo "  Updating sts-orison-mod..."
    git -C "$API_DIR/sts-orison-mod" pull --quiet
else
    echo "  Cloning sts-orison-mod..."
    git clone --quiet git@github.com:C-W-Z/sts-orison-mod.git "$API_DIR/sts-orison-mod"
fi

echo ""
echo "Done! API reference extracted to $API_DIR"
