#!/bin/sh
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot}"
"$JAVA_HOME/bin/java.exe" -Xmx2048m -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
