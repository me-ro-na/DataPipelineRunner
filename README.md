# DataPipelineRunner


## 프로젝트 소개

`DataPipelineRunner`는 다양한 데이터 처리 스크립트를 통합하여 일관된 커맨드 흐름으로 실행할 수 있도록 도와주는 Java 기반 커맨드 실행기입니다. 다음과 같은 배치 작업을 지원

- **bridge.sh**: SCD/JSON 데이터 수집(생성) 기능 수행
- **tea2_util.sh**: TEA2 사전 처리 및 후속 작업
- **gateway.sh**: 변환 및 인덱싱 작업

각 스크립트를 개별 호출하거나, 전체 파이프라인(2-1~2-11) 시나리오에 따라 연속 실행할 수 있습니다.

---

## 요구 사항

- Java 8 이상
- Unix 계열 OS (Linux/macOS)
- 쉘 스크립트 파일 (`bridge.sh`, `tea2_util.sh`, `gateway.sh`)에 실행 권한 부여

---

## 설치 및 빌드

1. 저장소를 클론합니다.
    ```bash
    git clone <repository_url>
    cd <repository_folder>
    ```
2. Java 컴파일
    ```bash
    javac -d out/dpr src/com/pipeline/DataPipelineRunner.java
    ```
3. 컴파일된 클래스 파일을 지정 디렉토리로 이동합니다.
    ```bash
    mkdir -p ${SF1_HOME}/lib/custom
    cp -r out/dpr/* ${SF1_HOME}/lib/custom/
    ```


이제 `${SF1_HOME}/lib/custom` 디렉토리에서 `DataPipelineRunner`를 실행할 수 있습니다. (SF1_HOME은 환경에 따라 설정)

또한 `DataPipelineRunner` 실행을 편리하게 하기 위해 Java 명령어를 감싼 쉘 스크립트(`run_dpr.sh` 등)를 함께 제공합니다. 해당 스크립트를 통해 명령어를 간편하게 실행할 수 있습니다. 예시:

```bash
./run_dpr.sh bridge scd static 12345
```

---

## 사용법

`DataPipelineRunner`는 첫 번째 인자에 **커맨드**를, 이후 인자에 **옵션**을 받습니다.

```bash
java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner <command> [options]
```

### 지원 커맨드

| 커맨드    | 옵션                                  | 설명                                                       |
|-----------|---------------------------------------|------------------------------------------------------------|
| bridge    | `<extension> <mode> <collectionId>`   | SCD/JSON 생성 스크립트 실행 (`bridge.sh`)                 |
| tea2      | `<collectionId> <listenerIP> <port>`  | TEA2 사전 처리 스크립트 실행 (`tea2_util.sh`)              |
| gateway   | `<collectionId> <operation> <mode>`   | 변환/인덱싱 스크립트 실행 (`gateway.sh`)                   |

- **extension**: `scd` 또는 `json`
- **mode**: `static` 또는 `dynamic`  
  파이프라인 전체에 공통 적용되는 모드입니다.  
  - `static`: 정적 수집 및 전체 인덱싱
  - `dynamic`: 동적 수집 및 증분 인덱싱

- **collectionId**: 작업 대상 컬렉션 식별자
- **listenerIP**, **port**: TEA2 유틸리티 리스너 정보
- **operation**: `convert-json`/`convert-vector`/`index-json`/`index-scd`

---


## 예제

아래 예제는 직접 java 명령어를 사용하는 방식이며, run_dpr.sh 스크립트를 사용할 경우 동일한 명령을 간단히 실행할 수 있습니다.

### 개별 스크립트 실행

```bash
# SCD static 모드로 bridge
java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner bridge scd static 12345

# TEA2 유틸리티 실행
java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner tea2 12345 127.0.0.1 9000

# JSON dynamic 모드로 gateway
java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 convert-json dynamic
```

### 전체 파이프라인 시나리오

아래 2-1부터 2-11까지의 커맨드 시퀀스로 전체 파이프라인을 실행할 수 있습니다. 각 `collectionId`를 원하는 값으로 교체하여 실행하세요:

- **2-1.** bridge(scd/static) → index-scd(static)
  ```bash
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner bridge scd static 12345
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 index-scd static
  ```

- **2-2.** bridge(scd/static) → convert-json → convert-vector → index-json(static)
  ```bash
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner bridge scd static 12345
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 convert-json static
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 convert-vector static
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 index-json static
  ```

- **2-3.** bridge(scd/static) → tea2 → convert-json → convert-vector → index-json(static)
  ```bash
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner bridge scd static 12345
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner tea2 12345 127.0.0.1 9000
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 convert-json static
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 convert-vector static
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 index-json static
  ```

- **2-5.** bridge(scd/dynamic) → index-scd(dynamic)
  ```bash
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner bridge scd dynamic 12345
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 index-scd dynamic
  ```

- **2-6.** bridge(scd/dynamic) → convert-json → convert-vector → index-json(dynamic)
  ```bash
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner bridge scd dynamic 12345
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 convert-json dynamic
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 convert-vector dynamic
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 index-json dynamic
  ```

- **2-7.** bridge(scd/dynamic) → tea2 → convert-json → convert-vector → index-json(dynamic)
  ```bash
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner bridge scd dynamic 12345
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner tea2 12345 127.0.0.1 9000
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 convert-json dynamic
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 convert-vector dynamic
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 index-json dynamic
  ```

- **2-8.** bridge(json/static) → index-json(static)
  ```bash
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner bridge json static 12345
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 index-json static
  ```

- **2-9.** bridge(json/static) → convert-vector → index-json(static)
  ```bash
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner bridge json static 12345
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 convert-vector static
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 index-json static
  ```

- **2-10.** bridge(json/dynamic) → index-json(dynamic)
  ```bash
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner bridge json dynamic 12345
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 index-json dynamic
  ```

- **2-11.** bridge(json/dynamic) → convert-vector → index-json(dynamic)
  ```bash
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner bridge json dynamic 12345
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 convert-vector dynamic
  java -cp ${SF1_HOME}/lib/custom com.pipeline.DataPipelineRunner gateway 12345 index-json dynamic
  ```

---

## 스크립트 동작 요약

1. **bridge.sh**
   - 입력: `extension`, `mode`, `collectionId`
   - 출력: `/sf1_data/collection/{collectionId}/{extension}/{mode}/B-...` 파일

2. **tea2_util.sh**
   - 입력: `collectionId`, `listenerIP`, `port`
   - 동작: `/sf1_data/collection/{collectionId}/scd/tea_before/` 이동 후 처리
   - 출력: `/sf1_data/collection/{collectionId}/scd/tea_done/`

3. **gateway.sh**
   - 입력: `collectionId`, `operation`, `mode`
   - 출력: 각 모드별 백업 디렉토리에 결과 저장

---

## 문제 해결

- **"Usage" 에러**: 인자 수를 확인하고 올바른 순서로 입력했는지 재검토하세요.
- **스크립트 실행 권한**: `chmod +x bridge.sh tea2_util.sh gateway.sh` 로 실행 권한을 부여하세요.
