[CmdletBinding()]
param(
    [switch]$OmitirCompilacion,
    [string[]]$OpcionesJava = @(),
    [string[]]$ArgumentosAplicacion = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'comun.ps1')
$RaizProyecto = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$JarGestera = Join-Path $RaizProyecto 'distribucion\gestera.jar'
$RaizCompilacion = Join-Path $RaizProyecto '.compilacion'
$DirectorioEjecucion = Join-Path $RaizCompilacion 'ejecuciones'

# Una copia del JAR ya iniciada conserva los recursos anteriores en memoria.
# Detectarla antes de compilar evita que el usuario siga viendo esa instancia.
$PuertoGestera = 8080
if ($env:GESTERA_PUERTO) { $PuertoGestera = [int]$env:GESTERA_PUERTO }
foreach ($Opcion in $OpcionesJava) {
    if ($Opcion -match '^-Dgestera\.puerto=(\d+)$') { $PuertoGestera = [int]$Matches[1] }
}
if ($PuertoGestera -gt 0) {
    $SondaGestera = [Net.Sockets.TcpClient]::new()
    try {
        $ConexionGestera = $SondaGestera.ConnectAsync('127.0.0.1', $PuertoGestera)
        try { [void]$ConexionGestera.Wait(600) } catch { }
        if ($SondaGestera.Connected) {
            throw "El puerto $PuertoGestera ya esta ocupado. La pagina abierta sigue usando el servidor anterior. Deten esa instancia con Ctrl+C en su consola y vuelve a ejecutar INICIAR_GESTERA.cmd. Compilar no actualiza un servidor que sigue abierto."
        }
    } finally { $SondaGestera.Dispose() }
}

if (-not $OmitirCompilacion) { & (Join-Path $PSScriptRoot 'compilar.ps1') }
if (-not (Test-Path -LiteralPath $JarGestera -PathType Leaf)) {
    throw "No existe $JarGestera. Ejecuta compilar.ps1 antes de usar -OmitirCompilacion."
}
$Java = Join-Path (Obtener-JavaHome) 'bin\java.exe'
if (-not (Test-Path -LiteralPath $Java -PathType Leaf)) {
    throw "No se encontro java.exe junto al compilador '$Javac'. Instala un JDK completo."
}
$BibliotecasNativas = Join-Path $RaizCompilacion 'native'

$DirectorioEjecucion = Confirmar-RutaBajoCompilacion `
        $DirectorioEjecucion $RaizCompilacion 'ejecucion'
New-Item -ItemType Directory -Path $DirectorioEjecucion -Force | Out-Null
$JarEjecucion = Join-Path $DirectorioEjecucion ("gestera-" + [guid]::NewGuid().ToString('N') + '.jar')
Copy-Item -LiteralPath $JarGestera -Destination $JarEjecucion

Write-Output 'Iniciando Gestera...'
Write-Output ("Paquete: {0}" -f $JarGestera)
Write-Output ("Interfaz: {0}" -f (Get-FileHash -LiteralPath (Join-Path $RaizProyecto 'frontend\publico\estilos\gestera.css') -Algorithm SHA256).Hash.Substring(0, 12))
try {
    & $Java "-Djava.library.path=$BibliotecasNativas" @OpcionesJava -jar $JarEjecucion @ArgumentosAplicacion
    if ($LASTEXITCODE -ne 0) { throw "Gestera termino con codigo $LASTEXITCODE." }
} finally {
    if (Test-Path -LiteralPath $JarEjecucion) { Remove-Item -LiteralPath $JarEjecucion -Force }
}
