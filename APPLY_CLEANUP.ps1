$ErrorActionPreference = "Stop"
if (-not (Test-Path "build.gradle.kts") -or -not (Test-Path "src/main/java")) {
    throw "Run this script from the root of the SF_NetworksExp repository."
}
$files = @(
    ".github/ISSUE_TEMPLATE/bug-report.yml",
    ".github/ISSUE_TEMPLATE/help-wanted.yml",
    ".github/ISSUE_TEMPLATE/other.yml",
    ".github/ISSUE_TEMPLATE/suggestion.yml",
    "src/main/resources/lang/zh-CN.yml"
)
foreach ($file in $files) {
    if (Test-Path $file) {
        Remove-Item -Force $file
        Write-Host "Deleted $file"
    } else {
        Write-Host "Already absent: $file"
    }
}
python scripts/verify_legacy_compatibility.py
