# =============================================================================
# Title:       Fast Recursive File Copier (PowerShell Edition)
# Description: High-performance file copier with .codeignore support
# Notes:
#   - Filename format: BaseName.OriginalExt.InsertToken.DestExt
#     Example: "foo.py" -> "foo.py.txt.md" when -InsertToken txt and -DestExt md
#   - No hardcoded "txt" — provide your own via -InsertToken or interactively.
#   - Supports non-interactive parameters and multiple mappings.
#   - Supports -Ignore parameter for filename patterns with wildcards
# =============================================================================

[CmdletBinding()]
param(
    # Provide explicit mappings like "py=md", "js=txt"
    [Parameter(Mandatory = $false)]
    [string[]]$Map,

    # Or provide a list of source extensions with a single destination extension
    [Parameter(Mandatory = $false)]
    [string[]]$SourceExts,

    [Parameter(Mandatory = $false)]
    [string]$DestExt,

    # Token inserted after the original extension and before the destination extension
    # Example: -InsertToken txt => BaseName.OriginalExt.txt.DestExt
    [Parameter(Mandatory = $false)]
    [string]$InsertToken,

    # Optional: filename patterns to ignore (supports wildcards)
    # Example: -Ignore "*.test.*","*.spec.*","LICENSE*","README*"
    [Parameter(Mandatory = $false)]
    [string[]]$Ignore,

    # Optional: change destination folder name
    [Parameter(Mandatory = $false)]
    [string]$DestFolderName = "codebase-txt",

    # Optional: search root (defaults to script folder)
    [Parameter(Mandatory = $false)]
    [string]$Root = $PSScriptRoot,

    # If set, skips final "Press any key to exit..." pause
    [switch]$NonInteractive
)

# =============================================================================
# Preferences
# =============================================================================
$ErrorActionPreference = "Stop"
$ProgressPreference = "Continue"

# =============================================================================
# Helpers
# =============================================================================
function Normalize-Ext {
    param([string]$Ext)
    if ([string]::IsNullOrWhiteSpace($Ext)) { return $null }
    if ($Ext.StartsWith(".")) { return $Ext }
    return ".$Ext"
}

function Normalize-NameSegment {
    param([string]$Seg)
    if ([string]::IsNullOrWhiteSpace($Seg)) { return $null }
    return $Seg.Trim().TrimStart(".")
}

function Test-ShouldIgnorePath {
    param([string]$Path)
    foreach ($pattern in $IgnorePathPatterns) {
        if ([string]::IsNullOrWhiteSpace($pattern)) { continue }
        # Treat pattern as a wildcard fragment (e.g., "node_modules", "dist", "bin*")
        if ($Path -like "*$pattern*") { return $true }
    }
    return $false
}

function Test-ShouldIgnoreFile {
    param([string]$FileName)
    foreach ($pattern in $IgnoreFilePatterns) {
        if ([string]::IsNullOrWhiteSpace($pattern)) { continue }
        # Use PowerShell's wildcard matching
        if ($FileName -like $pattern) { return $true }
    }
    return $false
}

function Get-SafeFileName {
    param(
        [string]$DestPath,
        [string]$BaseName,
        [string]$Extension
    )

    $FullPath = Join-Path $DestPath "$BaseName$Extension"
    if (-not (Test-Path $FullPath)) {
        return $FullPath
    }

    for ($i = 1; $i -lt 1000; $i++) {
        $FullPath = Join-Path $DestPath "$BaseName($i)$Extension"
        if (-not (Test-Path $FullPath)) {
            return $FullPath
        }
    }

    throw "Unable to find unique filename for $BaseName"
}

function Process-Mapping {
    param(
        [string]$SourceExt,
        [string]$DestExt,
        [string]$InsertToken
    )

    $SourceExt = Normalize-Ext $SourceExt
    $DestExt   = Normalize-Ext $DestExt
    $InsertSeg = Normalize-NameSegment $InsertToken

    if (-not $SourceExt -or -not $DestExt) {
        Write-Host "Invalid mapping: SourceExt='$SourceExt' DestExt='$DestExt'." -ForegroundColor Red
        return
    }

    Write-Host ""
    Write-Host "Searching for $SourceExt files..." -ForegroundColor Yellow

    $Files = Get-ChildItem -Path $Root -Filter "*$SourceExt" -Recurse -File |
        Where-Object {
            -not (Test-ShouldIgnorePath $_.DirectoryName) -and
            -not (Test-ShouldIgnoreFile $_.Name) -and
            ($_.FullName -notlike (Join-Path $DestDir "*"))
        }

    $FileCount = @($Files).Count
    if ($FileCount -eq 0) {
        Write-Host "No $SourceExt files found (after applying ignore patterns)." -ForegroundColor Red
        return
    }

    Write-Host "Found $FileCount file(s) to copy..." -ForegroundColor Green

    $i = 0
    $SessionCount = 0
    foreach ($File in $Files) {
        $i++
        $PercentComplete = [int](($i / $FileCount) * 100)
        Write-Progress -Activity "Copying files ($SourceExt -> $DestExt)" `
            -Status "Processing: $($File.Name)" `
            -PercentComplete $PercentComplete `
            -CurrentOperation "$i of $FileCount files"

        try {
            # Compose: BaseName.OriginalExt[.InsertToken] + DestExt
            $srcExtNoDot = $File.Extension.TrimStart(".")
            $segments = @()
            if ($srcExtNoDot) { $segments += $srcExtNoDot }
            if ($InsertSeg)   { $segments += $InsertSeg }

            $AugBase = if ($segments.Count -gt 0) {
                "$($File.BaseName)." + ($segments -join ".")
            } else {
                $File.BaseName
            }

            $SafePath = Get-SafeFileName -DestPath $DestDir -BaseName $AugBase -Extension $DestExt
            Copy-Item -Path $File.FullName -Destination $SafePath -Force

            $SessionCount++
            $script:TotalFileCount++
        }
        catch {
            Write-Host "Error copying $($File.Name): $_" -ForegroundColor Red
        }
    }

    Write-Progress -Activity "Copying files ($SourceExt -> $DestExt)" -Completed
    Write-Host "Copied $SessionCount file(s) from $SourceExt to $DestExt" -ForegroundColor Green
}

# =============================================================================
# Setup
# =============================================================================
$DestDir = Join-Path $Root $DestFolderName
$CodeIgnoreFile = Join-Path $Root ".codeignore"
$TotalFileCount = 0

$UsingArgs = ($Map -and $Map.Count -gt 0) -or ($SourceExts -and $DestExt)

if (-not $UsingArgs -and -not $NonInteractive) { Clear-Host }
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  Fast Recursive File Copier - PowerShell Edition" -ForegroundColor White
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host ""

# Load .codeignore patterns (for paths)
$IgnorePathPatterns = @()
if (Test-Path $CodeIgnoreFile) {
    Write-Host "Loading .codeignore file from $CodeIgnoreFile..." -ForegroundColor Yellow
    $IgnorePathPatterns = Get-Content $CodeIgnoreFile | ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and ($_ -notmatch '^\s*#') }  # keep non-empty, non-comment lines
    foreach ($pattern in $IgnorePathPatterns) {
        Write-Host "  Ignoring path pattern: $pattern" -ForegroundColor DarkGray
    }
    Write-Host ""
}

# Also ignore the destination folder itself
$IgnorePathPatterns += $DestFolderName

# Load filename ignore patterns from -Ignore parameter
$IgnoreFilePatterns = @()
if ($Ignore -and $Ignore.Count -gt 0) {
    Write-Host "Loading filename ignore patterns..." -ForegroundColor Yellow
    $IgnoreFilePatterns = $Ignore | ForEach-Object { $_.Trim() } | Where-Object { $_ }
    foreach ($pattern in $IgnoreFilePatterns) {
        Write-Host "  Ignoring files matching: $pattern" -ForegroundColor DarkGray
    }
    Write-Host ""
}

# Remove old destination folder if exists
if (Test-Path $DestDir) {
    Write-Host "Removing old '$DestFolderName' folder..." -ForegroundColor Yellow
    Remove-Item -Path $DestDir -Recurse -Force
}

# Create new destination folder
New-Item -ItemType Directory -Path $DestDir -Force | Out-Null
Write-Host "Created destination folder: $DestFolderName" -ForegroundColor Green
Write-Host ""

# =============================================================================
# Build mappings
# =============================================================================
$Mappings = New-Object System.Collections.Generic.List[object]

if ($Map -and $Map.Count -gt 0) {
    foreach ($m in $Map) {
        # Accept "py=md", "js:txt", ".ts = .md"
        if ($m -match '^\s*\.?([^:=\s]+)\s*[:=]\s*\.?([^;\s]+)\s*$') {
            $Mappings.Add([pscustomobject]@{
                SourceExt = $Matches[1]
                DestExt   = $Matches[2]
            })
        }
        else {
            throw "Invalid map entry '$m'. Use format like 'py=md' or 'js:txt'."
        }
    }
}
elseif ($SourceExts -and $DestExt) {
    foreach ($s in $SourceExts) {
        if (-not [string]::IsNullOrWhiteSpace($s)) {
            $Mappings.Add([pscustomobject]@{
                SourceExt = $s
                DestExt   = $DestExt
            })
        }
    }
}

# =============================================================================
# Execute
# =============================================================================
if ($Mappings.Count -gt 0) {
    foreach ($mapEntry in $Mappings) {
        Process-Mapping -SourceExt $mapEntry.SourceExt -DestExt $mapEntry.DestExt -InsertToken $InsertToken
    }
}
else {
    # Interactive mode (no args provided)
    while ($true) {
        Write-Host "=================================================" -ForegroundColor Cyan
        Write-Host " Total files copied: $TotalFileCount" -ForegroundColor White
        Write-Host "=================================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Enter extensions to convert (or press Enter to exit)" -ForegroundColor White
        Write-Host ""

        $SourceExt = Read-Host "SOURCE extension (e.g., py, js, cpp)"
        if ([string]::IsNullOrWhiteSpace($SourceExt)) {
            break
        }

        $DestExt = Read-Host "DESTINATION extension (e.g., md, txt)"
        if ([string]::IsNullOrWhiteSpace($DestExt)) {
            continue
        }

        $InsertT = Read-Host "MIDDLE token to insert after original extension (e.g., txt). Leave blank to omit"
        
        # Ask for ignore patterns in interactive mode
        $IgnoreInput = Read-Host "Files to ignore (wildcards OK, comma-separated). Leave blank to skip"
        if (-not [string]::IsNullOrWhiteSpace($IgnoreInput)) {
            $NewPatterns = $IgnoreInput -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ }
            $IgnoreFilePatterns = $IgnoreFilePatterns + $NewPatterns
            Write-Host "  Added ignore patterns: $($NewPatterns -join ', ')" -ForegroundColor DarkGray
        }

        Process-Mapping -SourceExt $SourceExt -DestExt $DestExt -InsertToken $InsertT

        Write-Host ""
        Write-Host "Copied files so far: $TotalFileCount" -ForegroundColor Green
        Write-Host ""
    }
}

# =============================================================================
# Cleanup and exit
# =============================================================================
if (-not $UsingArgs -and -not $NonInteractive) {
    Clear-Host
    Write-Host ""
    Write-Host "=================================================" -ForegroundColor Cyan
    Write-Host "  Operation Complete" -ForegroundColor White
    Write-Host "=================================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Total files copied: $TotalFileCount" -ForegroundColor Green
    Write-Host "Destination folder: $DestFolderName" -ForegroundColor White
    Write-Host ""
    Write-Host "Press any key to exit..." -ForegroundColor DarkGray
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
} else {
    Write-Host ""
    Write-Host "=================================================" -ForegroundColor Cyan
    Write-Host "  Operation Complete" -ForegroundColor White
    Write-Host "=================================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Total files copied: $TotalFileCount" -ForegroundColor Green
    Write-Host "Destination folder: $DestFolderName" -ForegroundColor White
}