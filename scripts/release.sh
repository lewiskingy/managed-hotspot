#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${RELEASE_STORE_FILE:?Set the private upload keystore path}"
: "${RELEASE_STORE_PASSWORD:?Set the upload keystore password}"
: "${RELEASE_KEY_ALIAS:?Set the upload key alias}"
: "${RELEASE_KEY_PASSWORD:?Set the upload key password}"
# CI pins Gradle 8.11.1. Supply an equivalent local Gradle installation.
gradle --no-daemon :app:bundleRelease :app:assembleRelease
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
