
function Obtener-Herramienta {
    param(
        [Parameter(Mandatory)]
        [string]$Nombre,

        [string]$MensajeError
    )

    $Comando = Get-Command $Nombre -ErrorAction SilentlyContinue
    if ($null -eq $Comando) {
        if ([String]::IsNullOrEmpty($MensajeError)) {
            $MensajeError = "No se encontro '$Nombre'. Instala JDK 17 o superior y agregalo a PATH."
        }
        throw $MensajeError
    }
    return $Comando.Source
}

function Obtener-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
        return $env:JAVA_HOME
    }
    $Java = Obtener-Herramienta 'java' 'Instala JDK 17 o superior.'
    $PropiedadesJava = & $Java '-XshowSettings:properties' '-version' 2>&1
    $CoincidenciaJava = $PropiedadesJava | Select-String '^\s*java\.home\s*=\s*(.+)$' | Select-Object -First 1
    if ($null -eq $CoincidenciaJava) { throw 'No se pudo localizar el JDK. Configura JAVA_HOME.' }
    $RutaJava = $CoincidenciaJava.Matches[0].Groups[1].Value.Trim()
    if (-not (Test-Path -LiteralPath (Join-Path $RutaJava 'bin\javac.exe'))) {
        throw 'JAVA_HOME debe apuntar a un JDK completo, no a un JRE.'
    }
    return $RutaJava
}

function Invocar-Maven {
    param(
        [Parameter(Mandatory)]
        [string[]]$Argumentos
    )

    $JavaHome = Obtener-JavaHome
    $Wrapper = Join-Path $PSScriptRoot '..\mvnw.cmd'
    $RaizProyecto = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
    $MavenHome = Join-Path $RaizProyecto '.compilacion\maven-home'
    $RepositorioMaven = Join-Path $MavenHome 'repository'
    if (-not (Test-Path -LiteralPath $Wrapper -PathType Leaf)) {
        throw "No existe Maven Wrapper: $Wrapper"
    }

    $JavaHomeAnterior = $env:JAVA_HOME
    $MavenHomeAnterior = $env:MAVEN_USER_HOME
    $env:JAVA_HOME = $JavaHome
    $env:MAVEN_USER_HOME = $MavenHome
    Push-Location -LiteralPath $RaizProyecto
    try {
        & $Wrapper "-Dmaven.repo.local=$RepositorioMaven" @Argumentos
        if ($LASTEXITCODE -ne 0) {
            throw "Maven termino con codigo $LASTEXITCODE."
        }
        $JarPrevioReempaquetado = Join-Path $RaizProyecto 'distribucion\gestera.jar.original'
        if (Test-Path -LiteralPath $JarPrevioReempaquetado -PathType Leaf) {
            Remove-Item -LiteralPath $JarPrevioReempaquetado -Force
        }
    } finally {
        Pop-Location
        $env:JAVA_HOME = $JavaHomeAnterior
        $env:MAVEN_USER_HOME = $MavenHomeAnterior
    }
}

function Confirmar-RutaBajoCompilacion {
    param(
        [Parameter(Mandatory)]
        [string]$Ruta,

        [Parameter(Mandatory)]
        [string]$RaizCompilacion,

        [string]$Descripcion = 'compilacion'
    )

    $RaizAbsoluta = [IO.Path]::GetFullPath($RaizCompilacion).TrimEnd('\', '/')
    $RutaAbsoluta = [IO.Path]::GetFullPath($Ruta)
    $PrefijoSeguro = $RaizAbsoluta + [IO.Path]::DirectorySeparatorChar
    if (-not $RutaAbsoluta.StartsWith(
            $PrefijoSeguro,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "Ruta de $Descripcion no segura: $RutaAbsoluta"
    }
    return $RutaAbsoluta
}
