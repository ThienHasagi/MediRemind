# Đóng góp của các thành viên

Repo ghi nhận đóng góp thực tế qua commit và pull request. Danh sách thành viên trong README không tự làm các tài khoản xuất hiện ở mục Contributors của GitHub.

## Quy trình

1. Mỗi thành viên dùng tài khoản của mình clone repo hoặc fork nếu chưa có quyền push.
2. Tạo nhánh cho công việc thực tế: sửa lỗi đã tái hiện, bổ sung kiểm thử nghiệp vụ còn thiếu, rà soát giao diện trên thiết bị, hoặc viết kết quả phân tích/demo đã thực hiện.
3. Ghi rõ thay đổi, cách kiểm tra và kết quả trong commit/PR. Không tạo commit rỗng hay đổi tác giả chỉ để tăng số contributor.
4. Commit bằng tên và email thuộc tài khoản của người thực hiện. Có thể dùng email noreply hiển thị trong GitHub Settings → Emails; không đoán email của người khác.
5. Mở PR, để nhóm review và merge vào main. Với đóng góp cùng thực hiện, chỉ ghi co-author khi các tác giả thực sự tham gia và đồng ý attribution.

Ví dụ trên nhánh riêng:

```bash
git switch -c fix/mo-ta-loi-da-xac-minh
git config user.name "Ten cua ban"
git config user.email "EMAIL_DA_XAC_MINH_CUA_CHINH_BAN"
# Thực hiện thay đổi thật và kiểm tra trước khi commit.
git add duong-dan-file-da-sua
git commit -m "Mo ta thay doi va ly do"
git push -u origin fix/mo-ta-loi-da-xac-minh
```

Các trường tên/email ở trên phải được thay bằng thông tin của chính người thực hiện, không dùng nguyên văn ví dụ.

Tài liệu GitHub: [Troubleshooting missing contributions](https://docs.github.com/en/account-and-profile/how-tos/contribution-settings/troubleshooting-missing-contributions).
