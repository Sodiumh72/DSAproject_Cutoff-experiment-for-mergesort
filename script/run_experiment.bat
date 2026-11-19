@echo off
setlocal EnableExtensions

rem ============================================================
rem  Cutoff experiment runner (Windows)
rem  Usage:  script\run_experiment.bat [analyze.py options, e.g. --tol 0.03]
rem  Set NOPAUSE=1 to skip the pause prompts (unattended runs).
rem ============================================================

cd /d "%~dp0.."
if %errorlevel% neq 0 (
    echo [ERROR] Cannot change to project root.
    exit /b 1
)
echo Project root: %CD%
echo.

rem ---------- Tool checks ----------
where javac >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] javac not found. Install a JDK and add it to PATH.
    exit /b 1
)
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] java not found. Install a JDK and add it to PATH.
    exit /b 1
)

set "PYTHON_CMD="
where python >nul 2>&1
if %errorlevel% equ 0 set "PYTHON_CMD=python"
if not defined PYTHON_CMD (
    where py >nul 2>&1
    if %errorlevel% equ 0 set "PYTHON_CMD=py -3"
)
if not defined PYTHON_CMD (
    echo [ERROR] Python not found. Install Python 3 and: pip install pandas numpy matplotlib
    exit /b 1
)

rem ---------- Reminder ----------
echo ============================================================
echo  BEFORE YOU START - for reliable timing results:
echo    * Plug in the charger (do NOT run on battery).
echo    * Set Windows power mode to "Best performance".
echo    * Close browsers / IDEs / other heavy applications.
echo    * Do not use the computer while the benchmark is running.
echo ============================================================
if not defined NOPAUSE pause
echo.

rem ---------- Step 1: compile ----------
echo [1/4] Compiling Java sources (src\ -^> bin\) ...
if not exist bin mkdir bin
javac -encoding UTF-8 -d bin src\*.java
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed.
    exit /b 1
)
echo Compilation OK.
echo.

rem ---------- Step 2: generate data (only if missing) ----------
if exist "data\manifest.csv" (
    echo [2/4] data\manifest.csv found - skipping DataGenerator.
) else (
    echo [2/4] data\manifest.csv not found - running DataGenerator ...
    java -cp bin DataGenerator
    if %errorlevel% neq 0 (
        echo [ERROR] DataGenerator failed.
        exit /b 1
    )
)
echo.

rem ---------- Step 3: benchmark ----------
echo [3/4] Running Benchmark (-Xms2g -Xmx4g). This can take a long time ...
java -Xms2g -Xmx4g -cp bin Benchmark
if %errorlevel% neq 0 (
    echo [ERROR] Benchmark failed or was aborted. See messages above.
    exit /b 1
)
echo.

rem ---------- Step 4: analysis ----------
echo [4/4] Running analysis ...
set "PYTHONIOENCODING=utf-8"
%PYTHON_CMD% script\analyze.py %*
if %errorlevel% neq 0 (
    echo [ERROR] analyze.py failed. Check that pandas, numpy, matplotlib are installed.
    exit /b 1
)

echo.
echo ============================================================
echo  DONE.  Raw data : result\raw_results*.csv
echo         Analysis : result\analysis\  (tables, summary_tables.md, plots\)
echo ============================================================
if not defined NOPAUSE pause
endlocal
exit /b 0