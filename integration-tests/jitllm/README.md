### How to run the integrated tests:

#### 1) Install TornadoVM with SDKMAN!:

Choose the SDK that matches your JDK version. `opencl` is shown; `cuda` and `full` work the same way.
The build resolves `io.github.beehive-lab:jitllm:1.0.2-jdk21` on JDK 21 and `1.0.2-jdk22plus` on
JDK 22 and newer; each runs on the TornadoVM 7.0.1 SDK for the same JDK line.

**JDK 21:**
```bash
sdk install java 21.0.2-open
sdk use java 21.0.2-open
sdk install tornadovm 7.0.1-jdk21-opencl
sdk use tornadovm 7.0.1-jdk21-opencl

# verify installation
tornado --devices
```

**JDK 22 and newer (tested with JDK 25):**
```bash
sdk install java 25.0.2-open
sdk use java 25.0.2-open
sdk install tornadovm 7.0.1-jdk22plus-opencl
sdk use tornadovm 7.0.1-jdk22plus-opencl

# verify installation
tornado --devices
```

Note that SDKMAN! automatically:
- Sets `TORNADOVM_HOME` environment variable to the path of the TornadoVM SDK.
- Ships the `tornado-argfile` under `$TORNADOVM_HOME`, which contains all the required JVM arguments to enable TornadoVM.
- The argfile is automatically used in Quarkus dev mode; however, in production mode, you need to manually pass the argfile to the JVM (see step 3).

#### 2) Build Quarkus-LangChain4j with JitLLM and integrated tests:

```bash
cd ~
git clone git@github.com:quarkiverse/quarkus-langchain4j.git
cd ~/quarkus-langchain4j
mvn clean install -pl integration-tests/jitllm -am -DskipTests -Dtornado
```

#### 3) Run the integrated tests:

##### 3.1 Deploy the Quarkus app:

```bash
cd ~/quarkus-langchain4j/integration-tests/jitllm
```
- For *dev* mode, run:
```
mvn quarkus:dev
```

- For *production* mode, run:
```bash
java  @$TORNADOVM_HOME/tornado-argfile \
      --add-modules jdk.incubator.vector \
      -jar target/quarkus-app/quarkus-run.jar
```
(Note: use `-Dquarkus.langchain4j.jitllm.chat-model.device-memory=<X>GB` to set the device memory if needed; a bare `-Dtornado.device.memory` is overwritten by the extension)
##### 3.2 Send requests to the Quarkus app:

when quarkus is running, open a new terminal and run:

```bash
curl http://localhost:8080/chat/blocking
curl http://localhost:8080/chat/streaming
```
