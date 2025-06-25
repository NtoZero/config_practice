# Keystore Properties Generator Scripts

이 스크립트들은 encloader 데모에서 사용할 PKCS#12 keystore 파일을 생성하고 필요한 속성들을 암호화하여 저장합니다.

## 🚀 스크립트 개요

### Shell Script (`create-keystore-with-properties.sh`)
Linux/macOS 환경에서 사용하는 bash 스크립트

### PowerShell Script (`create-keystore-with-properties.ps1`)
Windows 환경에서 사용하는 PowerShell 스크립트

## 📋 기본 생성 속성

두 스크립트 모두 다음 속성들을 keystore에 저장합니다:

- `JASYPT_PASSWORD`: Jasypt 암호화 패스워드
- `DEMO_SECRET`: 데모용 비밀값
- `DB_PASSWORD`: 데이터베이스 패스워드
- `API_KEY`: API 키

## 🛠️ 사용법

### Shell Script 사용법

```bash
# 실행 권한 부여
chmod +x create-keystore-with-properties.sh

# 기본값으로 실행
./create-keystore-with-properties.sh

# 커스텀 값으로 실행
./create-keystore-with-properties.sh "myKeystorePass" "myJasyptPass" "myDemoSecret" "myDbPass" "myApiKey"
```

### PowerShell Script 사용법

```powershell
# 기본값으로 실행
.\create-keystore-with-properties.ps1

# 커스텀 값으로 실행
.\create-keystore-with-properties.ps1 -KeystorePassword "myKeystorePass" -JasyptPassword "myJasyptPass" -DemoSecret "myDemoSecret" -DbPassword "myDbPass" -ApiKey "myApiKey"

# 위치 매개변수로 실행
.\create-keystore-with-properties.ps1 "myKeystorePass" "myJasyptPass" "myDemoSecret" "myDbPass" "myApiKey"
```

## 📝 매개변수

| 순서 | Shell Script | PowerShell Script | 기본값 | 설명 |
|------|-------------|------------------|-------|------|
| 1 | `$1` | `KeystorePassword` | `keystorePass123!` | Keystore 패스워드 |
| 2 | `$2` | `JasyptPassword` | `jasyptSecret456!` | Jasypt 암호화 패스워드 |
| 3 | `$3` | `DemoSecret` | `demoValue789!` | 데모용 비밀값 |
| 4 | `$4` | `DbPassword` | `dbPassword123!` | 데이터베이스 패스워드 |
| 5 | `$5` | `ApiKey` | `apiKey456789!` | API 키 |

## 📂 생성되는 파일들

### `secrets/keystore.p12`
- 메인 PKCS#12 keystore 파일
- 모든 암호화된 속성들이 저장됨

### `secrets/keystore_properties.txt`
- 생성된 속성들의 참조 파일
- 평문으로 저장되므로 보안에 주의 필요

### `secrets/encrypted_properties.properties`
- 암호화된 속성들의 프로퍼티 파일 형태
- KeystorePropertySource 구현에서 참조 가능

## 🎯 사용 예제

### 생성된 keystore로 encloader 실행

```bash
# Shell 환경변수 설정
export KEYSTORE_PATH="file:secrets/keystore.p12"
export KEYSTORE_PASSWORD="keystorePass123!"

# Java 실행
java -Dkeystore.path=file:secrets/keystore.p12 \
     -Dkeystore.password=keystorePass123! \
     -jar encloader.jar
```

```powershell
# PowerShell 환경변수 설정
$env:KEYSTORE_PATH = "file:secrets/keystore.p12"
$env:KEYSTORE_PASSWORD = "keystorePass123!"

# Java 실행
java -Dkeystore.path=file:secrets/keystore.p12 `
     -Dkeystore.password=keystorePass123! `
     -jar encloader.jar
```

## ⚠️ 주의사항

1. **Java keytool 필요**: 두 스크립트 모두 시스템에 Java가 설치되어 있고 `keytool` 명령어가 사용 가능해야 합니다.

2. **PKCS#12 제한사항**: PKCS#12 형식은 임의의 문자열 속성을 직접 저장하는 것을 지원하지 않습니다. 이 스크립트들은 AES 비밀키를 생성하여 각 속성을 나타내며, 실제 값들은 별도 프로퍼티 파일에 저장됩니다.

3. **보안 고려사항**: 
   - 생성된 `keystore_properties.txt` 파일은 평문이므로 프로덕션 환경에서는 삭제하거나 보안 저장소에 보관하세요.
   - 실제 환경에서는 더 강력한 패스워드를 사용하세요.

4. **권한 설정**: Shell 스크립트의 경우 실행 권한을 부여해야 합니다.

## 🔧 문제 해결

### "keytool을 찾을 수 없습니다" 오류
- Java가 설치되어 있고 PATH에 포함되어 있는지 확인하세요.
- `java -version` 명령으로 Java 설치를 확인하세요.

### 권한 관련 오류 (Linux/macOS)
```bash
chmod +x create-keystore-with-properties.sh
```

### PowerShell 실행 정책 오류 (Windows)
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

## 📞 추가 도움말

더 자세한 정보나 문제 해결이 필요하면 encloader 프로젝트의 다른 문서들을 참조하세요:
- `docs/keystore_property_source_plan.md`
- `docs/usage/keystore_property_source_usage_guide.md`
