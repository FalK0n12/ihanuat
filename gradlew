#!/bin/sh

set -eu

PRG="$0"
while [ -h "$PRG" ]; do
    ls_output=$(ls -ld "$PRG")
    link=${ls_output#*-> }
    case "$link" in
        /*) PRG="$link" ;;
        *) PRG=$(dirname "$PRG")/"$link" ;;
    esac
done

APP_HOME=$(CDPATH= cd -- "$(dirname "$PRG")" && pwd -P)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "${JAVA_HOME:-}" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi

exec "$JAVACMD" ${JAVA_OPTS:-} ${GRADLE_OPTS:-} -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
