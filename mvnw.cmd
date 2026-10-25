@echo off
setlocal
set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
set "WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar"
if not exist "%WRAPPER_JAR%" echo No se encuentra el Maven Wrapper: %WRAPPER_JAR%
if not exist "%WRAPPER_JAR%" exit /b 1
if not defined JAVA_HOME echo JAVA_HOME debe apuntar a un JDK para compilar Gestera.
if not defined JAVA_HOME exit /b 1
"%JAVA_HOME%\bin\java.exe" "-Dmaven.multiModuleProjectDirectory=%PROJECT_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%
