package com.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class DataPipelineRunner {

    private static final String DEFAULT_SF1_HOME = "/app/search/sf1-v7";
    private static final String DEFAULT_TEA_HOME = "/app/search/tea";
    private static String sf1Home = DEFAULT_SF1_HOME;
    private static String collectionBaseDir;
    private static String teaHome = DEFAULT_TEA_HOME;

    // Config path used for property loading
    private static String configPath = "dpr.properties";
    private static Properties props = new Properties();

    private static final Map<String, FilePattern> movePatternMap = new HashMap<>();
    private static final Map<String, String[]> deletePatternMap = new HashMap<>();

    // move file pattern
    static {
        movePatternMap.put("bridge", new FilePattern("B-", "-C"));
        movePatternMap.put("tea", new FilePattern("B-", "-C"));
        movePatternMap.put("convert-json", new FilePattern("B-", "-C"));
        movePatternMap.put("convert-vector", new FilePattern("V-", "-C"));
    }

    // delete file pattern
    static {
//        deletePatternMap.put("bridge", new String[] {"scd"});
        deletePatternMap.put("tea", new String[] {"scd"});
        deletePatternMap.put("convert-json", new String[] {"scd"});
        deletePatternMap.put("convert-vector", new String[] {"json"});
        deletePatternMap.put("index-json", new String[] {"json"});
        deletePatternMap.put("index-scd", new String[] {"json"});
    }

    // properties
    static {
        configPath = "dpr.properties";
        props = new Properties();
        Path cfg = Paths.get(configPath);
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

    private static class FilePattern{
        String prefix;
        String suffix;

        FilePattern(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
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
    private static void moveFiles(String srcDir, String destDir, String extension, String prevStep) throws IOException {
        Path src = Paths.get(srcDir);
        if (!Files.exists(src) || !Files.isDirectory(src)) {
            return; // source directory not present
        }
        Path dest = Paths.get(destDir);
        if (!Files.exists(dest)) {
            Files.createDirectories(dest);
        }

        FilePattern patternDef = movePatternMap.get(prevStep);
        if(patternDef == null) {
            System.err.println("Unknown prevStep for pattern mapping: " + prevStep);
            return;
        }

        // ex: ^V-.*-C\.json$ (prefix = V-, suffix = -C, extension = json)
        String regex = "(?i)^" + Pattern.quote(patternDef.prefix) + ".*" +
                Pattern.quote(patternDef.suffix) + "\\." + Pattern.quote(extension) + "$";

        Pattern pattern = Pattern.compile(regex);

        int movedCount = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(src, path -> {
            return pattern.matcher(path.getFileName().toString()).matches();
        })) {
            for (Path file : stream) {
                Files.move(file, dest.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                movedCount++;
            }
        }
        if (movedCount == 0) {
            System.out.println("No matching files found to move in: " + src);
        }
    }

    // 여러 소스 디렉토리의 파일을 한 번에 이동
    private static void moveFilesBatch(String destDir, String extension, String prevStep, String... srcDirs) throws IOException {
        for (String dir : srcDirs) {
            moveFiles(dir, destDir, extension, prevStep);
        }
    }

    // 공통 SCD 이동 로직
    private static void prepareScdMove(String base, String destDir, String prevStep) throws IOException {
        if (prevStep == null) {
            moveFilesBatch(destDir, "scd", prevStep,
                    base + "/scd/tea_done",
                    base + "/scd/static",
                    base + "/scd/dynamic");
        } else if ("tea".equals(prevStep)) {
            moveFilesBatch(destDir, "scd", prevStep,
                    base + "/scd/tea_done");
        } else if ("bridge".equals(prevStep)) {
            moveFilesBatch(destDir, "scd", prevStep,
                    base + "/scd/static",
                    base + "/scd/dynamic");
        }
    }

    // 공통 JSON 이동 로직
    private static void prepareJsonMove(String base, String destDir, String prevStep) throws IOException {
        if (prevStep == null) {
            moveFilesBatch(destDir, "json", prevStep,
                    base + "/convert-vector/backup",
                    base + "/convert-json/backup");
        } else if ("convert-vector".equals(prevStep)) {
            moveFilesBatch(destDir, "json", prevStep,
                    base + "/convert-vector/backup");
        } else if ("convert-json".equals(prevStep)) {
            moveFilesBatch(destDir, "json", prevStep,
                    base + "/convert-json/backup");
        }
    }

    // tea2_util.sh 실행 전 필요한 파일 이동 처리
    private static void prepareForTea(String collectionId) throws IOException {
        String base = collectionBaseDir + collectionId + "/scd";

        moveFilesBatch(base + "/tea_before", "scd", "bridge",
                base + "/static",
                base + "/dynamic");

        Path deleteDirectoryPath = Paths.get(base, "tea_done");
        String[] deleteExts = deletePatternMap.get("tea");
        if(deleteExts != null) {
            for(String ext : deleteExts) {
                deleteFilesMatching(deleteDirectoryPath, "tea", ext);
            }
        }
    }

    // gateway.sh 실행 전 필요한 파일 이동 처리
    private static void prepareForGateway(String collectionId, String operation, String prevStep) throws IOException {
        if (prevStep == null) {
            // 이전 단계 정보가 없으면 파일 이동을 수행하지 않는다.
            return;
        }

        String base = collectionBaseDir + collectionId;

        Path deleteDirectoryPath = null;
        switch (operation) {
            case "convert-json":
                deleteDirectoryPath = Paths.get(base, operation, "backup");
                if ("tea".equals(prevStep)) {
                    moveFiles(base + "/scd/tea_done", base + "/convert-json/index", "scd", prevStep);
                } else if ("bridge".equals(prevStep)) {
                    moveFiles(base + "/scd/static", base + "/convert-json/index", "scd", prevStep);
                    moveFiles(base + "/scd/dynamic", base + "/convert-json/index", "scd", prevStep);
                }
                break;
            case "convert-vector":
                deleteDirectoryPath = Paths.get(base, operation, "backup");
                if ("convert-json".equals(prevStep)) {
                    moveFiles(base + "/convert-json/backup", base + "/convert-vector/index", "json", prevStep);
                } else if ("bridge".equals(prevStep)) {
                    moveFiles(base + "/json/static", base + "/convert-vector/index", "json", prevStep);
                    moveFiles(base + "/json/dynamic", base + "/convert-vector/index", "json", prevStep);
                }
                break;
            case "index-json":
                deleteDirectoryPath = Paths.get(base, "json", "backup");
                if ("convert-vector".equals(prevStep)) {
                    moveFiles(base + "/convert-vector/backup", base + "/json/index", "json", prevStep);
                } else if ("convert-json".equals(prevStep)) {
                    moveFiles(base + "/convert-json/backup", base + "/json/index", "json", prevStep);
                } else if ("bridge".equals(prevStep)) {
                    moveFiles(base + "/json/static", base + "/json/index", "json", prevStep);
                    moveFiles(base + "/json/dynamic", base + "/json/index", "json", prevStep);
                }
                break;
            case "index-scd":
                deleteDirectoryPath = Paths.get(base, "scd", "backup");
                if ("tea".equals(prevStep)) {
                    moveFiles(base + "/scd/tea_done", base + "/scd/index", "scd", prevStep);
                } else if ("bridge".equals(prevStep)) {
                    moveFiles(base + "/scd/static", base + "/scd/index", "scd", prevStep);
                    moveFiles(base + "/scd/dynamic", base + "/scd/index", "scd", prevStep);
                }
                break;
        }

        String[] deleteExts = deletePatternMap.get(operation);
        if(deleteExts != null) {
            for(String ext : deleteExts) {
                deleteFilesMatching(deleteDirectoryPath, operation, ext);
            }
        }
    }

    // 삭제 유틸 메서드
    private static void deleteFilesMatching(Path dir, String operation, String extension) throws IOException {
        if (!Files.exists(dir)) return;
        String pattern = "(?i)^.*\\." + Pattern.quote(extension) + "$";
        Pattern compiled = Pattern.compile(pattern);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, path -> {
            return compiled.matcher(path.getFileName().toString()).matches();
        })) {
            for (Path file : stream) {
                Files.delete(file);
            }
        }
    }

    public static void main(String[] args) {
        // Check if config file specified as first argument
        int cmdIdx = 0;
        if (args.length > 0 && args[0].endsWith(".properties")) {
            configPath = args[0];
            // Reload properties from specified file
            props = new Properties();
            Path cfg = Paths.get(configPath);
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
            String val = props.getProperty("tea.home.dir");
            if (!sf1Home.endsWith("/")) {
                teaHome += "/";
            }
            cmdIdx = 1;
        }

        if (args.length <= cmdIdx) {
            System.out.println("Usage: java DataPipelineRunner <command> [options]");
            System.exit(1);
        }

        String command = args[cmdIdx];

        // 명령어에 따라 각 스크립트를 실행
        try {
//            String configFile = sf1Home;
            Path configFilePath = null;
            Path batchFilePath = null;
            switch (command) {
                case "bridge":
                    if (args.length - cmdIdx != 4) {
                        System.out.println("Usage: bridge <extension> <mode> <collectionId>");
                        System.exit(1);
                    }
                    String extension = args[cmdIdx + 1];
                    String mode = args[cmdIdx + 2];
                    if (!isValidExtension(extension)) {
                        System.out.println("Invalid extension: " + extension + " (allowed: scd, json)");
                        System.exit(1);
                    }
                    if (!isValidMode(mode)) {
                        System.out.println("Invalid mode: " + mode + " (allowed: static, dynamic)");
                        System.exit(1);
                    }
                    String collectionId = args[cmdIdx + 3];
                    String subDir = "json".equals(extension) ? "" : extension;

                    configFilePath = Paths.get(sf1Home, "config", "source", subDir, (collectionId + ".xml"));
//                    configFile += "/config/source/" + subDir + collectionId + ".xml";
//                    configFilePath = Paths.get(configFile);
                    if (configFilePath == null || !Files.exists(configFilePath)) {
                        System.err.println("Configuration file not found: " + configFilePath.toString());
                        System.exit(1);
                    }
                    batchFilePath = Paths.get(sf1Home, "batch", "bridge.sh");
                    new ProcessBuilder("sh", batchFilePath.toString(), configFilePath.toString(), "db", collectionId, mode)
                            .inheritIO()
                            .start()
                            .waitFor();
                    break;

                case "tea":
                    if (args.length - cmdIdx != 4) {
                        System.out.println("Usage: tea <collectionId> <listenerIP> <port>");
                        System.exit(1);
                    }
                    collectionId = args[cmdIdx + 1];
                    String listenerIP = args[cmdIdx + 2];
                    String port = args[cmdIdx + 3];
                    prepareForTea(collectionId);
                    batchFilePath = Paths.get(teaHome, "batch", "tea2_util.sh");
                    new ProcessBuilder("sh", batchFilePath.toString(), listenerIP, port, collectionId, "-te 2")
                            .inheritIO()
                            .start()
                            .waitFor();
                    break;

                case "gateway":
                    if ((args.length - cmdIdx) < 4 || (args.length - cmdIdx) > 5) {
                        System.out.println("Usage: gateway <collectionId> <operation> <mode> [prevStep]");
                        System.exit(1);
                    }
                    collectionId = args[cmdIdx + 1];
                    String operation = args[cmdIdx + 2];
                    mode = args[cmdIdx + 3];
                    String prevStep = (args.length - cmdIdx) == 5 ? args[cmdIdx + 4] : null;
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

                    configFilePath = Paths.get(sf1Home, "config", "collection", operation, (collectionId + ".yaml"));
                    if (configFilePath == null || !Files.exists(configFilePath)) {
                        System.err.println("Configuration file not found: " + configFilePath.toString());
                        System.exit(1);
                    }
                    batchFilePath = Paths.get(sf1Home, "batch", "gateway.sh");
                    new ProcessBuilder("sh", "gateway.sh", configFilePath.toString())
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
    }
}