#!/usr/bin/env bash
# Looks for personal data in the files of this project: private network addresses, hardware
# (MAC) addresses, e-mail addresses and personal folder paths. Exits with 1 when it finds any.
#
#   bash tools/privacy-scan.sh            scan the project
#   bash tools/privacy-scan.sh <folder>   scan another folder
#
# It cannot read text inside pictures. Check screenshots with your own eyes.
set -u
root="${1:-$(cd "$(dirname "$0")/.." && pwd)}"

declare -a names=(
  "private network address"
  "hardware (MAC) address"
  "e-mail address"
  "Windows personal folder"
  "macOS or Linux personal folder"
)
declare -a patterns=(
  '(^|[^0-9.])(10\.[0-9]{1,3}|192\.168|172\.(1[6-9]|2[0-9]|3[01]))\.[0-9]{1,3}\.[0-9]{1,3}([^0-9.]|$)'
  '(^|[^0-9A-Fa-f:-])([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}([^0-9A-Fa-f:-]|$)'
  '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}'
  '[A-Za-z]:\\Users\\[A-Za-z0-9._ -]+'
  '(/home/|/Users/)[A-Za-z0-9._-]+'
)
# Addresses that are meant to be public.
allowed='noreply@anthropic\.com|users\.noreply\.github\.com|@example\.(com|org)|git@github\.com'

found=0
for i in "${!patterns[@]}"; do
  hits=$(grep -rnIE --exclude-dir=.git --exclude-dir=.toolchain --exclude-dir=build \
    --exclude='privacy-scan.sh' --exclude='privacy-scan.ps1' -- "${patterns[$i]}" "$root" \
    | grep -vE "$allowed" || true)
  if [ -n "$hits" ]; then
    found=1
    echo "Possible ${names[$i]}:"
    echo "$hits" | sed 's/^/  /'
    echo
  fi
done

if [ "$found" -eq 0 ]; then
  echo "Privacy check passed: nothing personal found in the text files."
else
  echo "Remove or replace the lines above before you share this."
fi
exit "$found"
