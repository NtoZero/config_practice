@echo off
REM ---------------------------------------------------------------------------
REM create-keystore-from-secret.bat - Batch script for creating a .p12 keystore on Windows CMD (password-based)
REM
REM [Changes]
REM - Instead of generating an RSA private key (-genkeypair),
REM - this script directly saves a user-specified password value (-importpass).
REM ---------------------------------------------------------------------------

REM --- 1) Generate a 32-byte random password for the keystore 'container' ---
REM This password is used to encrypt the keystore file itself.
for /f "delims=" %%A in ('
  powershell -NoProfile -Command ^
    "[Convert]::ToBase64String((0..31 | ForEach-Object {Get-Random -Maximum 256}) -as [byte[]])"
') do set "STOREPASS=%%A"

REM --- 2) Save the generated keystore password to a secure file ---
set "passFile=keystore_pass.txt"
powershell -NoProfile -Command "Set-Content -Path '%passFile%' -Value '%STOREPASS%' -NoNewline"

REM Set read-only permissions for the file owner
icacls "%passFile%" /inheritance:r >nul
icacls "%passFile%" /grant:r "%USERNAME%:R" >nul

echo.
echo [INFO] Keystore container password has been saved to the %passFile% file.
echo.

REM --- 3) Verify keytool path ---
set "KEYTOOL=%JAVA_HOME%\bin\keytool.exe"
if not exist "%KEYTOOL%" (
  echo [ERROR] Could not find keytool.exe. Please check your JAVA_HOME environment variable.
  exit /b 1
)

REM --- 4) *** Store the hard-coded password in the .p12 keystore (Core Change) ***
REM Uses -importpass instead of -genkeypair to store a specific password value directly.

set "JASYPT_SECRET_ALIAS=jasypt-secret-key"
REM VVV Enter the actual password value to store in the keystore here. VVV
set "JASYPT_SECRET_VALUE=DAFDAFASFDSAFDSADSFDSAFDS"

echo [INFO] Storing the specified password in the keystore...
echo %JASYPT_SECRET_VALUE% | "%KEYTOOL%" ^
  -importpass ^
  -alias %JASYPT_SECRET_ALIAS% ^
  -keystore keystore.p12 ^
  -storetype PKCS12 ^
  -storepass %STOREPASS%

if %errorlevel% neq 0 (
  echo [ERROR] Failed to store the password in the keystore.
  set "STOREPASS="
  set "JASYPT_SECRET_VALUE="
  exit /b 1
)

REM --- 5) Clear sensitive information from memory ---
set "STOREPASS="
set "JASYPT_SECRET_VALUE="

echo.
echo [SUCCESS] keystore.p12 created successfully - The container password is saved in the %passFile% file.
echo.

REM --- Next Steps Guide ---
echo [Next Steps]
echo 1. Set the environment variable using: set /p KEYSTORE_PASSWORD^<keystore_pass.txt
echo 2. The `${jasypt-secret-key}` placeholder in your application.yml will be replaced by the value from the keystore.
echo 3. Start the application: cd .. && gradlew.bat bootRun --args="--spring.profiles.active=local"
echo.

echo [Security Enhancements]
echo - The password for Jasypt encryption is stored encrypted inside the keystore.
echo - Even if the keystore container password is leaked, the Jasypt password is not directly exposed.
echo.

echo [Security Notice]
echo - Never commit keystore_pass.txt and keystore.p12 files to version control.
echo - Ensure you set appropriate file permissions in a production environment.
echo.
