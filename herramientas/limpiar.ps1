[CmdletBinding(SupportsShouldProcess)]
param([switch]$IncluirDistribucion)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$RaizLimpieza = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path.TrimEnd('\')
$PrefijoLimpieza = $RaizLimpieza + [IO.Path]::DirectorySeparatorChar
$ObjetivosLimpieza = @('target', '.compilacion\interfaz-real', '.compilacion\temas', '.compilacion\verificacion-sqlserver', '.compilacion\sqlserver-pruebas')
if ($IncluirDistribucion) { $ObjetivosLimpieza += 'distribucion' }
$BytesLimpieza = 0L

function Confirmar-AncestrosLimpieza {
    param([string]$Ruta)
    $AncestroLimpieza = Get-Item -LiteralPath $Ruta -Force
    while ($null -ne $AncestroLimpieza) {
        if ($AncestroLimpieza.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "La ruta pasa por un enlace/junction: $($AncestroLimpieza.FullName)"
        }
        if ($AncestroLimpieza.FullName.TrimEnd('\') -eq $RaizLimpieza) { return }
        $AncestroLimpieza = $AncestroLimpieza.Parent
    }
    throw "La ruta no pertenece a la raiz verificada: $Ruta"
}

foreach ($ObjetivoLimpieza in $ObjetivosLimpieza) {
    $RutaLimpieza = [IO.Path]::GetFullPath((Join-Path $RaizLimpieza $ObjetivoLimpieza))
    if (-not $RutaLimpieza.StartsWith($PrefijoLimpieza, [StringComparison]::OrdinalIgnoreCase)) {
        throw "La ruta sale del proyecto: $RutaLimpieza"
    }
    if (-not (Test-Path -LiteralPath $RutaLimpieza)) { continue }
    Confirmar-AncestrosLimpieza $RutaLimpieza
    $ElementosLimpieza = @(Get-Item -LiteralPath $RutaLimpieza) + @(Get-ChildItem -LiteralPath $RutaLimpieza -Recurse -Force)
    if ($ElementosLimpieza | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) {
        throw "Se encontro un enlace/junction; no se elimina recursivamente: $RutaLimpieza"
    }
    $TamanoLimpieza = ($ElementosLimpieza | Where-Object { -not $_.PSIsContainer } | Measure-Object Length -Sum).Sum
    if ($PSCmdlet.ShouldProcess($RutaLimpieza, 'Eliminar archivos generados')) {
        Remove-Item -LiteralPath $RutaLimpieza -Recurse -Force
        $BytesLimpieza += [long]$TamanoLimpieza
    }
}

# Una ejecucion en curso puede mantener abierto su JAR. Se conserva ese archivo.
$DirectorioCopias = Join-Path $RaizLimpieza '.compilacion\ejecuciones'
if (Test-Path -LiteralPath $DirectorioCopias) {
    Confirmar-AncestrosLimpieza $DirectorioCopias
    foreach ($CopiaLimpieza in Get-ChildItem -LiteralPath $DirectorioCopias -Filter 'gestera-*.jar' -File) {
        $RutaCopia = [IO.Path]::GetFullPath($CopiaLimpieza.FullName)
        if (-not $RutaCopia.StartsWith($PrefijoLimpieza, [StringComparison]::OrdinalIgnoreCase)) { throw 'Ruta de copia no segura.' }
        if ($CopiaLimpieza.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'La copia es un enlace.' }
        try {
            $BloqueoCopia = [IO.File]::Open($RutaCopia, 'Open', 'ReadWrite', 'None')
            $BloqueoCopia.Dispose()
        } catch [IO.IOException] { continue }
        if ($PSCmdlet.ShouldProcess($RutaCopia, 'Eliminar copia antigua de ejecucion')) {
            Remove-Item -LiteralPath $RutaCopia -Force
            $BytesLimpieza += $CopiaLimpieza.Length
        }
    }
}
Write-Output ('Limpieza completada. Bytes liberados: ' + $BytesLimpieza)
