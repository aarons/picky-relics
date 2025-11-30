#!/bin/bash
# Extracts API reference from SlayTheSpire and BaseMod JARs for easier development
# Run this after installing the game and BaseMod via Steam Workshop

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
API_DIR="$PROJECT_DIR/api-reference"

# Steam paths (macOS)
STEAMAPPS="$HOME/Library/Application Support/Steam/steamapps"
STS_JAR="$STEAMAPPS/common/SlayTheSpire/SlayTheSpire.app/Contents/Resources/desktop-1.0.jar"
BASEMOD_JAR="$STEAMAPPS/workshop/content/646570/1605833019/BaseMod.jar"

echo "Extracting API reference..."

# Clean and create directories
rm -rf "$API_DIR"
mkdir -p "$API_DIR/slaythespire" "$API_DIR/basemod"

# Extract JARs
if [ -f "$STS_JAR" ]; then
    echo "Extracting SlayTheSpire..."
    unzip -q "$STS_JAR" -d "$API_DIR/slaythespire"
else
    echo "Warning: SlayTheSpire JAR not found at $STS_JAR"
fi

if [ -f "$BASEMOD_JAR" ]; then
    echo "Extracting BaseMod..."
    unzip -q "$BASEMOD_JAR" -d "$API_DIR/basemod"
else
    echo "Warning: BaseMod JAR not found at $BASEMOD_JAR"
    echo "Make sure BaseMod is installed via Steam Workshop"
fi

echo "Done! API reference extracted to $API_DIR"
