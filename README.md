# 교대달력 (ShiftCal)

교대근무자(당직/비번/야간/주간)를 위한 달력 + 정확 알람 안드로이드 앱.

## 주요 기능
- **달력 근무 입력**: 날짜 탭 → 당/비/야/주 원터치 지정 (첨부 스크린샷과 동일한 배지 스타일)
- **근무 패턴 자동입력**: "당비비" 같은 패턴 + 시작일 + 일수 → 수개월치 자동 채움
- **근무별 기본 알람**: 당/야/주 각각 기본 기상 알람 시간 지정, 달력에 등록만 하면 자동 예약
- **날짜별 개별 알람**: 특정 근무일에 원하는 알람 추가 (예: 병원 예약), 개별 on/off·삭제
- **비번날 알람 자동 제외**: '비'로 등록된 날은 어떤 근무 알람도 울리지 않음
- **정확 알람**: `AlarmManager.setAlarmClock()` 사용 → Doze(배터리 최적화) 모드에서도 정시에 울림
- **재부팅 복구**: 재부팅/시간변경 시 BootReceiver가 다음 알람 자동 재예약
- **잠금화면 알람 화면**: 화면 꺼진 상태에서도 알람음+진동+전체화면 표시

## 빌드 방법 (APK 만들기)

### 방법 1: Android Studio (권장)
1. Android Studio 설치 (https://developer.android.com/studio)
2. `File > Open` 으로 이 폴더(ShiftCal) 열기 → Gradle Sync 자동 진행
3. 메뉴 `Build > Build App Bundle(s) / APK(s) > Build APK(s)`
4. 생성 위치: `app/build/outputs/apk/debug/app-debug.apk`
5. 휴대폰에 복사 후 설치 (설정에서 '출처를 알 수 없는 앱 설치' 허용 필요)

### 방법 2: 명령줄 (Android SDK 설치된 환경, Claude Code 등)
```bash
# 최초 1회 wrapper 생성 (gradle이 설치되어 있다면)
gradle wrapper
./gradlew assembleDebug
```

## 설치 후 확인할 것
1. 첫 실행 시 **알림 권한** 허용 (Android 13+)
2. **"알람 및 리마인더" 정확 알람 권한** 허용 화면이 뜨면 허용
3. 설정 > 배터리 > 이 앱을 **배터리 사용량 최적화 제외**로 설정하면 가장 안정적

## 구조
```
app/src/main/java/com/hi/shiftcal/
├── MainActivity.kt          # Compose UI (달력, 다이얼로그)
├── data/Store.kt            # SharedPreferences+JSON 저장소 (근무/알람 데이터)
└── alarm/
    ├── AlarmScheduler.kt    # setAlarmClock 체인 예약 로직
    ├── AlarmReceiver.kt     # 알람 발생 → 알림 + 다음 알람 재예약
    ├── BootReceiver.kt      # 재부팅 후 알람 복구
    └── AlarmRingActivity.kt # 잠금화면 위 알람 벨/진동 화면
```

## 다음 단계 아이디어 (기획서 기반)
- 근무별 색상/명칭 커스터마이징 UI
- 구글 캘린더 일정 오버레이 (Calendar Provider API)
- 급여/수당 자동 계산
- 홈 화면 위젯 (Glance)
- 구글 드라이브 백업
