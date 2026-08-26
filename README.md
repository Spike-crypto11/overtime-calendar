# 잔업특근 캘린더 (Android)

한 달 달력 위젯으로 잔업·특근 시간을 기록하고, 구글 시트를 통해 폰↔PC 실시간 동기화.

## 구조
- 앱: 월 달력 + 날짜 탭 입력 + 월 합계
- 홈 위젯: 이번 달 달력, 칸 탭 → 입력 팝업
- 동기화: Apps Script 웹앱(GET) ↔ 구글 시트. 앱은 OAuth 불필요.

## 빌드 (GitHub Actions)
1. 이 폴더 전체를 깃허브 저장소에 올린다 (main 브랜치).
2. push 하면 자동 빌드. 또는 Actions 탭 → Build APK → Run workflow.
3. 빌드 끝나면 Artifacts 에서 `overtime-calendar-debug-apk` 다운로드 → 폰에 설치.

## 서버 연결
1. 구글 시트 생성 → 확장 프로그램 → Apps Script → Code.gs 붙여넣기.
2. 배포 → 새 배포 → 웹 앱 → 실행: 나 / 액세스: 모든 사용자 → URL 복사.
3. 앱 설치 후 우측 상단 ⚙ → URL 붙여넣기 → 연결 테스트 → 저장.

## 위젯 추가
홈 화면 길게 누르기 → 위젯 → "잔업특근 캘린더" → 배치.
