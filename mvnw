#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper
#
# This is a reduced but standard Apache Maven Wrapper script.
# It will download maven-wrapper.jar if missing.
# ----------------------------------------------------------------------------

MVNW_REPOURL=""

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

# OS specific support (must be 'true' or 'false').
cygwin=false
mingw=false
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  MINGW*) mingw=true ;;
esac

MAVEN_PROJECTBASEDIR="$BASE_DIR"
WRAPPER_DIR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"
WRAPPER_PROPERTIES="$WRAPPER_DIR/maven-wrapper.properties"

if [ ! -f "$WRAPPER_PROPERTIES" ]; then
  echo "Missing $WRAPPER_PROPERTIES" >&2
  exit 1
fi

# Download wrapper jar if needed
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading Maven wrapper..."
  WRAPPER_URL=$(grep -E '^wrapperUrl=' "$WRAPPER_PROPERTIES" | cut -d= -f2-)
  if [ -z "$WRAPPER_URL" ]; then
    echo "wrapperUrl is not set in $WRAPPER_PROPERTIES" >&2
    exit 1
  fi

  mkdir -p "$WRAPPER_DIR" || exit 1

  # Use curl or wget
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR" || exit 1
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR" || exit 1
  else
    echo "Neither curl nor wget is available to download Maven wrapper." >&2
    exit 1
  fi
fi

# Execute Maven Wrapper main class
JAVA_CMD="java"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
fi

exec "$JAVA_CMD" \
  -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" \
  -classpath "$WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain \
  "$@"
