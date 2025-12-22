# import-codebase.ps1

$InputFile = "imported.txt"
$ErrorActionPreference = "Stop"

# Ensure Ctrl+C breaks the script
[Console]::TreatControlCAsInput = $false

while ($true) {
    Clear-Host
    Write-Host "=================================================" -ForegroundColor Cyan
    Write-Host "      Continuous Code Importer (Strip Path)      " -ForegroundColor White
    Write-Host "=================================================" -ForegroundColor Cyan
    Write-Host "Reading from: $InputFile" -ForegroundColor DarkGray
    Write-Host "Format expected inside imported.txt:" -ForegroundColor DarkGray
    # Use single quotes here to prevent backticks from escaping the string
    Write-Host '  ```python' -ForegroundColor DarkGray
    Write-Host "  # src/path/to/file.py" -ForegroundColor Green
    Write-Host "  <file content>" -ForegroundColor DarkGray
    # Use single quotes here to prevent backticks from escaping the string
    Write-Host '  ```' -ForegroundColor DarkGray
    Write-Host "Note: The '# path' line will be removed from the saved file." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Press Ctrl+C to exit." -ForegroundColor DarkGray
    Write-Host ""

    # 1. Create imported.txt if it doesn't exist
    if (-not (Test-Path $InputFile)) {
        New-Item -Path $InputFile -ItemType File -Force | Out-Null
        Write-Host "Created empty '$InputFile'. Paste your AI response there." -ForegroundColor Yellow
    }

    # 2. Read content
    $lines = Get-Content -Path $InputFile -Encoding UTF8 -ErrorAction SilentlyContinue

    if ($null -eq $lines -or $lines.Count -eq 0) {
        Write-Host "File '$InputFile' is empty." -ForegroundColor Yellow
    }
    else {
        $inBlock = $false
        $currentRelativePath = $null
        $currentContent = @()
        $filesExtracted = 0
        
        # Regex to find start of block (ignores language like python/json)
        # Matches: Start of line, optional space, 3 backticks, optional anything else
        $startBlockRegex = '^\s*```.*$'
        
        # Regex to find end of block
        $endBlockRegex   = '^\s*```\s*$'

        # Regex to find the path inside the block (Must start with #)
        $pathLineRegex   = '^\s*#\s+(?<Path>.+)\s*$'

        foreach ($line in $lines) {
            if (-not $inBlock) {
                # Look for Start Block
                if ($line -match $startBlockRegex) {
                    $inBlock = $true
                    $currentRelativePath = $null
                    $currentContent = @()
                }
            }
            else {
                # Look for End Block
                if ($line -match $endBlockRegex) {
                    # Only save if we actually found a path inside this block
                    if (-not [string]::IsNullOrWhiteSpace($currentRelativePath)) {
                        try {
                            # Normalize path for Windows
                            $normPath = $currentRelativePath.Replace('/', [System.IO.Path]::DirectorySeparatorChar).Replace('\', [System.IO.Path]::DirectorySeparatorChar)
                            $fullPath = Join-Path $PSScriptRoot $normPath
                            $parentDir = Split-Path $fullPath -Parent

                            # Create directory if it doesn't exist
                            if (-not [string]::IsNullOrWhiteSpace($parentDir)) {
                                if (-not (Test-Path $parentDir)) {
                                    New-Item -ItemType Directory -Path $parentDir -Force | Out-Null
                                }
                            }

                            # Write content
                            $finalContent = $currentContent -join [Environment]::NewLine
                            
                            # Ensure file ends with a newline
                            if (-not $finalContent.EndsWith([Environment]::NewLine)) {
                                $finalContent += [Environment]::NewLine
                            }

                            Set-Content -Path $fullPath -Value $finalContent -Force -Encoding UTF8
                            Write-Host "  -> Saved to: $normPath" -ForegroundColor Green
                            $filesExtracted++
                        }
                        catch {
                            Write-Host "  -> Error saving $currentRelativePath : $_" -ForegroundColor Red
                        }
                    }
                    
                    # Reset state
                    $inBlock = $false
                    $currentRelativePath = $null
                    $currentContent = @()
                }
                else {
                    # We are inside a block.
                    
                    # Check if we are still looking for the path
                    if ($null -eq $currentRelativePath) {
                        if ($line -match $pathLineRegex) {
                            $rawPath = $Matches['Path'].Trim()
                            
                            # Basic validation: ensure it has a separator or extension to look like a file
                            if ($rawPath -match '\.|/|\\') {
                                $currentRelativePath = $rawPath
                                Write-Host "Found file: $currentRelativePath" -ForegroundColor Cyan
                                
                                # IMPORTANT: Continue loop here so this line is NOT added to $currentContent
                                continue 
                            }
                        }
                    }

                    # If we reach here, it's normal content (or a comment that wasn't a path)
                    $currentContent += $line
                }
            }
        }

        Write-Host ""
        Write-Host "Extraction cycle complete. Extracted: $filesExtracted files." -ForegroundColor White
    }

    Write-Host ""
    Write-Host "Press [Enter] to re-scan '$InputFile'..." -ForegroundColor Green
    $null = Read-Host
}