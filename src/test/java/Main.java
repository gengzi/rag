//import okhttp3.*;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.util.Base64;
//import java.util.concurrent.TimeUnit;
//
//public class Main {
//    public static void main(String[] args) throws IOException {
//        String API_URL = "http://localhost:8885/layout-parsing";
//        String pdfurl = "http://192.168.31.131:60546/api/v1/download-shared-object/aHR0cDovLzEyNy4wLjAuMTo5MDAwL3Rlc3QwMi9kb2NrZXIucGRmP1gtQW16LUFsZ29yaXRobT1BV1M0LUhNQUMtU0hBMjU2JlgtQW16LUNyZWRlbnRpYWw9REVXM01ZU0Q1RlRRWllNWThQUzYlMkYyMDI1MTAwNSUyRnVzLWVhc3QtMSUyRnMzJTJGYXdzNF9yZXF1ZXN0JlgtQW16LURhdGU9MjAyNTEwMDVUMDUwNDIyWiZYLUFtei1FeHBpcmVzPTQzMjAwJlgtQW16LVNlY3VyaXR5LVRva2VuPWV5SmhiR2NpT2lKSVV6VXhNaUlzSW5SNWNDSTZJa3BYVkNKOS5leUpoWTJObGMzTkxaWGtpT2lKRVJWY3pUVmxUUkRWR1ZGRmFXVTFaT0ZCVE5pSXNJbVY0Y0NJNk1UYzFPVFk0TXpjeU55d2ljR0Z5Wlc1MElqb2liV2x1YVc5aFpHMXBiaUo5Lm91a2JxbUxrbHc2RUZXTEN2TUlYMTdadGZyZ3VrSkVsYnhfVHl5Ym9WQVJqQk5mcnNYM0tuanpNeHdVbVZqTjFOWlM5aURxUEhUZ3phMU1rNnR4VnFRJlgtQW16LVNpZ25lZEhlYWRlcnM9aG9zdCZ2ZXJzaW9uSWQ9bnVsbCZYLUFtei1TaWduYXR1cmU9MGUzOWI1MzZmYzVhMjJhZmNkNDU1YzI0MmM4NTA0NzBlYWRiM2VhMjY5NjliMWY4N2E2NzljOGJhNDIxNTBjZQ";
//
//
//
//        OkHttpClient client = new OkHttpClient.Builder()
//                // 连接超时：建立 TCP 连接的超时时间
//                .connectTimeout(100, TimeUnit.SECONDS)
//                // 读取超时：从服务器获取响应数据的超时时间
//                .readTimeout(20, TimeUnit.MINUTES)
//                // 写入超时：向服务器发送数据的超时时间
//                .writeTimeout(20, TimeUnit.MINUTES)
//                // 调用超时（可选，OkHttp 4.3+ 支持）：整个请求的总超时时间
//                .callTimeout(20, TimeUnit.MINUTES)
//                .build();
//
//        ObjectMapper objectMapper = new ObjectMapper();
//        ObjectNode payload = objectMapper.createObjectNode();
//        payload.put("file", pdfurl);
//        payload.put("fileType", 0);
//
//
//        MediaType JSON = MediaType.get("application/json; charset=utf-8");
//
//        RequestBody body = RequestBody.create(JSON, payload.toString());
//
//        Request request = new Request.Builder()
//                .url(API_URL)
//                .post(body)
//                .build();
//
//        try (Response response = client.newCall(request).execute()) {
//            if (response.isSuccessful()) {
//                String responseBody = response.body().string();
//                JsonNode root = objectMapper.readTree(responseBody);
//                JsonNode result = root.get("result");
//
//                JsonNode layoutParsingResults = result.get("layoutParsingResults");
//                for (int i = 0; i < layoutParsingResults.size(); i++) {
//                    JsonNode item = layoutParsingResults.get(i);
//                    int finalI = i;
//                    JsonNode prunedResult = item.get("prunedResult");
//                    System.out.println("Pruned Result [" + i + "]: " + prunedResult.toString());
//
//                    JsonNode outputImages = item.get("outputImages");
//                    outputImages.fieldNames().forEachRemaining(imgName -> {
//                        try {
//                            String imgBase64 = outputImages.get(imgName).asText();
//                            byte[] imgBytes = Base64.getDecoder().decode(imgBase64);
//                            String imgPath = imgName + "_" + finalI + ".jpg";
//                            try (FileOutputStream fos = new FileOutputStream(imgPath)) {
//                                fos.write(imgBytes);
//                                System.out.println("Saved image: " + imgPath);
//                            }
//                        } catch (IOException e) {
//                            System.err.println("Failed to save image: " + e.getMessage());
//                        }
//                    });
//                }
//            } else {
//                System.err.println("Request failed with HTTP code: " + response.code());
//            }
//        }
//    }
//}


