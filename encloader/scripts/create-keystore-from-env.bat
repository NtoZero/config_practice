@echo off
setlocal enabledelayedexpansion

:: =================================================================
:: .env ���Ϸκ��� Ű����� ���� ��ũ��Ʈ (Batch)
:: ��й�ȣ �ڵ� ���� �� ���� ���� ����
:: =================================================================

:: --- ����: Ű����� ���ϰ� .env ���� ��θ� �����մϴ�. ---
set "KEYSTORE_FILE=secrets\keystore.p12"
set "PASSWORD_FILE=secrets\keystore_password.txt"
set "ENV_FILE=secrets.env"

echo =================================================================
echo  Creating a secure keystore from .env file (Batch)
echo  (Keystore password will be auto-generated)
echo =================================================================

:: 1. .env ������ �����ϴ��� Ȯ���մϴ�.
if not exist "%ENV_FILE%" (
    echo [ERROR] .env file not found: %ENV_FILE%
    goto :end
)

:: 2. ������ Ű����� ��й�ȣ�� �����մϴ�.
:: %RANDOM% ������ �����Ͽ� ������ ���� ��й�ȣ�� ����ϴ�.
set "KEYSTORE_PASSWORD=auto-gen-pass-%RANDOM%-%RANDOM%"
echo [INFO] A random keystore password has been generated.

:: 3. 'secrets' ������ �����ϰ�, ������ ��й�ȣ�� ���Ͽ� �����մϴ�.
if not exist "secrets" mkdir "secrets"

echo !KEYSTORE_PASSWORD! > "!PASSWORD_FILE!"
echo [INFO] The generated password has been saved to: !PASSWORD_FILE!

:: ���� Ű����� ������ ������ �����մϴ�.
if exist "%KEYSTORE_FILE%" (
    echo [INFO] Deleting existing keystore file: %KEYSTORE_FILE%...
    del "%KEYSTORE_FILE%"
)

:: 4. .env ������ �� �پ� �о� Ű-�� ���� Ű���� �߰��մϴ�.
echo [INFO] Reading '%ENV_FILE%' and creating the keystore...
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ENV_FILE%") do (
    set "KEY=%%A"
    set "VALUE=%%B"
    
    if defined KEY (
        echo   + Adding alias: !KEY!...
        
        :: echo ��ɾ�� ��й�ȣ ���� keytool�� ǥ�� �Է����� �����մϴ�.
        echo !VALUE! | keytool -importpass -alias !KEY! -keystore "%KEYSTORE_FILE%" -storetype PKCS12 -storepass "!KEYSTORE_PASSWORD!" -keypass "!KEYSTORE_PASSWORD!" -noprompt
        
        :: keytool ��ɾ� ���� �� ������ �߻��ߴ��� Ȯ���մϴ�.
        if errorlevel 1 (
            echo [ERROR] Failed to add alias: !KEY!
            goto :end
        )
    )
)

:: 5. ���������� ������ Ű������� ������ Ȯ���մϴ�.
echo.
echo [INFO] Verifying final keystore contents...
keytool -list -v -keystore "%KEYSTORE_FILE%" -storepass "!KEYSTORE_PASSWORD!"

echo.
echo =================================================================
echo [SUCCESS] Keystore file has been created: %KEYSTORE_FILE%
echo [IMPORTANT] The keystore password is saved in: %PASSWORD_FILE%
echo =================================================================

:end
endlocal
pause