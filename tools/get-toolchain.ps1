# Downloads the tools needed to build Fox Launcher into <repo>\.toolchain and checks each one.
#
#   powershell -ExecutionPolicy Bypass -File tools\get-toolchain.ps1
#   powershell -ExecutionPolicy Bypass -File tools\get-toolchain.ps1 -PlanOnly   (show, do not download)
#
# What it gets, about 320 MB in all:
#   - Android build-tools 34.0.0 and the Android 7.1 platform, from Google's own repository.
#     Their SHA-1 comes from Google's index file.
#   - A JDK 17 (Eclipse Temurin), from Adoptium. Its SHA-256 comes from Adoptium's API.
param([string]$Dest, [switch]$PlanOnly)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $Dest) { $Dest = Join-Path (Split-Path $PSScriptRoot -Parent) '.toolchain' }

$google = 'https://dl.google.com/android/repository/'
[xml]$index = (Invoke-WebRequest ($google + 'repository2-3.xml') -UseBasicParsing -TimeoutSec 60).Content
function GoogleArchive([string]$path) {
    $package = $index.DocumentElement.remotePackage | Where-Object { $_.path -eq $path } | Select-Object -First 1
    if (-not $package) { throw "Not in Google's index: $path" }
    $archives = @($package.archives.archive)
    $archive = $archives | Where-Object { $_.'host-os' -eq 'windows' } | Select-Object -First 1
    if (-not $archive) { $archive = $archives | Where-Object { -not $_.'host-os' } | Select-Object -First 1 }
    $sum = $archive.complete.checksum
    if ($sum -isnot [string]) { $sum = $sum.'#text' }
    return @{ Url = $google + $archive.complete.url; Hash = $sum; Algorithm = 'SHA1' }
}

$jdkInfo = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse' -TimeoutSec 60
$jdkPackage = @($jdkInfo)[0].binary.package

$buildTools = GoogleArchive 'build-tools;34.0.0'
$buildTools.Name = 'bt'
$platform = GoogleArchive 'platforms;android-25'
$platform.Name = 'plat'
$jdk = @{ Name = 'jdk'; Url = $jdkPackage.link; Hash = $jdkPackage.checksum; Algorithm = 'SHA256' }
$items = @($buildTools, $platform, $jdk)

if ($PlanOnly) {
    foreach ($item in $items) { '{0,-5} {1} {2} {3}' -f $item.Name, $item.Algorithm, $item.Hash, $item.Url }
    return
}

New-Item -ItemType Directory -Force $Dest, (Join-Path $Dest 'dl') | Out-Null
foreach ($item in $items) {
    $zip = Join-Path $Dest ('dl\' + $item.Name + '.zip')
    if (-not (Test-Path $zip)) {
        Write-Host ('Downloading ' + $item.Url)
        Invoke-WebRequest $item.Url -OutFile $zip -UseBasicParsing -TimeoutSec 1800
    }
    $actual = (Get-FileHash $zip -Algorithm $item.Algorithm).Hash.ToLower()
    if ($actual -ne $item.Hash.ToLower()) { throw ('Checksum does not match for ' + $item.Name + '. Delete ' + $zip + ' and run again.') }
    Expand-Archive $zip (Join-Path $Dest $item.Name) -Force
    Write-Host ($item.Name + ' ready, checksum correct')
}
Write-Host ('Tools are in ' + $Dest + '. Now run launcher\build.ps1')
