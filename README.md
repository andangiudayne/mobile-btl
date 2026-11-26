# Challenge Alarm

Báo thức Android (Kotlin + Jetpack Compose + Room + CameraX + ML Kit).
Muốn tắt chuông phải hoàn thành một thử thách: giải toán, quét mã QR/mã vạch, hoặc chụp đúng loại vật đã đăng ký.

## Tính năng v6 (10/11): lịch nghỉ Việt Nam, widget, lối tắt
| Vấn đề người dùng gặp | Tính năng | Nằm ở |
|---|---|---|
| Nghỉ Tết, nghỉ lễ, nghỉ học dài ngày: phải tắt chuông lặp, hết kỳ nghỉ lại quên bật nên ngủ quên buổi đầu tiên đi học lại. "Bỏ qua lần reo tới" chỉ bỏ được 1 lần | **Không reo ngày nghỉ**: bật trong từng chuông lặp. Chuông tự bỏ qua ngày lễ Việt Nam theo Bộ luật Lao động 2019 (Tết Dương lịch, **Tết Nguyên đán 5 ngày tính theo âm lịch**, **Giỗ Tổ 10/3 âm lịch**, 30/4, 1/5, Quốc khánh 2 ngày, nghỉ bù khi lễ trùng cuối tuần) và **kỳ nghỉ của tôi** (chọn khoảng ngày trên lịch, đặt tên một chạm: Nghỉ học, Về quê...). Hết kỳ nghỉ chuông tự reo lại. Thẻ chuông hiện "Nghỉ Quốc khánh · reo lại T5 3/9"; tab Chuông có thẻ "Ngày nghỉ sắp tới" kèm ngày âm lịch hôm nay | `LunarCalendar.kt` (lịch âm theo thuật toán thiên văn Hồ Ngọc Đức), `Holidays.kt`, `nextTriggerAt` (Repeat.kt), `DaysOffStore.kt`, `DaysOffScreen.kt`, migration v6 |
| Ít khi mở app nên không biết chuông mai mấy giờ, đang bật hay tắt | **Widget màn hình chính** 4x2: chuông kế tiếp, ngày reo, chuỗi ngày dậy đúng giờ, ngày nghỉ sắp tới, nút **Chợp mắt** (tạo chuông 20 phút ngay, không cần mở app). Tự cập nhật khi chuông thay đổi, khi chuông reo xong, khi mở app và mỗi 30 phút | `NextAlarmWidget.kt`, `WidgetContent.kt` (có test), `res/layout/widget_next_alarm.xml` |
| Việc hay làm phải mở app, bấm nhiều bước | **Lối tắt** khi giữ biểu tượng app: Chợp mắt 20 phút, Thêm chuông, Chúc ngủ ngon, Lịch nghỉ | `res/xml/shortcuts.xml`, `AppShortcuts` (MainActivity.kt) |

Cách thử nhanh: Sửa chuông "Đi học" (đã bật sẵn "Không reo ngày nghỉ") > Lịch nghỉ > Thêm kỳ nghỉ > chọn từ ngày mai đến
cuối tuần > Thêm. Thẻ chuông đổi thành "Nghỉ học · reo lại ...". Widget: giữ màn hình chính > Tiện ích > Challenge Alarm.

## Đợt kiểm thử v6 (10/11)
- **Ô số điện thoại (tab Gia đình)**: ô nhập bị tạo lại sau mỗi phím gõ (vì lưu số ngay khi gõ), gõ nhanh bị mất số,
  con trỏ nhảy về cuối. Đã sửa: ô nhập chỉ đọc lại khi đổi người/tài khoản.
- **Màn hình chính khi mở bàn phím**: có một khoảng trống thừa (bằng chiều cao thanh tab và thanh điều hướng) giữa ô đang gõ
  và bàn phím. Đã sửa bằng `consumeWindowInsets`.
- **Thêm mã QR / vật để chụp**: ô "Tên thử thách" trong hộp thoại toàn màn hình chưa chừa chỗ cho bàn phím, bàn phím che nút
  "Lưu thử thách"; tên không giới hạn độ dài làm tràn thẻ. Đã sửa (co lại phía trên bàn phím, tối đa 40 ký tự).
- **Gõ lại câu**: ô nhiều dòng nên nhiều bàn phím (Gboard, bàn phím Oppo) hiện phím Enter thay vì "Xong", bấm Enter không cất
  bàn phím. Đã sửa: Enter cũng cất bàn phím.
- **Chuông không reo sau khi app bị "Buộc dừng"**: Android xóa mọi báo thức của app khi app bị buộc dừng (Oppo, Xiaomi tự làm khi
  dọn RAM; Android Studio làm khi bấm Run lại). Trước đây chỉ đặt lại khi khởi động lại máy. Đã sửa: mỗi lần mở app và mỗi lần
  cài bản mới (`MY_PACKAGE_REPLACED`) đều đặt lại lịch reo.

## Tính năng v5: mỗi tính năng giải quyết một vấn đề thật
| Vấn đề người dùng gặp | Tính năng | Nằm ở |
|---|---|---|
| Giải xong thử thách khi còn ngái ngủ rồi nằm xuống ngủ tiếp (lỗi số 1 của báo thức thử thách) | **Kiểm tra đã tỉnh**: tắt chuông xong 3/5/10 phút (chọn trong từng chuông, chuông mới mặc định 5), app hỏi "Bạn còn thức không?". Không bấm "Tôi đã dậy" trong 90 giây thì chuông reo lại 1 màn, và lần dậy đó bị đánh dấu "ngủ lại" (không tính đúng giờ, đứt chuỗi). Không trả lời trước được (chỉ khi app đã hỏi); tắt nguồn cũng không thoát (BootReceiver đặt lại). | `WakeCheckRules.kt`, `WakeCheck.kt`, `RingViewModel`, thẻ trên tab Chuông |
| Ngày lễ, được nghỉ học: tắt chuông lặp rồi quên bật lại, hôm sau ngủ quên | **Bỏ qua lần reo tới**: trong trình sửa chuông lặp, chỉ bỏ đúng một lần, sau đó tự reo lại. Thẻ chuông hiện "Bỏ qua T2 06:30". Lời nhắc đi ngủ và kế hoạch giấc ngủ tự theo lần reo thật. | `nextTriggerAt` (Repeat.kt), `AlarmRepository.toggleSkip` |
| Ngủ trưa không đặt chuông nên quá giấc, dậy uể oải, tối khó ngủ | **Chợp mắt** 10/20/30 phút (khuyên 20), cộng 5 phút để chìm vào giấc, nhắc nếu ngủ sau 15h. Reo xong tự xóa, không làm lệch thống kê dậy sáng và giấc ngủ đêm, có hỏi lại sau 3 phút. | `NapPlanner.kt`, `NapSheet.kt`, nút "Chợp mắt" ban ngày |
| Lo chuông không reo; máy hết pin qua đêm; Không làm phiền chặn cả báo thức | **Kiểm tra trước khi ngủ** khi bấm "Bắt đầu ngủ": pin (dưới 20% là lỗi, dưới 35% là cảnh báo, đang sạc là ổn), Không làm phiền có chặn báo thức không, đã có chuông sáng mai chưa, quyền báo thức chính xác, thông báo, đo giấc ngủ; mỗi mục có nút sửa. | `NightCheck.kt`, `NightViewModel.kt` (DeviceStatus) |
| Phòng ồn (ký túc xá, gần đường), khó vào giấc | **Âm thanh ru ngủ**: mưa (ồn nâu), quạt (ồn hồng), ồn trắng, sinh bằng code (không cần file nhạc), hẹn 15/30/60 phút, to dần khi bắt đầu và nhỏ dần trong phút cuối, tự dừng khi chuông reo. | `NoiseGenerator.kt`, `NoiseService.kt` |
| Có số liệu nhưng ít ai mở tab xem | **Bản tin buổi sáng** ngay sau khi tắt chuông: tỉnh sau bao lâu, chuỗi ngày, đêm qua ngủ bao lâu so với mục tiêu, nợ ngủ 7 ngày, giờ app sẽ hỏi lại, gợi ý ngắn. | `MorningReport.kt`, `MorningScreen.kt` |
| Không biết mình thiếu ngủ bao nhiêu | **Nợ ngủ 7 ngày** (cộng phần ngủ thiếu so với mục tiêu theo tuổi) ở tab Giấc ngủ, cảnh báo khi từ 2 tiếng trở lên. | `SleepAdvisor.sleepDebtMin` |
| Không thấy ngủ đủ có ích gì | **Ngủ đủ, bạn dậy nhanh hơn X%**: ghép nhật ký giấc ngủ với nhật ký thức dậy của chính người dùng (cần ít nhất 2 đêm mỗi nhóm). | `Insights.sleepVsWake`, tab Thống kê |

Cơ sở dữ liệu lên **v5** (có migration từ v4, không mất dữ liệu): thêm `skipUntil`, `wakeCheckMin`, `nap` cho chuông và `relapsed` cho nhật ký.
Chuông cũ không tự bật kiểm tra đã tỉnh; mở chuông ra chọn 3/5/10 phút.

## Đợt kiểm thử thử thách (v5.1)
- **Gõ lại câu**: bàn phím che nút "Đổi sang giải toán" và "Thoát khẩn cấp", nút Back lại bị khóa nên không cất được bàn phím.
  Đã sửa: màn hình reo chừa chỗ cho bàn phím (`imePadding` + `adjustResize`), thêm nút "Ẩn bàn phím", phím Xong cũng cất
  bàn phím, bấm "Đổi sang giải toán" hoặc gõ xong thì bàn phím tự cất. Các thử thách khác không dùng bàn phím hệ thống.
- **Vẽ lại hình**: có thể sinh hình phải vuốt xuyên qua một chấm chưa dùng (ví dụ 0 → 2 bỏ qua chấm 1), vuốt thẳng sẽ chạm
  nhầm chấm giữa nên không bao giờ đúng. Đã sửa: sinh hình theo quy tắc mở khóa của Android (`PatternRules`, có test).
- **Quét mã / Chụp vật**: máy không có camera sau hoặc camera đang bận làm sập app lúc chuông reo; đổi sang giải toán khi
  camera chưa kịp mở thì camera vẫn chạy ngầm. Đã sửa (bắt lỗi, không mở camera nếu màn đã đóng).
- **Android 14**: thêm mục thiết lập "Hiện toàn màn hình khi khóa máy" (quyền full-screen intent) để màn thử thách tự bật lên.
- Thêm 6 thử thách mới (bảng bên dưới), tổng 22 thử thách có sẵn. Logic nằm ở `BrainLogic.kt` (có test), giao diện ở
  `BrainChallenges.kt`. Không thử thách mới nào mở bàn phím hệ thống.

## Đợt kiểm thử bàn phím (v5.2, máy thử OPPO chạy Android 10)
Trên Android 10 trở xuống, `imePadding()` không chạy ở hai chỗ: màn hình reo (ẩn thanh trạng thái, nên `adjustResize` bị bỏ qua)
và các bảng trượt `ModalBottomSheet` (nằm trong cửa sổ riêng). Bàn phím vì vậy vẫn che ô đang gõ và các nút bên dưới.
- `Keyboard.kt`: `keyboardPadding()` dùng `WindowInsets.ime` trên Android 11 trở lên; Android 10 trở xuống thì đo thẳng phần
  cửa sổ bị bàn phím che (`getWindowVisibleDisplayFrame`). `ResizeWindowForKeyboard()` cho cửa sổ của bảng trượt co lại thay
  vì bị đẩy lệch lên. Dùng ở màn hình reo, màn hình chính, Sửa chuông (tên chuông), Hồ sơ, Thêm người thân, Nhập mã.
- `MainActivity` khai báo `adjustResize`.
- **Gõ lại câu**: gõ có dấu hay không dấu đều được (máy không có bàn phím tiếng Việt vẫn tắt được chuông); không bị báo sai
  khi bộ gõ Telex/VNI đang ghép dấu hoặc tự thêm dấu cách cuối câu; tắt tự sửa chữ; không sửa chữ người dùng đang gõ (trước
  đây chuẩn hóa NFC ngay trong ô nhập làm một số bộ gõ ghép dấu sai). Nút ẩn/hiện bàn phím nằm ngay trong ô nhập và theo
  đúng trạng thái bàn phím thật. Logic so khớp ở `TypingMatch` (`BrainLogic.kt`, có test).
- Mọi ô nhập có phím "Tiếp"/"Xong" trên bàn phím: Tên → Năm sinh → Xong cất bàn phím.
- Thêm người thân: 6 lựa chọn "Bố, Mẹ, Ông, Bà, Con, Em" tự xuống dòng (trước bị tràn trên máy màn hình 360dp).
- Số điện thoại nhận SMS được lưu ngay khi gõ (trước chỉ lưu khi đã bật công tắc, rời màn hình là mất).

## Gia đình: chăm giấc ngủ cho bố mẹ lớn tuổi và con nhỏ (tab thứ 5)
**Vấn đề:** con đi làm xa lo bố mẹ già ngủ ít, hay thức giấc, sáng không dậy (ở một mình, có chuyện không ai biết);
bố mẹ lo con thức khuya dùng điện thoại, ngủ không đủ 9 đến 11 tiếng, sáng không dậy nổi đi học.

**Hai vai trò** (chọn ở tab Gia đình, đổi được):
- *Người quản lý*: thêm người thân (Bố, Mẹ, Ông, Bà, Con...), đặt **kế hoạch ngủ** cho từng người: giờ ngủ, giờ dậy, ngày lặp,
  bộ thử thách theo lứa tuổi (Nhẹ nhàng cho người lớn tuổi: đọc đồng hồ, toán dễ, bật đèn; Vui cho trẻ em: lật cặp, bắt nắng,
  tìm ô khác màu), và "báo khi chưa tắt chuông sau 15/20/30 phút". Xem thẻ từng người: đêm qua ngủ bao lâu, từ mấy giờ,
  sáng nay tắt chuông sau bao lâu, 7 cột ngủ so với mức nên ngủ theo tuổi.
- *Người được quản lý*: nhập mã mời, **đồng ý chia sẻ** (thấy rõ chia sẻ gì: giờ ngủ, thời lượng, số lần thức, giờ tắt chuông;
  không gì khác), app tự tạo chuông "Gia đình đặt" theo kế hoạch. Mỗi sáng bấm "Gửi báo cáo". Ngừng chia sẻ được bất cứ lúc nào.

**Cảnh báo theo độ tuổi** (`FamilyAdvisor`, cùng khuyến nghị NSF/AASM với phần Giấc ngủ):
| Ai | Cảnh báo |
|---|---|
| Người lớn tuổi | KHẨN: chuông reo quá N phút chưa tắt (gọi hỏi thăm ngay); ngủ quá ít; ngủ nhiều bất thường; thức giấc từ 4 lần/đêm (nên hỏi bác sĩ) |
| Trẻ em | Ngủ muộn hơn giờ đặt từ 30 phút (màn hình còn sáng = còn dùng điện thoại); ngủ ít hơn 9 tiếng (trẻ 6 đến 13 tuổi) |
| Mọi người | Thiếu ngủ cả tuần; tắt chuông xong ngủ lại; lâu không có báo cáo mới (36 giờ) |

**Trao đổi giữa hai điện thoại, không cần máy chủ** (`FamilyCodec`): mã chữ `CA1:I:...` (mời), `CA1:P:...` (kế hoạch),
`CA1:R:...` (báo cáo 7 đêm). Ở gần thì quét mã QR (tạo bằng ZXing, quét bằng ML Kit); ở xa thì bấm "Gửi qua Zalo..." rồi
người kia "Dán" cả tin nhắn, app tự tìm mã bên trong.

**Báo SMS cho người thân** (máy người lớn tuổi, tự bật): chuông reo quá 15/20/30 phút mà chưa tắt thì máy tự nhắn tin
"[Challenge Alarm] Bố chưa tắt chuông báo thức lúc 05:30 (đã 20 phút). Hãy gọi điện hỏi thăm." Không cần mạng.
Cần quyền gửi SMS (chỉ xin khi bật), tốn phí tin nhắn thường.

**Bản thương mại** sẽ thay mã bằng máy chủ (ví dụ Firebase Auth + Firestore + thông báo đẩy) để báo cáo và cảnh báo tự động;
phần logic (`Family.kt`, có test) giữ nguyên.

### Kịch bản trình bày (1 điện thoại, khoảng 3 phút)
1. Tab **Gia đình**: đã có Ông nội 74 tuổi (ổn), Bà nội 70 tuổi (ngủ ít, thức giấc nhiều), Bé Na 9 tuổi, em gái
   (ngủ muộn, thiếu ngủ), mỗi người 7 đêm gần nhất.
2. Chạm vào **Bà nội**: xem 7 cột ngủ so với mức 7 đến 8 tiếng, lời khuyên, sửa kế hoạch, "Lưu và gửi kế hoạch" để hiện mã QR.
3. Trong "Xem trước cảnh báo" bấm **"Ông nội không tắt chuông (khẩn)"**: thẻ KHẨN màu đỏ hiện trên cùng, thanh thông báo
   hiện "Ông nội chưa tắt chuông sau 25 phút". Bấm "Ông nội dậy bình thường" để thấy cảnh báo biến mất.
4. Bấm **"Bé Na thức khuya dùng điện thoại"**: cảnh báo "Bé Na ngủ muộn 95 phút".

### Kịch bản demo (1 điện thoại, 2 tài khoản Con và Bố)
Không có máy thứ hai thì dùng 2 tài khoản trên cùng một máy (mỗi tài khoản có vai trò và dữ liệu Gia đình riêng):
1. Máy đã có sẵn 30 ngày giấc ngủ, nên tài khoản Bố có số liệu để gửi đi.
2. Tab Gia đình > dòng "Không có máy thứ hai? Tạo 2 tài khoản thử (Con và Bố)" ở cuối trang: có thanh chọn tài khoản
   **Con** (đang dùng, chăm sóc, giữ nguyên Ông nội, Bà nội, Bé Na) và **Bố** (được chăm sóc).
3. Ở tài khoản Con: "Thêm người thân" > Bố > nhập năm sinh > hiện mã mời > bấm **"Mở tài khoản Bố và nhập mã này"**.
4. App chuyển sang tài khoản Bố và hỏi "Chia sẻ giấc ngủ với Con?" > "Đồng ý chia sẻ": tab Chuông có chuông "Gia đình đặt".
5. Ở tài khoản Bố: "Gửi báo cáo" > **"Mở tài khoản Con và nhập mã này"**: về tài khoản Con, thẻ Bố có số liệu 7 đêm.
6. Ở tài khoản Con: chạm Bố > đổi giờ dậy > "Lưu và gửi kế hoạch" > **"Mở tài khoản Bố và nhập mã này"**: chuông của Bố tự đổi giờ.
7. Xong thì "Bỏ tài khoản thử" và tắt chuông "Gia đình đặt" ở tab Chuông (chuông này reo theo kế hoạch của Bố).

### Kịch bản demo (2 điện thoại)
1. Máy A (con): Gia đình > "Tôi chăm sóc gia đình" > "Thêm người thân" > chọn Bố, năm sinh > hiện mã QR mời.
2. Máy B (bố): Gia đình > "Tôi được gia đình chăm sóc" > "Quét mã QR" > "Đồng ý chia sẻ": máy B có chuông "Gia đình đặt".
3. Máy B: "Gửi báo cáo" hiện mã QR; máy A: "Nhập mã báo cáo" > quét: thẻ Bố có số liệu. (Ở xa thì gửi mã qua Zalo rồi Dán.)
4. Máy B có SIM: bật "Nhắn tin khi chưa tắt chuông", nhập số máy A, đặt chuông 1 phút nữa và không tắt: sau 15 phút
   máy A nhận SMS.

## Dữ liệu có sẵn khi cài app
Mở app lần đầu là thấy một app đã dùng được một tháng, không cần bấm gì, không có nhãn "mẫu" (`InitialData.kt`, gọi trong
`MainActivity.onCreate`, chỉ chạy một lần cho mỗi lần cài, không đè dữ liệu đã có):
- **Hồ sơ**: sinh viên 21 tuổi (sửa trong Hồ sơ). Vì đã có hồ sơ nên lần đầu mở app đi thẳng tới bảng cấp quyền.
- **Chuông** "Đi học" 6:30 thứ Hai đến thứ Sáu (3 màn: giải toán, gõ câu, nhớ màu, màu chữ, ô khác màu) và "Cuối tuần"
  7:30 thứ Bảy (xếp hình trượt, đọc đồng hồ). Hai chuông **đang bật và sẽ reo thật**; không muốn thì gạt công tắc tắt.
- **30 ngày nhật ký** thức dậy và giấc ngủ gắn với hai chuông đó (`DemoData.kt`): Chủ nhật không đặt chuông; đầu tháng ngủ muộn,
  dậy chậm, có 2 lần ngủ lại và 1 lần thoát khẩn cấp; cuối tháng ngủ sớm hơn, dậy nhanh hơn. Nhờ vậy chuỗi ngày, tỉ lệ đúng giờ,
  biểu đồ, nợ ngủ, "ngủ đủ thì dậy nhanh hơn" đều có số liệu. Những sáng sau đó chuông reo thật sẽ ghi tiếp như bình thường.
- **Gia đình**: Ông nội, Bà nội, Bé Na (`FamilyDemo` trong `Family.kt`). Mỗi sáng mở app, mỗi người có thêm một đêm mới theo
  thói quen ngủ của họ (như máy người thân gửi báo cáo về), nên thẻ gia đình không bao giờ "lâu không có báo cáo".
  Người nào đã gửi báo cáo thật qua mã thì thôi tự thêm đêm.

Muốn nạp lại từ đầu: gỡ app rồi cài lại (hoặc Cài đặt > Ứng dụng > Challenge Alarm > Bộ nhớ > Xóa dữ liệu).

## Kiến trúc MVVM
```
View (Compose)            ViewModel (StateFlow)        Model
MainActivity (khung,      AlarmViewModel        ->     Repository.kt
  thanh điều hướng)       StatsViewModel                 AlarmRepository -> Room (Data.kt), AlarmScheduler,
AlarmsScreen              SleepViewModel                                    RingSession, BedtimeReminder
ChallengesScreen          SetupViewModel                 SleepRepository -> ProfileStore, Room, SleepTracker
SleepScreen (SleepUi.kt)  RingViewModel                Logic thuần (có unit test): Stats, SleepAnalyzer,
StatsScreen, AlarmEditor,                                SleepAdvisor, RoundPlanner, MathChallenge
ProfileSheet, SetupSheet,                            Hệ thống chạy nền: AlarmService, Receivers,
RingActivity + thử thách                               BedtimeReceiver, SleepTracker
```
- View chỉ đọc state (`collectAsStateWithLifecycle`) và gọi hàm của ViewModel; không gọi DAO hay AlarmManager.
- ViewModel dùng `AndroidViewModel` + `viewModelScope`, giữ state bằng `StateFlow`, thông báo ngắn bằng `SharedFlow`
  (hiện thành Snackbar). ViewModel sống qua xoay màn hình nên không mất tiến độ.
- Repository là cửa duy nhất tới dữ liệu; `AppGraph` giữ một bản duy nhất của mỗi Repository (thay cho Hilt cho gọn).
- Màn hình chuông reo: `RingViewModel` giữ số màn, giờ nghỉ, thử thách hiện tại, hoãn, "Bạn đã tỉnh", thoát khẩn cấp;
  `RingActivity` chỉ lo ghim màn hình, giảm độ sáng lúc nghỉ và đóng màn hình khi ViewModel báo xong.
- Vẫn một package `com.nhom.challengealarm` như cũ; file mới: `Repository.kt`, `*ViewModel.kt`, `*Screen.kt`,
  `Components.kt`, `ExtraChallenges.kt`.

## Giao diện: hai chế độ Ngày / Đêm
- Tự đổi theo giờ: **6:00 đến 18:00 là chế độ Ngày**, **18:00 đến 6:00 là chế độ Đêm** (`AppMode.byClock`, `ThemeViewModel`
  kiểm tra mỗi 30 giây nên đổi đúng giờ khi app đang mở).
- Nút tròn ở góc trên (biểu tượng chữ A, mặt trời hoặc mặt trăng) đổi lần lượt: Tự động → Luôn sáng → Luôn đêm,
  để thử cả hai giao diện bất kỳ lúc nào. Lựa chọn được lưu lại.
- **Ngày (neumorphism, theo ảnh mẫu 1):** nền xám xanh nhạt, mọi khối cùng màu nền và nổi lên nhờ bóng sáng trên trái,
  bóng tối dưới phải (`Modifier.softShadow`); thanh tab chữ ở trên, tab đang chọn nổi lên; đồng hồ kim lớn chạy theo giờ thật
  (vạch phút, số 12/3/6/9 kiểu điện tử, kim phút hồng, tâm tím); thẻ chuông "CHUÔNG 1 / 06:30 / Hằng ngày" với công tắc nhỏ;
  nút "THÊM" bo tròn.
- **Đêm (theo ảnh mẫu 2):** nền tím đậm có sao và mảng tròn mờ; tiêu đề ngày tháng; thẻ "Tối nay" tím chuyển màu có nét lá
  trang trí, "Giờ ngủ: 22:15 - 06:30" và nút trắng "Bắt đầu ngủ"; hai thẻ "Chất lượng" (tím hồng) và "Thời lượng" (tím)
  có thanh tiến độ; biểu đồ sóng "Ngủ trong tuần" có vạch 6h/7h/8h và ngôi sao; thanh điều hướng dưới chỉ có biểu tượng.
- **Màn "Chúc ngủ ngon"** (bấm "Bắt đầu ngủ"): trăng lưỡi liềm, vòng mảnh và mây vẽ bằng Canvas, giờ hiện tại ở giữa,
  giờ chuông, thời gian đã nằm và nút "Dừng".
- Chất lượng giấc ngủ (`SleepAdvisor.nightQuality`): ngủ đủ mục tiêu theo tuổi được tối đa 100, trừ 6 điểm mỗi lần
  thức dậy dùng điện thoại.
- Các thành phần nằm ở `Components.kt` (thẻ, nút, công tắc, thẻ thống kê) và `Decor.kt` (đồng hồ, biểu đồ sóng, trăng mây,
  thanh tab, thanh điều hướng); bảng màu ở `Theme.kt` (`DayPalette`, `NightPalette`). Tất cả vẽ bằng code, không dùng ảnh.
- Màn hình chuông reo giữ giao diện bầu trời bình minh riêng.

## Cách chạy
1. Android Studio (Koala trở lên) -> File > Open -> chọn thư mục `ChallengeAlarm`.
2. Đợi Gradle sync (Android Studio tự tạo Gradle wrapper và tải thư viện).
3. Chạy trên máy thật (khuyến nghị) hoặc emulator API 26+.
4. Lần đầu: cấp quyền thông báo, quyền "Báo thức & lời nhắc" (Android 12+) và quyền camera khi thêm thử thách.

## Lỗi `IllegalArgumentException: 25.0.3` khi build
Gradle đang chạy bằng JDK 25, quá mới so với Kotlin 1.9 và AGP 8.5. Đổi sang JDK 17 hoặc 21:
1. Android Studio > File > Settings > Build, Execution, Deployment > Build Tools > Gradle.
2. Ở "Gradle JDK" chọn "jbr-17" (Embedded JDK) hoặc bất kỳ JDK 17 đến 21.
3. File > Invalidate Caches > Invalidate and Restart, rồi Sync lại và Build.
Nếu build bằng dòng lệnh thì đặt `JAVA_HOME` trỏ tới JDK 17 trước khi chạy `./gradlew`.

## Thiết lập lần đầu
Lần đầu mở app sẽ hiện bảng "Thiết lập để chuông chạy chắc": thông báo, báo thức chính xác, hiển thị trên ứng dụng khác,
không tối ưu pin, tự khởi động, ghim màn hình, khóa app trong đa nhiệm. Mục nào xong sẽ có dấu tích vàng (tự cập nhật khi quay lại app).
Mục hệ điều hành không cho app tự kiểm tra (tự khởi động, ghim màn hình, khóa đa nhiệm) thì bấm "Đã xong" sau khi bật.
Nút "Thử chuông ngay" reo một chuông thử để xem cả luồng; chuông thử không ghi vào thống kê.
Bảng này mở lại được bằng dải "Hoàn tất thiết lập" ở đầu tab Chuông khi còn mục chưa xong.

## Nhiều màn, nghỉ giữa màn và nút "Bạn đã tỉnh"
- Mỗi chuông có "Số màn" (1 đến 10, chuông mới mặc định 3). Mỗi màn random 1 thử thách trong danh sách đã chọn,
  không lặp lại thử thách của màn ngay trước. Quét mã/chụp vật mà buộc phải lặp lại thì màn đó đổi sang giải toán.
- "Nghỉ sau màn trí não" (không, 1 hoặc 2 phút): qua một màn trí não (toán, gõ chữ, nhớ màu, chạm số, bắt nhịp, vẽ hình,
  xếp chữ, bắt nắng) thì chuông im, màn hình tối lại và đếm ngược; hết giờ chuông reo lại cho màn tiếp.
  Màn vận động (lắc, đi bộ, quét mã, chụp vật) không nghỉ để người dùng khỏi nằm xuống lại.
- Từ màn 2 có nút "Bạn đã tỉnh": giữ 3 giây để tắt chuông luôn, bỏ qua các màn còn lại.
- Tiến độ (đang ở màn mấy, đang nghỉ đến lúc nào) được lưu lại, nên app bị vuốt khỏi đa nhiệm thì chuông dự phòng
  dựng lại đúng màn đang dở, không bắt làm lại từ đầu.
- Thống kê "dậy đúng giờ" tính theo lúc qua màn đầu tiên, thời gian nghỉ giữa màn không bị tính.

## Giấc ngủ và hồ sơ theo độ tuổi
- Hồ sơ (bấm vòng tròn ở góc trên màn hình chính, lần đầu mở app tự hiện): tên, năm sinh, công việc, mức vận động,
  kiểu người (dậy sớm, bình thường, cú đêm), có uống cà phê/trà sau 14h không, giờ bắt đầu tính giấc ngủ, có nhắc đi ngủ không.
  Hồ sơ chỉ lưu trên máy (`ProfileStore`), không có máy chủ.
- Khuyến nghị theo nhóm tuổi (National Sleep Foundation 2015, AASM):
  | Nhóm | Nên ngủ | Mục tiêu mặc định | Bắt đầu tính từ |
  |---|---|---|---|
  | 6–13 tuổi | 9–11 tiếng | 10 tiếng | 19:30 |
  | 14–17 tuổi | 8–10 tiếng | 9 tiếng | 21:00 |
  | 18–25 tuổi | 7–9 tiếng | 8 tiếng | 21:30 |
  | 26–64 tuổi | 7–9 tiếng | 7,5 tiếng | 21:00 |
  | 65 tuổi trở lên | 7–8 tiếng | 7,5 tiếng | 20:00 |
  Vận động nhiều cộng thêm 30 phút (không vượt mức trên).
- Đo giấc ngủ (`SleepTracker`, `SleepAnalyzer`): cần Android 9+ và quyền "Truy cập dữ liệu sử dụng" (có trong bảng thiết lập).
  Lúc tắt chuông buổi sáng, app đọc lịch sử bật/tắt màn hình đêm qua: từ giờ bắt đầu tính, màn hình tắt liền 15 phút trở lên
  thì là bắt đầu ngủ, đến lúc chuông reo. Lấy quãng tắt dài nhất làm giấc chính, nên lúc đặt máy xuống rồi lại cầm lên dùng
  cả tiếng không bị tính là ngủ. Các lần bật máy giữa đêm bị trừ ra; lần nào từ 1 phút trở lên tính là một lần thức giấc.
  Không cần chạy nền cả đêm, không tốn pin.
- Tab Thống kê > Giấc ngủ: thời gian ngủ trung bình, thẻ "Tối nay" (giờ lên giường đề xuất = giờ chuông - mục tiêu - 15 phút
  để chìm vào giấc ngủ, giờ muộn nhất, giờ tắt màn hình, giờ ngừng cà phê), biểu đồ 7 đêm với dải khuyến nghị,
  giờ ngủ trung bình, độ lệch giờ ngủ, số lần thức mỗi đêm và lời khuyên (thiếu ngủ, ngủ muộn, giờ ngủ thất thường,
  hay mở máy giữa đêm, cà phê, cú đêm, làm ca, cùng lời khuyên riêng cho từng nhóm tuổi; người 65+ hay tỉnh giấc
  hơn là bình thường nên ngưỡng cảnh báo cao hơn).
- Nhắc đi ngủ (`BedtimeReminder`): thông báo trước giờ lên giường đề xuất 30 phút, tính theo chuông kế tiếp.

## Cách dùng thử thách
1. Tab "Thử thách": thêm "+ Mã QR/vạch" (quét một mã dán ở chỗ cố định, ví dụ bồn rửa mặt)
   hoặc "+ Chụp vật" (hướng camera vào vật, chọn nhãn nhận diện được, ví dụ Sink, Bottle).
2. Tab "Chuông": tạo chuông, tick tối đa 5 thử thách (gồm cả "Giải toán").
3. Khi chuông reo, app chọn ngẫu nhiên 1 thử thách trong danh sách. Không chọn gì = giải toán.

## Cấu trúc
- `Data.kt`: Room (AlarmEntity, ChallengeItem, DAO, AppDatabase, migration 1->2)
- `AlarmScheduler.kt`: tính giờ kế tiếp, đặt/hủy báo thức bằng AlarmManager.setAlarmClock
- `Receivers.kt`: AlarmReceiver (đến giờ) và BootReceiver (reboot/đổi giờ -> đặt lại)
- `AlarmService.kt`: foreground service phát chuông, tăng âm lượng dần, rung, wake lock
- `RingActivity.kt`: màn hình thử thách (chặn Back), nhiều màn, thoát khẩn cấp (mở sau 3 phút, giữ 15 giây)
- `Challenges.kt`: giao diện thử thách QR/chụp vật và hộp thoại đăng ký thử thách
- `CameraScan.kt`: CameraX + ML Kit (quét mã, nhận diện nhãn vật, chạy offline)
- `MathChallenge.kt`: sinh đề toán ngẫu nhiên 3 mức khó
- `MainActivity.kt`: tab Chuông / Thử thách / Thống kê, trình sửa chuông
- `RoundPlanner.kt`, `Rounds.kt`: chuông nhiều màn, màn nghỉ, nút "Bạn đã tỉnh", lưu tiến độ lần reo
- `SleepAnalyzer.kt`, `SleepTracker.kt`: đoán giờ ngủ từ lịch sử bật/tắt màn hình
- `SleepAdvisor.kt`, `Profile.kt`, `SleepUi.kt`, `Bedtime.kt`: hồ sơ, khuyến nghị theo tuổi, trang Giấc ngủ, nhắc đi ngủ
- `Family.kt`, `FamilyRepository.kt`, `FamilyViewModel.kt`, `FamilyScreen.kt`: chế độ Gia đình
- `InitialData.kt`, `DemoData.kt`: dữ liệu có sẵn khi cài app (hồ sơ, chuông, lịch sử 30 ngày, gia đình)
- `Keyboard.kt`: chừa chỗ cho bàn phím ảo, chạy đúng cả trên Android 10 trở xuống
- `LunarCalendar.kt`, `Holidays.kt`: lịch âm, ngày lễ Việt Nam, kỳ nghỉ (thuần Kotlin, có test)
- `DaysOffStore.kt`, `DaysOffScreen.kt`: lưu kỳ nghỉ, thẻ "Ngày nghỉ sắp tới" và bảng Lịch nghỉ
- `NextAlarmWidget.kt`, `WidgetContent.kt`: widget màn hình chính và nút Chợp mắt trên widget

## Các thử thách
| Thử thách | Cách làm | Cần gì |
|---|---|---|
| Giải toán | Giải N bài toán ngẫu nhiên | Không |
| Lắc điện thoại | Lắc mạnh 15/25/40 lần (theo độ khó) | Gia tốc kế |
| Đi bộ | Đi 15/30/50 bước | Cảm biến đếm bước, quyền nhận dạng hoạt động (Android 10+) |
| Gõ lại câu | Gõ đúng nguyên câu, ký tự đúng sáng lên, sai đỏ | Không |
| Nhớ dãy màu | Nhìn dãy 4/6/8 ô sáng rồi chạm lại đúng thứ tự | Không |
| Chạm số theo thứ tự | Chạm 1..9, 1..16 hoặc 1..25 trong lưới xáo trộn | Không |
| Bắt nhịp | Chạm đúng lúc chấm sáng chạy qua vùng vàng (3/5/7 lần, vùng hẹp và nhanh dần) | Không |
| Vẽ lại hình | Xem đường đi qua lưới 3x3 rồi vuốt lại đúng thứ tự (4/5/7 chấm) | Không |
| Xếp chữ | Ghép chữ cái xáo trộn thành từ tiếng Việt (1/2/3 từ) | Không |
| Bắt nắng | Chạm mặt trời nhỏ hiện ngẫu nhiên, chạm nhầm bị trừ điểm (8/12/16 lần) | Không |
| Quét mã QR/mã vạch | Quét đúng mã đã đăng ký (người dùng tự thêm) | Camera |
| Chụp vật | Hướng camera vào đúng loại vật đã đăng ký (người dùng tự thêm) | Camera |
| Màu chữ (Stroop) | Chữ "ĐỎ" tô màu xanh: chọn màu tô chứ không đọc chữ; mức khó xen kẽ câu hỏi màu tô / nghĩa chữ (5/8/12 câu, sai trừ điểm) | Không |
| Lật cặp | Lật hai thẻ một lượt, tìm đủ 4/6/8 cặp hình giống nhau | Không |
| Bật đèn | Bật đèn hoặc mở rèm, giữ máy ở chỗ sáng từ 30/60/120 lux liền 3/5/8 giây (thử thách vận động) | Cảm biến ánh sáng |
| Vuốt mũi tên | Vuốt theo mũi tên vàng, ngược hướng mũi tên đỏ (8/12/16 lần; mức dễ không có mũi tên đỏ) | Không |
| Tìm ô khác màu | Lưới 3x3/4x4/5x5 cùng màu, chạm ô hơi sáng hoặc tối hơn (5/7/9 lần, độ chênh nhỏ dần, sai trừ điểm) | Không |
| Nhớ dãy số | Nhìn dãy 4/5/7 số vài giây rồi bấm lại bằng bàn phím số trên màn hình (2/3/3 dãy) | Không |
| Xếp hình trượt | Trượt các ô 1 đến 8 về đúng thứ tự trong lưới 3x3 (xáo 12/24/40 nước, luôn giải được) | Không |
| Giữ thăng bằng | Nghiêng máy đưa viên bi vào vòng vàng rồi giữ yên 2 giây (2/3/4 vòng, vòng nhỏ dần) | Gia tốc kế |
| So sánh nhanh | Chọn phép tính có kết quả lớn hơn, hai kết quả luôn sát nhau (6/8/10 lần; mức khó có phép nhân) | Không |
| Đọc đồng hồ | Nhìn đồng hồ kim, chọn đúng giờ trong 4 đáp án (bước 15/5/1 phút; đáp án nhiễu gồm cả nhầm kim giờ với kim phút) | Không |

Nội dung cũng đa dạng hơn: toán có 4 dạng mỗi mức (thêm tìm số còn thiếu "? + 27 = 64", chia hết, có ngoặc,
phương trình hai bước), 28 câu để gõ lại, 42 từ để xếp chữ.

- Mặt trời trên màn hình reo mọc dần theo tiến độ của mọi thử thách.
- Thiếu quyền hoặc máy không có cảm biến thì app tự dùng giải toán; luôn có nút "Đổi sang giải toán".
- Thêm thử thách mới: thêm một giá trị vào `ChallengeType`, một dòng vào `BuiltIn.all` (id âm kế tiếp),
  một composable trong `MoreChallenges.kt` và một nhánh trong `RingScreen`.

## Thống kê, hoãn chuông, nhạc chuông
- Tab "Thống kê": chuỗi ngày dậy đúng giờ, tỉ lệ đúng giờ, thời gian tắt chuông trung bình, biểu đồ 7 ngày và các lần dậy gần đây.
  Dậy đúng giờ = tắt chuông trong 5 phút và không dùng thoát khẩn cấp (`ON_TIME_LIMIT_MS` trong `Stats.kt`).
- Hoãn chuông: mỗi chuông đặt số lần hoãn tối đa (0 đến 3, mặc định 0 = không cho hoãn). Mỗi lần hoãn reo lại sau 5 phút.
  Trong lúc hoãn, chuông đó bị khóa ở màn hình chính (không tắt, sửa hay xóa được) và tiến độ các màn được giữ lại.
- Nhạc chuông: chọn riêng cho từng chuông; nhạc không đọc được thì tự dùng nhạc báo thức mặc định.
- Khóa màn hình khi reo: ghim màn hình (`startLockTask`) chặn Home, Recents và thanh thông báo; ẩn thanh hệ thống;
  nuốt nút âm lượng và Back; giữ âm lượng báo thức từ 80% trở lên. Lần đầu mỗi chuông, Android hỏi "Ghim ứng dụng", bấm Bắt đầu.
  Cấp quyền "Hiển thị trên ứng dụng khác" (banner ở màn hình chính) để service mở lại màn hình thử thách mỗi giây nếu bị thoát ra.
- Chống lách: tiếng chuông không dừng cho đến khi xong thử thách. Nếu người dùng thoát ra màn hình chính,
  `AlarmService` cứ 5 giây thử mở lại màn hình thử thách và đăng lại thông báo toàn màn hình
  (Android 10+ có thể chặn việc mở Activity từ nền, nên đây là nỗ lực tốt nhất).
- Cơ sở dữ liệu lên v4 (có migration từ v1, v2, v3, không mất chuông cũ). v4 thêm số màn, thời gian nghỉ, bảng `sleep_logs`.

## Các lớp chống lách (sau đợt kiểm thử)
- Chuông đang reo hoặc đang hoãn thì không tắt, sửa, xóa được ở màn hình chính; tắt chuông ở màn hình chính
  cũng không hủy chuông hoãn đang chờ.
- Tắt nguồn, khởi động lại hoặc chỉnh giờ/múi giờ khi chuông chưa tắt hẳn: chuông reo lại ngay (sau khi mở khóa máy),
  làm tiếp từ màn đang dở.
- Màn thử thách khóa dọc và không cho chia đôi màn hình; xoay máy không đổi được thử thách khó.
- Nút "Đổi sang giải toán" chỉ hiện sau 90 giây ở một màn, toán đổi sang khó hơn một bậc và không được hưởng giờ nghỉ
  nếu thử thách gốc là vận động. Thu hồi quyền camera trước khi ngủ thì cũng chỉ được toán khó hơn.
- Thoát khẩn cấp chỉ mở sau 3 phút chuông reo và phải giữ liền 15 giây; vẫn ghi là "thoát khẩn cấp" trong thống kê.
- Hai chuông trùng giờ: chuông sau chờ, 1 phút thử lại một lần, không ghi đè chuông đang reo.
- Nhạc chuông hỏng: thử lần lượt nhạc báo thức, nhạc chuông, âm thông báo; không phát được gì thì vẫn rung.
- Giới hạn của Android (app thường không chặn được): Buộc dừng (Force stop) hoặc xóa dữ liệu app trong Cài đặt, gỡ app,
  từ chối hộp thoại ghim màn hình rồi dùng cử chỉ thoát, Không làm phiền chặn cả báo thức, chụp lại mã QR bằng máy khác,
  vung tay để đếm bước.

## Chạy test
`./gradlew testDebugUnitTest` (hoặc chuột phải thư mục `app/src/test` trong Android Studio > Run Tests).
Có 159 test: sinh đề toán, tính giờ báo kế tiếp, thống kê, thử thách, tóm tắt ngày lặp, chọn màn và giờ nghỉ (`RoundPlannerTest`),
đoán giờ ngủ từ lịch sử màn hình (`SleepAnalyzerTest`), gợi ý theo tuổi và nợ ngủ (`SleepAdvisorTest`), bỏ qua lần reo tới
(`SkipNextTest`), chợp mắt (`NapPlannerTest`), kiểm tra đã tỉnh (`WakeCheckRulesTest`), kiểm tra trước khi ngủ (`NightCheckTest`),
âm thanh ru ngủ (`NoiseTest`), bản tin buổi sáng và so sánh giấc ngủ với giờ dậy (`MorningReportTest`),
6 thử thách mới, quy tắc vẽ hình và so khớp câu gõ lại (`BrainLogicTest`), lịch sử 30 ngày có sẵn khi cài app (`DemoDataTest`),
chế độ Gia đình: mã trao đổi, cảnh báo theo tuổi, gia đình có sẵn luôn có báo cáo mới mỗi ngày (`FamilyTest`),
lịch âm: ngày Tết 2020 đến 2028, Giỗ Tổ, Trung thu, tháng nhuận, đổi qua lại dương/âm đúng cho mọi ngày từ 2000 đến 2035
(`LunarCalendarTest`), ngày lễ và kỳ nghỉ, chuông tự bỏ qua ngày nghỉ rồi reo lại (`HolidaysTest`), nội dung widget
(`WidgetContentTest`).

## Trình chỉnh chuông kiểu iOS
- `Pickers.kt`: bánh xe giờ/phút cuộn vô hạn (tự căn vào giữa, mục xa giữa mờ và nghiêng, rung nhẹ mỗi số), danh sách cài đặt nhóm bo góc.
- `Repeat.kt`: dòng "Lặp lại" hiện tóm tắt (Hằng ngày, Ngày thường, Cuối tuần, T2, T4...); chạm vào để mở danh sách ngày có dấu tích.
- `AlarmEditor.kt`: thanh Hủy / Lưu cố định ở trên, rồi bánh xe giờ, nhóm cài đặt (Lặp lại, Tên chuông, Nhạc chuông, Hoãn chuông), thử thách và độ khó.

## Giao diện "bình minh"
- `Theme.kt`: bảng màu (xanh đêm, tím hoàng hôn, vàng nắng), hỗ trợ giao diện sáng và tối theo hệ thống.
- `Sky.kt`: bầu trời đổi màu và mặt trời mọc (dùng ở cả màn hình chính lẫn màn hình reo).
- Màn hình chính ("đường chân trời"): nửa trên là bầu trời với giờ báo kế tiếp thật lớn; khi còn dưới 8 tiếng trời sáng dần,
  sao mờ đi và mặt trời nhô lên khỏi mặt đất. Nửa dưới là danh sách phẳng (không chia thẻ): giờ nhẹ và lớn, ngày lặp là chữ,
  chuông tắt thì mờ. Tab chữ có gạch vàng; thống kê là trang đọc với con số chuỗi ngày thật lớn. Chạm chuông để sửa trong bottom sheet.
- Màn hình reo: sao mờ dần, thử thách nằm trong khung kính mờ; giải xong mỗi bài toán thì mặt trời mọc cao hơn; bàn phím số riêng; sai thì khung đáp án rung.
- Font dùng font hệ thống. Muốn dùng font riêng (ví dụ Be Vietnam Pro): tải file .ttf bỏ vào `res/font`
  rồi khai báo FontFamily trong `Theme.kt`.

## Muốn đưa lên Google Play: cần làm thêm
Những việc dưới đây chưa làm vì ngoài phạm vi bài tập, nhưng nên ghi vào báo cáo để thấy nhóm hiểu quy trình phát hành:
- **targetSdk 36**: từ 31/8/2026, app mới và bản cập nhật trên Google Play phải nhắm Android 16 (API 36) trở lên
  (developer.android.com/google/play/requirements/target-sdk). App đang ở 34: cần nâng `compileSdk`/`targetSdk` lên 36 và
  Android Gradle Plugin lên bản hỗ trợ API 36, rồi thử lại trên máy Android 15/16 (tràn viền bắt buộc, giới hạn chạy nền mới).
  App đã vẽ tràn viền (edge-to-edge) nên ít phải sửa giao diện.
- **Quyền SEND_SMS**: Google Play chỉ cho app SMS mặc định dùng quyền này. Bản phát hành nên đổi sang mở sẵn tin nhắn
  (`Intent.ACTION_SENDTO`, người dùng bấm gửi) hoặc gửi qua mạng; giữ SMS tự động cho bản demo.
- **USE_EXACT_ALARM, USE_FULL_SCREEN_INTENT, PACKAGE_USAGE_STATS**: được phép cho app báo thức nhưng phải khai báo lý do trong
  Play Console (mục quyền nhạy cảm) và mục An toàn dữ liệu (app không gửi dữ liệu đi đâu, mọi thứ lưu trên máy).
- **Ký và rút gọn bản phát hành**: tạo keystore, bật `isMinifyEnabled = true` (R8) kèm quy tắc giữ lớp Room, xuất file `.aab`.
- **Sao lưu dữ liệu**: đang tắt `allowBackup` để không khôi phục nhầm phiên chuông đang reo; bản phát hành nên có sao lưu
  có chọn lọc (`dataExtractionRules`) để đổi máy không mất lịch sử.
- **Khởi động lại máy lúc chưa mở khóa**: báo thức chỉ được đặt lại sau lần mở khóa đầu tiên. Muốn reo cả khi máy khởi động
  lại giữa đêm cần chế độ Direct Boot (lưu chuông ở bộ nhớ được bảo vệ của thiết bị).
- **Chữ trong app**: đang viết thẳng tiếng Việt trong code; nên chuyển ra `strings.xml` để dịch sang tiếng Anh.
- **Báo lỗi và đo lường**: thêm Firebase Crashlytics để biết máy nào bị lỗi, nhất là các máy Oppo/Xiaomi hay chặn chạy nền.

## Giới hạn (nên ghi thật trong báo cáo)
- "Chụp vật" nhận diện theo loại vật (nhãn ML Kit), không phân biệt đúng cái bồn của bạn với bồn khác.
  Muốn chắc hơn, dùng thử thách QR.
- Chưa cấp quyền camera hoặc camera lỗi thì app tự quay về giải toán (có nút "Đổi sang giải toán").
- Không chặn được việc tắt nguồn hoặc gỡ app (giới hạn của hệ điều hành).
- Kiểm tra đã tỉnh cần thông báo được bật (chưa bật thì app không hẹn hỏi, để chuông không reo lại oan).
  Nếu bị giết hẳn bằng "Buộc dừng" thì Android hủy luôn các hẹn giờ của app (mở app lại là chuông được đặt lại).
- Lịch nghỉ lễ tính theo luật; năm nào Chính phủ hoán đổi ngày làm việc (ví dụ nghỉ Tết dài hơn 5 ngày) thì người dùng thêm
  "Kỳ nghỉ của tôi". Tết tính theo giờ Việt Nam (UTC+7).
- Âm thanh ru ngủ phát theo âm lượng nhạc (media) của máy.
- Đo giấc ngủ dựa vào màn hình: nằm im không dùng máy (đọc sách, xem TV) sau giờ bắt đầu tính có thể bị tính là ngủ;
  ngủ mà màn hình vẫn bật (xem video ngủ quên) thì không tính. Chỉ đo những đêm có chuông.
- Gợi ý giấc ngủ là tham khảo theo khuyến nghị chung, không thay cho tư vấn y tế.
- Một số hãng (Xiaomi, Oppo, Samsung) cần tắt "Tối ưu pin" cho app.
- Android 14+ có thể cần cấp quyền full-screen intent để màn hình thử thách tự hiện khi khóa máy.
