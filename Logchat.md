# Nhật ký làm việc với AI – Cutoff_exp_DSA

**Đề tài:** Tìm cutoff tối ưu cho Hybrid MergeSort (MergeSort + InsertionSort).
**Công cụ AI:** Claude (Anthropic). 
**Vai trò của AI:** viết code, chạy thử trong môi trường cloud (Linux), đề xuất cách xử lý. Người thực hiện cung cấp source gốc (InsertionSort, MergeSort, MergeWithCutoff), ra yêu cầu, quyết định hướng đi và chạy thí nghiệm cuối cùng trên máy thật.

> Bên dưới là tóm tắt nội dung prompt.

---

## M1 – Kiểm thử tính đúng đắn (`Test.java`)

**Yêu cầu:** Một class Java thuần, không dùng JUnit, chỉ kiểm tra correctness của MergeSort gốc và MergeWithCutoff. Mỗi dataset phải kiểm tra:
- cả hai kết quả đều được sắp xếp;
- cả hai đều bằng expected (tạo bằng `Arrays.sort` trên bản sao độc lập);
- hai kết quả bằng nhau.

Phải đa dạng kích thước, 10 dạng phân bố, các trường hợp biên, nhiều cutoff (2, 3, 4, 5, 8, 10, 16; không dùng 0) và các nhóm test bổ sung (cutoff boundary, odd/even, 20 dataset seed cố định).

**Kết quả:**
- 392 dataset, 2744 lần chạy Hybrid, 9016 lần kiểm tra, 0 lỗi → ALL TESTS PASSED.
- Đã thử làm hỏng cố ý một bản sao của Hybrid: test báo FAILED (1333 lỗi) và in đủ thông tin để tái hiện, nên cơ chế bắt lỗi hoạt động.
- Kết luận PASS còn kiểm tra thêm bộ đếm khớp (số lần chạy = số dataset × số cutoff) để tránh PASS giả.

---

## M2 – Sinh dữ liệu (`DataGenerator.java`)

**Yêu cầu:** Nâng cấp từ code mẫu:
- 8 kích thước: 100; 1.000; 10.000; 100.000; 500.000; 1.000.000; 2.000.000; 5.000.000.
- 6 phân phối: `random`, `sorted`, `reverse`, `nearly_sorted`, `few_uniques`, `interleaved`.
- Mỗi loại nằm trong thư mục con (`data/random/random_100.txt`, …).
- Xuất `data/manifest.csv` với cột `relative_path,type,size,seed,bytes`.
- Giữ cơ chế seed xác định, ghi bằng `BufferedWriter` 64 KB, in tiến độ và tổng thời gian.

**Kết quả:** 48 file, khoảng 333 MB, khoảng 2 giây; chạy được với heap 256 MB. Đã kiểm tra nội dung từng loại (ví dụ `few_uniques` chỉ có giá trị 0–9, `interleaved` đúng dạng chẵn rồi lẻ).

**Quyết định cần nhớ:** `nearly_sorted` hiện hoán đổi n/20 cặp, tức tối đa khoảng 10% phần tử bị dịch chỗ. Muốn đúng 5% phần tử thì đổi `NEARLY_SORTED_DIVISOR` từ 20 thành 40 (phải sinh lại dữ liệu và chạy lại benchmark).

---

## M3 – Benchmark (`Benchmark.java`)

**Yêu cầu:** Benchmark theo chuẩn nghiên cứu, chỉ dùng thư viện chuẩn Java:
- Không sửa các file Java khác; đọc danh sách dataset từ `manifest.csv`.
- 19 cutoff, `{0, 2, 4, 5, 6, 7, 8, 9, 10, 12, 16, 20, 24, 32, 40, 48, 64, 96, 128}`, trong đó 0 là baseline.
- Shuffle thứ tự cutoff ở mỗi rep với seed cố định.
- Warm-up toàn cục (mảng 50.000 phần tử) và warm-up theo dataset.
- Số rep thay đổi theo kích thước: 25 / 15 / 8.
- Vùng đo chỉ bọc đúng lời gọi `MergeWithCutoff.sort`.
- Kiểm tra checksum dữ liệu gốc, verify kết quả sau mỗi lần đo.
- Xuất 3 file trong `result/`, không ghi đè bản cũ.

**Kết quả:**
- AI chạy thử trên 24 dataset (kích thước 100 đến 100.000), JVM `-Xms1g -Xmx1g`, mất khoảng 26 giây.
- Chạy từ project root và từ `src/` đều hoạt động; lần chạy thứ hai tự gắn timestamp.
- Chưa chạy đủ 48 dataset và chưa đo thời gian thật trên i7-6600U.

**Khuyến nghị VM options:** Heap 2 GB là đủ cho mảng 5 triệu phần tử (peak khoảng 250–300 MB), nên đặt `-Xms` bằng `-Xmx` để heap không phải co giãn trong lúc đo (`-Xms2g -Xmx2g`). Script `.bat` hiện dùng `-Xms2g -Xmx4g` theo yêu cầu.

---

## M4 – Phân tích và tự động hóa (`analyze.py`, `run_experiment.bat`)

**Yêu cầu:**
- `analyze.py` (pandas, numpy, matplotlib) với tham số `--tol` mặc định 0.02, đọc `raw_results*.csv` mới nhất.
- Bảng: `detailed_stats.csv`, `best_cutoff.csv`, `overall_cutoff.csv` (trung bình nhân), `doubling_and_scaling.csv` (tỉ lệ doubling và hồi quy log-log), kèm `summary_tables.md`.
- 16 đồ thị 300 DPI trong `result/analysis/plots/`, gồm so sánh với mô hình lý thuyết `T(K) ∝ N·log2(N/K) + N·K/4`.
- `run_experiment.bat`: về thư mục gốc, biên dịch vào `bin/`, chỉ sinh dữ liệu nếu chưa có manifest, chạy Benchmark rồi chạy `analyze.py`, kiểm tra lỗi từng bước.

**Kết quả:**
- `analyze.py` đã chạy thử bằng dữ liệu giả lập (6 loại × 8 kích thước × 19 cutoff), sinh đủ bảng và 16 ảnh; đã xem lại 2 ảnh để kiểm tra bố cục.
- Số liệu trong lần thử là dữ liệu giả, **không dùng để kết luận cutoff**.
- `run_experiment.bat` chưa chạy thử vì môi trường AI là Linux; cần chạy thử trên Windows.

---

## M5 – Báo file bị cắt ngang

**Vấn đề:** Người dùng báo `analyze.py` và `run_experiment.bat` bị cắt giữa chừng.

**Xử lý:** AI kiểm tra lại: `analyze.py` khoảng 550 dòng và `.bat` 102 dòng, đều kết thúc đúng chỗ, không bị cắt. Nguyên nhân khả dĩ là hai file chưa được gửi dưới dạng file đính kèm. AI gửi lại nguyên cả hai dưới dạng file.

---

## M6 – Xử lý kích thước nhỏ và cutoff lớn hơn N

**Câu hỏi:** Với N = 100 và cutoff 128 thì Hybrid chỉ còn là InsertionSort thuần, trong khi đề bài hỏi cutoff tối ưu của Hybrid. Có nên loại không, và có phải sửa code hay chạy lại benchmark?

**Kết luận:**
- Đúng, nên loại. Điều kiện `hi - lo + 1 <= cutoff` được kiểm tra ngay lần gọi đầu, nên khi N ≤ cutoff thuật toán chạy InsertionSort thuần trên cả mảng. Với dữ liệu hiện có, chỉ cặp (N = 100, cutoff = 128) rơi vào trường hợp này. Thời gian đo ở N = 100 cũng chỉ vài micro-giây nên nhiễu cao.
- **Không cần** sửa `src/`, không cần sửa `.bat`, không cần chạy lại benchmark. Giữ nguyên dữ liệu thô, chỉ lọc ở bước phân tích.
- AI phát hiện thêm một lỗi trong bản `analyze.py` trước đó: cutoff 0 (MergeSort thuần) vẫn có thể được chọn làm `best_cutoff` hoặc nằm trong `near_optimal_cutoffs`.

**Thay đổi trong `analyze.py`:**
- Thêm tham số `--min-size` (mặc định 1000; `--min-size 0` để giữ N = 100 làm phụ lục).
- Tự loại các cặp có `cutoff >= size`.
- `best_cutoff` và `near_optimal_cutoffs` chỉ xét cutoff > 0; cutoff 0 chỉ làm baseline.
- Mô tả bộ lọc được in ra console và ghi trong `summary_tables.md`.

**Kiểm chứng:** Với dữ liệu giả lập, mặc định N = 100 bị loại và không còn `0` trong `near_optimal_cutoffs`; với `--min-size 0` bộ lọc chỉ loại đúng 150 dòng (6 loại × 25 lần đo) của cặp N = 100 với cutoff 128.

**Câu gợi ý cho báo cáo:**
> Cutoff K chỉ có ý nghĩa với hybrid khi 0 < K < N. Với N ≤ K, MergeWithCutoff suy biến thành InsertionSort thuần, và cutoff = 0 là MergeSort thuần. Vì vậy cutoff = 0 chỉ dùng làm baseline, và các kích thước N < 1.000 bị loại khỏi phần kết luận (thời gian đo ở mức micro-giây, nhiễu cao). Dữ liệu N = 100 vẫn được lưu trong raw data để đối chứng.
