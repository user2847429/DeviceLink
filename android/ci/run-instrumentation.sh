#!/usr/bin/env bash
set +e
gradle --no-daemon -p android :app:connectedDebugAndroidTest
test_status=$?
mkdir -p android/test-diagnostics
echo "===== ANDROID TEST XML RESULTS ====="
find android/app/build/outputs/androidTest-results android/app/build/reports/androidTests -type f -name '*.xml' -print -exec cat {} + 2>/dev/null | tee android/test-diagnostics/test-results.txt || true
echo "===== TEST REPORT FILES ====="
find android/app/build/reports/androidTests android/app/build/outputs/androidTest-results -type f -print 2>/dev/null | tee android/test-diagnostics/report-files.txt || true
echo "===== COMPLETE DEVICE LOGCAT ====="
adb logcat -d > android/test-diagnostics/emulator-logcat.txt 2>&1 || true
grep -E -i 'AndroidJUnitRunner|Instrumentation|FATAL EXCEPTION|SSL|KeyStore|DeviceIdentity|AndroidTls|TestRunner|INSTRUMENTATION_STATUS' android/test-diagnostics/emulator-logcat.txt | tail -n 400 || true
echo "Instrumentation test exit code: $test_status"
exit "$test_status"
