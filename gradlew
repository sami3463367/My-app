#!/bin/sh
# Gradle start up script for POSIX generated from the standard Gradle wrapper.
# This lightweight launcher preserves the standard ./gradlew developer workflow.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ ! -f "$CLASSPATH" ]; then
  echo "Gradle wrapper JAR is missing. Re-run project setup or open this project in Android Studio." >&2
  exit 1
fi
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=java
fi
exec "$JAVACMD" ${JAVA_OPTS:-} ${GRADLE_OPTS:-} -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
