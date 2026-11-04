package com.example.employeemanagement.domain.attendance

import com.example.employeemanagement.data.local.entity.WorkLog

object AttendanceValidation {
    fun validateCheckIn(today: WorkLog?) {
        require(today == null) { "Bạn đã Check In hôm nay. Mỗi ngày chỉ được Check In một lần." }
    }

    fun validateCheckOut(today: WorkLog?, workDate: String): WorkLog {
        requireNotNull(today) { "Bạn chưa Check In hôm nay, không thể Check Out." }
        require(today.workDate == workDate) { "Chỉ được Check Out bản ghi của hôm nay." }
        require(today.checkOutTime == null && today.status != "COMPLETED") { "Bạn đã Check Out hôm nay." }
        require(today.status == "CHECKED_IN") { "Trạng thái chấm công không hợp lệ. Vui lòng liên hệ Admin." }
        return today
    }
}
