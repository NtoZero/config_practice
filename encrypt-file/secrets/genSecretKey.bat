@echo off
REM ---------------------------------------------------------------------------
REM create-keystore.bat — Windows CMD용 .p12 키스토어 생성 배치 스크립트 (개인키 기반)
REM 실행: 관리자 권한 CMD에서 실행
REM ---------------------------------------------------------------------------

REM 1) 32바이트 난수 기반 STOREPASS 생성 (PowerShell 호출)
for /f "delims=" %%A in ('
  powershell -NoProfile -Command ^
    "[Convert]::ToBase64String((0..31 | ForEach-Object {Get-Random -Maximum 256}) -as [byte[]])"
') do set "STOREPASS=%%A"

REM 2) 비밀번호를 안전한 파일에 저장 (현재 경로)
set "passFile=keystore_pass.txt"
echo %STOREPASS% > "%passFile%"

REM 파일 소유자만 읽기 권한 설정
icacls "%passFile%" /inheritance:r >nul
icacls "%passFile%" /grant:r "%USERNAME%:R" >nul

echo.
echo [정보] 키스토어 비밀번호를 %passFile% 파일에 저장했습니다.
echo.

REM 3) keytool 경로 확인
set "KEYTOOL=%JAVA_HOME%\bin\keytool.exe"
if not exist "%KEYTOOL%" (
  echo [ERROR] keytool.exe 을 찾을 수 없습니다. JAVA_HOME 환경변수를 확인하세요.
  exit /b 1
)

REM 4) .p12 키스토어 생성 (새로운 별칭 사용)
echo [진행] 키스토어를 생성 중입니다...
"%KEYTOOL%" ^
  -genkeypair ^
  -alias jasypt-secret-key ^
  -storetype PKCS12 ^
  -keystore keystore.p12 ^
  -storepass %STOREPASS% ^
  -keyalg RSA -keysize 4096 ^
  -validity 3650 ^
  -dname "CN=jasypt-private-key, OU=Security, O=YourOrg, L=Seoul, C=KR"

REM 민감 정보 메모리 해제
set "STOREPASS="

echo.
echo [완료] keystore.p12 생성 완료 — 비밀번호는 %passFile% 파일에 저장되었습니다.
echo.

REM 다음 단계 (개인키 기반 JASYPT):
echo 1. set /p KEYSTORE_PASSWORD^=<keystore_pass.txt 로 환경변수 설정
echo 2. JASYPT 암호화는 키스토어 내부 개인키를 자동으로 사용합니다
echo 3. 애플리케이션 시작: cd .. && gradlew.bat bootRun --args="--spring.profiles.active=local"
echo.

echo [보안 개선사항]
echo - JASYPT 암호화에 키스토어 개인키 사용
echo - 키스토어 비밀번호와 JASYPT 암호화 키 완전 분리
echo - 키스토어 비밀번호 노출되어도 암호화 키는 안전
echo - 키 로테이션 시 유연성 증가
echo.

echo [보안 주의사항]
echo - keystore_pass.txt 파일과 keystore.p12 파일은 절대 버전 관리에 포함하지 마세요
echo - 운영 환경에서는 적절한 권한 설정을 하세요
echo - 개인키는 더욱 강력한 보안을 제공합니다
