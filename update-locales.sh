#!/bin/bash
set -euo pipefail

# Update locale translations for Picky Relics mod using Claude Code
# This script prompts Claude Code to update locale translations for each language
#
# Usage: ./update-locales.sh [options] [language_code]
#
# Options:
#   -j, --jobs N       Run N translations in parallel (default: 1, sequential)
#   --start-at CODE    Start from a specific language and continue
#
# Examples:
#   ./update-locales.sh zhs              # Update only Simplified Chinese
#   ./update-locales.sh -j 4             # Update all languages, 4 at a time
#   ./update-locales.sh -j 3 --start-at kor  # Start from Korean, 3 parallel
#
# Requirements: jq (for JSON validation)

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Paths
LOCALIZATION_DIR="src/main/resources/pickyrelicsResources/localization"
REFERENCE_FILE="$LOCALIZATION_DIR/eng/UIStrings.json"

# Check dependencies
if ! command -v jq &> /dev/null; then
    echo -e "${RED}Error: jq is required but not installed.${NC}"
    echo "Install with: brew install jq"
    exit 1
fi

# Check if reference file exists
if [ ! -f "$REFERENCE_FILE" ]; then
    echo -e "${RED}Error: Reference file $REFERENCE_FILE not found!${NC}"
    exit 1
fi

# Language mapping: code:name pairs (Slay the Spire language codes)
LANGUAGES=(
    "zhs:Simplified Chinese"
    "jpn:Japanese"
    "kor:Korean"
    "deu:German"
    "fra:French"
    "ita:Italian"
    "spa:Spanish"
    "rus:Russian"
    "ptb:Portuguese (Brazil)"
    "pol:Polish"
    "tha:Thai"
    "tur:Turkish"
    "ukr:Ukrainian"
    "vie:Vietnamese"
    "ind:Indonesian"
    "gre:Greek"
    "srp:Serbian"
)

# ============================================================================
# Validation Functions (using jq)
# ============================================================================

# Validate JSON syntax
validate_json() {
    local file="$1"
    if jq empty "$file" 2>/dev/null; then
        return 0
    else
        echo "Invalid JSON syntax"
        return 1
    fi
}

# Validate keys match English reference
validate_keys() {
    local locale_file="$1"
    local ref_keys=$(jq -r 'keys[]' "$REFERENCE_FILE" | sort)
    local locale_keys=$(jq -r 'keys[]' "$locale_file" | sort)

    local missing=$(comm -23 <(echo "$ref_keys") <(echo "$locale_keys"))
    local extra=$(comm -13 <(echo "$ref_keys") <(echo "$locale_keys"))

    local errors=""
    if [ -n "$missing" ]; then
        errors="Missing keys: $(echo $missing | tr '\n' ' ')"
    fi
    if [ -n "$extra" ]; then
        [ -n "$errors" ] && errors="$errors; "
        errors="${errors}Extra keys: $(echo $extra | tr '\n' ' ')"
    fi

    if [ -n "$errors" ]; then
        echo "$errors"
        return 1
    fi
    return 0
}

# Validate TEXT array lengths match
validate_array_lengths() {
    local locale_file="$1"
    local errors=""

    for key in $(jq -r 'keys[]' "$REFERENCE_FILE"); do
        local ref_len=$(jq -r --arg k "$key" '.[$k].TEXT | length' "$REFERENCE_FILE")
        local locale_len=$(jq -r --arg k "$key" '.[$k].TEXT | length' "$locale_file" 2>/dev/null || echo "0")

        if [ "$ref_len" != "$locale_len" ]; then
            [ -n "$errors" ] && errors="$errors; "
            errors="${errors}$key: expected $ref_len strings, got $locale_len"
        fi
    done

    if [ -n "$errors" ]; then
        echo "$errors"
        return 1
    fi
    return 0
}

# Extract placeholders from a string
extract_placeholders() {
    local text="$1"
    # Extract %d, %s, #y, #r, #b, #g patterns
    echo "$text" | grep -oE '%[ds]|#[yrbg]' | sort || true
}

# Validate placeholders are preserved
validate_placeholders() {
    local locale_file="$1"
    local errors=""

    for key in $(jq -r 'keys[]' "$REFERENCE_FILE"); do
        local ref_texts=$(jq -r --arg k "$key" '.[$k].TEXT[]' "$REFERENCE_FILE")
        local locale_texts=$(jq -r --arg k "$key" '.[$k].TEXT[]' "$locale_file" 2>/dev/null || echo "")

        # Compare each text entry
        local i=0
        while IFS= read -r ref_text && IFS= read -r locale_text <&3; do
            local ref_ph=$(extract_placeholders "$ref_text")
            local locale_ph=$(extract_placeholders "$locale_text")

            if [ "$ref_ph" != "$locale_ph" ]; then
                [ -n "$errors" ] && errors="$errors; "
                errors="${errors}$key[$i]: placeholder mismatch (expected: $ref_ph, got: $locale_ph)"
            fi
            ((i++))
        done <<< "$ref_texts" 3<<< "$locale_texts"
    done

    if [ -n "$errors" ]; then
        echo "$errors"
        return 1
    fi
    return 0
}

# Run all validations
validate_locale() {
    local lang_code="$1"
    local lang_name="$2"
    local locale_file="$3"
    local attempt="$4"

    echo -e "${BLUE}Validating $lang_name translation...${NC}"

    local all_passed=true
    local validation_errors=""

    # Check JSON validity
    if error=$(validate_json "$locale_file"); then
        echo -e "  ${GREEN}✓${NC} JSON valid"
    else
        echo -e "  ${RED}✗${NC} JSON invalid: $error"
        all_passed=false
        validation_errors="JSON syntax error"
        # Can't continue if JSON is invalid
        echo -e "${YELLOW}Validation failed for $lang_name (attempt $attempt/3)${NC}"
        return 1
    fi

    # Check keys match
    if error=$(validate_keys "$locale_file"); then
        echo -e "  ${GREEN}✓${NC} Keys match English"
    else
        echo -e "  ${RED}✗${NC} Key mismatch: $error"
        all_passed=false
        validation_errors="${validation_errors:+$validation_errors; }$error"
    fi

    # Check array lengths
    if error=$(validate_array_lengths "$locale_file"); then
        echo -e "  ${GREEN}✓${NC} TEXT array lengths match"
    else
        echo -e "  ${RED}✗${NC} Array length mismatch: $error"
        all_passed=false
        validation_errors="${validation_errors:+$validation_errors; }$error"
    fi

    # Check placeholders
    if error=$(validate_placeholders "$locale_file"); then
        echo -e "  ${GREEN}✓${NC} Placeholders preserved"
    else
        echo -e "  ${RED}✗${NC} Placeholder issues: $error"
        all_passed=false
        validation_errors="${validation_errors:+$validation_errors; }$error"
    fi

    if [ "$all_passed" = true ]; then
        return 0
    else
        echo -e "${YELLOW}Validation failed for $lang_name (attempt $attempt/3)${NC}"

        # Prompt Claude to fix
        fix_prompt="The $lang_name translation in $locale_file has validation errors:

$validation_errors

Please fix the errors in $locale_file. Requirements:
- Valid JSON syntax
- Keys must match $REFERENCE_FILE exactly (do not translate keys like \"pickyrelics:ModInfo\")
- TEXT arrays must have the same number of strings as English
- Preserve all placeholders: %d, %s (format specifiers) and #y, #r, #b, #g (color codes)

Reference file: $REFERENCE_FILE"

        echo -e "${BLUE}Prompting Claude Code to fix validation errors...${NC}"
        claude --allowedTools "Glob Grep Read Edit($locale_file) Write($locale_file)" -p "$fix_prompt"

        return 1
    fi
}

# ============================================================================
# Process Single Language (can be run in parallel)
# ============================================================================

process_language() {
    local lang_code="$1"
    local lang_name="$2"
    local locale_dir="$LOCALIZATION_DIR/$lang_code"
    local locale_file="$locale_dir/UIStrings.json"
    local log_file="$3"

    {
        echo -e "${YELLOW}Processing language: $lang_name ($lang_code)${NC}"

        # Create locale directory if it doesn't exist
        if [ ! -d "$locale_dir" ]; then
            echo "Creating directory: $locale_dir"
            mkdir -p "$locale_dir"
        fi

        # Check if locale file exists to determine prompt intro
        if [ -f "$locale_file" ]; then
            intro="We made some recent changes to $REFERENCE_FILE. Please evaluate the $lang_name translation in $locale_file and apply updates if needed. Use $REFERENCE_FILE as the reference."
        else
            intro="We're introducing $lang_name language support for the Picky Relics mod. Please create a translation file at $locale_file. Use $REFERENCE_FILE as the reference."
        fi

        # Translation guidelines
        guidelines="Important translation rules:
  - The file must be valid JSON
  - Keys (like \"pickyrelics:ModInfo\") must NOT be translated - keep them exactly as in English
  - Only translate the strings inside the TEXT arrays
  - Preserve all placeholders: %d, %s (format specifiers) and #y, #r, #b, #g (color codes)
  - TEXT arrays must have the exact same number of strings as the English version
  - For mod context, see CLAUDE.md in the project root"

        prompt="$intro

$guidelines"

        echo -e "${BLUE}Prompting Claude Code for $lang_name translation...${NC}"
        echo ""

        # Execute claude command
        claude --allowedTools "Glob Grep Read Edit($locale_file) Write($locale_file)" -p "$prompt"

        echo ""

        # Validation with retry logic
        local attempt=1
        local max_attempts=3
        local validation_passed=false

        while [ $attempt -le $max_attempts ]; do
            if [ ! -f "$locale_file" ]; then
                echo -e "${RED}Error: Locale file was not created${NC}"
                break
            fi

            if validate_locale "$lang_code" "$lang_name" "$locale_file" "$attempt"; then
                validation_passed=true
                break
            fi

            if [ $attempt -lt $max_attempts ]; then
                echo -e "${YELLOW}Retrying validation (attempt $((attempt + 1))/$max_attempts)...${NC}"
                attempt=$((attempt + 1))
            else
                echo -e "${RED}Validation failed after $max_attempts attempts${NC}"
                break
            fi
        done

        if [ "$validation_passed" = true ]; then
            echo -e "${GREEN}✓ Completed processing for $lang_name [$lang_code]${NC}"
            echo "SUCCESS" > "${log_file}.status"
        else
            echo -e "${YELLOW}⚠ Completed $lang_name [$lang_code] with validation issues${NC}"
            echo "FAILED" > "${log_file}.status"
        fi
        echo "----------------------------------------"
        echo ""
    } > "$log_file" 2>&1
}

# ============================================================================
# Main Script
# ============================================================================

# Parse command line arguments
SINGLE_LANGUAGE=""
START_AT_LANGUAGE=""
PARALLEL_JOBS=1

while [ $# -gt 0 ]; do
    case "$1" in
        -j|--jobs)
            if [ -z "${2:-}" ] || [[ "$2" == -* ]]; then
                echo -e "${RED}Error: -j/--jobs requires a number${NC}"
                exit 1
            fi
            PARALLEL_JOBS="$2"
            shift 2
            ;;
        --start-at)
            if [ -z "${2:-}" ] || [[ "$2" == -* ]]; then
                echo -e "${RED}Error: --start-at requires a language code${NC}"
                exit 1
            fi
            START_AT_LANGUAGE="$2"
            shift 2
            ;;
        --start-at=*)
            START_AT_LANGUAGE="${1#--start-at=}"
            shift
            ;;
        -*)
            echo -e "${RED}Error: Unknown option $1${NC}"
            exit 1
            ;;
        *)
            SINGLE_LANGUAGE="$1"
            shift
            ;;
    esac
done

# Validate language codes if provided
for target_lang in "$SINGLE_LANGUAGE" "$START_AT_LANGUAGE"; do
    if [ -n "$target_lang" ]; then
        found_language=false
        for lang_pair in "${LANGUAGES[@]}"; do
            lang_code="${lang_pair%%:*}"
            if [ "$lang_code" = "$target_lang" ]; then
                found_language=true
                break
            fi
        done

        if [ "$found_language" = false ]; then
            echo -e "${RED}Error: Language code '$target_lang' not found.${NC}"
            echo -e "Available language codes:"
            for lang_pair in "${LANGUAGES[@]}"; do
                lang_code="${lang_pair%%:*}"
                lang_name="${lang_pair#*:}"
                echo "  $lang_code ($lang_name)"
            done
            exit 1
        fi
    fi
done

if [ -n "$SINGLE_LANGUAGE" ]; then
    echo -e "${BLUE}Single language mode: Processing only $SINGLE_LANGUAGE${NC}"
elif [ -n "$START_AT_LANGUAGE" ]; then
    echo -e "${BLUE}Starting from language: $START_AT_LANGUAGE${NC}"
fi

echo -e "${BLUE}Picky Relics Locale Update Script${NC}"
echo -e "${BLUE}==================================${NC}"
echo ""
echo -e "This script will prompt Claude Code to update locale translations."
echo -e "Reference file: ${GREEN}$REFERENCE_FILE${NC}"
if [ -n "$SINGLE_LANGUAGE" ]; then
    echo -e "Target language: ${GREEN}$SINGLE_LANGUAGE${NC}"
elif [ -n "$START_AT_LANGUAGE" ]; then
    echo -e "Starting from: ${GREEN}$START_AT_LANGUAGE${NC}"
fi
if [ "$PARALLEL_JOBS" -gt 1 ]; then
    echo -e "Parallel jobs: ${GREEN}$PARALLEL_JOBS${NC}"
fi
echo ""

# Create temp directory for job logs
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# Build list of languages to process
LANGS_TO_PROCESS=()
start_processing=false
if [ -z "$START_AT_LANGUAGE" ]; then
    start_processing=true
fi

for lang_pair in "${LANGUAGES[@]}"; do
    lang_code="${lang_pair%%:*}"

    # Skip if single language mode and this isn't the target language
    if [ -n "$SINGLE_LANGUAGE" ] && [ "$lang_code" != "$SINGLE_LANGUAGE" ]; then
        continue
    fi

    # Handle start-at logic
    if [ -n "$START_AT_LANGUAGE" ]; then
        if [ "$lang_code" = "$START_AT_LANGUAGE" ]; then
            start_processing=true
        fi
        if [ "$start_processing" = false ]; then
            continue
        fi
    fi

    LANGS_TO_PROCESS+=("$lang_pair")
done

# Process languages
if [ "$PARALLEL_JOBS" -eq 1 ]; then
    # Sequential processing (original behavior, output directly to console)
    for lang_pair in "${LANGS_TO_PROCESS[@]}"; do
        lang_code="${lang_pair%%:*}"
        lang_name="${lang_pair#*:}"
        locale_dir="$LOCALIZATION_DIR/$lang_code"
        locale_file="$locale_dir/UIStrings.json"

        echo -e "${YELLOW}Processing language: $lang_name ($lang_code)${NC}"

        # Create locale directory if it doesn't exist
        if [ ! -d "$locale_dir" ]; then
            echo "Creating directory: $locale_dir"
            mkdir -p "$locale_dir"
        fi

        # Check if locale file exists to determine prompt intro
        if [ -f "$locale_file" ]; then
            intro="We made some recent changes to $REFERENCE_FILE. Please evaluate the $lang_name translation in $locale_file and apply updates if needed. Use $REFERENCE_FILE as the reference."
        else
            intro="We're introducing $lang_name language support for the Picky Relics mod. Please create a translation file at $locale_file. Use $REFERENCE_FILE as the reference."
        fi

        # Translation guidelines
        guidelines="Important translation rules:
  - The file must be valid JSON
  - Keys (like \"pickyrelics:ModInfo\") must NOT be translated - keep them exactly as in English
  - Only translate the strings inside the TEXT arrays
  - Preserve all placeholders: %d, %s (format specifiers) and #y, #r, #b, #g (color codes)
  - TEXT arrays must have the exact same number of strings as the English version
  - For mod context, see CLAUDE.md in the project root"

        prompt="$intro

$guidelines"

        echo -e "${BLUE}Prompting Claude Code for $lang_name translation...${NC}"
        echo ""

        # Execute claude command
        claude --allowedTools "Glob Grep Read Edit($locale_file) Write($locale_file)" -p "$prompt"

        echo ""

        # Validation with retry logic
        attempt=1
        max_attempts=3
        validation_passed=false

        while [ $attempt -le $max_attempts ]; do
            if [ ! -f "$locale_file" ]; then
                echo -e "${RED}Error: Locale file was not created${NC}"
                break
            fi

            if validate_locale "$lang_code" "$lang_name" "$locale_file" "$attempt"; then
                validation_passed=true
                break
            fi

            if [ $attempt -lt $max_attempts ]; then
                echo -e "${YELLOW}Retrying validation (attempt $((attempt + 1))/$max_attempts)...${NC}"
                attempt=$((attempt + 1))
            else
                echo -e "${RED}Validation failed after $max_attempts attempts${NC}"
                break
            fi
        done

        if [ "$validation_passed" = true ]; then
            echo -e "${GREEN}✓ Completed processing for $lang_name [$lang_code]${NC}"
        else
            echo -e "${YELLOW}⚠ Completed $lang_name [$lang_code] with validation issues${NC}"
        fi
        echo "----------------------------------------"
        echo ""
    done
else
    # Parallel processing
    echo -e "${BLUE}Starting parallel processing with $PARALLEL_JOBS jobs...${NC}"
    echo ""

    running_jobs=0
    job_pids=()
    job_langs=()

    for lang_pair in "${LANGS_TO_PROCESS[@]}"; do
        lang_code="${lang_pair%%:*}"
        lang_name="${lang_pair#*:}"
        log_file="$TEMP_DIR/${lang_code}.log"

        # Wait if we've hit the job limit
        while [ $running_jobs -ge $PARALLEL_JOBS ]; do
            # Wait for any job to finish
            wait -n 2>/dev/null || true
            running_jobs=$((running_jobs - 1))
        done

        echo -e "${BLUE}Starting: $lang_name ($lang_code)${NC}"

        # Export functions and variables for subshell
        export -f process_language validate_locale validate_json validate_keys validate_array_lengths validate_placeholders extract_placeholders
        export LOCALIZATION_DIR REFERENCE_FILE GREEN BLUE YELLOW RED NC

        # Start job in background
        process_language "$lang_code" "$lang_name" "$log_file" &
        job_pids+=($!)
        job_langs+=("$lang_code:$lang_name")
        running_jobs=$((running_jobs + 1))
    done

    # Wait for all remaining jobs
    echo ""
    echo -e "${BLUE}Waiting for all jobs to complete...${NC}"
    wait

    # Print results
    echo ""
    echo -e "${BLUE}=== Results ===${NC}"
    echo ""

    success_count=0
    failed_count=0

    for i in "${!job_langs[@]}"; do
        lang_info="${job_langs[$i]}"
        lang_code="${lang_info%%:*}"
        lang_name="${lang_info#*:}"
        log_file="$TEMP_DIR/${lang_code}.log"
        status_file="${log_file}.status"

        if [ -f "$status_file" ] && [ "$(cat "$status_file")" = "SUCCESS" ]; then
            echo -e "${GREEN}✓ $lang_name [$lang_code]${NC}"
            success_count=$((success_count + 1))
        else
            echo -e "${YELLOW}⚠ $lang_name [$lang_code] - see log below${NC}"
            failed_count=$((failed_count + 1))
        fi
    done

    echo ""
    echo -e "Completed: ${GREEN}$success_count succeeded${NC}, ${YELLOW}$failed_count with issues${NC}"

    # Show logs for failed jobs
    if [ $failed_count -gt 0 ]; then
        echo ""
        echo -e "${YELLOW}=== Logs for jobs with issues ===${NC}"
        for i in "${!job_langs[@]}"; do
            lang_info="${job_langs[$i]}"
            lang_code="${lang_info%%:*}"
            lang_name="${lang_info#*:}"
            log_file="$TEMP_DIR/${lang_code}.log"
            status_file="${log_file}.status"

            if [ ! -f "$status_file" ] || [ "$(cat "$status_file")" != "SUCCESS" ]; then
                echo ""
                echo -e "${YELLOW}--- $lang_name [$lang_code] ---${NC}"
                cat "$log_file"
            fi
        done
    fi
fi

echo ""
echo -e "${GREEN}All locale updates completed!${NC}"
echo ""
echo "Verify the translations and make any necessary adjustments."
