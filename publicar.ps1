# Publica una version nueva de WikiDiosesmonViewer para que el mod de todos se actualice.
#
# Uso (en PowerShell, dentro de la carpeta WikiDiosesmonViewer):
#   .\publicar.ps1 1.1.0 "Arreglos de la ventana pequena"
#
# Hace esto:
#   1. Pone la version en gradle.properties y compila el mod.
#   2. Crea la carpeta release\v1.1.0 con WikiDiosesmonViewer.jar y latest.json (version + huella SHA-256).
#   3. Si tienes la herramienta "gh" de GitHub, crea la release sola. Si no, abre la pagina de
#      GitHub para que subas esos dos archivos a mano.

param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Notas = ""
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if ($Version -notmatch '^[0-9]+(\.[0-9]+){1,3}$') {
    throw "Version no valida: '$Version'. Usa numeros como 1.1.0"
}

$repoLine = Get-Content "src\main\resources\wikidiosesmonviewer-update.properties" | Where-Object { $_ -match '^repo=' }
$repo = ($repoLine -replace '^repo=', '').Trim()
if ($repo -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
    throw "Falta el repositorio en src\main\resources\wikidiosesmonviewer-update.properties (repo=usuario/repositorio)"
}

# 1. Version + compilacion
(Get-Content gradle.properties) -replace '^mod_version=.*', "mod_version=$Version" | Set-Content gradle.properties -Encoding ascii
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot" }
& .\gradlew.bat build --console=plain
if ($LASTEXITCODE -ne 0) { throw "La compilacion fallo." }

# 2. Archivos de la release
$out = Join-Path $PSScriptRoot "release\v$Version"
New-Item -ItemType Directory -Force $out | Out-Null
$jar = Join-Path $out "WikiDiosesmonViewer.jar"
Copy-Item "build\libs\WikiDiosesmonViewer.jar" $jar -Force
$sha = (Get-FileHash $jar -Algorithm SHA256).Hash.ToLower()
$json = [ordered]@{ version = $Version; sha256 = $sha; notes = $Notas } | ConvertTo-Json -Compress
# UTF-8 sin BOM (el mod no lee el BOM).
[IO.File]::WriteAllText((Join-Path $out "latest.json"), $json, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "Listo: $out" -ForegroundColor Green
Write-Host "  WikiDiosesmonViewer.jar"
Write-Host "  latest.json  (sha256 $sha)"
Write-Host ""

# 3. Publicar
$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $gh -and (Test-Path "C:\Program Files\GitHub CLI\gh.exe")) { $gh = "C:\Program Files\GitHub CLI\gh.exe" }
if ($gh) {
    & $gh release create "v$Version" $jar (Join-Path $out "latest.json") --repo $repo --title "WikiDiosesmonViewer $Version" --notes $Notas
    Write-Host "Release v$Version publicada en https://github.com/$repo/releases" -ForegroundColor Green
} else {
    Write-Host "Ahora en GitHub (se abre solo):" -ForegroundColor Yellow
    Write-Host "  1. Tag: v$Version  (escribelo y elige 'Create new tag')"
    Write-Host "  2. Titulo: WikiDiosesmonViewer $Version"
    Write-Host "  3. Arrastra los DOS archivos de $out"
    Write-Host "  4. Pulsa 'Publish release'"
    Start-Process "https://github.com/$repo/releases/new?tag=v$Version&title=WikiDiosesmonViewer%20$Version"
    Start-Process $out
}
