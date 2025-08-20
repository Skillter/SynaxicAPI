# =============================================================================
# Title:       Fast Recursive File Copier (PowerShell Edition)
# Description: High-performance file copier with .codeignore support
# Usage:       
#   Interactive: .\copy-codebase-and-change-extension.ps1
#   Automated:   .\copy-codebase-and-change-extension.ps1 -Extensions "py:txt","js:md","cpp:txt"
#   Or:          .\copy-codebase-and-change-extension.ps1 -Extensions @("py:txt","js:md")
# =============================================================================

param(
    [Parameter(Mandatory=$false)]
    [string[]]$Extensions,  # Array of "source:dest" pairs like "py:txt", "js:md"
    
    [Parameter(Mandatory=$false)]
    [string]$DestFolderName = "codebase-txt",
    
    [Parameter(Mandatory=$false)]
    [switch]$Silent  # Suppress interactive prompts when using arguments
)

# Set PowerShell preferences
$ErrorActionPreference = "Stop"
$ProgressPreference = "Continue"

# Configuration
$DestDir = Join-Path $PSScriptRoot $DestFolderName
$CodeIgnoreFile = Join-Path $PSScriptRoot ".codeignore"
$TotalFileCount = 0
$IsAutomated = $Extensions.Count -gt 0

# Clear screen and show header (only in interactive mode)
if (-not $IsAutomated -or -not $Silent) {
    Clear-Host
    Write-Host "=================================================" -ForegroundColor Cyan
    Write-Host "  Fast Recursive File Copier - PowerShell Edition" -ForegroundColor White
    Write-Host "=================================================" -ForegroundColor Cyan
    Write-Host ""
}

# Load .codeignore patterns
$IgnorePatterns = @()
if (Test-Path $CodeIgnoreFile) {
    if (-not $Silent) {
        Write-Host "Loading .codeignore file..." -ForegroundColor Yellow
    }
    $IgnorePatterns = Get-Content $CodeIgnoreFile | 
        Where-Object { $_ -and $_ -notmatch '^#' -and $_ -notmatch '\.' } |
        ForEach-Object { 
            $pattern = $_.Trim()
            if (-not $Silent) {
                Write-Host "  Ignoring folder: $pattern" -ForegroundColor DarkGray
            }
            $pattern
        }
    if (-not $Silent) {
        Write-Host ""
    }
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
# Modified to insert .txt before the destination extension
function Get-SafeFileName {
    param(
        [string]$DestPath,
        [string]$BaseName,
        [string]$Extension
    )
    
    # Insert .txt before the extension
    $ModifiedName = "$BaseName$Extension.txt"
    $FullPath = Join-Path $DestPath $ModifiedName
    
    if (-not (Test-Path $FullPath)) {
        return $FullPath
    }
    
    # Find available number for duplicates
    for ($i = 1; $i -lt 1000; $i++) {
        $ModifiedName = "$BaseName$Extension($i).txt"
        $FullPath = Join-Path $DestPath $ModifiedName
        if (-not (Test-Path $FullPath)) {
            return $FullPath
        }
    }
    
    throw "Unable to find unique filename for $BaseName"
}

# Function to process a single extension pair
function Process-ExtensionPair {
    param(
        [string]$SourceExt,
        [string]$DestExt
    )
    
    # Normalize extensions
    if ($SourceExt -notmatch '^\.') { $SourceExt = ".$SourceExt" }
    if ($DestExt -notmatch '^\.') { $DestExt = ".$DestExt" }
    
    if (-not $Silent) {
        Write-Host ""
        Write-Host "Searching for $SourceExt files to convert to .txt$DestExt..." -ForegroundColor Yellow
    }
    
    # Get all matching files (excluding ignored folders)
    $Files = Get-ChildItem -Path $PSScriptRoot -Filter "*$SourceExt" -Recurse -File |
        Where-Object { -not (Test-ShouldIgnore $_.DirectoryName) }
    
    $FileCount = @($Files).Count
    
    if ($FileCount -eq 0) {
        if (-not $Silent) {
            Write-Host "No $SourceExt files found." -ForegroundColor Red
        }
        return 0
    }
    
    if (-not $Silent) {
        Write-Host "Found $FileCount file(s). Copying..." -ForegroundColor Green
    }
    
    # Process files with progress bar
    $i = 0
    $SessionCount = 0
    
    foreach ($File in $Files) {
        $i++
        $PercentComplete = [int](($i / $FileCount) * 100)
        
        # Update progress bar (only if not silent)
        if (-not $Silent) {
            Write-Progress -Activity "Copying $SourceExt files" `
                -Status "Processing: $($File.Name)" `
                -PercentComplete $PercentComplete `
                -CurrentOperation "$i of $FileCount files"
        }
        
        try {
            # Get safe destination path with .txt inserted
            $SafePath = Get-SafeFileName -DestPath $DestDir `
                -BaseName $File.BaseName `
                -Extension $DestExt
            
            # Copy file
            Copy-Item -Path $File.FullName -Destination $SafePath -Force
            $SessionCount++
        }
        catch {
            if (-not $Silent) {
                Write-Host "Error copying $($File.Name): $_" -ForegroundColor Red
            }
        }
    }
    
    # Clear progress bar
    if (-not $Silent) {
        Write-Progress -Activity "Copying $SourceExt files" -Completed
    }
    
    if (-not $Silent) {
        Write-Host "Copied $SessionCount $SourceExt file(s) successfully!" -ForegroundColor Green
    }
    
    return $SessionCount
}

# Remove old destination folder if exists
if (Test-Path $DestDir) {
    if (-not $Silent) {
        Write-Host "Removing old '$DestFolderName' folder..." -ForegroundColor Yellow
    }
    Remove-Item -Path $DestDir -Recurse -Force
}

# Create new destination folder
New-Item -ItemType Directory -Path $DestDir -Force | Out-Null
if (-not $Silent) {
    Write-Host "Created destination folder: $DestFolderName" -ForegroundColor Green
    Write-Host ""
}

# Process based on mode (automated or interactive)
if ($IsAutomated) {
    # Automated mode - process provided extension pairs
    foreach ($ExtPair in $Extensions) {
        if ($ExtPair -match "^([^:]+):([^:]+)$") {
            $SourceExt = $Matches[1].Trim()
            $DestExt = $Matches[2].Trim()
            
            $CopiedCount = Process-ExtensionPair -SourceExt $SourceExt -DestExt $DestExt
            $TotalFileCount += $CopiedCount
        }
        else {
            Write-Warning "Invalid extension pair format: $ExtPair (Expected format: 'source:dest')"
        }
    }
    
    # Show summary
    if (-not $Silent) {
        Write-Host ""
        Write-Host "=================================================" -ForegroundColor Cyan
        Write-Host "  Operation Complete" -ForegroundColor White
        Write-Host "=================================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Total files copied: $TotalFileCount" -ForegroundColor Green
        Write-Host "Destination folder: $DestFolderName" -ForegroundColor White
        Write-Host ""
    }
    else {
        # For automation, just output the count
        Write-Output $TotalFileCount
    }
}
else {
    # Interactive mode - original behavior with loop
    while ($true) {
        Write-Host "=================================================" -ForegroundColor Cyan
        Write-Host " Total files copied: $TotalFileCount" -ForegroundColor White
        Write-Host "=================================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Enter extensions to convert (or press Enter to exit)" -ForegroundColor White
        Write-Host "Note: Files will be saved as filename.extension.txt" -ForegroundColor DarkGray
        Write-Host ""
        
        # Get source extension
        $SourceExt = Read-Host "SOURCE extension (e.g., py, js, cpp)"
        if ([string]::IsNullOrWhiteSpace($SourceExt)) {
            break
        }
        
        # Get destination extension
        $DestExt = Read-Host "DESTINATION extension (e.g., txt, md)"
        if ([string]::IsNullOrWhiteSpace($DestExt)) {
            continue
        }
        
        $CopiedCount = Process-ExtensionPair -SourceExt $SourceExt -DestExt $DestExt
        $TotalFileCount += $CopiedCount
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
}