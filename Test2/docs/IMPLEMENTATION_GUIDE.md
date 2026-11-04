# Employee Working Hours and Salary Management

Bản tích hợp cuối: Admin có hai tab **Quản lý Nhân viên / Quản lý Bảng lương**; Employee có hai tab **Chấm công & Lịch sử / Bảng lương cá nhân**. Cả hai dashboard có nút Đăng xuất ở TopBar. Hướng dẫn nộp bài và demo: [SUBMISSION_GUIDE.md](../SUBMISSION_GUIDE.md). Chạy `scripts/package-submission.ps1` sau khi build để tạo APK và ZIP mã nguồn trong `submission/`.

Phần database Kotlin/Room nằm trong `app/src/main/java/com/example/employeemanagement/data/local`:

- `entity/`: Account, Employee, WorkLog, Salary; giữ nguyên các trường và kiểu dữ liệu yêu cầu.
- `dao/`: CRUD; đăng nhập, tìm nhân viên, truy vấn chấm công và lương theo nhân viên/tháng, tổng giờ đã checkout, cập nhật trạng thái lương.
- `AppDatabase.kt`: singleton, schema version 1, callback khởi tạo dữ liệu.

## Khởi tạo và sử dụng

Mở thư mục này bằng Android Studio, dùng JDK 17 trở lên và Android SDK 35, rồi Sync Gradle và Run `app`. `MainActivity` là launcher Activity và hiển thị giao diện Compose.

```kotlin
import com.example.employeemanagement.data.local.AppDatabase

val database = AppDatabase.getInstance(applicationContext)

// Gọi các hàm suspend trong coroutine (ví dụ viewModelScope.launch).
val account = database.accountDao().login("emp01", "123456")
val employee = account?.let { database.employeeDao().getByAccountId(it.id) }
val salary = employee?.let {
    database.salaryDao().getByEmployeeAndMonth(it.id, month = 10, year = 2026)
}

// Các truy vấn observe trả về Flow; collect để nhận cập nhật tự động.
val employees = database.employeeDao().observeAll()
```

Room mở file ở lần truy vấn đầu tiên. Callback `onCreate` chèn trực tiếp qua `SupportSQLiteDatabase` trong transaction tạo database, trước khi truy vấn được thực hiện. Callback không mở lại singleton và không chạy coroutine nền, tránh đệ quy hoặc truy vấn đến trước dữ liệu mẫu. ID tự sinh của Account được dùng cho Employee tương ứng. Dữ liệu chỉ được chèn khi tạo database mới, không chèn lại mỗi lần khởi động.

| Username | Password | Role | Employee code | Hourly rate |
| --- | --- | --- | --- | --- |
| admin | 123456 | ADMIN | — | — |
| emp01 | 123456 | EMPLOYEE | NV01 | 50000.0 |
| emp02 | 123456 | EMPLOYEE | NV02 | 50000.0 |

Tên, email và số điện thoại trong callback là dữ liệu mẫu có thể sửa trước lần tạo database đầu tiên. WorkLog và Salary ban đầu rỗng.

## Quy ước dữ liệu

- `workDate`: `yyyy-MM-dd` (ví dụ `2026-10-24`), để SQLite truy vấn đúng tháng/năm. Giờ: `HH:mm:ss`. `checkOutTime = null` nghĩa là ca đang mở.
- `getTotalHoursForMonth` cộng `totalHours` của các ca đã checkout; việc tính thời lượng, nghỉ giữa ca, tăng ca và quy tắc trả lương thuộc tầng nghiệp vụ.
- `role`: `ADMIN`, `EMPLOYEE`. Các trường `status` giữ kiểu String; tầng nghiệp vụ thống nhất giá trị. Ví dụ: `WORKING`/`COMPLETED` và `PENDING`/`PAID`.
- `username`, `code`, `accountId` của Employee và bộ `(employeeId, month, year)` của Salary có unique index: một hồ sơ cho mỗi tài khoản, một bảng lương cho mỗi nhân viên/tháng.
- Employee tham chiếu Account với CASCADE. WorkLog và Salary cũng tham chiếu Employee với CASCADE để tránh bản ghi mồ côi.
- Insert dùng `ABORT`, update dùng `@Update`; tránh `REPLACE` xóa bản ghi cha và kích hoạt CASCADE ngoài ý muốn.
- Schema được xuất vào `app/schemas` qua KSP. Khi đổi schema, tăng version và bổ sung migration.
- Password hiện lưu String theo yêu cầu của giai đoạn này; triển khai đăng nhập thực tế cần cơ chế xác thực và bảo vệ mật khẩu phù hợp.

## Kiểm tra

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Lệnh thứ hai cần emulator hoặc thiết bị Android. Instrumentation tests kiểm tra dữ liệu mẫu có ngay ở truy vấn đầu tiên, mở lại không chèn lại, khóa ngoại CASCADE, ràng buộc bảng lương theo tháng và checkout chỉ được thực hiện một lần.

## Đăng nhập và quản lý phiên

- `data/session/SharedPrefManager.kt`: SharedPreferences riêng `employee_session`, lưu `isLoggedIn`, `userId` (Account.id), `employeeId` (Employee.id), `role`, `username`. Không lưu password. `saveSession()` ghi cả phiên trong một editor; `logout()` xóa toàn bộ preferences này. Getter ID trả `NO_ID = -1L` khi không có; ADMIN không lưu `employeeId`. `getSession()` trả null khi phiên thiếu ID hoặc có role không hợp lệ.
- `ui/auth/AuthViewModel.kt`: dùng `viewModelScope` và Room transaction để truy vấn Account, rồi Employee nếu role là EMPLOYEE. Chỉ lưu phiên sau khi xác thực đầy đủ. Có trạng thái loading, lỗi thiếu thông tin, sai tài khoản/mật khẩu, thiếu hồ sơ nhân viên, role không hợp lệ và lỗi truy cập database. Username được trim; password giữ nguyên. Bấm liên tiếp trong lúc tải không tạo thêm yêu cầu.
- `ui/auth/LoginScreen.kt`: form username/password, hiện/ẩn mật khẩu, IME Done để đăng nhập, loading và thông báo lỗi. Password chỉ ở bộ nhớ, không lưu vào saved instance state. Giao diện hỗ trợ cuộn khi bàn phím mở hoặc màn hình nhỏ.
- `ui/dashboard/DashboardScreens.kt`: `AdminDashboardScreen` và `EmployeeDashboardScreen` tích hợp đầy đủ các module qua NavigationBar; TopBar hiển thị thông tin phiên và nút đăng xuất. Các tab giữ trạng thái khi chuyển đổi.

Đọc phiên ở nơi cần thông tin người dùng:

```kotlin
val sessionManager = SharedPrefManager(applicationContext)
val isLoggedIn: Boolean = sessionManager.isLoggedIn
val accountId: Long = sessionManager.userId
val employeeId: Long = sessionManager.employeeId // -1L nếu là ADMIN hoặc chưa đăng nhập
val role: String = sessionManager.role
val username: String = sessionManager.username
```

Trong UI, gọi `authViewModel.logout()` để vừa xóa SharedPreferences vừa cập nhật StateFlow và điều hướng ngay về Login.

### Tích hợp Navigation

Các dependency Compose, Lifecycle, Navigation Compose và Compose compiler plugin đã cấu hình trong Gradle. Manifest đã khai báo `MainActivity` với MAIN/LAUNCHER. Activity tạo một AuthViewModel dùng chung cho toàn bộ graph:

```kotlin
private val database by lazy { AppDatabase.getInstance(applicationContext) }
private val sessionManager by lazy { SharedPrefManager(applicationContext) }
private val authViewModel: AuthViewModel by viewModels {
    AuthViewModel.Factory(database, sessionManager)
}

// Bên trong setContent và MaterialTheme:
AppNavigation(authViewModel, database, sessionManager)
```

`ui/navigation/AppNavigation.kt` khai báo `NavHost` với ba route:

| Route | Màn hình | Điều kiện |
| --- | --- | --- |
| `login` | LoginScreen | Chưa có phiên hợp lệ |
| `admin_dashboard` | AdminDashboardScreen, hai tab Nhân viên/Bảng lương | role ADMIN |
| `employee_dashboard` | EmployeeDashboardScreen, hai tab Chấm công/Bảng lương cá nhân | role EMPLOYEE, có employeeId |

Khi mở app, AuthViewModel đọc phiên từ SharedPreferences. Route bắt đầu được chọn theo phiên nên người đã đăng nhập đi thẳng tới dashboard tương ứng. UI collect StateFlow bằng `collectAsStateWithLifecycle()`. NavHost chỉ có ba route ở trên; các module được nhúng trong hai tab mỗi dashboard. Khi route khác route của phiên hiện tại, `LaunchedEffect` điều hướng với:

```kotlin
navController.navigate(targetRoute) {
    popUpTo(navController.graph.id) { inclusive = true }
    launchSingleTop = true
}
```

Cách này xóa toàn bộ stack cũ: Back sau đăng nhập không trở về Login; Back sau đăng xuất không mở lại dashboard. Mỗi dashboard kiểm tra role trước khi hiển thị. Back tại tab thứ hai trở về tab đầu; Back tại tab đầu thoát Activity. Không giữ backstack của phiên đăng nhập cũ.

Kịch bản thử thủ công:

1. Đăng nhập `admin / 123456`: vào Trang quản trị, không có ID nhân viên.
2. Đóng/mở app: vào thẳng Trang quản trị.
3. Đăng xuất: trở về Login; Back không khôi phục dashboard.
4. Đăng nhập `emp01 / 123456` hoặc `emp02 / 123456`: vào Trang nhân viên với đúng Account.id và Employee.id.
5. Nhập sai mật khẩu: hiển thị lỗi, không tạo phiên.

`AuthViewModelTest` dùng Room thật và SharedPreferences riêng để kiểm tra đăng nhập hai role, lưu/khôi phục/xóa phiên, thiếu hồ sơ nhân viên, role không hỗ trợ và thông tin đăng nhập sai. `AuthNavigationTest` kiểm tra form đăng nhập, thông báo lỗi, chuyển sang dashboard nhân viên, mở dashboard quản trị từ phiên có sẵn và đăng xuất.

Ở giai đoạn Login/Session, đã xác minh trên emulator Android 13 (API 33): `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` và `connectedDebugAndroidTest` đều thành công; 12/12 instrumentation tests đạt. Lint không có lỗi; còn cảnh báo phiên bản dependency, cấu hình data extraction và thiếu launcher icon riêng.

## Module quản lý nhân viên — phần cần nhớ khi trình bày

Mở app → đăng nhập `admin / 123456` → **Quản lý nhân viên**.

Luồng xử lý: **EmployeeManagementScreen → EmployeeViewModel → EmployeeDao → Room → Flow → màn hình tự cập nhật**.

| File | Trách nhiệm |
| --- | --- |
| `ui/employee/EmployeeManagementScreen.kt` | Search, danh sách thẻ, form thêm/sửa, xác nhận xóa |
| `ui/employee/EmployeeViewModel.kt` | StateFlow danh sách, trạng thái form, kiểm tra dữ liệu, Add/Update/Delete/Search |
| `data/local/dao/EmployeeDao.kt` | SQL và các transaction Account + Employee |
| `ui/navigation/AppNavigation.kt` | Scope ViewModel theo dashboard; module nhân viên nằm trong tab dành cho ADMIN |

Điểm chính:

1. **Search:** `LIKE` theo `fullname` hoặc `code`, dùng tên bảng thực tế `employees`. `flatMapLatest` chuyển sang truy vấn mới khi nội dung tìm kiếm thay đổi. Room phát Flow khi dữ liệu thay đổi, nên không cần gọi tải lại sau CRUD.
2. **Add:** `insertWithAccount()` có `@Transaction`: tạo Account với username = code, password = `123456`, role = EMPLOYEE; dùng ID tự sinh làm accountId của Employee. Một bước lỗi thì rollback cả hai bước, không để tài khoản dư.
3. **Update:** `updateWithAccount()` giữ accountId. Nếu đổi code thì đổi username trong cùng transaction; không đổi mật khẩu. Với dữ liệu mẫu, emp01/emp02 vẫn giữ nguyên username nếu chỉ sửa các trường khác.
4. **Delete:** xác nhận trước khi gọi `deleteWithAccount()`. Xóa Account sẽ CASCADE xuống Employee, WorkLog và Salary. Dialog nêu rõ phạm vi xóa.
5. **Validation:** tất cả trường bắt buộc; mã không chứa khoảng trắng; email đúng định dạng; điện thoại là `0` + 9 chữ số hoặc `+84` + 9 chữ số; hourlyRate là số hữu hạn lớn hơn 0 (ví dụ `50000` hoặc `50000.5`). Trùng code/username báo lỗi và giữ form để sửa.
6. **Phân quyền:** Navigation chỉ mở module cho ADMIN; ViewModel kiểm tra phiên Admin trước khi ghi dữ liệu. Các nút lưu/xóa được khóa khi đang xử lý để tránh thao tác lặp.

Thử nhanh: thêm NV03 → tìm theo NV03 hoặc họ tên → sửa mức lương → đăng xuất và đăng nhập `NV03 / 123456` → quay lại Admin và xóa NV03. Danh sách hiển thị mức lương theo định dạng Việt Nam, đơn vị VNĐ/giờ.

## Tính lương, OT và bảng lương — phần Thành viên 4

Admin → **Bảng lương** → nhập tháng/năm → **Xem tháng đã chọn** → **Tính toán lương tháng** → xác nhận. Mỗi bảng PENDING có nút chuyển sang PAID. Employee → **Bảng lương** → chọn tháng/năm để xem bảng lương của chính mình; không có quyền tính lương hay thay đổi thanh toán.

Luồng cần nhớ: **SalaryScreen → SalaryViewModel → SalaryCalculator → WorkLogDao + EmployeeDao → SalaryDao → Flow cập nhật giao diện**.

| File | Trách nhiệm |
| --- | --- |
| `domain/salary/SalaryMath.kt` | Gom giờ theo ngày, tính giờ thường, OT và tổng tiền |
| `domain/salary/SalaryCalculator.kt` | Đọc Room, tính lương một nhân viên hoặc toàn bộ nhân viên, insert/update trong transaction |
| `ui/salary/SalaryViewModel.kt` | Chọn tháng/năm, StateFlow danh sách, phân quyền, tính lương và đánh dấu PAID |
| `ui/salary/SalaryScreens.kt` | AdminSalaryScreen và EmployeeSalaryScreen |
| `data/local/model/SalaryDetails.kt` | Kết quả JOIN bảng lương với tên/mã nhân viên và thông tin mức lương |

Công thức đúng theo đề bài:

```text
Chỉ lấy WorkLog.status = COMPLETED trong đúng tháng/năm.
Giờ của ngày = tổng totalHours của tất cả ca COMPLETED cùng workDate.
OT ngày = max(0, giờ của ngày - 8).
totalHours = tổng giờ các ngày.
overtimeHours = tổng OT ngày.
totalSalary = (totalHours - overtimeHours) × hourlyRate
            + overtimeHours × hourlyRate × 1.5.
```

Ví dụ: ngày 1 làm 10 giờ, ngày 2 làm 6 giờ, mức lương 50.000 VNĐ/giờ → tổng 16 giờ, OT 2 giờ → `14 × 50.000 + 2 × 50.000 × 1,5 = 850.000 VNĐ`. Nếu một ngày có hai ca 6 giờ và 4 giờ, vẫn tính tổng ngày 10 giờ và OT 2 giờ. Không áp dụng ngưỡng 160 giờ/tháng.

- `calculateMonthlySalary(employeeId, month, year)` trả Salary đã lưu. Nếu bản ghi của tháng đã tồn tại, cập nhật đúng ID, không tạo trùng. Kết quả luôn có trạng thái PENDING theo yêu cầu, kể cả khi tính lại bản ghi PAID; giao diện yêu cầu xác nhận trước khi thực hiện.
- `calculateAllEmployees()` lặp qua toàn bộ nhân viên trong một transaction. Có lỗi thì rollback cả đợt tính. Không có ngày COMPLETED thì tạo bảng lương 0 giờ, 0 tiền.
- `markAsPaid()` chỉ đổi PENDING → PAID bằng điều kiện trong SQL. EmployeeId của màn hình nhân viên lấy từ session, không nhận ID tùy ý qua giao diện.
- Schema Salary giữ nguyên các trường ban đầu. Với bảng có giờ làm, mức lương đã áp dụng được suy ra từ `totalSalary / (totalHours + overtimeHours × 0,5)` để không hiển thị sai khi mức lương hiện tại của nhân viên thay đổi. Bảng 0 giờ hiển thị mức lương hiện tại và ghi nhãn rõ.
- Tổng lương thực nhận trong module là kết quả công thức đề bài; chưa có các khoản phụ cấp hoặc khấu trừ.
- WorkLog ban đầu rỗng. Muốn xem lương khác 0 cần có dữ liệu chấm công COMPLETED; module không tự thêm giờ làm mẫu.

Kiểm tra nhanh công thức trên JVM, không cần emulator:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline
```

`SalaryMathTest` kiểm tra OT theo ngày, nhiều ca cùng ngày, loại ca chưa COMPLETED, tháng rỗng, tổng tháng vượt 160 giờ nhưng không có OT ngày, và dữ liệu số không hợp lệ.

## Chấm công và lịch sử — phần Thành viên 3

Đăng nhập Employee → **Chấm công và lịch sử**. Card hôm nay hiển thị ngày, đồng hồ hiện tại, giờ vào/ra và tổng giờ đã chốt. Bên dưới có lịch sử tháng, nút chuyển tháng và tổng giờ lũy kế của các bản ghi COMPLETED.

Luồng cần nhớ: **AttendanceScreen → AttendanceViewModel → kiểm tra dữ liệu → Room transaction → WorkLog → Flow cập nhật màn hình**.

| File | Trách nhiệm |
| --- | --- |
| `domain/attendance/AttendanceTime.kt` | Định dạng ngày/giờ và tính số giờ làm |
| `domain/attendance/AttendanceValidation.kt` | Chặn check-in trùng, checkout trước check-in, checkout lặp hoặc sai ngày/trạng thái |
| `ui/attendance/AttendanceViewModel.kt` | Đọc phiên Employee, xử lý transaction, StateFlow trạng thái và lịch sử tháng |
| `ui/attendance/AttendanceScreen.kt` | Card hôm nay, nút Check In/Out, lịch sử và tổng giờ tháng |
| `data/local/dao/WorkLogDao.kt` | `getTodayWorkLog`, `getHistoryByEmployee`, `completeAttendance` |

**Check In:** trong một `database.withTransaction`, đọc bản ghi hôm nay theo employeeId từ session. Nếu đã có bất kỳ bản ghi nào thì báo lỗi. Nếu chưa có, insert trạng thái CHECKED_IN, checkOutTime = null, totalHours = 0. Kiểm tra và insert cùng transaction giúp hai yêu cầu đồng thời qua luồng chấm công không cùng tạo bản ghi. API insert tổng quát vẫn được giữ cho các phần khác; module này không thay đổi schema hoặc xóa dữ liệu cũ.

**Check Out:** trong transaction, đọc lại bản ghi hôm nay. Chặn nếu chưa check-in, đã checkout, sai ngày hoặc trạng thái khác CHECKED_IN. Sau khi tính giờ, câu UPDATE còn kiểm tra id, employeeId, workDate, status = CHECKED_IN và checkOutTime IS NULL; phải cập nhật đúng một dòng. Kết quả có status COMPLETED để module tính lương sử dụng.

Phần tính thời gian chi tiết:

```kotlin
// secondsOfDay kiểm tra đúng HH:mm:ss, giờ 00–23, phút/giây 00–59.
val elapsedSeconds = secondsOfDay(checkOutTime) - secondsOfDay(checkInTime)
require(elapsedSeconds >= 0)
val totalHours = BigDecimal.valueOf(elapsedSeconds.toLong())
    .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP)
    .toDouble()
```

Ví dụ `08:15:00 → 16:45:00` = 30.600 giây / 3.600 = **8,50 giờ**; `08:00:00 → 09:01:00` = **1,02 giờ**. Hai thao tác cùng giây cho 0,00 giờ. Không làm tròn phút trước khi tính giờ.

Ngày lưu là `yyyy-MM-dd` và giờ là `HH:mm:ss`, dùng Locale.US để đảm bảo chữ số định dạng ổn định. Trong Java phải dùng **yyyy**, không dùng YYYY vì YYYY là năm theo tuần và có thể sai ở cuối năm. Ngày và giờ được lấy từ cùng một thời điểm theo múi giờ thiết bị. Đồng hồ trong màn hình cập nhật mỗi giây; ngày và tháng hiện tại tự đổi khi qua nửa đêm.

| Trạng thái hôm nay | Check In | Check Out |
| --- | --- | --- |
| Đang tải/đang lưu | Khóa | Khóa |
| Chưa có bản ghi | Bật | Khóa |
| CHECKED_IN và chưa có giờ ra | Khóa | Bật |
| Đã COMPLETED/đã có giờ ra | Khóa | Khóa |

Validation vẫn chạy khi gọi trực tiếp ViewModel, không chỉ dựa vào việc khóa nút. EmployeeId được lấy từ session; kiểm tra hồ sơ Employee còn thuộc Account hiện tại trước khi ghi.

Theo phạm vi đề bài, chỉ checkout bản ghi **hôm nay**, không xử lý ca qua đêm và không tự cộng 24 giờ nếu giờ ra nhỏ hơn giờ vào. Bản ghi ngày cũ chưa checkout vẫn hiện trong lịch sử nhưng không được tính vào tổng giờ COMPLETED. Ngày mới được check-in mới theo điều kiện không có bản ghi của ngày đó.

`AttendanceValidationTest` kiểm tra tính giờ, làm tròn HALF_UP, định dạng giờ, năm cuối tháng 12, duplicate check-in, checkout trước check-in, checkout lặp, sai ngày và sai trạng thái. Chạy cùng lệnh build/unit test ở trên; không cần emulator.
