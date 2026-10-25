param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleHome = '',
    [switch]$SmokeTest,
    [switch]$CheckMap,
    [switch]$RecordShowcase,
    [switch]$BuilderTest,
    [switch]$BuilderInteractions,
    [switch]$BuilderPlay,
    [switch]$SusChunkTest,
    [switch]$VanillaUiTest,
    [switch]$AppearancePreview,
    [switch]$FeaturePreview,
    [switch]$SkyPreview,
    [switch]$BuilderDemo,
    [switch]$BuilderDemoFirstPerson,
    [switch]$BuilderRedstoneDemo,
    [switch]$BuilderScaffoldTest
)
if ($BuilderScaffoldTest) { $BuilderTest = $true }
if ($BuilderRedstoneDemo) { $BuilderDemoFirstPerson = $true }
if ($BuilderDemoFirstPerson) { $BuilderDemo = $true }
if ($BuilderDemo) { $BuilderTest = $true }
if ($SkyPreview) { $FeaturePreview = $true }
if ($FeaturePreview) { $VanillaUiTest = $true }
if ($AppearancePreview) { $VanillaUiTest = $true }
if ($BuilderInteractions -or $BuilderPlay) { $BuilderTest = $true }
if ($BuilderPlay -and ($BuilderInteractions -or $SusChunkTest -or $RecordShowcase)) { throw 'BuilderPlay must run by itself.' }
if ($RecordShowcase) { $SmokeTest = $true }
if ($BuilderTest) { $SmokeTest = $true }
if ($SusChunkTest) { $SmokeTest = $true }
if ($VanillaUiTest) { $SmokeTest = $true }
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
if (!$JavaHome) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) { $JavaHome = Split-Path (Split-Path $javaCommand.Source) }
}
if (!$JavaHome -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) {
    throw 'Install a Java 25 JDK and set JAVA_HOME, or pass -JavaHome <JDK directory>.'
}
$env:JAVA_HOME = $JavaHome
& (Join-Path $JavaHome 'bin\java.exe') -version
if ($LASTEXITCODE -ne 0) { throw 'Java could not start.' }
$cache = Join-Path $env:LOCALAPPDATA 'Spectre'
New-Item -ItemType Directory -Force -Path $cache | Out-Null
if (!$GradleHome) {
    $GradleHome = Join-Path $cache 'tools\gradle-9.5.1'
    if (!(Test-Path -LiteralPath (Join-Path $GradleHome 'bin\gradle.bat'))) {
        $toolsPath = Join-Path $cache 'tools'
        New-Item -ItemType Directory -Force -Path $toolsPath | Out-Null
        $archive = Join-Path $toolsPath 'gradle-9.5.1-bin.zip'
        $url = 'https://services.gradle.org/distributions/gradle-9.5.1-bin.zip'
        Write-Host 'Downloading Gradle 9.5.1 and verifying its SHA-256 checksum...'
        Invoke-WebRequest $url -OutFile $archive
        $expected = (Invoke-RestMethod "$url.sha256").Trim()
        if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ine $expected) { throw 'Gradle checksum mismatch.' }
        Expand-Archive -LiteralPath $archive -DestinationPath $toolsPath -Force
    }
}
# OneDrive Cloud Files reparse points are not regular inputs to Gradle's snapshotter.
# Stage only this version's source in a private local directory, keeping caches across builds.
$hasher = [Security.Cryptography.SHA256]::Create()
try { $hash = [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($root))).Replace('-','').Substring(0,12) }
finally { $hasher.Dispose() }
$stage = [IO.Path]::GetFullPath((Join-Path $cache "build-$hash"))
$module = Join-Path $stage 'Spectre-26.2'
New-Item -ItemType Directory -Force -Path $module | Out-Null
Copy-Item -LiteralPath (Join-Path $root 'settings.gradle'),(Join-Path $root 'gradle.properties') -Destination $stage -Force
Copy-Item -LiteralPath (Join-Path $root 'Spectre-26.2\build.gradle') -Destination $module -Force
$source = Join-Path $root 'Spectre-26.2\src'
$destination = [IO.Path]::GetFullPath((Join-Path $module 'src'))
if (!$destination.StartsWith($stage + '\',[StringComparison]::OrdinalIgnoreCase) -or !(Test-Path -LiteralPath $source)) { throw 'Unsafe staging path.' }
# /MIR deletes stale files only in the verified private staging src directory.
& robocopy $source $destination /MIR /COPY:DT /DCOPY:T /NFL /NDL /NJH /NJS /NP
if ($LASTEXITCODE -ge 8) { throw "Source staging failed: $LASTEXITCODE" }
$arguments = @('-p',$stage,'--daemon')
if ($SmokeTest) {
    $passFile = Join-Path $module 'run\atlas-smoke-pass.txt'
    if ($BuilderTest) { $passFile = Join-Path $module 'run\builder-smoke-pass.txt' }
    if ($BuilderScaffoldTest) {
        $passFile = Join-Path $module 'run\scaffold-smoke-pass.txt'
        $scaffoldFailure = Join-Path $module 'run\scaffold-smoke-failure.txt'
        if (Test-Path -LiteralPath $scaffoldFailure) { Remove-Item -LiteralPath $scaffoldFailure }
    }
    if ($BuilderInteractions) { $passFile = Join-Path $module 'run\builder-interaction-pass.txt' }
    if ($SusChunkTest) {
        $passFile = Join-Path $module 'run\sus-chunk-pass.txt'
        $failureFile = Join-Path $module 'run\sus-chunk-failure.txt'
        if (Test-Path -LiteralPath $failureFile) { Remove-Item -LiteralPath $failureFile }
    }
    if ($VanillaUiTest) {
        $passFile = Join-Path $module 'run\vanilla-ui-pass.txt'
        $failureFile = Join-Path $module 'run\vanilla-ui-failure.txt'
        if (Test-Path -LiteralPath $failureFile) { Remove-Item -LiteralPath $failureFile }
        if ($AppearancePreview) {
            $readyFile = Join-Path $module 'run\appearance-preview-ready.txt'
            if (Test-Path -LiteralPath $readyFile) { Remove-Item -LiteralPath $readyFile }
        }
    }
    if (Test-Path -LiteralPath $passFile) { Remove-Item -LiteralPath $passFile }
    if ($BuilderTest) {
        $failureFile = Join-Path $module 'run\builder-smoke-failure.txt'
        if (Test-Path -LiteralPath $failureFile) { Remove-Item -LiteralPath $failureFile }
    }
    $arguments += @(':Spectre-26.2:runClient','-PsmokeTest')
    if ($BuilderTest) { $arguments += '-PbuilderTest' }
    if ($BuilderScaffoldTest) { $arguments += '-PbuilderScaffold' }
    if ($BuilderDemo) {
        $arguments += @('-PbuilderDemo',"-PffmpegPath=$((Get-Command ffmpeg -ErrorAction Stop).Source)")
        if ($BuilderDemoFirstPerson) { $arguments += '-PbuilderFirstPerson' }
        if ($BuilderRedstoneDemo) { $arguments += '-PbuilderRedstone' }
        foreach ($demoNotice in @('builder-demo-pass.txt','builder-demo-failure.txt')) {
            $demoNoticePath=Join-Path $module "run\$demoNotice"
            if(Test-Path -LiteralPath $demoNoticePath){Remove-Item -LiteralPath $demoNoticePath}
        }
    }
    if ($BuilderInteractions) { $arguments += '-PbuilderInteractions' }
    if ($BuilderPlay) {
        $arguments += '-PbuilderPlay'
        $readyFile = Join-Path $module 'run\builder-play-ready.txt'
        if (Test-Path -LiteralPath $readyFile) { Remove-Item -LiteralPath $readyFile }
    }
    if ($SusChunkTest) { $arguments += '-PsusChunkTest' }
    if ($VanillaUiTest) { $arguments += '-PvanillaUiTest' }
    if ($AppearancePreview) { $arguments += '-PappearancePreview' }
    if ($FeaturePreview) { $arguments += '-PfeaturePreview' }
    if ($SkyPreview) { $arguments += '-PskyOnly' }
    if ($RecordShowcase) {
        $ffmpegPath = (Get-Command ffmpeg -ErrorAction Stop).Source
        $arguments += @('-Pshowcase',"-PffmpegPath=$ffmpegPath")
        $videoPass = Join-Path $module 'run\spectre-recording-pass.txt'
        if (Test-Path -LiteralPath $videoPass) { Remove-Item -LiteralPath $videoPass }
    }
}
else {
    # Smoke runs inject a test entrypoint into generated resources. A production
    # package must start clean so neither those resources nor test classes survive.
    $buildOutput = [IO.Path]::GetFullPath((Join-Path $module 'build'))
    if (!$buildOutput.StartsWith($stage + '\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe build output path.' }
    $arguments += @(':Spectre-26.2:clean',':Spectre-26.2:build')
    if ($CheckMap) { $arguments += @(':Spectre-26.2:atlasChecks',':Spectre-26.2:atlasGpuChecks') }
}
& (Join-Path $GradleHome 'bin\gradle.bat') @arguments
if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
if ($BuilderDemo) {
    $demoRun = Join-Path $module 'run'
    if (!(Test-Path -LiteralPath (Join-Path $demoRun 'builder-demo-pass.txt'))) { throw 'Builder demo failed; inspect staging logs.' }
    $demoOutput = Join-Path $root 'validation'
    if ($BuilderDemoFirstPerson) { $demoOutput = Join-Path $demoOutput 'builder-first-person'; New-Item -ItemType Directory -Force -Path $demoOutput | Out-Null }
    if ($BuilderRedstoneDemo) { $demoOutput = Join-Path $root 'validation\builder-redstone'; New-Item -ItemType Directory -Force -Path $demoOutput | Out-Null }
    foreach ($name in @('builder-demo-raw.mp4','spectre-tour-chapters.tsv','builder-demo-pass.txt')) {
        Copy-Item -LiteralPath (Join-Path $demoRun $name) -Destination $demoOutput -Force
    }
    & ffmpeg -hide_banner -loglevel error -y -sseof -2 -i (Join-Path $demoOutput 'builder-demo-raw.mp4') -frames:v 1 -update 1 (Join-Path $demoOutput 'builder-demo-finished.png')
    if($LASTEXITCODE -ne 0){throw 'Could not extract finished demo frame'}
    return
}
if ($BuilderPlay) { Write-Host 'Interactive builder session closed.'; return }
if ($AppearancePreview) { Write-Host 'Interactive appearance preview closed.'; return }
if ($FeaturePreview) { Write-Host 'Interactive feature preview closed.'; return }
if ($SmokeTest) {
    $latestPass = Join-Path $module 'run\atlas-smoke-pass.txt'
    if ($BuilderTest) { $latestPass = Join-Path $module 'run\builder-smoke-pass.txt' }
    if ($BuilderScaffoldTest) { $latestPass = Join-Path $module 'run\scaffold-smoke-pass.txt' }
    if ($BuilderInteractions) { $latestPass = Join-Path $module 'run\builder-interaction-pass.txt' }
    if ($SusChunkTest) { $latestPass = Join-Path $module 'run\sus-chunk-pass.txt' }
    if ($VanillaUiTest) { $latestPass = Join-Path $module 'run\vanilla-ui-pass.txt' }
    if (!(Test-Path -LiteralPath $latestPass)) { throw 'Client exited without passing the smoke test. Inspect the staging run/logs/latest.log.' }
    Write-Host "Smoke evidence: $module\run"
    if ($VanillaUiTest) {
        Get-ChildItem -LiteralPath (Join-Path $module 'run') -Filter 'vanilla-ui-*' -File | Copy-Item -Destination (Join-Path $root 'validation') -Force
    }
    if ($SusChunkTest) {
        foreach ($capture in @('sus-chunk-pass.txt','sus-chunk-activity.png','sus-chunk-natural.png','sus-chunk-classic.png','sus-objects-panel.png','sus-geode-panel.png','sus-objects-distance.png','sus-growth-panel.png')) {
            Copy-Item -LiteralPath (Join-Path $module "run\$capture") -Destination (Join-Path $root 'validation') -Force
        }
    }
    if ($BuilderInteractions) {
        Copy-Item -LiteralPath $latestPass -Destination (Join-Path $root 'validation\builder-interaction-pass.txt') -Force
        Copy-Item -LiteralPath (Join-Path $module 'run\builder-walking-ghost.png') -Destination (Join-Path $root 'validation\builder-walking-ghost.png') -Force
        Copy-Item -LiteralPath (Join-Path $module 'run\builder-textured-ghost.png') -Destination (Join-Path $root 'validation\builder-textured-ghost.png') -Force
    } elseif ($BuilderScaffoldTest) {
        Copy-Item -LiteralPath $latestPass -Destination (Join-Path $root 'validation\scaffold-smoke-pass.txt') -Force
    } elseif ($BuilderTest) {
        $builderEvidence = Join-Path $root 'validation'
        Copy-Item -LiteralPath $latestPass -Destination (Join-Path $builderEvidence 'builder-smoke-pass.txt') -Force
        foreach ($capture in @('builder-built-0.png','builder-built-1.png','builder-ghost-0.png','builder-ghost-1.png','builder-placement-gui.png')) {
            Copy-Item -LiteralPath (Join-Path $module "run\$capture") -Destination $builderEvidence -Force
        }
    }
    if ($RecordShowcase) {
        if (!(Test-Path -LiteralPath (Join-Path $module 'run\spectre-recording-pass.txt'))) { throw 'Recording did not finish successfully.' }
        $videoOutput = Join-Path $root 'validation'
        Copy-Item -LiteralPath (Join-Path $module 'run\spectre-continuous-tour.mp4'),(Join-Path $module 'run\spectre-tour-chapters.tsv') -Destination $videoOutput -Force
        Write-Host "Continuous recording: $videoOutput\spectre-continuous-tour.mp4"
    }
} else {
    $dist = Join-Path $root 'dist'
    New-Item -ItemType Directory -Force -Path $dist | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $module 'build\libs') -Filter 'spectre-*.jar' | Copy-Item -Destination $dist -Force
    Write-Host "26.2 artifacts: $dist"
}
