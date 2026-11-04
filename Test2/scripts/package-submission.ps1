$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$projectRoot = Split-Path -Parent $PSScriptRoot
$apkSource = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apkSource)) {
    throw 'Build the debug APK first: .\gradlew.bat :app:assembleDebug'
}
$outputDirectory = Join-Path $projectRoot 'submission'
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$sourceArchive = Join-Path $outputDirectory 'EmployeeManagement-source.zip'
$apkDestination = Join-Path $outputDirectory 'EmployeeManagement-debug.apk'

# Whitelist source/build configuration; exclude caches, local SDK paths and generated builds.
$relativeFiles = @(
    '.gitignore', 'README.md', 'SUBMISSION_GUIDE.md', 'build.gradle.kts',
    'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'app\build.gradle.kts'
)
$sourceFiles = @($relativeFiles | ForEach-Object { Join-Path $projectRoot $_ })
foreach ($directory in @('app\src', 'app\schemas', 'gradle', 'scripts', 'docs')) {
    $sourceFiles += Get-ChildItem -LiteralPath (Join-Path $projectRoot $directory) -Recurse -File |
        Select-Object -ExpandProperty FullName
}

$stream = [System.IO.File]::Open($sourceArchive, [System.IO.FileMode]::Create)
$archive = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($sourceFile in $sourceFiles) {
        $entryName = $sourceFile.Substring($projectRoot.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive, $sourceFile, $entryName, [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
} finally {
    $archive.Dispose()
    $stream.Dispose()
}
Copy-Item -LiteralPath $apkSource -Destination $apkDestination -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'SUBMISSION_GUIDE.md') -Destination $outputDirectory -Force
Get-FileHash -LiteralPath $apkDestination, $sourceArchive -Algorithm SHA256 |
    ForEach-Object { '{0}  {1}' -f $_.Hash, (Split-Path -Leaf $_.Path) } |
    Set-Content -LiteralPath (Join-Path $outputDirectory 'SHA256SUMS.txt') -Encoding ASCII
Get-ChildItem -LiteralPath $outputDirectory -File | Select-Object Name, Length
