# 적용 상태와 안전한 배포

이 변경은 GitHub main cf14c6499f4dd5e496b2eb4fce4452996f26a503 기준입니다.
수정 코드는 `codex/payroll-monthly-sync` 브랜치에서 검토합니다.
Apps Script 실서버 배포와 기기 내 업데이트 설치 검증은 별도로 필요합니다.
기본 브랜치 병합 또는 실제 스프레드시트 변경을 이 소스 변경으로 수행하지 않습니다.

## 추가로 필요한 서버 자료

1. `잔업특근DB` → 확장 프로그램 → Apps Script → **모든 .gs 파일 및 appsscript.json**.
2. 현재 사용 중인 배포가 그 프로젝트인지 확인할 수 있는 **편집기 URL과 배포 버전**.
3. 독립형 스크립트라면 어떤 함수로 대상 스프레드시트를 여는지.

웹 앱의 `/exec` 주소로는 서버 원본을 읽을 수 없습니다. 저장소에도 서버 파일이 없었고,
연결된 Drive의 Apps Script MIME 검색에서도 프로젝트를 찾지 못했습니다.
따라서 기존 서버 구현과 병합·실서버 동기화 완료를 주장하지 않습니다.

## 서버 확장

`server/Payroll.gs`는 기존 save/list/events/holidays 함수를 대체하지 않는 추가 모듈입니다.
`EntryPoints.example.gs.txt`는 기존 소스를 확인한 다음 적용할 진입점 예시입니다.
기존 doGet/doPost를 legacyDoGet/legacyDoPost로 이름만 바꾸고, 양쪽 데이터 접근에 같은
ScriptLock을 적용해야 합니다. 기존 코드의 잠금과 중복 취득하지 않도록 실제 소스 병합이 필요합니다.
독립형이면 `getActiveSpreadsheet()`를 기존 서버의 대상 열기 함수로 연결하세요.

서버 원본·스프레드시트를 백업한 후 **복사본에서** 다음을 확인하고 같은 기존 배포의 버전을 갱신합니다.
원래 URL은 유지해야 앱의 기존 설정도 유지됩니다.
월별 집계 전송은 기본 꺼짐입니다. 서버 소스를 확인·배포한 뒤 새 급여 화면에서
`서버 월별 집계 기능 배포 확인 · 자동 전송 사용`을 켭니다. 그 전에는 새 요청을 보내지 않으며
기존 앱의 근무기록·일정·공휴일 동기화만 그대로 동작합니다.

- 기존 list/save, 빈 기록 저장(삭제), events/saveEvent/deleteEvent, holidays가 그대로 동작.
- payrollCapabilities가 `{ok:true,payrollProtocol:1}`로 응답.
- 앱 월별 설정의 역할·회사 규칙을 확인 후 월별 집계 전송.
- 전송 실패/오프라인 시 원본 로컬 기록 보존, 다음 실행/새로고침 때 재전송.
- 집계 직전에 Data가 바뀌면 SOURCE_CHANGED_RETRY로 보류되고 재시도됨.
- 로컬·서버 원본 기록이 다르면 집계 전송을 보류하고 두 원본을 모두 보존함.
- Data/Events/Holidays의 행·수식이 그대로이며 다른 월 집계도 그대로임.
- 급여월별집계 E/F 기존 수식 유지. 새 열은 기존 마지막 열 뒤에 추가.

월별 서버 전송은 새로운 POST 프로토콜입니다. 미지원 서버에는 기존 기록만 동기화됩니다.
앱·위젯 저장 성공, 월 조회 성공, 앱 실행/새로고침에서 재시도하며, 앱이 종료된 동안의
상시 백그라운드 전송이나 서버에서만 변경한 기록의 푸시 알림은 구현하지 않았습니다.
서버에서만 변경한 뒤에는 앱을 열거나 새로고침해야 집계가 갱신됩니다.

## 집계의 의미

- 역할은 카테고리 ID로 지정. 기본 잔업/overtime, 특근/special만 기본 연결.
- 출근·반차 항목은 사용자 설정에서 연결. 미분류 ID는 경고하며 자동 삭제하지 않음.
- 출근+반차가 같은 날 있으면 반차 1일로 집계. 잔업·특근만 있으면 출근으로 추정하지 않음.
- 기록일수와 회사 급여명세서 근무일수는 별개. 회사 기준 확인 체크와 적용 근거가 있어야 회사 일수 산출.
- 기본시간은 확인한 출근/반차 시간만 사용. 반차가 있어도 반차 시간이 미확정이면 전체 기본시간 미확정.
- 주휴는 '확인된 주휴 1회 표시'로 연결한 기록을 셈. 달력의 일요일/주 개수로 인정 횟수를 추정하지 않음.
- 주차수당은 확인된 1회당 금액 × 확인된 횟수. 미확정 값은 빈칸이며 0원과 다름.
- 설정·금액은 월별 저장. 과거 월의 기준을 새 월에 임의로 적용하지 않음.
- 지급 항목 기본급/잔업/특근/직책/기타는 현재 월별 확인 금액 입력 방식. 주차수당만 별도 규칙 계산.
- 소득세/지방세/국민연금/건강보험/장기요양/고용보험/보험정산/기타 공제를 각각 저장·합산.
  추가 지급·공제 항목도 모델에서 지원. 하나라도 미확정이면 실수령액은 미확정.
- 현재 법정 요율·상하한·간이세액표는 내장하지 않음. `PayrollDeduction`은 별도의 기준액,
  요율, 절사 단위·방식, 근거를 받는 확장 지점. 실제 표/요율/부양가족/과세 기준을 확인해야 연결 가능.
- `PayrollEngine.compare(예상, 실제)`는 실제-예상 차이를 반환. 미확정 값은 차액도 미확정.
  실제 명세서 자동 OCR/법적 과소지급 판정은 구현 범위가 아님.

## 기존 APK와 소스 불일치

GitHub 소스: `com.dohyun.overtimecalendar`, 기존 versionCode 1.
제공된 APK: `com.dohyun.overtimecalendar.preview`, versionCode 14, 1.13-weekend-legibility.
제공된 APK SHA256: `8e76c240c5d824a9f316a29514be457cc4011197e32685c738b11f49fbb7cfa4`.
제공된 APK 인증서 SHA256: `a5c9c6e7735df5ca4e9dfdab873cd7d8ac6926e7618395e699c8906492dd8207`.
복구 자료에는 preview 전용 개발키가 있으며, 원래 일반 패키지의 서명키가 아닙니다.

GitHub 원본 빌드는 versionCode 2이며 **위 preview 앱을 업데이트할 수 없습니다**.
따라서 원본 debug APK를 설치용으로 제공하지 않습니다.
별도로 제공하는 preview 업데이트는 APK 복구본에 새 모듈을 추가한 versionCode 15입니다.
기존 화면·위젯·알림 코드, 저장소 이름, 키와 기존 패키지를 보존합니다.
이 복구 경로를 원본 Kotlin 전체 소스 복원이라고 부르지 않습니다.
폰 설치 및 실제 위젯 실행 검증은 별도 필요합니다. 삭제·재설치나 저장소 초기화는 하지 마세요.
패키지/인증서/버전 비교는 `scripts/check_apk_update.py`로 재현합니다.

## GitHub 반영

저장소 접근 권한이 있는 로컬 Git 환경에서 수정 소스나 제공된 패치를 적용해 다음처럼 반영합니다.

```bash
git switch -c codex/payroll-monthly-sync
git apply /path/to/overtime-calendar-payroll.patch
git add .
git commit -m "Add non-destructive monthly payroll sync and explicit pay rules"
git push -u origin codex/payroll-monthly-sync
```

GitHub에서 이 브랜치를 main 대상 PR로 엽니다. 현재 main의 기본 상태가 바뀌었다면 먼저 충돌을 검토해야 합니다.
GitHub 인증은 사용하는 환경의 연결을 따릅니다. 비밀번호·개인 액세스 토큰·서명키를
채팅이나 저장소에 올리지 마세요.

## 빌드·검증

JDK17, Gradle8.7, Android SDK34 / build-tools34.0.0.

```bash
node --test tests/payroll-server.test.cjs
gradle testDebugUnitTest lintDebug assembleDebug --no-daemon
python scripts/check_apk_update.py old.apk new.apk --build-tools "$ANDROID_HOME/build-tools/34.0.0"
```

원본 소스 앱 빌드와 별도 preview APK 재조립은 서로 다른 작업입니다.
실제 서버 배포/폰 업데이트 설치/저장기록의 기기 내 유지 테스트는 완료한 것으로 간주하지 않습니다.

## 제공된 확인용 APK를 다시 만드는 방법

`WeekendPlanner-recovery.zip`을 풀어 preview-project, signing 폴더가 있는 경로를 지정합니다.
서명키는 기존 복구 자료에서만 사용하며 GitHub/새 소스 ZIP에 포함하지 않습니다.
먼저 위 Gradle 명령으로 Kotlin 클래스를 빌드한 뒤 실행합니다.

```bash
python scripts/build_preview.py --recovery /path/to/recovery --baseline /path/to/WeekendPlanner-old.apk --sdk "$ANDROID_HOME" --apktool /path/to/apktool_2.12.1.jar --output /path/to/WeekendPlanner-payroll-preview.apk
```

이 빌더는 SHA256으로 특정 제공 APK를 검사하며 다른 기초 APK를 임의로 수정하지 않습니다.
새 Payroll 모듈만 별도 dex에 추가하고, 설정 화면 진입 버튼과 저장/조회/전송 완료 후 집계 호출을
기존 메서드에 연결합니다. 동시 수정 보호를 위해 Prefs 읽기·쓰기와 전송 확인을 같은 잠금으로 묶습니다.
기존 알림·일정·위젯·리소스는 복구본에서 유지합니다.

정적 코드 보존 검사는 Python androguard가 필요합니다.

```bash
python scripts/verify_preview.py old.apk new.apk --report preview-code-preservation.json
```
