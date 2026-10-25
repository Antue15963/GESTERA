[CmdletBinding()]
param([Parameter(Mandatory)][int]$IdProceso)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$RaizGestera = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$ProcesoGestera = Get-CimInstance Win32_Process -Filter "ProcessId = $IdProceso"
if ($null -eq $ProcesoGestera -or
        $ProcesoGestera.CommandLine -notmatch '(com\.gestera\.GesteraAplicacion|\-jar\s)' -or
        -not $ProcesoGestera.CommandLine.Contains((Join-Path $RaizGestera '.compilacion\ejecuciones\'))) {
    throw 'El proceso indicado no corresponde a una instancia de este proyecto.'
}
$RaizEjecuciones = (Resolve-Path -LiteralPath (Join-Path $RaizGestera '.compilacion\ejecuciones')).Path
$JarAnterior = [regex]::Match($ProcesoGestera.CommandLine, [regex]::Escape($RaizEjecuciones) + '\\gestera-[a-f0-9]+\.jar').Value
if (-not $JarAnterior -or -not (Test-Path -LiteralPath $JarAnterior)) { throw 'No se pudo identificar el JAR anterior.' }
$JarNuevo = Join-Path $RaizEjecuciones ('gestera-' + [guid]::NewGuid().ToString('N') + '.jar')
Copy-Item -LiteralPath (Join-Path $RaizGestera 'distribucion\gestera.jar') -Destination $JarNuevo
$InicioArgumentos = $ProcesoGestera.CommandLine.IndexOf(' --add-modules')
if ($InicioArgumentos -lt 0) { $InicioArgumentos = $ProcesoGestera.CommandLine.IndexOf(' -Djava.library.path=') }
if ($InicioArgumentos -lt 0) { throw 'No se reconocieron los argumentos del servidor.' }
$ArgumentosOriginales = $ProcesoGestera.CommandLine.Substring($InicioArgumentos + 1)
$ArgumentosNuevos = $ArgumentosOriginales.Replace($JarAnterior, $JarNuevo)
if ($ArgumentosNuevos -eq $ArgumentosOriginales) { throw 'No se sustituyo la ruta del paquete.' }
$LogSalida = Join-Path $RaizEjecuciones 'servidor-actualizado.log'
$LogError = Join-Path $RaizEjecuciones 'servidor-actualizado-error.log'

# Solo se detiene el PID validado; las demás instancias Java permanecen intactas.
Stop-Process -Id $IdProceso
$NuevoProceso = Start-Process -FilePath $ProcesoGestera.ExecutablePath -ArgumentList $ArgumentosNuevos `
        -WorkingDirectory $RaizGestera -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $LogSalida -RedirectStandardError $LogError
$Disponible = $false
for ($Intento = 0; $Intento -lt 30; $Intento++) {
    Start-Sleep -Milliseconds 300
    if ($NuevoProceso.HasExited) { throw "El servidor termino al iniciar. Revisa $LogError" }
    try {
        $Respuesta = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8080/publico/scripts/tema.js' -TimeoutSec 2
        $Disponible = $Respuesta.StatusCode -eq 200 -and $Respuesta.Content.Contains('gestera.tema')
        if ($Disponible) { break }
    } catch { }
}
if (-not $Disponible) { throw 'El servidor nuevo no respondio con el recurso de temas.' }
$ArchivoPid = Join-Path $RaizGestera '.compilacion\servidor-sqlserver.pid'
if (Test-Path -LiteralPath $ArchivoPid) {
    if ([int](Get-Content -LiteralPath $ArchivoPid) -eq $IdProceso) {
        $NuevoProceso.Id | Set-Content -LiteralPath $ArchivoPid
    }
}
Write-Output ("GESTERA actualizado en http://127.0.0.1:8080. Proceso: {0}" -f $NuevoProceso.Id)
Write-Output ("Registro: {0}" -f $LogSalida)
