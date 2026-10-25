[CmdletBinding()]
param(
    [string]$Servidor = 'localhost',
    [ValidateRange(1,65535)][int]$Puerto = 1433,
    [ValidatePattern('^[A-Za-z_][A-Za-z0-9_]{0,127}$')][string]$Base = 'gestera'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'comun.ps1')
$ClienteRespaldo = Obtener-Herramienta 'sqlcmd' 'Se necesita sqlcmd para respaldar SQL Server.'
$IdentificadorRespaldo = [guid]::NewGuid().ToString('N')
$NombreRespaldo = $Base + '_antes_migracion_' + $IdentificadorRespaldo + '.bak'
# SQL Server escribe en su carpeta de respaldos, con la identidad de su servicio.
# COPY_ONLY conserva la cadena existente; el nombre aleatorio evita sobrescrituras.
$ConsultaRespaldo = @"
SET NOCOUNT ON;
DECLARE @base sysname = N'$Base';
IF DB_ID(@base) IS NULL THROW 54000,N'No existe la base que se quiere respaldar.',1;
DECLARE @directorio nvarchar(4000) = CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'));
IF @directorio IS NULL THROW 54001,N'No se pudo determinar la carpeta de respaldo de la instancia.',1;
DECLARE @archivo nvarchar(4000) = @directorio
    + CASE WHEN RIGHT(@directorio,1) IN (N'\',N'/') THEN N'' ELSE N'\' END + N'$NombreRespaldo';
BACKUP DATABASE @base TO DISK=@archivo WITH COPY_ONLY,CHECKSUM,COMPRESSION;
RESTORE VERIFYONLY FROM DISK=@archivo WITH CHECKSUM;
SELECT @base AS base_de_datos,@archivo AS archivo_respaldo,
       CONVERT(bit,1) AS verificado FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
"@
& $ClienteRespaldo -S "$Servidor,$Puerto" -E -C -b -l 10 -t 120 -y 0 -Q $ConsultaRespaldo
if ($LASTEXITCODE -ne 0) { throw 'El respaldo o su verificacion fallaron. No continuar con la migracion.' }
