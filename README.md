# Project tìm CUTOFF tốt nhất cho Hybrid MergeSort

## 1. Tóm tắt dự án
Mergesort và Quicksort có chung một cải tiến: khi mảng con nhỏ hơn ngưỡng **CUTOFF** thì chuyển sang Insertion Sort. Giáo trình gợi ý 7 (bản cũ) và 10 (bản mới). Dự án này đo thực nghiệm để trả lời: CUTOFF nào là phù hợp, 7 hay 10 tốt hơn, và giá trị tốt nhất trên máy thí nghiệm là bao nhiêu. Thuật toán được đo là `MergeWithCutoff.sort(Comparable[] a, int cutoff)` (MergeSort dựng theo bài giảng + InsertionSort); `cutoff = 0` là baseline MergeSort thuần.

## 2. Cấu hình máy thí nghiệm
| Mục | Giá trị |
|---|---|
| CPU / RAM | Intel Core i7-6600U, 2 nhân / 4 luồng, 8 GB |
| OS | Windows 10 (10.0, amd64) |
| JDK / JVM | BellSoft Liberica 26 (java.version 26, build 26+37), OpenJDK 64-Bit Server VM |
| JVM options | `-Xms2g -Xmx4g` (chạy qua IntelliJ IDEA 2025.3.3) |
| Python | 3.14.0; pandas 3.0.6, numpy 2.5.3, matplotlib 3.11.2 |
| Chạy chính thức | 18/11/2026, 2.366,3 s, đơn luồng, trạng thái COMPLETED |

## 3. Dữ liệu và dải CUTOFF
- **Phân phối (6):** random, sorted, reverse, nearly_sorted, few_uniques, interleaved.
- **Kích thước N (8 mức chạy):** 100; 1.000; 10.000; 100.000; 500.000; 1.000.000; 2.000.000; 5.000.000 → 48 dataset (sinh bằng seed cố định, xem `data/manifest.csv`).
- **CUTOFF (19 giá trị chạy):** 0 (baseline), 2, 4, 5, 6, 7, 8, 9, 10, 12, 16, 20, 24, 32, 40, 48, 64, 96, 128.
- **Phạm vi phân tích: N từ 1.000 đến 5·10⁶ (7 mức), không lấy N = 100.** Ở N = 100 thì CUTOFF = 128 biến thuật toán thành Insertion Sort thuần trên toàn mảng, thời gian cỡ micro-giây cũng nhiễu mạnh. Dữ liệu N = 100 chỉ được giữ làm tham chiếu ở phụ lục báo cáo.

## 4. Cách trình bày thí nghiệm
- Mỗi dataset: warm-up toàn cục + warm-up cục bộ; số lần lặp thích ứng theo N (25 / 15 / 8 lần); thứ tự các CUTOFF được xáo trộn lại ở mỗi lần lặp để tránh thiên lệch do thứ tự.
- Kiểm tra tính đúng đắn: `Test.java` và `Arrays.equals` trong `Benchmark`.
- Chỉ số: median thời gian, thời gian tương đối so với baseline (trung bình nhân – GM – trên các phân phối/kích thước), speedup, vùng gần tối ưu ±2%, CV, doubling ratio và hồi quy log-log.

## 5. Kết luận chính
**1. Cách biết CUTOFF phù hợp và giá trị tốt nhất theo từng nhóm.** Không có giá trị chung cho mọi trường hợp; phải đo, vì đường cong có đáy rất phẳng (vùng ±2%) và phụ thuộc phân phối.
- *Theo kích thước N (GM trên 6 phân phối):* 1.000→20; 10.000→32; 100.000→16; 500.000→20; 1.000.000→24; 2.000.000→16; 5.000.000→12.
- *Theo phân phối (GM trên N):* random 16 (vùng 9–24); sorted 128 (chạm biên); reverse 5 (4–8); nearly_sorted 48 (40–96); few_uniques 16 (10–24); interleaved 128 (chạm biên).
- *Theo kích thước × phân phối* (`result/analysis/best_cutoff.csv`):

| Phân phối \ N | 1.000 | 10.000 | 100.000 | 500.000 | 1.000.000 | 2.000.000 | 5.000.000 |
|---|---:|---:|---:|---:|---:|---:|---:|
| random | 16 | 10 | 16 | 10 | 9 | 5 | 12 |
| sorted | 128 | 128 | 128 | 128 | 128 | 128 | 96 |
| reverse | 7 | 8 | 4 | 6 | 5 | 4 | 8 |
| nearly_sorted | 64 | 48 | 48 | 64 | 48 | 32 | 40 |
| few_uniques | 16 | 16 | 20 | 20 | 24 | 24 | 12 |
| interleaved | 128 | 128 | 128 | 128 | 128 | 128 | 96 |

Nguyên nhân (chi tiết ở mục Discussion của báo cáo): chi phí gần đúng T(K) = N·log₂(N/K) + c·N·K; K càng lớn thì bớt được tầng đệ quy nhưng Insertion Sort tốn thêm c·N·K. Dữ liệu sorted/interleaved gần như không có chi phí dịch chuyển nên K lớn luôn lợi; reverse có c lớn nhất nên K tối ưu nhỏ nhất (vùng 4–8); random nằm giữa (K* lý thuyết ≈ 5,8, thực nghiệm đáy phẳng 9–24). Mức lợi giảm khi N tăng (speedup trung bình từ 1,47× ở N = 1.000 xuống 1,15× ở N = 5·10⁶).

**2. CUTOFF giáo trình: 7 hay 10?** **10 tốt hơn 7** về thực nghiệm: GM thời gian tương đối so với baseline là 0,8354 (K = 10) so với 0,8604 (K = 7), thấp hơn là nhanh hơn; K = 10 thắng K = 7 ở 33/42 ô (size × phân phối); chỉ dữ liệu reverse là K = 7 nhỉnh hơn (thắng 5/7 ô). Độ chậm trung vị so với giá trị tốt nhất của từng ô: 10,1% (K = 7) và 6,9% (K = 10).

**3. Ngoài 7 và 10: CUTOFF tốt nhất cho máy này = 24** (GM 0,8178; nhanh hơn baseline trung bình 1,223×). K = 16 (0,8193) và K = 20 (0,8196) gần như ngang bằng; vùng gần tối ưu toàn cục là {12, 16, 20, 24, 32}. Lưu ý K = 24 chậm hơn baseline 7,2% trên dữ liệu reverse; nếu ưu tiên an toàn cho mọi phân phối thì chọn **16**. Sorted và interleaved chạm biên 128, nên giá trị tối ưu thật của hai loại này có thể lớn hơn dải đã thử.

## 6. Cấu trúc dự án
```
Cutoff_exp_DSA/
├── src/      (MergeSort, MergeWithCutoff, InsertionSort, DataGenerator, Benchmark, Test)
├── script/   (run_experiment.bat, analyze.py)
├── data/     (các file dữ liệu + manifest.csv)
├── result/   (raw_results.csv, summary.csv, raw_results_environment.txt, analysis/: bảng tổng hợp, summary_tables.md, plots/)
├── report/   (báo cáo)
└── README.md
```

## 7. Tự chạy / kiểm chứng trên máy cá nhân
**Yêu cầu:** JDK (có `javac`, `java` trong PATH), Python 3 với `pip install pandas numpy matplotlib`.
Để kết quả đo đáng tin: cắm sạc, đặt Windows ở "Best performance", đóng IDE/trình duyệt và không dùng máy khi đang chạy.

**Cách 1:**
```
script\run_experiment.bat
```
Script biên dịch `src\*.java` vào `bin`, sinh dữ liệu nếu chưa có `data\manifest.csv`, chạy `Benchmark` với `-Xms2g -Xmx4g`, rồi chạy `analyze.py`. Tham số thêm được chuyển cho `analyze.py` (ví dụ `--tol 0.03`). Đặt `NOPAUSE=1` để bỏ các lần dừng chờ phím.

**Cách 2:**
```
javac -encoding UTF-8 -d bin src\*.java
java -cp bin Test                       :: kiểm tra tính đúng đắn (kỳ vọng: ALL TESTS PASSED)
java -cp bin DataGenerator              :: sinh data/ (bỏ qua nếu đã có)
java -Xms2g -Xmx4g -cp bin Benchmark    :: đo, ghi result/raw_results.csv, summary.csv
python script/analyze.py                :: phân tích, ghi result/analysis/
```
**Phân tích lại từ số liệu có sẵn (không cần chạy lại benchmark):** `python script/analyze.py` (mặc định `--min-size 1000`, `--tol 0.02`; dùng `--input` để chỉ định file raw khác).

**Kiểm chứng:** so sánh `result/analysis/overall_cutoff.csv` và `best_cutoff.csv` với kết quả ở mục 5. Thời gian tuyệt đối sẽ khác theo máy, nhưng hình dạng đường cong (đáy phẳng ở 12–32 cho random, K lớn cho sorted, K nhỏ cho reverse) cần giống nhau.

## 8. Phạm vi thực hiện và sử dụng công cụ AI

Em tự thực hiện việc cài đặt MergeSort, Insertion Sort và Hybrid MergeSort (`MergeWithCutoff`) dựa trên nội dung bài giảng; đồng thời xây dựng câu hỏi nghiên cứu, lựa chọn phạm vi giá trị CUTOFF và phân tích, diễn giải kết quả thực nghiệm. Công cụ AI đã được sử dụng để thảo luận hướng đi và hỗ trợ xây dựng các thành phần phục vụ thí nghiệm, bao gồm `Test.java`, `DataGenerator.java`, `Benchmark.java`, `run_experiment.bat` và `analyze.py`, thông qua prompt. Em trực tiếp chạy thử, kiểm tra và sử dụng các thành phần này trên máy tính. Các nội dung còn lại được thực hiện dựa trên kiến thức đã học trong môn học và tài liệu tham khảo trên Youtube, ResearchGate, ... do em tự tìm hiểu.