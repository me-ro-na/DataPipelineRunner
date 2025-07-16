# DataPipelineRunner


## 프로젝트 소개

`DataPipelineRunner`는 다양한 데이터 처리 스크립트를 통합하여 일관된 커맨드 흐름으로 실행할 수 있도록 도와주는 Java 기반 커맨드 실행기입니다. 다음과 같은 배치 작업을 지원

### 지원 항목
- **bridge**: 데이터 수집 작업
- **tea**: TEA 연계 작업
- **gateway**: 변환 및 인덱싱 작업
  - **convert-json**: `SCD` > `json` 컨버팅 작업
  - **convert-vector**: `json` 파일을 벡터화 작업(컬렉션 설정에 vector 컬럼이 지정되어야하고, `json`파일만 가능)
  - **index-json**: `json` 파일 인덱싱 작업
  - **index-scd**: `SCD` 파일 인덱싱 작업

---

## 요구 사항

- Java 8 이상
- Unix 계열 OS (Linux/macOS)
- 쉘 스크립트 파일 (`bridge.sh`, `tea2_util.sh`, `gateway.sh`)에 실행 권한 부여

---

## 디렉토리 구조 예시
```bash
${SF1_HOME}/
├── batch/
│   └── run_dpr.sh                 # 실행용 쉘 래퍼
├── lib/
│   └── custom/
│       ├── DataPipelineRunner.java  # 메인 실행 클래스
│       └── dpr.properties           # 설정 파일 (선택)
```

---

## 설치 및 빌드

1. 프로젝트 루트에 있는 `dpr.properties` 파일을 수정합니다. `sf1.home.dir`이나 `collection.base.dir`, `tea.homd.dir` 값을 지정해 경로를 변경할 수 있습니다.
   ```properties
   sf1.home.dir=/app/search/sf1-v7
   collection.base.dir=/sf1_data/collection/
   tea.home.dir=/app/search/tea
   ```
2. 파일들을 지정 디렉토리로 이동합니다.
    ```bash
   mkdir -p ${SF1_HOME}/lib/custom/DataPipelineRunner
   mv ./DataPipelineRunner.java ${SF1_HOME}/lib/custom/DataPipelineRunner
   mv ./dpr.properties ${SF1_HOME}/lib/custom/DataPipelineRunner
   mv ./run_dpr.sh ${SF1_HOME}/batch
    ```

3. `run_dpr.sh` 스크립트 파일은 실행 권한 부여 필요
    ```bash
   chmod +x ${SF1_HOME}/batch/run_dpr.sh
    ```

---

## 스크립트 사용법

```bash
cd ${SF1_HOME}/batch
./run_dpr.sh <command> [options]
```

### 지원 커맨드

| 커맨드    | 옵션                                  | 설명                                                       |
|-----------|---------------------------------------|------------------------------------------------------------|
| bridge    | `<extension> <mode> <collectionId>`   | SCD/JSON 생성 스크립트 실행 (`bridge.sh`)                 |
| tea       | `<collectionId> <listenerIP> <port>`  | TEA2 사전 처리 스크립트 실행 (`tea2_util.sh`)              |
| gateway   | `<collectionId> <operation> <mode> [prevStep]`   | 변환/인덱싱 스크립트 실행 (`gateway.sh`)                   |

- **extension**: `scd` 또는 `json`
- **mode**: `static` 또는 `dynamic`
  파이프라인 전체에 공통 적용되는 모드입니다.
  - `static`: 정적 수집 및 전체 인덱싱
  - `dynamic`: 동적 수집 및 증분 인덱싱

- **collectionId**: 작업 대상 컬렉션 식별자
- **listenerIP**, **port**: TEA2 유틸리티 리스너 정보
- **operation**: `convert-json`/`convert-vector`/`index-json`/`index-scd`
- **prevStep**: 이전 단계 이름. 지정하면 해당 단계의 결과 디렉터리에서 파일을
  이동한 뒤 `gateway.sh`를 실행합니다. 생략하면 파일 이동 없이 실행합니다.

### 명령어 별 옵션 사용 예시
1. bridge
   ```bash
   ./run_dpr.sh bridge scd static collection_id
    ```
2. tea
    ```bash
   ./run_dpr.sh tea collection_id 127.0.0.1 11000
    ```

3. convert-json
    ```bash
   ./run_dpr.sh gateway collection_id convert-json static [tea/bridge]
    ```

4. convert-vector
    ```bash
   ./run_dpr.sh gateway collection_id convert-vector static [bridge/convert-json]
    ```

5. index-json
    ```bash
   ./run_dpr.sh gateway collection_id index-json static [bridge/convert-json/convert-vector]
    ```

6. index-scd
    ```bash
   ./run_dpr.sh gateway collection_id index-scd static [bridge/tea]
    ```

---

## 문제 해결

- **"Usage" 에러**: 인자 수를 확인하고 올바른 순서로 입력했는지 재검토하세요.
- **스크립트 실행 권한**: `chmod +x bridge.sh tea2_util.sh gateway.sh run_dpr.sh` 로 실행 권한을 부여하세요.
