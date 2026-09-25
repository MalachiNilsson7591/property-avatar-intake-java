#!/usr/bin/env sh
set -eu

mkdir -p target/test-classes
javac -d target/test-classes $(find src/main/java src/test/java -name '*.java')
java -cp target/test-classes cc.infrai.property.PropertyProfileTest
