package com.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public class DataPipelineRunner {

    private static final String CONFIG_FILE = "dpr.properties";
    private static final String DEFAULT_SF1_HOME = "/app/search/sf1-v7";
    private static String sf1Home = DEFAULT_SF1_HOME;
    private static String collectionBaseDir;

    static {
        Properties props = new Properties();
        Path cfg = Paths.get(CONFIG_FILE);
        if (Files.exists(cfg)) {
            try (InputStream in = Files.newInputStream(cfg)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Failed to load properties: " + e.getMessage());
            }
        }

        sf1Home = props.getProperty("sf1.home.dir", DEFAULT_SF1_HOME);
        if (!sf1Home.endsWith("/")) {
            sf1Home += "/";
        }

        String val = props.getProperty("collection.base.dir");
        if (val != null && !val.isEmpty()) {
            collectionBaseDir = val.endsWith("/") ? val : val + "/";
        } else {
            collectionBaseDir = sf1Home + "collection/";
        }
    }

    // 확장자가 scd 또는 json 인지 확인
    private static boolean isValidExtension(String ext) {
        return "scd".equals(ext) || "json".equals(ext);
    }

    // 실행 모드가 static 또는 dynamic 인지 확인
    private static boolean isValidMode(String mode) {
        return "static".equals(mode) || "dynamic".equals(mode);
    }

    // gateway 명령의 operation 값 유효성 검사
    private static boolean isValidOperation(String op) {
        return "convert-json".equals(op) ||
               "convert-vector".equals(op) ||
               "index-json".equals(op) ||
               "index-scd".equals(op);
    }

    // 이전 단계 이름이 유효한지 확인
    private static boolean isValidPrevStep(String step) {
        return "bridge".equals(step) ||
               "tea".equals(step) ||
               "convert-json".equals(step) ||
               "convert-vector".equals(step) ||
               "index-json".equals(step) ||
               "index-scd".equals(step);
    }

    // 지정한 확장자의 파일을 대상 디렉토리로 이동
    private static void moveFiles(String srcDir, String destDir, String extension) throws IOException {
        Path src = Paths.get(srcDir);
        if (!Files.exists(src) || !Files.isDirectory(src)) {
            return; // source directory not present
        }
        Path dest = Paths.get(destDir);
        if (!Files.exists(dest)) {
            Files.createDirectories(dest);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(src, "*." + extension)) {
            for (Path file : stream) {
                Files.move(file, dest.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // tea2_util.sh 실행 전 필요한 파일 이동 처리
    private static void prepareForTea(String collectionId) throws IOException {
        String base = collectionBaseDir + collectionId + "/scd";
        moveFiles(base + "/static", base + "/tea_before", "scd");
        moveFiles(base + "/dynamic", base + "/tea_before", "scd");
    }

    // gateway.sh 실행 전 필요한 파일 이동 처리
    private static void prepareForGateway(String collectionId, String operation, String prevStep) throws IOException {
        if (prevStep == null) {
            // 이전 단계 정보가 없으면 파일 이동을 수행하지 않는다.
            return;
        }

        String base = collectionBaseDir + collectionId;
        switch (operation) {
            case "convert-json":
                if ("tea".equals(prevStep)) {
                    moveFiles(base + "/scd/tea_done", base + "/convert-json/index", "scd");
                } else if ("bridge".equals(prevStep)) {
                    moveFiles(base + "/scd/static", base + "/convert-json/index", "scd");
                    moveFiles(base + "/scd/dynamic", base + "/convert-json/index", "scd");
                }
                break;
            case "convert-vector":
                if ("convert-json".equals(prevStep)) {
                    moveFiles(base + "/convert-json/backup", base + "/convert-vector/index", "json");
                }
                break;
            case "index-json":
                if ("convert-vector".equals(prevStep)) {
                    moveFiles(base + "/convert-vector/backup", base + "/json/index", "json");
                } else if ("convert-json".equals(prevStep)) {
                    moveFiles(base + "/convert-json/backup", base + "/json/index", "json");
                }
                break;
            case "index-scd":
                if ("tea".equals(prevStep)) {
                    moveFiles(base + "/scd/tea_done", base + "/scd/index", "scd");
                } else if ("bridge".equals(prevStep)) {
                    moveFiles(base + "/scd/static", base + "/scd/index", "scd");
                    moveFiles(base + "/scd/dynamic", base + "/scd/index", "scd");
                }
                break;
        }
    }
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java DataPipelineRunner <command> [options]");
            System.exit(1);
        }

        String command = args[0];

        // 명령어에 따라 각 스크립트를 실행
        try {
            switch (command) {
                case "bridge":
                    if (args.length != 4) {
                        System.out.println("Usage: bridge <extension> <mode> <collectionId>");
                        System.exit(1);
                    }
                    String extension = args[1];
                    String mode = args[2];
                    if (!isValidExtension(extension)) {
                        System.out.println("Invalid extension: " + extension + " (allowed: scd, json)");
                        System.exit(1);
                    }
                    if (!isValidMode(mode)) {
                        System.out.println("Invalid mode: " + mode + " (allowed: static, dynamic)");
                        System.exit(1);
                    }
                    String collectionId = args[3];
                    new ProcessBuilder("sh", "bridge.sh", extension, mode, collectionId)
                            .inheritIO()
                            .start()
                            .waitFor();
                    break;

                case "tea":
                    if (args.length != 4) {
                        System.out.println("Usage: tea <collectionId> <listenerIP> <port>");
                        System.exit(1);
                    }
                    collectionId = args[1];
                    String listenerIP = args[2];
                    String port = args[3];
                    prepareForTea(collectionId);
                    new ProcessBuilder("sh", "tea2_util.sh", collectionId, listenerIP, port)
                            .inheritIO()
                            .start()
                            .waitFor();
                    break;

                case "gateway":
                    if (args.length < 4 || args.length > 5) {
                        System.out.println("Usage: gateway <collectionId> <operation> <mode> [prevStep]");
                        System.exit(1);
                    }
                    collectionId = args[1];
                    String operation = args[2];
                    mode = args[3];
                    String prevStep = args.length == 5 ? args[4] : null;
                    if (!isValidOperation(operation)) {
                        System.out.println("Invalid operation: " + operation + " (allowed: convert-json, convert-vector, index-json, index-scd)");
                        System.exit(1);
                    }
                    if (!isValidMode(mode)) {
                        System.out.println("Invalid mode: " + mode + " (allowed: static, dynamic)");
                        System.exit(1);
                    }
                    if (prevStep != null && !isValidPrevStep(prevStep)) {
                        System.out.println("Invalid prevStep: " + prevStep + " (allowed: bridge, tea, convert-json, convert-vector, index-json, index-scd)");
                        System.exit(1);
                    }
                    prepareForGateway(collectionId, operation, prevStep);
                    new ProcessBuilder("sh", "gateway.sh", collectionId, operation, mode)
                            .inheritIO()
                            .start()
                            .waitFor();
                    break;

                default:
                    System.out.println("Unknown command: " + command);
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error running command: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }


        /**
         * 1. 스크립트 개별실행
         *  1-1. bridge.sh 실행
         *      * 인자값: extension: scd or json, mode: static or dynamic, collectionId
         *      1-1-1. scd static 모드 실행
         *      1-1-2. scd dynamic 모드 실행
         *      1-1-3. json static 모드 실행
         *      1-1-4. json dynamic 모드 실행
         *      * 결과물: B-{이렇게저렇게}-{static일 경우 C, dynamic일 경우 U/D/I}.{extension.upperCase}
         *      * 결과물 저장 위치(자동저장됨): /sf1_data/collection/{collecitonId}/{extension}/{mode}/
         *  1-2. tea2_util.sh 실행
         *      * 선제작업: 1-1에서 진행한 결과물을 이동해야함
         *          - 이동해야할 디렉토리: /sf1_data/collection/{collecitonId}/scd/tea_before/
         *      * 인자값: collectionId, listenerIP, listenerPort
         *      1-2-1. 실행
         *      * 결과물 저장 위치(자동저장됨): /sf1_data/collection/{collecitonId}/scd/tea_done/
         *  1-3. gateway.sh 실행
         *      * 인자값: collectionId, mode: [convert-json, convert-vector, index-json, index-scd], mode2: static or dynamic
         *      * 선제작업
         *          - 1-2 또는 1-1에서 진행한 결과물을 이동해야함(이동할 디렉토리는 아래 케이스별로 다름)
         *          - convert-json, index-scd는 파일 확장자기 SCD인지 확인, convert-vector, index-json은 파일 확장자기 json인지 확인
         *      1-3-1. convert-json 실행
         *          - 선제작업으로 이동해야할 디렉토리: /sf1_data/collection/{collecitonId}/convert-json/index
         *          - 결과물: 기존 결과물 파일에서 확장자만 SCD -> json(소문자)로 변경됨
         *          - 결과물 저장 위치(자동저장됨): /sf1_data/collection/{collecitonId}/convert-json/backup
         *      1-3-2. convert-vector 실행
         *          - 선제작업으로 이동해야할 디렉토리: /sf1_data/collection/{collecitonId}/convert-vector/index
         *          - 결과물: 기존 결과물 파일에서 맨 앞 B -> V로 변경됨
         *          - 결과물 저장 위치(자동저장됨): /sf1_data/collection/{collecitonId}/convert-vector/backup
         *      1-3-3. index-json static 모드 실행
         *          - 선제작업으로 이동해야할 디렉토리: /sf1_data/collection/{collecitonId}/json/index
         *          - 결과물: 기존 결과물과 동일
         *          - 결과물 저장 위치(자동저장됨): /sf1_data/collection/{collecitonId}/json/backup
         *      1-3-4. index-json dynamic 모드 실행
         *          - 선제작업으로 이동해야할 디렉토리: /sf1_data/collection/{collecitonId}/json/index
         *          - 결과물: 기존 결과물과 동일
         *          - 결과물 저장 위치(자동저장됨): /sf1_data/collection/{collecitonId}/json/backup
         *      1-3-5. index-scd static 모드 실행
         *          - 선제작업으로 이동해야할 디렉토리: /sf1_data/collection/{collecitonId}/scd/index
         *          - 결과물: 기존 결과물과 동일
         *          - 결과물 저장 위치(자동저장됨): /sf1_data/collection/{collecitonId}/scd/backup
         *      1-3-6. index-scd dynamic 모드 실행
         *          - 선제작업으로 이동해야할 디렉토리: /sf1_data/collection/{collecitonId}/scd/index
         *          - 결과물: 기존 결과물과 동일
         *          - 결과물 저장 위치(자동저장됨): /sf1_data/collection/{collecitonId}/scd/backup
         * 2. 스크립트 전체 실행
         *  2-1. bridge scd static -> index-scd static
         *      => [1-1-1] -> [1-3-5]
         *  2-2. bridge scd static -> convert-json -> convert-vector -> index-json static
         *      => [1-1-1] -> [1-3-1] -> [1-3-2] -> [1-3-5]
         *  2-3. bridge scd static -> tea2_util -> convert-json -> convert-vector -> index-json static
         *      => [1-1-1] -> [1-2-1] -> [1-3-1] -> [1-3-2] -> [1-3-5]
         *  2-5. bridge scd dynamic -> index-scd dynamic
         *      => [1-1-2] -> [1-3-6]
         *  2-6. bridge scd dynamic -> convert-json -> convert-vector -> index-json dynamic
         *      => [1-1-2] -> [1-3-1] -> [1-3-2] -> [1-3-6]
         *  2-7. bridge scd dynamic -> tea2_util -> convert-json -> convert-vector -> index-json dynamic
         *      => [1-1-2] -> [1-2-1] -> [1-3-1] -> [1-3-2] -> [1-3-6]
         *  2-8. bridge json static -> index-json static
         *      => [1-1-3] -> [1-3-3]
         *  2-9. bridge json static -> convert-vector -> index-json static
         *      => [1-1-3] -> [1-3-2] -> [1-3-3]
         *  2-10. bridge json dynamic -> index-json dynamic
         *      => [1-1-4] -> [1-3-4]
         *  2-11. bridge json dynamic -> convert-vector -> index-json dynamic
         *      => [1-1-4] -> [1-3-2] -> [1-3-4]
         */


    }
}