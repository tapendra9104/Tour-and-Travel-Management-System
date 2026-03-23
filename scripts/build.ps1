Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

Push-Location $repoRoot
try {
  Push-Location (Join-Path $repoRoot "frontend")
  try {
    if (-not (Test-Path "node_modules")) {
      npm install
    }

    npm run build:css
  }
  finally {
    Pop-Location
  }

  Push-Location (Join-Path $repoRoot "backend")
  try {
    mvn clean package
  }
  finally {
    Pop-Location
  }
}
finally {
  Pop-Location
}
