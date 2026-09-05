#!/usr/bin/env bash
# Three tagged, silent MP3 samples. They are not downloaded music.
set -euo pipefail
cd "$(dirname "$0")/.."
out=app/src/androidTest/assets/local-fixtures
mkdir -p "$out"
for name in a b unknown; do
  args=(-hide_banner -loglevel warning -y -f lavfi -i anullsrc=r=22050:cl=mono -t 60 -c:a libmp3lame -q:a 9)
  if [[ "$name" != unknown ]]; then
    label="$(printf '%s' "$name" | tr '[:lower:]' '[:upper:]')"
    artist="Browse Artist $label"
    album="Browse Album $label"
    args+=(-metadata "artist=$artist" -metadata "album_artist=$artist" -metadata "album=$album")
  fi
  ffmpeg "${args[@]}" "$out/$name.mp3"
done
