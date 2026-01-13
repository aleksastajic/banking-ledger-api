@REM ----------------------------------------------------------------------------
@REM Maven Wrapper for Windows
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set BASE_DIR=%~dp0
set WRAPPER_DIR=%BASE_DIR%\.mvn\wrapper
set WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar
set WRAPPER_PROPERTIES=%WRAPPER_DIR%\maven-wrapper.properties

if not exist "%WRAPPER_PROPERTIES%" (
  echo Missing %WRAPPER_PROPERTIES%
  exit /b 1
)

if not exist "%WRAPPER_JAR%" (
  echo Downloading Maven wrapper...
  for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b /c:"wrapperUrl=" "%WRAPPER_PROPERTIES%"`) do set WRAPPER_URL=%%B
  if "%WRAPPER_URL%"=="" (
    echo wrapperUrl is not set in %WRAPPER_PROPERTIES%
    exit /b 1
  )
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "(New-Object Net.WebClient).DownloadFile('%WRAPPER_URL%','%WRAPPER_JAR%')" || exit /b 1
)

set JAVA_CMD=java
if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\java.exe" set JAVA_CMD=%JAVA_HOME%\bin\java.exe

"%JAVA_CMD%" -Dmaven.multiModuleProjectDirectory="%BASE_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
endlocal
