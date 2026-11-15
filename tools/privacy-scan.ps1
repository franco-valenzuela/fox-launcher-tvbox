# Looks for personal data in the files of this project: private network addresses, hardware
# (MAC) addresses, e-mail addresses and personal folder paths. Exits with 1 when it finds any.
#
#   powershell -ExecutionPolicy Bypass -File tools\privacy-scan.ps1
#   powershell -ExecutionPolicy Bypass -File tools\privacy-scan.ps1 -Root <folder>
#   powershell -ExecutionPolicy Bypass -File tools\privacy-scan.ps1 -Extra <file>
#
# -Extra names a text file of your own with one more pattern per line (your Wi-Fi name, your
# surname, ...). Keep that file outside the project: it is itself personal data.
#
# It cannot read text inside pictures. Check screenshots with your own eyes.
param([string]$Root, [string]$Extra)

$ErrorActionPreference = 'Stop'
if (-not $Root) { $Root = Split-Path $PSScriptRoot -Parent }

$checks = [ordered]@{
    'private network address'        = '(^|[^0-9.])(10\.[0-9]{1,3}|192\.168|172\.(1[6-9]|2[0-9]|3[01]))\.[0-9]{1,3}\.[0-9]{1,3}([^0-9.]|$)'
    'hardware (MAC) address'         = '(^|[^0-9A-Fa-f:-])([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}([^0-9A-Fa-f:-]|$)'
    'e-mail address'                 = '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}'
    'Windows personal folder'        = '[A-Za-z]:\\Users\\[A-Za-z0-9._ -]+'
    'macOS or Linux personal folder' = '(/home/|/Users/)[A-Za-z0-9._-]+'
}
if ($Extra) {
    $n = 0
    foreach ($line in [IO.File]::ReadAllLines($Extra, [Text.Encoding]::UTF8)) {
        if ($line.Trim() -and -not $line.StartsWith('#')) { $n++; $checks["your own pattern $n"] = $line.Trim() }
    }
}
# Addresses that are meant to be public.
$allowed = 'noreply@anthropic\.com|users\.noreply\.github\.com|@example\.(com|org)|git@github\.com'
$skipFolders = '\\(\.git|\.toolchain|build)(\\|$)'
$binary = '\.(png|jpg|jpeg|gif|webp|ico|apk|zip|keystore|jks|jar|dex|pdf)$'

$files = Get-ChildItem $Root -Recurse -File -Force | Where-Object {
    $_.FullName -notmatch $skipFolders -and $_.Name -notmatch $binary -and $_.Name -notlike 'privacy-scan.*'
}
$found = $false
foreach ($name in $checks.Keys) {
    $hits = $files | Select-String -Pattern $checks[$name] -Encoding UTF8 | Where-Object { $_.Line -notmatch $allowed }
    if ($hits) {
        $found = $true
        "Possible ${name}:"
        foreach ($h in $hits) { '  {0}:{1}: {2}' -f $h.Path.Substring($Root.Length + 1), $h.LineNumber, $h.Line.Trim() }
        ''
    }
}
if ($found) {
    'Remove or replace the lines above before you share this.'
    exit 1
}
'Privacy check passed: nothing personal found in the text files.'
