#!/bin/bash
# Wrapper script to compile and run the DataPipelineRunner.
# Usage: ./run_dpr.sh <command> [options]

set -e

WORKING_DIRECTORY="$(cd "$(dirname "$0")" && pwd)"
SF1_HOME=$(
    cd "$WORKING_DIRECTORY/../"
    pwd
)

JAVA_HOME=$SF1_HOME/jdk
export JAVA_HOME

CLASS_DIRECTORY="$SF1_HOME/lib/custom/DataPipelineRunner"
SRC="$CLASS_DIRECTORY/DataPipelineRunner.java"
OUT="$CLASS_DIRECTORY/out"
CLASS_FILE="$OUT/DataPipelineRunner.class"

# Compile if necessary
if [ ! -f "$CLASS_FILE" ] || [ "$SRC" -nt "$CLASS_FILE" ]; then
    mkdir -p "$OUT"
    $JAVA_HOME/bin/javac -d "$OUT" "$SRC"
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

cd "$WORKING_DIRECTORY"
$JAVA_HOME/bin/java -cp "$OUT" com.pipeline.DataPipelineRunner "$CLASS_DIRECTORY/dpr.properties" "$CMD" "${ARGS[@]}"
