### How to run the integrated tests:

#### 1) Install TornadoVM with SDKMAN!:

```bash
sdk install tornadovm 2.2.0-opencl

# verify installation
tonrado --devices
```

Note that SDKMAN! automatically:
- Sets `TORNADOVM_HOME` environment variable to the path of the TornadoVM SDK.
- Creates the `tornado-argfile` under `~/TornadoVM` which contains all the required JVM arguments to enable TornadoVM.
- The argfile is automatically used in Quarkus dev mode; however, in production mode, you need to manually pass the argfile to the JVM (see step 3).

#### 2) Build Quarkus-LangChain4j with GPULlama3 and integrated tests:

```bash
cd ~
git clone git@github.com:quarkiverse/quarkus-langchain4j.git
cd ~/quarkus-langchain4j
mvn clean install -pl integration-tests/gpu-llama3 -am -DskipTests -Dtornado
```

#### 3) Run the integrated tests:

##### 3.1 Deploy the Quarkus app:

```bash
cd ~/quarkus-langchain4j/integration-tests/gpullama3
```
- For *dev* mode, run:
```
mvn quarkus:dev
```

- For *production* mode, run:
```bash
java  @$TORNADOVM_HOME/tornado-argfile \
      -jar target/quarkus-app/quarkus-run.jar
```
(Note: use `-Dtornado.device.memory=<X>GB` to set the device memory if needed)
##### 3.2 Send requests to the Quarkus app:

when quarkus is running, open a new terminal and run:

```bash
curl http://localhost:8080/chat/blocking
curl http://localhost:8080/chat/streaming
```
