[CmdletBinding()]
param([switch]$OmitirCompilacion)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'comun.ps1')
if ($OmitirCompilacion) {
    Invocar-Maven @('-B', 'test')
} else {
    Invocar-Maven @('-B', 'verify')
}
