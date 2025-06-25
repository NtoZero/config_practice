@echo off
setlocal enabledelayedexpansion

:: =================================================================
:: .env 파일로부터 키스토어 생성 스크립트 (Batch)
:: 비밀번호 자동 생성 및 파일 저장 버전
:: =================================================================

:: --- 설정: 키스토어 파일과 .env 파일 경로를 지정합니다. ---
set "KEYSTORE_FILE=secrets\keystore.p12"
set "PASSWORD_FILE=secrets\keystore_password.txt"
set "ENV_FILE=secrets.env"

echo =================================================================
echo  Creating a secure keystore from .env file (Batch)
echo  (Keystore password will be auto-generated)
echo =================================================================

:: 1. .env 파일이 존재하는지 확인합니다.
if not exist "%ENV_FILE%" (
    echo [ERROR] .env file not found: %ENV_FILE%
    goto :end
)

:: 2. 임의의 키스토어 비밀번호를 생성합니다.
:: %RANDOM% 변수를 조합하여 간단한 난수 비밀번호를 만듭니다.
set "KEYSTORE_PASSWORD=auto-gen-pass-%RANDOM%-%RANDOM%"
echo [INFO] A random keystore password has been generated.

:: 3. 'secrets' 폴더를 생성하고, 생성된 비밀번호를 파일에 저장합니다.
if not exist "secrets" mkdir "secrets"

echo !KEYSTORE_PASSWORD! > "!PASSWORD_FILE!"
echo [INFO] The generated password has been saved to: !PASSWORD_FILE!

:: 기존 키스토어 파일이 있으면 삭제합니다.
if exist "%KEYSTORE_FILE%" (
    echo [INFO] Deleting existing keystore file: %KEYSTORE_FILE%...
    del "%KEYSTORE_FILE%"
)

:: 4. .env 파일을 한 줄씩 읽어 키-값 쌍을 키스토어에 추가합니다.
echo [INFO] Reading '%ENV_FILE%' and creating the keystore...
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ENV_FILE%") do (
    set "KEY=%%A"
    set "VALUE=%%B"
    
    if defined KEY (
        echo   + Adding alias: !KEY!...
        
        :: echo 명령어로 비밀번호 값을 keytool의 표준 입력으로 전달합니다.
        echo !VALUE! | keytool -importpass -alias !KEY! -keystore "%KEYSTORE_FILE%" -storetype PKCS12 -storepass "!KEYSTORE_PASSWORD!" -keypass "!KEYSTORE_PASSWORD!" -noprompt
        
        :: keytool 명령어 실행 중 오류가 발생했는지 확인합니다.
        if errorlevel 1 (
            echo [ERROR] Failed to add alias: !KEY!
            goto :end
        )
    )
)

:: 5. 최종적으로 생성된 키스토어의 내용을 확인합니다.
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