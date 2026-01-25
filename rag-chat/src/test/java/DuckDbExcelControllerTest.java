//import com.gengzi.controller.DuckDbExcelController;
//import com.gengzi.request.ExcelQueryRequest;
//import org.apache.poi.ss.usermodel.Row;
//import org.apache.poi.ss.usermodel.Sheet;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.Test;
//
//import java.io.OutputStream;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.List;
//import java.util.Map;
//
//class DuckDbExcelControllerTest {
//
//    private final DuckDbExcelController controller = new DuckDbExcelController();
//
//    private Path tempExcel;
//
//    @AfterEach
//    void cleanup() throws Exception {
//        if (tempExcel != null) {
//            Files.deleteIfExists(tempExcel);
//        }
//    }
//
//    @Test
//    void queryExcelReturnsRows() throws Exception {
//        tempExcel = Files.createTempFile("duckdb-excel-", ".xlsx");
//        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
//            Sheet sheet = workbook.createSheet("Sheet1");
//            Row header = sheet.createRow(0);
//            header.createCell(0).setCellValue("name");
//            header.createCell(1).setCellValue("age");
//
//            Row row1 = sheet.createRow(1);
//            row1.createCell(0).setCellValue("alice");
//            row1.createCell(1).setCellValue(30);
//
//            Row row2 = sheet.createRow(2);
//            row2.createCell(0).setCellValue("bob");
//            row2.createCell(1).setCellValue(41);
//
//            try (OutputStream out = Files.newOutputStream(tempExcel)) {
//                workbook.write(out);
//            }
//        }
//
//        ExcelQueryRequest request = new ExcelQueryRequest();
//        request.setFilePath(tempExcel.toString());
//        request.setSheetName("Sheet1");
//        request.setLimit(10);
//
//        List<Map<String, Object>> body = controller.queryExcel(request).block();
//
//        Assertions.assertNotNull(body);
//        System.out.println(body);
//        Assertions.assertEquals(2, body.size());
//        Assertions.assertEquals("alice", body.get(0).get("name"));
//        Assertions.assertEquals(30, ((Number) body.get(0).get("age")).intValue());
//    }
//}
