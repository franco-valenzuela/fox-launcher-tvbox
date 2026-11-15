# Builds Fox Launcher into launcher\build\foxlauncher.apk with the bare Android build tools.
# No Gradle, no Android Studio.
#
#   powershell -ExecutionPolicy Bypass -File launcher\build.ps1
#
# The tools (a JDK, Android build-tools 34 and the Android 7.1 platform) are looked for in
# -Toolchain, then in the FOX_TOOLCHAIN environment variable, then in <repo>\.toolchain.
# tools\get-toolchain.ps1 downloads them there.
#
# Signing: set FOX_KEYSTORE (keystore file) and FOX_KEYSTORE_PASS_FILE (a text file holding its
# password) to sign with your own key. Without them the script makes a throwaway debug key in
# launcher\debug.keystore, which is fine for your own box. Neither file belongs in the repo.
param([string]$Toolchain = $env:FOX_TOOLCHAIN)

$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
if (-not $Toolchain) { $Toolchain = Join-Path (Split-Path $here -Parent) '.toolchain' }
if (-not (Test-Path $Toolchain)) { throw "Toolchain folder not found: $Toolchain. Run tools\get-toolchain.ps1 first." }

$bt = (Get-ChildItem (Join-Path $Toolchain 'bt') -Directory | Select-Object -First 1).FullName
$androidJar = (Get-ChildItem (Join-Path $Toolchain 'plat') -Recurse -Filter android.jar | Select-Object -First 1).FullName
$jdkBin = Join-Path (Get-ChildItem (Join-Path $Toolchain 'jdk') -Directory | Select-Object -First 1).FullName 'bin'
$out = Join-Path $here 'build'

function Run($exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "$exe failed with exit code $LASTEXITCODE" }
}

New-Item -ItemType Directory -Force $out | Out-Null
Get-ChildItem $out | Remove-Item -Recurse -Force -Confirm:$false
New-Item -ItemType Directory -Force "$out\classes", "$out\dex" | Out-Null

# Resources and manifest.
Run "$bt\aapt2.exe" @('compile', '--dir', "$here\res", '-o', "$out\res.zip")
Run "$bt\aapt2.exe" @('link', '-o', "$out\base.apk", '-I', $androidJar,
    '--manifest', "$here\AndroidManifest.xml", '--min-sdk-version', '25', '--target-sdk-version', '25',
    "$out\res.zip")

# Java, compiled against the Android 7.1 API so nothing newer can slip in.
$sources = (Get-ChildItem "$here\src" -Recurse -Filter *.java).FullName
Run "$jdkBin\javac.exe" (@('-source', '8', '-target', '8', '-encoding', 'UTF-8', '-Xlint:-options',
    '-nowarn', '-bootclasspath', $androidJar, '-d', "$out\classes") + $sources)

# Dex.
$classes = (Get-ChildItem "$out\classes" -Recurse -Filter *.class).FullName
Run "$jdkBin\java.exe" (@('-cp', "$bt\lib\d8.jar", 'com.android.tools.r8.D8', '--release',
    '--min-api', '25', '--lib', $androidJar, '--output', "$out\dex") + $classes)

# Package and align.
Push-Location "$out\dex"
try { Run "$bt\aapt.exe" @('add', "$out\base.apk", 'classes.dex') } finally { Pop-Location }
Run "$bt\zipalign.exe" @('-f', '4', "$out\base.apk", "$out\aligned.apk")

# Sign.
$keystore = $env:FOX_KEYSTORE
$passFile = $env:FOX_KEYSTORE_PASS_FILE
if (-not $keystore -or -not $passFile) {
    $keystore = Join-Path $here 'debug.keystore'
    $passFile = Join-Path $out 'debug-pass.txt'
    [IO.File]::WriteAllText($passFile, 'android')
    if (-not (Test-Path $keystore)) {
        Run "$jdkBin\keytool.exe" @('-genkeypair', '-keystore', $keystore, '-storepass:file', $passFile,
            '-alias', 'debug', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
            '-dname', 'CN=Fox Launcher local build')
    }
    Write-Host 'Signed with a local throwaway key (launcher\debug.keystore).'
}
$apk = Join-Path $out 'foxlauncher.apk'
Run "$jdkBin\java.exe" @('-jar', "$bt\lib\apksigner.jar", 'sign', '--ks', $keystore,
    '--ks-pass', "file:$passFile", '--ks-key-alias', 'debug', '--out', $apk, "$out\aligned.apk")

$hash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
Write-Host ("{0}  {1} bytes  sha256 {2}" -f $apk, (Get-Item $apk).Length, $hash)
