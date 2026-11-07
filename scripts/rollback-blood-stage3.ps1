param([switch]$Apply)

# Default: validate and preview only. Never use git reset/clean or restore unrelated files.
$ErrorActionPreference = 'Stop'
function Get-Stage3Hash([string]$FilePath) {
    $stream = [IO.File]::OpenRead($FilePath)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '') }
    finally { $algorithm.Dispose(); $stream.Dispose() }
}
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$baselineRoot = Join-Path $projectRoot '.codex-tmp/blood-stage3-baseline-20261106'
$manifestPath = Join-Path $baselineRoot 'manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath)) { throw "Missing local rollback manifest: $manifestPath" }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$operations = @()
$conflicts = @()
foreach ($entry in $manifest.files) {
    $target = [IO.Path]::GetFullPath((Join-Path $projectRoot $entry.path))
    $sourceRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'src')) + [IO.Path]::DirectorySeparatorChar
    if (-not $target.StartsWith($sourceRoot, [StringComparison]::OrdinalIgnoreCase)) { throw "Out-of-scope path: $target" }
    $backup = [IO.Path]::GetFullPath((Join-Path $baselineRoot $entry.path))
    if (-not $backup.StartsWith($baselineRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw "Invalid backup path: $backup" }
    if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { $conflicts += "$($entry.path): current file missing"; continue }
    if ((Get-Stage3Hash $target) -ne $entry.installedSha256) {
        $conflicts += "$($entry.path): changed after stage 3"; continue
    }
    if ($entry.existed) {
        if (-not (Test-Path -LiteralPath $backup -PathType Leaf)) { $conflicts += "$($entry.path): baseline missing"; continue }
        if ((Get-Stage3Hash $backup) -ne $entry.baselineSha256) {
            $conflicts += "$($entry.path): baseline checksum mismatch"; continue
        }
    }
    $operations += [PSCustomObject]@{ Path = $entry.path; Target = $target; Backup = $backup; Existed = $entry.existed }
}
if ($conflicts.Count -gt 0) {
    $conflicts | ForEach-Object { Write-Output $_ }
    throw 'No files changed. Resolve later edits manually before rollback.'
}
$operations | ForEach-Object { Write-Output ((@{ $true = 'RESTORE'; $false = 'REMOVE' }[$_.Existed]) + ' ' + $_.Path) }
if (-not $Apply) { Write-Output "Preview only: $($operations.Count) verified files. Use -Apply to restore the baseline."; exit 0 }

# Save every installed file before mutating any, for recovery if a copy fails midway.
$rescueRoot = Join-Path $baselineRoot ('before-rollback-' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
foreach ($operation in $operations) {
    $rescue = Join-Path $rescueRoot $operation.Path
    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($rescue)) | Out-Null
    Copy-Item -LiteralPath $operation.Target -Destination $rescue
}
foreach ($operation in $operations) {
    if ($operation.Existed) { Copy-Item -LiteralPath $operation.Backup -Destination $operation.Target -Force }
    else { Remove-Item -LiteralPath $operation.Target }
}
Write-Output "Stage 3 source restored. Recovery snapshot: $rescueRoot"
Write-Output 'No world data, config, Gradle properties, Git index or unrelated files were changed. Recompile before launching.'
