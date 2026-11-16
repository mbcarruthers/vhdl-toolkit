#!/usr/bin/env bash
# Downloads VHDL-LS release binaries and stages them into bundled/ with the
# release layout preserved (bin/vhdl_ls beside vhdl_libraries/), which VHDL-LS
# relies on to find the std/ieee libraries.
set -euo pipefail

REPO="VHDL-LS/rust_hdl"
OUT="$(cd "$(dirname "$0")/.." && pwd)/bundled"

api() {
  if [ -n "${GITHUB_TOKEN:-}" ]; then
    curl -fsSL -H "Authorization: Bearer ${GITHUB_TOKEN}" "$1"
  else
    curl -fsSL "$1"
  fi
}

echo "Resolving latest ${REPO} release..."
JSON="$(api "https://api.github.com/repos/${REPO}/releases/latest")"
TAG="$(printf '%s' "$JSON" | python3 -c 'import sys,json;print(json.load(sys.stdin)["tag_name"])')"
echo "Latest release: ${TAG}"

fetch_platform() {
  local match="$1" dest="$2"
  local url
  url="$(printf '%s' "$JSON" | python3 -c "
import sys, json
for a in json.load(sys.stdin)['assets']:
    n = a['name']
    if n.startswith('vhdl_ls-') and '$match' in n and n.endswith('.zip'):
        print(a['browser_download_url']); break
")"
  if [ -z "$url" ]; then
    echo "WARNING: no asset matching '$match' in ${TAG}; skipping" >&2
    return 0
  fi
  echo "Fetching $url"
  local tmp
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/pkg.zip" "$url"
  unzip -q "$tmp/pkg.zip" -d "$tmp/unpacked"
  # The zip contains a single top-level dir holding bin/ and vhdl_libraries/.
  local top
  top="$(find "$tmp/unpacked" -mindepth 1 -maxdepth 1 -type d | head -n1)"
  if [ ! -d "$top/bin" ]; then
    echo "ERROR: unexpected layout in $url (no bin/ under top dir)" >&2
    exit 1
  fi
  mkdir -p "$OUT/$dest"
  cp -R "$top/bin" "$OUT/$dest/"
  if [ -d "$top/vhdl_libraries" ]; then
    cp -R "$top/vhdl_libraries" "$OUT/$dest/"
  fi
  chmod +x "$OUT/$dest"/bin/vhdl_ls* 2>/dev/null || true
  rm -rf "$tmp"
  echo "Staged $dest"
}

rm -rf "$OUT"
fetch_platform "x86_64-unknown-linux-gnu" "linux-x86_64"
fetch_platform "x86_64-pc-windows-msvc" "windows-x86_64"

echo "Done. Contents:"
find "$OUT" -maxdepth 3 | sed "s|$OUT|bundled|"
