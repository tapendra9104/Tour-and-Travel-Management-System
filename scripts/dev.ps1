Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

param(
  [switch]$InstallFrontend,
  [switch]$StartDatabase
)

$repoRoot = Split-Path -Parent $PSScriptRoot

Push-Location $repoRoot
try {
  if ($StartDatabase) {
    docker compose up -d mysql
  }

  Push-Location (Join-Path $repoRoot "frontend")
  try {
    if ($InstallFrontend -or -not (Test-Path "node_modules")) {
      npm install
    }

    npm run build:css
  }
  finally {
    Pop-Location
  }

  Push-Location (Join-Path $repoRoot "backend")
  try {
    mvn spring-boot:run
  }
  finally {
    Pop-Location
  }
}
finally {
  Pop-Location
}
