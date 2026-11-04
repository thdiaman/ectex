@echo off
rem Builds the EcTeX p2 update site (Windows launcher; the work is done by BuildSite.java).
rem
rem   build-site.bat --eclipse-home "C:\Program Files\eclipsesdk" [--clean] [--qualifier TEXT]
rem
rem Instead of --eclipse-home you can set the ECLIPSE_HOME environment variable.
rem Needs a JDK 17+ on the PATH. Run "build-site.bat --help" for all options.

where java >nul 2>nul
if errorlevel 1 (
    echo ERROR: java not found on the PATH - install a JDK 17+ and try again.
    exit /b 1
)

rem "%~dp0." (instead of "%~dp0") avoids a trailing backslash that would escape the closing quote.
java -Dectex.build.dir="%~dp0." "%~dp0BuildSite.java" %*
exit /b %ERRORLEVEL%
