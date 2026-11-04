    # Hướng dẫn chạy thử và trình bày

## Bản đóng gói

- `EmployeeManagement-debug.apk`: APK debug đã ký bằng debug key, dùng cài đặt và demo trên Android 6.0/API 23 trở lên.
- `EmployeeManagement-source.zip`: mã nguồn, Gradle Wrapper, schema Room, các test và tài liệu. Giải nén rồi mở thư mục bằng Android Studio.
- `SHA256SUMS.txt`: mã kiểm tra tính toàn vẹn APK và ZIP.

Máy build cần JDK 17 trở lên và Android SDK 35. Android Studio sẽ tạo `local.properties` theo SDK của máy nhận. Lần Sync đầu cần Internet nếu chưa có dependency; bản APK có thể chạy offline.

## Tài khoản có sẵn

| Username | Password | Vai trò |
| --- | --- | --- |
| admin | 123456 | ADMIN |
| emp01 | 123456 | EMPLOYEE, NV01 |
| emp02 | 123456 | EMPLOYEE, NV02 |

Nhân viên tạo mới có username bằng mã nhân viên, password ban đầu 123456. Dữ liệu mẫu chỉ được tạo khi database được mở lần đầu.

## Luồng demo ngắn

1. Đăng nhập admin: tab **Quản lý Nhân viên** hiện danh sách. Thử tìm tên/mã, thêm một nhân viên và sửa mức lương.
2. Chuyển tab **Quản lý Bảng lương**. Chọn tháng/năm, tính lương. Chưa có chấm công COMPLETED thì bảng lương là 0.
3. Đăng xuất bằng nút TopBar. Đăng nhập emp01: tab **Chấm công & Lịch sử** hiển thị hôm nay và lịch sử tháng.
4. Check In: nút Check In bị khóa, Check Out được bật. Check Out: cả hai nút khóa, bản ghi chuyển COMPLETED. Cùng một giây sẽ là 0 giờ; chờ ít nhất 18 giây để làm tròn thành 0,01 giờ.
5. Đăng nhập lại admin, tính lại đúng tháng. Tổng lương cập nhật theo số giờ COMPLETED; chọn chuyển PENDING sang PAID.
6. Đăng nhập lại emp01, chọn tab **Bảng lương cá nhân** để xem số giờ, OT, mức lương áp dụng, tổng lương và PAID.
7. Đóng/mở app: phiên đã lưu mở đúng dashboard. Đăng xuất rồi Back không quay về dashboard. Back ở tab thứ hai trở về tab đầu.

## Cách giải thích kiến trúc

**Compose Screen → ViewModel → DAO/Calculator → Room → Flow/StateFlow → Compose tự cập nhật.**

- `AppNavigation.kt` chỉ có ba route: `login`, `admin_dashboard`, `employee_dashboard`. Session quyết định route; khi đăng nhập/đăng xuất, `popUpTo(graph.id) { inclusive = true }` xóa stack cũ.
- `AdminDashboardScreen` chứa hai tab Nhân viên/Bảng lương; `EmployeeDashboardScreen` chứa hai tab Chấm công/Bảng lương cá nhân. Thanh dưới chỉ đổi nội dung trong dashboard, không đẩy thêm route.
- `rememberSaveable` giữ tab đã chọn, `SaveableStateHolder` giữ trạng thái giao diện từng tab. ViewModel được scope theo dashboard, giữ dữ liệu khi chuyển tab và được hủy khi logout.
- Room có 4 Entity: Account → Employee → WorkLog/Salary. Khóa ngoại CASCADE giúp không còn dữ liệu con mồ côi.
- Check-in kiểm tra và insert trong transaction; checkout kiểm tra trạng thái rồi cập nhật có điều kiện. Thời lượng = số giây chênh lệch / 3600, làm tròn HALF_UP hai chữ số.
- Tính lương chỉ lấy COMPLETED, gom giờ theo ngày, OT ngày = max(0, giờ ngày − 8). Lương = giờ thường × mức lương + OT × mức lương × 1,5.
- Tính lại bảng lương sẽ chuyển kết quả về PENDING theo đề bài, kể cả bảng đã PAID; giao diện yêu cầu xác nhận.

## Lệnh kiểm tra và đóng gói

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --offline
powershell -ExecutionPolicy Bypass -File .\scripts\package-submission.ps1
```

`--offline` dùng khi dependency đã tải đủ; nếu máy mới chưa có cache thì bỏ cờ này. Bộ unit test chạy không cần emulator. Muốn chạy test Room/Compose trên thiết bị hoặc emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

App dùng dữ liệu cục bộ. Chấm công xử lý trong cùng ngày; chưa hỗ trợ ca qua đêm. Công thức lương theo đề bài chưa có phụ cấp/khấu trừ. Hướng dẫn chi tiết từng thành viên nằm trong README.

## Kết quả xác minh bản tích hợp

- APK debug và APK instrumentation test biên dịch thành công.
- 21/21 unit test đạt: 14 test thời gian/validation chấm công, 7 test công thức lương.
- Android Lint không có lỗi; vẫn có cảnh báo không chặn build.
- Test giao diện đã cập nhật để kiểm tra chuyển hai tab và đăng xuất. Ở bước đóng gói này chỉ biên dịch bộ test giao diện, chưa chạy lại trên emulator để tiết kiệm thời gian.
