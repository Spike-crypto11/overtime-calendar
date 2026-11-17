# 데이로그 v17 · 기존 preview 앱 업데이트

앱 표시 이름을 manifest의 임시 이름에서 `@string/app_name`(데이로그)으로 연결합니다.
기존 preview 패키지·서명·컴포넌트·권한·저장소를 유지합니다. 위젯 이름은 데이로그 달력입니다.

## 네 스킨의 크기

- 위켄드 플래너·미드나이트: 가로 폭 기준 날짜 칸 1:1.6 상한 유지. 머리글/요일/여백 예산을 96→104dp로 수정하고 높이가 부족할 때 24dp 최소 행 높이 때문에 넘치던 것을 방지합니다.
- 미드나이트에도 기존 위켄드의 최종 글자 확대(행 높이 70dp 이상)를 적용합니다. compact 모드와 1.2배 확대 상한은 유지합니다.
- 오비탈 패널·필드 키트: 750:918 그림 비율과 동일 축척의 터치 위치를 유지합니다. 설정 미리보기도 가로·세로 공간에 맞추며 인위적인 하단 24dp 공백을 제거합니다.
- 기본 5×6칸 및 런처의 크기 변경 기능은 유지합니다. 앱은 홈 런처가 이미 배치한 위젯의 칸 수를 강제 변경하지 않습니다.

## 재현

CI의 preview-build-inputs, 기존 v16 APK, 사용자의 비공개 recovery를 준비합니다.

```bash
python3 scripts/build_daylog_widgets.py --installed /path/to/v16.apk --bundle /path/to/preview-build-inputs --recovery /path/to/recovery --output /path/to/Daylog-v17.apk
```

기존 비위젯 DEX 5개는 바이트 단위로 동일하며, 위젯 DEX에서는 WidgetSizing.plainRowHeight, WeekendLegibility.apply, ArtworkWidget.views 3개 메서드만 변경됩니다.
같은 패키지·서명, 더 높은 versionCode 17, zipalign, 기존 컴포넌트·권한 보존을 검증했습니다.
근무기록·서버 동기화·급여 DEX는 변경하지 않았습니다. 폰/런처에서 실제 모양은 미검증입니다.
