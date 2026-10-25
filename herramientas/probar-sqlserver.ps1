[CmdletBinding()]
param([string]$Servidor = 'localhost', [ValidateRange(1,65535)][int]$Puerto = 1433, [switch]$SoloEsquema, [switch]$MigrarDesdeV1)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'comun.ps1')
$RaizPruebaSql = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$ClientePruebaSql = Obtener-Herramienta 'sqlcmd' 'Instala sqlcmd para crear la base aislada de pruebas.'
$NombrePruebaSql = 'gestera_pruebas_' + [guid]::NewGuid().ToString('N')
if ($NombrePruebaSql -notmatch '^gestera_pruebas_[0-9a-f]{32}$') { throw 'Nombre de prueba no valido.' }
$DirectorioPruebaSql = Join-Path $RaizPruebaSql '.compilacion\sqlserver-pruebas'
New-Item -ItemType Directory -Path $DirectorioPruebaSql -Force | Out-Null
$ArchivoPruebaSql = Join-Path $DirectorioPruebaSql 'instalador.sql'
$RegistroPruebaSql = Join-Path $DirectorioPruebaSql 'instalacion.log'
$InstaladorPruebaSql = [IO.File]::ReadAllText((Join-Path $RaizPruebaSql 'base-datos\sqlserver\CREAR_GESTERA_SQLSERVER.sql'))
$InstaladorPruebaSql = $InstaladorPruebaSql.Replace('[gestera]', ('[' + $NombrePruebaSql + ']')).Replace("N'gestera'", ("N'" + $NombrePruebaSql + "'"))
[IO.File]::WriteAllText($ArchivoPruebaSql, $InstaladorPruebaSql, [Text.UTF8Encoding]::new($true))
$UrlPruebaSqlAnterior = $env:GESTERA_PRUEBAS_SQLSERVER_URL
$UsuarioPruebaSqlAnterior = $env:GESTERA_PRUEBAS_SQLSERVER_USUARIO
$ContrasenaPruebaSqlAnterior = $env:GESTERA_PRUEBAS_SQLSERVER_CONTRASENA
Push-Location -LiteralPath $RaizPruebaSql
try {
    if ($MigrarDesdeV1) {
        $ArchivoV1=Join-Path $DirectorioPruebaSql 'instalador-v1.sql'
        $FuenteV1=[IO.File]::ReadAllText((Join-Path $RaizPruebaSql 'base-datos\sqlserver\historico\CREAR_GESTERA_7_TABLAS.sql'))
        $FuenteV1=$FuenteV1.Replace('[gestera]',('['+$NombrePruebaSql+']')).Replace("N'gestera'",("N'"+$NombrePruebaSql+"'"))
        [IO.File]::WriteAllText($ArchivoV1,$FuenteV1,[Text.UTF8Encoding]::new($true))
        & $ClientePruebaSql -S "$Servidor,$Puerto" -E -C -b -l 10 -f 65001 -i $ArchivoV1 -o $RegistroPruebaSql
        if ($LASTEXITCODE -ne 0) { throw 'Fallo el fixture de instalacion V1.' }
        & $ClientePruebaSql -S "$Servidor,$Puerto" -d $NombrePruebaSql -E -C -b -l 10 -f 65001 -i (Join-Path $RaizPruebaSql 'base-datos\sqlserver\pruebas\datos_v1.sql')
        if ($LASTEXITCODE -ne 0) { throw 'Fallo el fixture de datos V1.' }
        & $ClientePruebaSql -S "$Servidor,$Puerto" -d $NombrePruebaSql -E -C -b -l 10 -Q "INSERT dbo.estancias(hotel_codigo,numero_habitacion,ubicacion,check_in,check_out,estado) VALUES(N'brisa-marina',4,N'Habitacion 4','2030-01-01T10:00:00','2030-01-01T16:00:00','ACTIVA');"
        if ($LASTEXITCODE -ne 0) { throw 'Fallo la preparacion de rollback.' }
        & $ClientePruebaSql -S "$Servidor,$Puerto" -E -C -b -l 10 -t 30 -f 65001 -i $ArchivoPruebaSql -o $RegistroPruebaSql
        if ($LASTEXITCODE -eq 0) { throw 'La migracion acepto una estancia activa sin tarifa historica.' }
        & $ClientePruebaSql -S "$Servidor,$Puerto" -d $NombrePruebaSql -E -C -b -l 10 -Q "SET NOCOUNT ON; IF (SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0 AND NOT (name=N'sysdiagrams' AND schema_id=SCHEMA_ID(N'dbo')))<>7 OR NOT EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=0 AND name=N'Gestera.Instalador' AND CONVERT(nvarchar(128),value)=N'GESTERA_SQLSERVER_V001_V005_1') THROW 54310,N'El rollback no restauro el esquema V1.',1; IF (SELECT SUM(monto) FROM dbo.ingresos_habitacion)<>95.50 THROW 54311,N'El rollback altero importes.',1; DELETE dbo.estancias WHERE hotel_codigo=N'brisa-marina' AND numero_habitacion=4 AND check_in='2030-01-01T10:00:00';"
        if ($LASTEXITCODE -ne 0) { throw 'Fallo la comprobacion del rollback.' }
        Write-Output 'Rollback de migracion fallida verificado: V1 y sus importes permanecen intactos.'
    }
    & $ClientePruebaSql -S "$Servidor,$Puerto" -E -C -b -l 10 -t 30 -f 65001 -i $ArchivoPruebaSql -o $RegistroPruebaSql
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $RegistroPruebaSql -Tail 30 -Encoding UTF8
        throw 'No se pudo instalar el esquema en la base temporal.'
    }
    if ($MigrarDesdeV1) {
        & $ClientePruebaSql -S "$Servidor,$Puerto" -d $NombrePruebaSql -E -C -b -l 10 -f 65001 -i (Join-Path $RaizPruebaSql 'base-datos\sqlserver\pruebas\comprobar_migracion.sql')
        if ($LASTEXITCODE -ne 0) { throw 'No se conservaron todos los datos migrados.' }
    }
    $env:GESTERA_PRUEBAS_SQLSERVER_URL = ("jdbc:sqlserver://{0}:{1};databaseName={2};integratedSecurity=true;encrypt=true;trustServerCertificate=true" -f $Servidor,$Puerto,$NombrePruebaSql)
    $env:GESTERA_PRUEBAS_SQLSERVER_USUARIO = $null
    $env:GESTERA_PRUEBAS_SQLSERVER_CONTRASENA = $null
    if ($SoloEsquema) {
        & $ClientePruebaSql -S "$Servidor,$Puerto" -E -C -b -l 10 -t 30 -f 65001 -i $ArchivoPruebaSql -o $RegistroPruebaSql
        if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $RegistroPruebaSql -Tail 30; throw 'Fallo la repeticion del instalador.' }
        Write-Output 'Esquema de 14 tablas instalado y verificado dos veces.'
    } else {
        Invocar-Maven @('-B', '-ntp', '-Dtest=SqlServerIntegracionTest', 'test')
    }
} finally {
    $env:GESTERA_PRUEBAS_SQLSERVER_URL = $UrlPruebaSqlAnterior
    $env:GESTERA_PRUEBAS_SQLSERVER_USUARIO = $UsuarioPruebaSqlAnterior
    $env:GESTERA_PRUEBAS_SQLSERVER_CONTRASENA = $ContrasenaPruebaSqlAnterior
    $LimpiezaPruebaSql = "IF DB_ID(N'$NombrePruebaSql') IS NOT NULL BEGIN IF EXISTS(SELECT 1 FROM [$NombrePruebaSql].sys.tables WHERE is_ms_shipped=0 AND NOT (name=N'sysdiagrams' AND schema_id=SCHEMA_ID(N'dbo'))) AND NOT EXISTS(SELECT 1 FROM [$NombrePruebaSql].sys.extended_properties WHERE class=0 AND name=N'Gestera.Instalador' AND CONVERT(nvarchar(128),value) IN(N'GESTERA_SQLSERVER_14_V2',N'GESTERA_SQLSERVER_V001_V005_1')) THROW 53001,N'La base temporal no tiene la marca esperada; no se elimina.',1; DROP DATABASE [$NombrePruebaSql]; END;"
    & $ClientePruebaSql -S "$Servidor,$Puerto" -E -C -b -l 10 -t 30 -Q $LimpiezaPruebaSql
    $ResultadoLimpiezaSql = $LASTEXITCODE
    Pop-Location
    if ($ResultadoLimpiezaSql -ne 0) { throw "Revise la base temporal $NombrePruebaSql; la limpieza no se completo." }
    Write-Output "Base temporal eliminada: $NombrePruebaSql"
}
