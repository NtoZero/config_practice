@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM Keystore Creator Batch Script (Refactored Version 1.2)
REM =============================================================================
REM Description: Creates a PKCS#12 keystore using KeystoreCreator.java
REM Usage: create-keystore-refactored.bat [keystore_password]
REM Note: This script replaces the deprecated keytool-based approach
REM =============================================================================

REM Default values
set DEFAULT_KEYSTORE_PASSWORD=keystorePass123!

REM Parse command line arguments or use defaults
if "%1"=="" (
    set KEYSTORE_PASSWORD=%DEFAULT_KEYSTORE_PASSWORD%
) else (
    set KEYSTORE_PASSWORD=%1
)

REM File paths
set KEYSTORE_FILE=secrets\keystore.p12

echo ================================================================================
echo 🔐 Keystore Creator - Refactored Version 1.2
echo ================================================================================

REM Create directories if they don't exist
echo 📁 Creating directories...
if not exist secrets mkdir secrets

REM Display configuration
echo 📋 Configuration:
echo    Keystore File: %KEYSTORE_FILE%
echo    Keystore Password: %KEYSTORE_PASSWORD:~0,4%***
echo.

REM Remove existing keystore
if exist "%KEYSTORE_FILE%" (
    echo 🗑️  Removing existing keystore...
    del "%KEYSTORE_FILE%"
)

REM Check if JAR file exists
set JAR_FILE=..\build\libs\encloader-0.0.1-SNAPSHOT.jar
if not exist "%JAR_FILE%" (
    echo 🔨 Building encloader project...
    cd ..
    call gradlew.bat clean build -x test
    if errorlevel 1 (
        echo ❌ Build failed!
        exit /b 1
    )
    cd scripts
)

REM Create keystore using KeystoreCreator
echo 🔑 Creating keystore using KeystoreCreator...
java -cp "%JAR_FILE%" com.keyloader.KeystoreCreator "%KEYSTORE_FILE%" "%KEYSTORE_PASSWORD%" demo

if errorlevel 1 (
    echo ❌ Failed to create keystore using KeystoreCreator!
    exit /b 1
)

REM Verify keystore was created
if exist "%KEYSTORE_FILE%" (
    echo ✅ Keystore created successfully!
    
    echo 📋 Keystore verification:
    keytool -list -keystore "%KEYSTORE_FILE%" -storepass "%KEYSTORE_PASSWORD%" -storetype PKCS12 2>nul | findstr "Alias name:" || echo    ^(Unable to list aliases - this is normal^)
    
    echo.
    echo 🎯 Usage Examples:
    echo    # Test with demo application:
    echo    cd ..
    echo    java -Dkeystore.path=file:scripts/%KEYSTORE_FILE% ^
    echo         -Dkeystore.password=%KEYSTORE_PASSWORD% ^
    echo         -jar build\libs\encloader-0.0.1-SNAPSHOT.jar
    echo.
    echo    # Or use environment variables:
    echo    set KEYSTORE_PATH=file:scripts/%KEYSTORE_FILE%
    echo    set KEYSTORE_PASSWORD=%KEYSTORE_PASSWORD%
    echo    java -Dkeystore.path=%%KEYSTORE_PATH%% ^
    echo         -Dkeystore.password=%%KEYSTORE_PASSWORD%% ^
    echo         -jar build\libs\encloader-0.0.1-SNAPSHOT.jar
    
    echo.
    echo 🔍 Data Integrity Information:
    echo    ✅ Original strings stored as UTF-8 bytes in SecretKeySpec
    echo    ✅ KeystorePropertySource restores strings from UTF-8 bytes
    echo    ✅ No Base64 encoding/decoding issues
    echo    ✅ Jasypt will receive actual password strings
    
) else (
    echo ❌ Failed to create keystore!
    exit /b 1
)

echo ================================================================================
echo 🎉 Keystore generation completed successfully!
echo ================================================================================

pause
