param(
    [ValidateRange(1, 65535)]
    [int]$Puerto = 8080
)

$ErrorActionPreference = 'Stop'
$rutaConfig = Join-Path $PSScriptRoot 'arca.local.json'
if (-not (Test-Path -LiteralPath $rutaConfig -PathType Leaf)) {
    throw 'Copia arca.ejemplo.json como arca.local.json y completa tus datos de homologacion.'
}

$config = Get-Content -LiteralPath $rutaConfig -Raw -Encoding UTF8 | ConvertFrom-Json
if ($config.cuit -notmatch '^[0-9]{11}$' -or $config.puntoVenta -notmatch '^[1-9][0-9]{0,4}$') {
    throw 'Configura un CUIT de 11 digitos y un punto de venta valido en arca.local.json.'
}

Push-Location -LiteralPath $PSScriptRoot
try {
    $variables = @{
        ARCA_CUIT = [string]$config.cuit
        ARCA_PUNTO_VENTA = [string]$config.puntoVenta
        ARCA_CLAVE_PRIVADA = (Resolve-Path -LiteralPath $config.clavePrivada).ProviderPath
        ARCA_CERTIFICADO = (Resolve-Path -LiteralPath $config.certificado).ProviderPath
    }
    if ($config.javaHome) {
        $variables.JAVA_HOME = (Resolve-Path -LiteralPath $config.javaHome).ProviderPath
    }
    $anteriores = @{}
    foreach ($nombre in $variables.Keys) {
        $anteriores[$nombre] = [Environment]::GetEnvironmentVariable($nombre)
        [Environment]::SetEnvironmentVariable($nombre, $variables[$nombre])
    }
    try {
        & .\gradlew.bat bootRun "--args=--spring.profiles.active=homologacion --server.port=$Puerto"
        if ($LASTEXITCODE -ne 0) {
            throw "El backend termino con codigo $LASTEXITCODE."
        }
    } finally {
        foreach ($nombre in $anteriores.Keys) {
            [Environment]::SetEnvironmentVariable($nombre, $anteriores[$nombre])
        }
    }
} finally {
    Pop-Location
}
