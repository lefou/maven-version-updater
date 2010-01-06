@echo off
@SET JAVA_HOME=c:\Users\De-54060\Apps\jdk1.6.0_13

:init
@REM Decide how to startup depending on the version of windows

@REM -- Windows NT with Novell Login
if "%OS%"=="WINNT" goto WinNTNovell

@REM -- Win98ME
if NOT "%OS%"=="Windows_NT" goto Win9xArg

:WinNTNovell

@REM -- 4NT shell
if "%@eval[2+2]" == "4" goto 4NTArgs

@REM -- Regular WinNT shell
set MAVEN_CMD_LINE_ARGS=%*
goto endInit

@REM The 4NT Shell from jp software
:4NTArgs
set MAVEN_CMD_LINE_ARGS=%$
goto endInit

:Win9xArg
@REM Slurp the command line arguments.  This loop allows for an unlimited number
@REM of agruments (up to the command line limit, anyway).
set MAVEN_CMD_LINE_ARGS=
:Win9xApp
if %1a==a goto endInit
set MAVEN_CMD_LINE_ARGS=%MAVEN_CMD_LINE_ARGS% %1
shift
goto Win9xApp

@REM Reaching here means variables are defined and arguments have been captured
:endInit
SET MAVEN_JAVA_EXE="%JAVA_HOME%\bin\java.exe"

%MAVEN_JAVA_EXE% -jar c:\Users\DE-54060\Apps\mvu\de.tobiasroeser.maven.versionupdater-0.0.2-jar-with-dependencies.jar %MAVEN_CMD_LINE_ARGS%

set MAVEN_JAVA_EXE=
set MAVEN_CMD_LINE_ARGS=