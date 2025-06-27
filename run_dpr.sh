#!/bin/bash
# Wrapper script to compile and run the DataPipelineRunner.
# Usage: ./run_dpr.sh <command> [options]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/src/com/pipeline/DataPipelineRunner.java"
OUT="$SCRIPT_DIR/out"
CLASS_FILE="$OUT/com/pipeline/DataPipelineRunner.class"

# Compile if necessary
if [ ! -f "$CLASS_FILE" ] || [ "$SRC" -nt "$CLASS_FILE" ]; then
    mkdir -p "$OUT"
    javac -d "$OUT" "$SRC"
fi

if [ $# -eq 0 ]; then
    echo "Usage: $0 <command> [options]" >&2
    exit 1
fi

CMD="$1"
shift
ARGS=("$@")

# Validate command and options
case "$CMD" in
    bridge)
        if [ ${#ARGS[@]} -ne 3 ]; then
            echo "Usage: $0 bridge <extension> <mode> <collectionId>" >&2
            exit 1
        fi
        EXT="${ARGS[0]}"
        MODE="${ARGS[1]}"
        [[ "$EXT" =~ ^(scd|json)$ ]] || { echo "Invalid extension: $EXT" >&2; exit 1; }
        [[ "$MODE" =~ ^(static|dynamic)$ ]] || { echo "Invalid mode: $MODE" >&2; exit 1; }
        ;;
    tea)
        if [ ${#ARGS[@]} -ne 3 ]; then
            echo "Usage: $0 tea <collectionId> <listenerIP> <port>" >&2
            exit 1
        fi
        PORT="${ARGS[2]}"
        [[ "$PORT" =~ ^[0-9]+$ ]] || { echo "Invalid port: $PORT" >&2; exit 1; }
        ;;
    gateway)
        if [ ${#ARGS[@]} -lt 3 ] || [ ${#ARGS[@]} -gt 4 ]; then
            echo "Usage: $0 gateway <collectionId> <operation> <mode> [prevStep]" >&2
            exit 1
        fi
        OP="${ARGS[1]}"
        MODE="${ARGS[2]}"
        [[ "$OP" =~ ^(convert-json|convert-vector|index-json|index-scd)$ ]] || { echo "Invalid operation: $OP" >&2; exit 1; }
        [[ "$MODE" =~ ^(static|dynamic)$ ]] || { echo "Invalid mode: $MODE" >&2; exit 1; }
        if [ ${#ARGS[@]} -eq 4 ]; then
            PREV="${ARGS[3]}"
            [[ "$PREV" =~ ^(bridge|tea|convert-json|convert-vector|index-json|index-scd)$ ]] || {
                echo "Invalid prevStep: $PREV" >&2; exit 1; }
        fi
        ;;
    *)
        echo "Unknown command: $CMD" >&2
        echo "Available commands: bridge, tea, gateway" >&2
        exit 1
        ;;
esac

cd "$SCRIPT_DIR"
java -cp "$OUT" com.pipeline.DataPipelineRunner "$CMD" "${ARGS[@]}"
