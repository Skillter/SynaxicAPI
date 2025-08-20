# =============================================================================
# Title:       Fast Recursive File Copier (PowerShell Edition)
# Description: High-performance file copier with .codeignore support
# =============================================================================

# Set PowerShell preferences
$ErrorActionPreference = "Stop"
$ProgressPreference = "Continue"

# Configuration
$DestFolderName = "codebase-txt"
$DestDir = Join-Path $PSScriptRoot $DestFolderName
$CodeIgnoreFile = Join-Path $PSScriptRoot ".codeignore"
$TotalFileCount = 0

# Clear screen and show header
Clear-Host
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  Fast Recursive File Copier - PowerShell Edition" -ForegroundColor White
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host ""

# Load .codeignore patterns
$IgnorePatterns = @()
if (Test-Path $CodeIgnoreFile) {
    Write-Host "Loading .codeignore file..." -ForegroundColor Yellow
    $IgnorePatterns = Get-Content $CodeIgnoreFile | 
        Where-Object { $_ -and $_ -notmatch '^#' -and $_ -notmatch '\.' } |
        ForEach-Object { 
            $pattern = $_.Trim()
            Write-Host "  Ignoring folder: $pattern" -ForegroundColor DarkGray
            $pattern
        }
    Write-Host ""
}

# Function to check if path should be ignored
function Test-ShouldIgnore {
    param([string]$Path)
    
    foreach ($pattern in $IgnorePatterns) {
        if ($Path -match [regex]::Escape("\$pattern\")) {
            return $true
        }
    }
    return $false
}

# Function to get safe filename (handles duplicates)
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
    
    # Find available number
    for ($i = 1; $i -lt 1000; $i++) {
        $FullPath = Join-Path $DestPath "$BaseName($i)$Extension"
        if (-not (Test-Path $FullPath)) {
            return $FullPath
        }
    }
    
    throw "Unable to find unique filename for $BaseName"
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

# Main loop
while ($true) {
    Write-Host "=================================================" -ForegroundColor Cyan
    Write-Host " Total files copied: $TotalFileCount" -ForegroundColor White
    Write-Host "=================================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Enter extensions to convert (or press Enter to exit)" -ForegroundColor White
    Write-Host ""
    
    # Get source extension
    $SourceExt = Read-Host "SOURCE extension (e.g., py, js, cpp)"
    if ([string]::IsNullOrWhiteSpace($SourceExt)) {
        break
    }
    
    # Get destination extension
    $DestExt = Read-Host "DESTINATION extension (e.g., txt)"
    if ([string]::IsNullOrWhiteSpace($DestExt)) {
        continue
    }
    
    # Normalize extensions
    if ($SourceExt -notmatch '^\.') { $SourceExt = ".$SourceExt" }
    if ($DestExt -notmatch '^\.') { $DestExt = ".$DestExt" }
    
    Write-Host ""
    Write-Host "Searching for $SourceExt files..." -ForegroundColor Yellow
    
    # Get all matching files (excluding ignored folders)
    $Files = Get-ChildItem -Path $PSScriptRoot -Filter "*$SourceExt" -Recurse -File |
        Where-Object { -not (Test-ShouldIgnore $_.DirectoryName) }
    
    $FileCount = @($Files).Count
    
    if ($FileCount -eq 0) {
        Write-Host "No $SourceExt files found." -ForegroundColor Red
        Write-Host ""
        continue
    }
    
    Write-Host "Found $FileCount file(s). Copying..." -ForegroundColor Green
    
    # Process files with progress bar
    $i = 0
    $SessionCount = 0
    
    foreach ($File in $Files) {
        $i++
        $PercentComplete = [int](($i / $FileCount) * 100)
        
        # Update progress bar
        Write-Progress -Activity "Copying files" `
            -Status "Processing: $($File.Name)" `
            -PercentComplete $PercentComplete `
            -CurrentOperation "$i of $FileCount files"
        
        try {
            # Get safe destination path
            $SafePath = Get-SafeFileName -DestPath $DestDir `
                -BaseName $File.BaseName `
                -Extension $DestExt
            
            # Copy file
            Copy-Item -Path $File.FullName -Destination $SafePath -Force
            $SessionCount++
            $TotalFileCount++
        }
        catch {
            Write-Host "Error copying $($File.Name): $_" -ForegroundColor Red
        }
    }
    
    # Clear progress bar
    Write-Progress -Activity "Copying files" -Completed
    
    Write-Host ""
    Write-Host "Copied $SessionCount file(s) successfully!" -ForegroundColor Green
    Write-Host ""
}

# Cleanup and exit
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
