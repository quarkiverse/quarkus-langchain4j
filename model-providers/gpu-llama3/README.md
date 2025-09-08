### How to run the integrated tests:

#### 1) Install TornadoVM:

```bash
cd ~
git clone git@github.com:beehive-lab/TornadoVM.git
cd ~/TornadoVM
./bin/tornadovm-installer --jdk jdk21 --backend opencl
source setvars.sh
```

Note that the above steps:
- Set `TORNADOVM_SDK` environment variable to the path of the TornadoVM SDK.
- Create the `tornado-argfile` under `~/TornadoVM` which contains all the required JVM arguments to enable TornadoVM.
- The argfile is automatically used in Quarkus dev mode; however, in production mode, you need to manually pass the argfile to the JVM (see step 3).

#### 2) Build Quarkus-langchain4j:

```bash
cd ~
git clone git@github.com:mikepapadim/quarkus-langchain4j.git
cd ~/quarkus-langchain4j
git checkout gpu-llama3-integration
mvn clean install -DskipTests
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
java @~/TornadoVM/tornado-argfile -jar target/quarkus-app/quarkus-run.jar
```
##### 3.2 Send requests to the Quarkus app:

when quarkus is running, open a new terminal and run:

```bash
curl http://localhost:8080/chat/blocking
```

