[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'comun.ps1')

Write-Output 'Compilando Gestera y preparando sus dependencias...'
Invocar-Maven @('-B', '-DskipTests', 'package')
Write-Output 'Compilacion completada: distribucion\gestera.jar'
